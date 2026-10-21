package dev.resqnet.android.transport;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionOptions;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.resqnet.mesh.routing.MeshTransport;
import dev.resqnet.mesh.crypto.SigningIdentity;

/**
 * {@link MeshTransport} backed by Google Nearby Connections.
 *
 * <p>Nearby Connections does exactly one job: it gives two devices in radio
 * range a reliable byte pipe and reports when that pipe appears and disappears.
 * It does <em>not</em> route, does <em>not</em> relay, and gives no evidence that
 * a human read anything. Every multi-hop behaviour lives in {@code mesh-core}
 * above this class, which is exactly why the simulator can substitute a
 * different radio without changing a single routing decision.
 *
 * <h3>Identity versus endpoint</h3>
 *
 * <p>A Nearby endpoint id is a per-session radio handle. It rotates on reboot,
 * on a switch between Bluetooth and Wi-Fi Direct, and whenever the OS decides it
 * should. Treating it as an identity would mean the same person is a different
 * person tomorrow and that every authorisation decision is wrong after a
 * reboot.
 *
 * <p>So the mapping is one-way and explicit:
 *
 * <pre>
 *   endpointId (session-scoped, from Nearby)  -&gt;  deviceId (key-derived, stable)
 * </pre>
 *
 * <p>A stable binding is recorded only after the endpoint answers a fresh
 * challenge with a signature from the claimed key. Until then the endpoint id
 * stands in. This proof authenticates the link's key holder; the trust store
 * separately records whether a human has verified that key.
 *
 * <h3>Connection policy</h3>
 *
 * <p>Connections are requested for every discovered endpoint and incoming
 * requests are accepted automatically. That is deliberate. In an emergency the
 * person holding the other phone cannot be expected to find and tap a dialog,
 * and a dialog that appears while someone is calling for help is a dialog that
 * gets dismissed. The property that actually matters is not who may connect on
 * the radio, it is who may <em>send trusted content</em>, and that is enforced
 * by signature verification against a user-verified key inside the router.
 *
 * <h3>Strategy</h3>
 *
 * <p>{@link Strategy#P2P_CLUSTER} rather than the star topology. In a disaster
 * the relevant neighbours are the people in sight, and a star routes everything
 * through one device that may be the one with the lowest battery. P2P cluster
 * lets phones connect directly wherever the radio allows and leaves the choice
 * of who relays to the routing layer.
 */
public final class NearbyTransport implements MeshTransport {

    private static final String TAG = "ResQNetTransport";

    /** Discovery namespace. Scoped to this project so it cannot collide. */
    private static final String SERVICE_ID = "resqnet-mesh-v1";

    /**
     * Largest frame this transport will hand to Nearby.
     *
     * <p>This is ResQNet's own limit, not the platform's. The real ceiling is
     * {@code Connections.MAX_BYTES_DATA_SIZE}, about 1 MiB, so the guard here is
     * roughly sixty times more conservative than necessary.
     *
     * <p>That is intentional and is enforced anyway. A frame this size is
     * already larger than an SOS needs to be, and the envelope layer caps an
     * encoded message at 16 KiB, so anything above that is a bug rather than a
     * large message. Checking it at the transport means a bug is caught here
     * with a clear reason instead of surfacing as an opaque Nearby rejection.
     */
    private static final long NEARBY_MAX_PAYLOAD_BYTES = 16 * 1024L;

    private static final AtomicLong TOKEN_SEQUENCE = new AtomicLong(1L);

    private final Context context;
    private final byte[] localEndpointInfo;
    private final String localDeviceId;
    private final String localDisplayName;
    private final Listener listener;

    private final Object lock = new Object();

    /** Endpoint id to stable device id. TreeMap keeps iteration deterministic. */
    /**
     * Endpoint to identity mapping.
     *
     * <p>Held in its own class so the promotion rules can be tested without a
     * ConnectionsClient. That is where the real defect lived: nothing ever
     * promoted a binding, so the router compared key-derived device ids against
     * a set of radio handles and never found a match.
     */
    private final PeerBindings bindings = new PeerBindings();
    private final LinkIdentityProof identityProof;
    private volatile java.util.function.Predicate<String> bindingApproval = id -> false;

    private volatile InboundHandler inboundHandler = (peer, payload) -> {
    };
    private volatile ConnectionListener connectionListener = new ConnectionListener() {
    };
    /** Endpoint ids discovery has reported, connected or not. */
    private final java.util.Set<String> discovered =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private ConnectionsClient client;
    private boolean started;
    private volatile boolean ready;

    /** Observability hook for the nearby devices screen. */
    public interface Listener {

        /** Discovery or advertising failed; the mesh is degraded, not dead. */
        void onTransportError(String operation, String message);

        /** A peer connection appeared or disappeared. */
        void onPeerStateChanged(String deviceId, boolean connected);
    }

    public NearbyTransport(@NonNull Context context, SigningIdentity identity,
                           String localDisplayName, @NonNull Listener listener) {
        this.context = context.getApplicationContext();
        this.localDeviceId = identity.deviceId();
        this.localDisplayName = localDisplayName;
        this.listener = listener;
        this.localEndpointInfo = (localDeviceId + "|" + localDisplayName)
                .getBytes(StandardCharsets.UTF_8);
        this.identityProof = new LinkIdentityProof(identity,
                () -> System.nanoTime() / 1_000_000L);
    }

    /** Consulted again when proof completes, so a newly blocked key cannot bind. */
    public void setBindingApproval(java.util.function.Predicate<String> approval) {
        bindingApproval = java.util.Objects.requireNonNull(approval);
    }

    /** Challenge a direct provisional neighbour; relayed announcements cannot bind. */
    public void requestBindingProof(String endpointId, String deviceId, PublicKey key) {
        byte[] challenge;
        synchronized (lock) {
            if (!endpointId.equals(bindings.resolve(endpointId)) || client == null) {
                return;
            }
            challenge = identityProof.challenge(endpointId, deviceId, key);
        }
        if (challenge != null) {
            client.sendPayload(endpointId, Payload.fromBytes(challenge))
                    .addOnFailureListener(error -> reportFailure("identity-proof",
                            "challenge failed for " + endpointId));
        }
    }

    /**
     * Whether the mesh has finished starting.
     *
     * <p>The nearby devices screen uses this to separate "no peers nearby" from
     * "not ready yet", which are very different things to tell someone who is
     * deciding whether the device works.
     */
    public boolean isReady() {
        return ready && started;
    }

    @Override
    public Optional<Long> send(String peerDeviceId, byte[] encodedPayload) {
        if (!started || client == null || encodedPayload.length > NEARBY_MAX_PAYLOAD_BYTES) {
            return Optional.empty();
        }
        String endpointId = bindings.endpointFor(peerDeviceId);
        if (endpointId == null) {
            return Optional.empty();
        }
        try {
            long token = TOKEN_SEQUENCE.getAndIncrement();
            Task<Void> task = client.sendPayload(endpointId,
                    Payload.fromBytes(encodedPayload));
            // The transfer outcome is observed rather than discarded.
            //
            // send() returned a token the moment the payload was handed over,
            // and nothing ever looked at whether the radio delivered it. The
            // router records SENT_TO_PEER, increments its metrics and schedules
            // the next attempt on the strength of that. On a phone link that
            // was dropping frames the UI would show "sent" indefinitely with no
            // error anywhere, which is the worst possible failure for a system
            // whose only product is an honest answer about delivery.
            task.addOnFailureListener(error ->
                    reportFailure("payload", "transfer to " + endpointId
                            + " failed: " + error.getMessage()));
            task.addOnSuccessListener(ignored -> { });
            return Optional.of(token);
        } catch (SecurityException e) {
            reportError("send", e);
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a stable sorted view. The router relies on this order when the
     * fan-out cap selects which peers receive a copy, so an implementation backed
     * by a hash set would make forwarding unreproducible.
     */
    @Override
    public Set<String> connectedPeerIds() {
        // No outer lock: PeerBindings synchronises its own state, and nesting
        // the two would make the invariant look like it spans two objects when
        // it does not.
        return bindings.connectedPeerIds();
    }

    @Override
    public void setInboundHandler(InboundHandler handler) {
        this.inboundHandler = handler;
    }

    @Override
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
        }
        try {
            client = Nearby.getConnectionsClient(context);
        } catch (Throwable t) {
            // Play Services may be missing, disabled or too old. The mesh cannot
            // run without it and the UI has to say so rather than look healthy.
            Log.e(TAG, "Play Services unavailable", t);
            listener.onTransportError("client", "Google Play services unavailable on this device");
            return;
        }
        if (client == null) {
            listener.onTransportError("client", "Nearby Connections client unavailable");
            return;
        }
        startAdvertising();
        startDiscovery();
        ready = true;
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (!started) {
                return;
            }
            started = false;
            ready = false;
            bindings.clearAll();
            identityProof.clear();
        }
        if (client == null) {
            return;
        }
        try {
            client.stopAllEndpoints();
            client.stopDiscovery();
            client.stopAdvertising();
        } catch (SecurityException e) {
            Log.w(TAG, "Error while stopping the Nearby client", e);
        } finally {
            client = null;
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    // ------------------------------------------------------------- advertising

    private void startAdvertising() {
        try {
            client.startAdvertising(localEndpointInfo, SERVICE_ID, lifecycleCallback,
                            new AdvertisingOptions.Builder()
                                    .setStrategy(Strategy.P2P_CLUSTER)
                                    .setDisruptiveUpgrade(true)
                                    .build())
                    .addOnFailureListener(failure -> listener.onTransportError("advertising",
                            "This device could not advertise: " + failure.getMessage()));
        } catch (SecurityException e) {
            reportError("advertising", e);
        }
    }

    private void startDiscovery() {
        try {
            client.startDiscovery(SERVICE_ID,
                            new EndpointDiscoveryCallback() {
                                @Override
                                public void onEndpointFound(String endpointId,
                                                            DiscoveredEndpointInfo info) {
                                    bindings.markDiscovered(endpointId);
                                    requestConnection(endpointId);
                                }

                                @Override
                                public void onEndpointLost(String endpointId) {
                                    unbind(endpointId);
                                }
                            },
                            new DiscoveryOptions.Builder()
                                    .setStrategy(Strategy.P2P_CLUSTER)
                                    .build())
                    .addOnFailureListener(failure -> listener.onTransportError("discovery",
                            "This device could not discover: " + failure.getMessage()));
        } catch (SecurityException e) {
            reportError("discovery", e);
        }
    }

    /**
     * Opens a connection to a discovered endpoint, at most once per session.
     *
     * <p>Under {@link Strategy#P2P_CLUSTER} the discovering side is responsible
     * for requesting connections; Nearby does not connect discovered peers on its
     * own.
     */
    private void requestConnection(String endpointId) {
        if (client == null || endpointId == null) {
            return;
        }
        synchronized (lock) {
            if (!bindings.markRequested(endpointId)) {
                return;
            }
        }
        try {
            client.requestConnection(localEndpointInfo, endpointId, lifecycleCallback,
                            new ConnectionOptions.Builder()
                                    .setDisruptiveUpgrade(true)
                                    .build())
                    .addOnFailureListener(failure -> {
                        synchronized (lock) {
                            bindings.clearRequested(endpointId);
                        }
                        listener.onTransportError("connection",
                                "Could not reach " + endpointId + ": " + failure.getMessage());
                    });
        } catch (SecurityException e) {
            reportError("requestConnection", e);
        }
    }

    /** Shared lifecycle for both incoming and outgoing connections. */
    private final ConnectionLifecycleCallback lifecycleCallback =
            new ConnectionLifecycleCallback() {

                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo info) {
                    // Someone wants to talk to us. Accepting is automatic on
                    // purpose; see the class comment.
                    if (client == null) {
                        return;
                    }
                    try {
                        client.acceptConnection(endpointId, payloadCallback);
                    } catch (SecurityException e) {
                        reportError("acceptConnection", e);
                    }
                }

                @Override
                public void onConnectionResult(String endpointId,
                                               ConnectionResolution resolution) {
                    int code = resolution.getStatus().getStatusCode();
                    if (code == ConnectionsStatusCodes.STATUS_OK) {
                        bind(endpointId);
                    } else {
                        listener.onTransportError("connection",
                                endpointId + ": " + resolution.getStatus().getStatusMessage());
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    unbind(endpointId);
                }
            };

    private final PayloadCallback payloadCallback = new PayloadCallback() {

        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES) {
                return;
            }
            byte[] bytes = payload.asBytes();
            if (bytes == null || bytes.length == 0) {
                return;
            }
            try {
                if (LinkIdentityProof.isProofFrame(bytes)) {
                    LinkIdentityProof.Result result;
                    synchronized (lock) {
                        result = identityProof.receive(endpointId, bytes);
                        if (result.verifiedDeviceId() != null
                                && bindingApproval.test(result.verifiedDeviceId())) {
                            promoteBinding(endpointId, result.verifiedDeviceId());
                        }
                    }
                    if (result.reply() != null && client != null) {
                        client.sendPayload(endpointId, Payload.fromBytes(result.reply()))
                                .addOnFailureListener(error -> reportFailure("identity-proof",
                                        "response failed for " + endpointId));
                    }
                    return;
                }
                inboundHandler.onPayload(resolvePeerIdentity(endpointId), bytes);
            } catch (RuntimeException e) {
                Log.e(TAG, "Inbound payload handling failed", e);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId,
                                            com.google.android.gms.nearby.connection
                                                    .PayloadTransferUpdate update) {
            // Per-chunk progress stays unlogged: a frame is 16 KB and arrives
            // in several chunks, so logging would bury the errors. The
            // pass/fail outcome is handled where it matters, on the Task from
            // sendPayload above.
            if (update.getStatus() == com.google.android.gms.nearby.connection
                    .PayloadTransferUpdate.Status.FAILURE) {
                reportFailure("payload", "transfer to " + endpointId + " failed");
            }
        }
    };

    // ----------------------------------------------------------------- binding

    private void bind(String endpointId) {
        // Provisional binding under the endpoint id. The router refuses
        // traffic from an origin it has no key for, so this exists only so
        // the refusal names something concrete, and so the peer appears in a
        // diagnostics screen while it waits to be verified.
        String resolved;
        synchronized (lock) {
            resolved = bindings.bind(endpointId);
            identityProof.connected(endpointId);
        }
        connectionListener.onPeerConnected(resolved);
        listener.onPeerStateChanged(resolved, true);
    }

    /**
     * Promotes a provisional endpoint binding to a proved stable identity.
     *
     * <p>The key may still be UNVERIFIED by the user; this only establishes
     * possession on the current link.
     */
    private void promoteBinding(String endpointId, String verifiedDeviceId) {
        String previous = bindings.resolve(endpointId);
        if (!bindings.promote(endpointId, verifiedDeviceId)) {
            return;
        }
        if (!previous.equals(verifiedDeviceId)) {
            connectionListener.onPeerDisconnected(previous);
            connectionListener.onPeerConnected(verifiedDeviceId);
        }
        listener.onPeerStateChanged(verifiedDeviceId, true);
    }

    private void unbind(String endpointId) {
        String deviceId;
        synchronized (lock) {
            identityProof.disconnected(endpointId);
            deviceId = bindings.unbind(endpointId);
        }
        if (deviceId == null) {
            return;
        }
        connectionListener.onPeerDisconnected(deviceId);
        listener.onPeerStateChanged(deviceId, false);
    }

    private String resolvePeerIdentity(String endpointId) {
        return bindings.resolve(endpointId);
    }

    /** A runtime permission was refused. */
    private void reportError(String operation, SecurityException failure) {
        Log.w(TAG, operation + " failed: missing runtime permission", failure);
        listener.onTransportError(operation, "Missing permission: " + failure.getMessage());
    }

    /**
     * The radio refused the operation for a reason that is not a permission.
     *
     * <p>Separated from the overload above because "missing permission" is a
     * fixable configuration problem and "transfer failed" is a field problem,
     * and a responder staring at a phone needs to be able to tell them apart.
     */
    private void reportFailure(String operation, String message) {
        Log.w(TAG, operation + " failed: " + message);
        listener.onTransportError(operation, message);
    }

    /**
     * Endpoints discovery currently knows about, connected or not.
     *
     * <p>Added for the diagnostics screen. "Discovered but not connected"
     * and "never discovered" are different failures with different causes:
     * the first is a permission or a cluster-owner problem, the second is
     * advertising. Telling them apart from the existing bindings map alone is
     * not possible, because a binding only appears once a connection succeeds.
     */
    public java.util.List<String> discoveredEndpoints() {
        java.util.List<String> found = new java.util.ArrayList<>(discovered);
        java.util.Collections.sort(found);
        return found;
    }

    /** Current endpoint to device bindings, for the nearby devices screen. */
    public Map<String, String> bindings() {
        return bindings.snapshot();
    }

    /** This device's stable identity, used by the UI during verification. */
    public String localDeviceId() {
        return localDeviceId;
    }

    /** This device's advertised label. */
    public String localDisplayName() {
        return localDisplayName;
    }

    /** Whether the OS version requires the Android 12+ split Bluetooth permissions. */
    public static boolean requiresSplitBluetoothPermissions() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** Maximum bytes Nearby Connections will carry in one payload. */
    public static long maxPayloadBytes() {
        return NEARBY_MAX_PAYLOAD_BYTES;
    }
}
