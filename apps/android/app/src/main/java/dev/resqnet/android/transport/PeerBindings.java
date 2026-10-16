package dev.resqnet.android.transport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The mapping from a Nearby endpoint id to a device identity.
 *
 * <h2>Why this is separate</h2>
 *
 * <p>It is the piece that was silently wrong, and it was wrong in a way that no
 * test could see because it lived inside a class that needs a real
 * {@code ConnectionsClient} to construct. It is pure logic over two maps, so
 * it lives on its own and is tested directly.
 *
 * <h2>What went wrong</h2>
 *
 * <p>Nothing ever promoted a binding. A connection was recorded as
 * endpoint-to-endpoint, so {@code connectedPeerIds()} returned radio handles
 * while {@code MeshRouter} compared that set against key-derived device ids.
 * The comparison never matched, so a directed message never preferred its
 * addressee and flooded to every peer instead. Nothing crashed and nothing
 * looked wrong: the message still arrived, one hop later than it should have,
 * by a path that cost every neighbour's battery on the way.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Nearby delivers its callbacks on binder threads and the router runs on the
 * engine's single executor, so this is read from one thread and written from
 * another. Synchronised throughout rather than using a concurrent map, because
 * the invariant spans two maps and a per-map atomic update would let a
 * half-promoted pair be observed.
 */
public final class PeerBindings {

    private final Map<String, String> deviceIdByEndpoint = new LinkedHashMap<>();
    private final Map<String, String> endpointByDeviceId = new TreeMap<>();
    private final Set<String> discovered = new java.util.LinkedHashSet<>();
    private final Set<String> endpointsRequested = new java.util.LinkedHashSet<>();

    /**
     * Records a newly connected endpoint, provisionally mapped to itself.
     *
     * <p>Provisional because the radio handle is all that is known at this
     * point. {@link #promote} replaces it once a signed identity claim has been
     * verified.
     *
     * @return the identity the caller should treat as the peer, which is the
     *         endpoint id until the binding is promoted
     */
    public synchronized String bind(String endpointId) {
        String existing = deviceIdByEndpoint.get(endpointId);
        if (existing == null) {
            deviceIdByEndpoint.put(endpointId, endpointId);
            endpointByDeviceId.put(endpointId, endpointId);
        }
        return deviceIdByEndpoint.get(endpointId);
    }

    /**
     * Replaces a provisional binding with a verified device identity.
     *
     * @return true when the mapping actually changed
     */
    public synchronized boolean promote(String endpointId, String verifiedDeviceId) {
        String current = deviceIdByEndpoint.get(endpointId);
        if (current != null && current.equals(verifiedDeviceId)) {
            return false;
        }
        deviceIdByEndpoint.put(endpointId, verifiedDeviceId);
        // Drop the provisional reverse entry before adding the real one, so a
        // device that is later reached through a different endpoint does not
        // accumulate stale handles.
        if (current != null) {
            endpointByDeviceId.remove(current);
        }
        endpointByDeviceId.put(verifiedDeviceId, endpointId);
        return true;
    }

    /**
     * Forgets an endpoint.
     *
     * @return the identity that was bound, so the caller can tell the router
     *         which peer disappeared
     */
    public synchronized String unbind(String endpointId) {
        discovered.remove(endpointId);
        endpointsRequested.remove(endpointId);
        String deviceId = deviceIdByEndpoint.remove(endpointId);
        endpointByDeviceId.remove(endpointId);
        if (deviceId != null) {
            // Only clear the reverse entry if it still points here. A promoted
            // binding shares its endpoint with exactly one device, but an
            // unbind racing a promote must not unbind the new one.
            if (endpointId.equals(endpointByDeviceId.get(deviceId))) {
                endpointByDeviceId.remove(deviceId);
            }
        }
        return deviceId;
    }

    /** The identity to use for an inbound payload from this endpoint. */
    public synchronized String resolve(String endpointId) {
        String known = deviceIdByEndpoint.get(endpointId);
        return known != null ? known : endpointId;
    }

    /** The endpoint to send to for a device identity, or null when unbound. */
    public synchronized String endpointFor(String deviceId) {
        return endpointByDeviceId.get(deviceId);
    }

    /** Every peer this device can currently reach, as device identities. */
    public synchronized Set<String> connectedPeerIds() {
        return Set.copyOf(deviceIdByEndpoint.values());
    }

    /** Endpoints discovery has reported, in a stable order for display. */
    public synchronized List<String> discoveredEndpoints() {
        List<String> sorted = new java.util.ArrayList<>(discovered);
        java.util.Collections.sort(sorted);
        return sorted;
    }

    public synchronized void markDiscovered(String endpointId) {
        discovered.add(endpointId);
    }

    /** Marks a connection as requested, so it is not requested twice. */
    public synchronized boolean markRequested(String endpointId) {
        return endpointsRequested.add(endpointId);
    }

    public synchronized boolean wasRequested(String endpointId) {
        return endpointsRequested.contains(endpointId);
    }

    /**
     * A stable, immutable snapshot of the device to endpoint mapping.
     *
     * <p>Copied into a sorted unmodifiable map rather than {@code Map.copyOf}.
     * {@code Map.copyOf} discards iteration order and the JDK then iterates its
     * immutable maps in a per-JVM randomised order, so the diagnostics screen
     * would list the same peers in a different order on every launch and on
     * every device.
     */
    public synchronized Map<String, String> snapshot() {
        return java.util.Collections.unmodifiableMap(new TreeMap<>(endpointByDeviceId));
    }

    /** Forgets everything, when the radio is torn down. */
    public synchronized void clearAll() {
        deviceIdByEndpoint.clear();
        endpointByDeviceId.clear();
        discovered.clear();
        endpointsRequested.clear();
    }

    /** Allows a failed connection request to be retried. */
    public synchronized void clearRequested(String endpointId) {
        endpointsRequested.remove(endpointId);
    }

    public synchronized int size() {
        return deviceIdByEndpoint.size();
    }
}