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
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.resqnet.mesh.routing.MeshTransport;

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
 * <p>A binding is only recorded once a signed envelope has arrived and the router
 * has verified the origin against a key the owner confirmed in person. Until
 * then the endpoint id stands in, so the router refuses the traffic with a
 * specific reason rather than misrouting it. A rebind after an endpoint rotation
 * therefore requires re-verification rather than being accepted silently.
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

    /** Nearby Connections caps a single payload at 16 KiB. */
    private static final long NEARBY_MAX_PAYLOAD_BYTES = 16 * 1024L;

    private static final AtomicLong TOKEN_SEQUENCE = new AtomicLong(1L);

    private final Context context;
    private final byte[] localEndpointInfo;
    private final String localDeviceId;
    private final String localDisplayName;
    private final Listener listener;

    private final Object lock = new Object();

    /** Endpoint id to stable device id. TreeMap keeps iteration deterministic. */
    private final Map<String, String> deviceIdByEndpoint = new TreeMap<>();
    private final Map<String, String> endpointByDeviceId = new ConcurrentHashMap<>();
    private final Set<String> endpointsRequested = new TreeSet<>();

    private volatile InboundHandler inboundHandler = (peer, payload) -> {
    };
    private volatile ConnectionListener connectionListener = new ConnectionListener() {
    };

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

    public NearbyTransport(@NonNull Context context, String localDeviceId,
                           String localDisplayName, @NonNull Listener listener) {
        this.context = context.getApplicationContext();
        this.localDeviceId = localDeviceId;
        this.localDisplayName = localDisplayName;
        this.listener = listener;
        this.localEndpointInfo = (localDeviceId + "|" + localDisplayName)
                .getBytes(StandardCharsets.UTF_8);
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
        String endpointId = endpointByDeviceId.get(peerDeviceId);
        if (endpointId == null) {
            return Optional.empty();
        }
        try {
            client.sendPayload(endpointId, Payload.fromBytes(encodedPayload));
            return Optional.of(TOKEN_SEQUENCE.getAndIncrement());
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
        synchronized (lock) {
            return Collections.unmodifiableSet(new TreeSet<>(endpointByDeviceId.keySet()));
        }
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
            deviceIdByEndpoint.clear();
            endpointByDeviceId.clear();
            endpointsRequested.clear();
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
            if (!endpointsRequested.add(endpointId)) {
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
                            endpointsRequested.remove(endpointId);
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
                inboundHandler.onPayload(resolvePeerIdentity(endpointId), bytes);
            } catch (RuntimeException e) {
                Log.e(TAG, "Inbound payload handling failed", e);
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId,
                                            com.google.android.gms.nearby.connection
                                                    .PayloadTransferUpdate update) {
            // Transfer progress is reported by the engine through delivery state
            // transitions; logging every chunk would be noise.
        }
    };

    // ----------------------------------------------------------------- binding

    private void bind(String endpointId) {
        String resolved;
        synchronized (lock) {
            String existing = deviceIdByEndpoint.get(endpointId);
            if (existing == null) {
                // Provisional binding under the endpoint id. The router will
                // refuse traffic from an unknown origin; this exists only so the
                // refusal names something concrete.
                deviceIdByEndpoint.put(endpointId, endpointId);
                endpointByDeviceId.put(endpointId, endpointId);
            }
            resolved = deviceIdByEndpoint.get(endpointId);
        }
        connectionListener.onPeerConnected(resolved);
        listener.onPeerStateChanged(resolved, true);
    }

    /**
     * Promotes a provisional endpoint binding to a verified stable identity.
     *
     * <p>Called once the router has authenticated a signed envelope from the
     * peer and the owner has confirmed the fingerprint.
     */
    public void promoteBinding(String endpointId, String verifiedDeviceId) {
        synchronized (lock) {
            String current = deviceIdByEndpoint.get(endpointId);
            if (current != null && current.equals(verifiedDeviceId)) {
                return;
            }
            deviceIdByEndpoint.put(endpointId, verifiedDeviceId);
            endpointByDeviceId.put(verifiedDeviceId, endpointId);
            endpointByDeviceId.remove(endpointId);
        }
        connectionListener.onPeerDisconnected(endpointId);
        connectionListener.onPeerConnected(verifiedDeviceId);
        listener.onPeerStateChanged(verifiedDeviceId, true);
    }

    private void unbind(String endpointId) {
        String deviceId;
        synchronized (lock) {
            deviceId = deviceIdByEndpoint.remove(endpointId);
            endpointByDeviceId.values().remove(endpointId);
            endpointsRequested.remove(endpointId);
        }
        if (deviceId != null) {
            connectionListener.onPeerDisconnected(deviceId);
            listener.onPeerStateChanged(deviceId, false);
        }
    }

    private String resolvePeerIdentity(String endpointId) {
        synchronized (lock) {
            String known = deviceIdByEndpoint.get(endpointId);
            return known != null ? known : endpointId;
        }
    }

    private void reportError(String operation, SecurityException failure) {
        Log.w(TAG, operation + " failed: missing runtime permission", failure);
        listener.onTransportError(operation, "Missing permission: " + failure.getMessage());
    }

    /** Current endpoint to device bindings, for the nearby devices screen. */
    public Map<String, String> bindings() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new TreeMap<>(deviceIdByEndpoint));
        }
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