package dev.resqnet.android.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.model.EmergencyPayload;
import dev.resqnet.android.data.SqliteMessageStore;
import dev.resqnet.android.identity.KeystoreIdentity;
import dev.resqnet.android.transport.NearbyTransport;
import dev.resqnet.mesh.crypto.SigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.DuplicateCache;
import dev.resqnet.mesh.routing.IdentityAnnouncement;
import dev.resqnet.mesh.routing.MeshEvent;
import dev.resqnet.mesh.routing.MeshEventListener;
import dev.resqnet.mesh.routing.MeshRouter;
import dev.resqnet.mesh.routing.RouterConfig;
import dev.resqnet.mesh.routing.RoutingMetrics;
import dev.resqnet.mesh.triage.KeywordTriageClassifier;

/**
 * Composition root for the handset mesh.
 *
 * <p>Owns the five long-lived pieces and nothing else: the device identity, the
 * trust store, the durable queue, the radio and the router. Everything that makes
 * ResQNet work is delegated to {@code mesh-core}; this class exists to wire
 * Android's lifetimes to those of the engine, and to translate engine events
 * into something the UI can render.
 *
 * <p>Threading contract:
 * <ul>
 *   <li>Every engine call happens on {@link #executor}, a single background
 *       thread. The router is thread safe, but a single writer keeps the SQLite
 *       queue and the dedup cache from contending, and keeps the event order in
 *       the UI meaningful.</li>
 *   <li>Callbacks into the UI are delivered on the main thread.</li>
 * </ul>
 */
public final class MeshEngine {

    private static final String TAG = "ResQNetEngine";

    /** How often the retry queue is swept while the mesh is idle. */
    private static final long IDLE_SWEEP_INTERVAL_MILLIS = 5_000L;

    private final Context context;
    private final SigningIdentity identity;
    private final TrustStore trustStore;
    private final ResQNetDatabase database;
    private final SqliteMessageStore store;
    private final NearbyTransport transport;
    private final MeshRouter router;
    private final ExecutorService executor;
    private final List<EngineListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public MeshEngine(@NonNull Context context, @NonNull String displayName)
            throws GeneralSecurityException, java.io.IOException {
        this.context = context.getApplicationContext();
        this.identity = KeystoreIdentity.loadOrCreate(displayName);
        this.database = ResQNetDatabase.get(this.context);
        this.store = new SqliteMessageStore(database, 512,
                (messageId, previous, current, detail) ->
                        database.appendDelivery(messageId, current, detail,
                                System.currentTimeMillis(), identity.deviceId()));
        this.trustStore = new TrustStore(new SQLiteTrustPersistence(database), 256);
        this.transport = new NearbyTransport(this.context, identity.deviceId(),
                displayName, new NearbyTransport.Listener() {
            @Override
            public void onTransportError(String operation, String message) {
                publish(new EngineStatus("transport-error", operation + ": " + message));
            }

            @Override
            public void onPeerStateChanged(String deviceId, boolean connected) {
                publish(new EngineStatus(connected ? "peer-up" : "peer-down", deviceId));
            }
        });

        RouterConfig config = RouterConfig.builder()
                // Six hops reaches a school-sized site. Beyond that, a message
                // is travelling further than a phone battery can carry.
                .hopLimit(6)
                .lifetimeMillis(30 * 60 * 1000L)
                .maxPendingMessages(512)
                .maxRelayMessages(384)
                .maxFanOut(6)
                .acknowledgementsEnabled(true)
                .build();

        this.router = new MeshRouter(identity, trustStore, store, transport,
                dev.resqnet.mesh.routing.MeshClock.system(), config,
                new KeywordTriageClassifier(),
                new DuplicateCache(2048, 6 * 60 * 60 * 1000L, 8 * 1024 * 1024),
                dev.resqnet.mesh.routing.RetryPolicy.defaultMobile());

        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "resqnet-mesh");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });

        router.addEventListener(this::handleMeshEvent);
    }

    // ------------------------------------------------------------- lifecycle

    /** Starts the radio and begins sweeping the retry queue. Idempotent. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            transport.start();
            router.attemptPending();
            publish(new EngineStatus("started", transport.isStarted()
                    ? "mesh running" : "mesh degraded: radio unavailable"));
        });
        executor.execute(this::sweepLoop);
    }

    /** Stops the radio. The durable queue is untouched. Idempotent. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        executor.execute(() -> {
            transport.stop();
            publish(new EngineStatus("stopped", "mesh stopped"));
        });
    }

    /** Re-runs the retry pass now, e.g. when the user returns to the app. */
    public void nudge() {
        executor.execute(router::attemptPending);
    }

    private void sweepLoop() {
        while (running.get()) {
            try {
                Thread.sleep(IDLE_SWEEP_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running.get()) {
                return;
            }
            try {
                store.expireStale(System.currentTimeMillis());
                router.attemptPending();
            } catch (RuntimeException e) {
                Log.e(TAG, "Retry sweep failed", e);
            }
        }
    }

    /** Releases long-lived resources. The process must be shutting down. */
    public void close() {
        stop();
        executor.execute(transport::stop);
        executor.shutdownNow();
        router.close();
    }

    // --------------------------------------------------------------- messaging

    /**
     * Composes an SOS and hands it to the mesh.
     *
     * <p>Runs on the background executor, so the caller is never blocked on the
     * radio or on SQLite.
     */
    public void sendSos(String body, String destinationDeviceId, boolean attachLocation,
                        double latitude, double longitude) throws Exception {
        executor.execute(() -> {
            try {
                MessageEnvelope envelope = router.originate(destinationDeviceId,
                        MessageType.SOS, Priority.SOS,
                        EmergencyPayload.encode(body, attachLocation, latitude, longitude),
                        0L);
                publish(new EngineStatus("sent", envelope.messageId()));
            } catch (GeneralSecurityException | dev.resqnet.mesh.protocol.ProtocolException e) {
                Log.e(TAG, "Could not send SOS", e);
                publish(new EngineStatus("send-failed", String.valueOf(e.getMessage())));
            }
        });
    }

    /** Sends an ordinary text message, at lower priority. */
    public void sendText(String body, String destinationDeviceId) throws Exception {
        executor.execute(() -> {
            try {
                router.originate(destinationDeviceId, MessageType.TEXT, Priority.NORMAL,
                        EmergencyPayload.encode(body, false, 0, 0), 0L);
            } catch (GeneralSecurityException | dev.resqnet.mesh.protocol.ProtocolException e) {
                Log.e(TAG, "Could not send text", e);
            }
        });
    }

    /** Broadcasts this device's key claim so neighbours can prompt for verification. */
    public void publishIdentity() {
        executor.execute(() -> {
            Optional<MessageEnvelope> claim = router.publishIdentity();
            claim.ifPresent(envelope -> {
                try {
                    byte[] encoded = EnvelopeCodec.encode(envelope);
                    for (String peer : transport.connectedPeerIds()) {
                        transport.send(peer, encoded);
                    }
                } catch (dev.resqnet.mesh.protocol.ProtocolException e) {
                    Log.w(TAG, "Could not publish identity claim", e);
                }
            });
        });
    }

    /**
     * Records a peer key the user has confirmed in person.
     *
     * @return the resulting trust entry, or empty when the key was rejected
     */
    public Optional<TrustStore.Entry> acceptPeer(String label, String publicKeyBase64) {
        try {
            PublicKey publicKey = dev.resqnet.mesh.crypto.DeviceIdentity
                    .decodePublicKey(publicKeyBase64);
            TrustStore.Entry entry = executor.submit(
                    () -> router.acceptPeerKey(label, publicKey)).get();
            return Optional.of(entry);
        } catch (Exception e) {
            Log.w(TAG, "Rejected peer key", e);
            return Optional.empty();
        }
    }

    /** Revokes a peer everywhere it is known. */
    public void blockPeer(String deviceId) {
        executor.execute(() -> {
            trustStore.block(deviceId);
            publish(new EngineStatus("peer-blocked", deviceId));
        });
    }

    // ---------------------------------------------------------------- reading

    public RoutingMetrics metrics() {
        return router.metrics();
    }

    public MeshRouter router() {
        return router;
    }

    public SqliteMessageStore store() {
        return store;
    }

    public ResQNetDatabase database() {
        return database;
    }

    public TrustStore trustStore() {
        return trustStore;
    }

    public NearbyTransport transport() {
        return transport;
    }

    public SigningIdentity identity() {
        return identity;
    }

    public String deviceId() {
        return identity.deviceId();
    }

    /** Short code for this device, shown during in-person verification. */
    public String shortCode() {
        return IdentityAnnouncement.shortCode(identity.keyFingerprint());
    }

    // -------------------------------------------------------------- observers

    /** UI-facing status change. Always delivered on the main thread. */
    public record EngineStatus(String kind, String detail) {
    }

    /** Listener implemented by activities. */
    public interface EngineListener {
        void onStatus(EngineStatus status);

        void onMessageDelivered(MessageEnvelope envelope, int hopCount);

        void onQueueChanged();
    }

    public void addListener(EngineListener listener) {
        listeners.add(listener);
    }

    public void removeListener(EngineListener listener) {
        listeners.remove(listener);
    }

    private void publish(EngineStatus status) {
        for (EngineListener listener : listeners) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> listener.onStatus(status));
        }
    }

    private void handleMeshEvent(MeshEvent event) {
        if (event instanceof dev.resqnet.mesh.routing.MeshEvent.Delivered delivered) {
            MessageEnvelope envelope = delivered.envelope();
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> {
                        for (EngineListener listener : listeners) {
                            listener.onMessageDelivered(envelope, envelope.hopCount());
                        }
                    });
            database.saveTriage(envelope.messageId(),
                    TriageHintCache.get(envelope.messageId()),
                    TriageHintCache.confidence(envelope.messageId()));
        }
        if (event instanceof dev.resqnet.mesh.routing.MeshEvent.TriageSuggestion suggestion) {
            TriageHintCache.put(suggestion.messageId(), suggestion.result());
        }
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> {
                    for (EngineListener listener : listeners) {
                        listener.onQueueChanged();
                    }
                });
    }

    /** Small in-memory cache of the latest advisory classification per message. */
    private static final class TriageHintCache {
        private static final java.util.Map<String, dev.resqnet.mesh.triage.TriageResult> CACHE =
                new java.util.concurrent.ConcurrentHashMap<>();

        static void put(String messageId, dev.resqnet.mesh.triage.TriageResult result) {
            CACHE.put(messageId, result);
        }

        static String get(String messageId) {
            dev.resqnet.mesh.triage.TriageResult result = CACHE.get(messageId);
            return result == null ? "UNKNOWN" : result.category().name();
        }

        static double confidence(String messageId) {
            dev.resqnet.mesh.triage.TriageResult result = CACHE.get(messageId);
            return result == null ? 0 : result.confidence();
        }
    }

    /** Persists trust entries to SQLite. */
    private static final class SQLiteTrustPersistence implements TrustStore.Persistence {

        private final ResQNetDatabase database;

        SQLiteTrustPersistence(ResQNetDatabase database) {
            this.database = database;
        }

        @Override
        public void save(TrustStore.Entry entry) {
            database.savePeer(entry);
        }

        @Override
        public java.util.Collection<TrustStore.Entry> loadAll() {
            return database.loadPeers();
        }

        @Override
        public void remove(String deviceId) {
            database.deletePeer(deviceId);
        }
    }

    /** Convenience for the settings screen. */
    public Instant now() {
        return Instant.ofEpochMilli(System.currentTimeMillis());
    }

    /** Human-readable device label used in lists. */
    public String displayName() {
        return identity.displayName();
    }

    /** Package-private access to the context for gateway sync. */
    Context appContext() {
        return context;
    }

    /** Listeners snapshot, used by tests. */
    public List<EngineListener> listeners() {
        return List.copyOf(listeners);
    }
}