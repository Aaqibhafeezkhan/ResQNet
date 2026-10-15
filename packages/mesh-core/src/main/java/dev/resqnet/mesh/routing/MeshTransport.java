package dev.resqnet.mesh.routing;

import java.util.Optional;

/**
 * Radio abstraction the routing engine drives.
 *
 * <p>mesh-core deliberately knows nothing about Nearby Connections, Bluetooth or
 * Wi-Fi Direct. On a handset this is implemented by an adapter that maps a stable
 * {@code deviceId} onto the current Nearby endpoint id. In the simulator it is
 * implemented by the simulated radio, which can drop links, delay frames or go
 * offline. Both drive the identical {@link MeshRouter}, so a routing result that
 * the simulator reports is a result the phone can actually produce.
 */
public interface MeshTransport {

    /**
     * Offers an encoded envelope to one directly connected peer.
     *
     * @param peerDeviceId stable identity of the next hop
     * @param encodedPayload canonical bytes from
     *     {@link dev.resqnet.mesh.protocol.EnvelopeCodec#encode}
     * @return the peer session token to bind the acknowledgement, or empty when
     *     the peer went away before the frame was queued
     */
    Optional<Long> send(String peerDeviceId, byte[] encodedPayload);

    /**
     * Ids of peers that are connected right now, in a deterministic order.
     *
     * <p>Implementations must return a stable ordering. The router uses it to
     * decide which peers receive a copy when the fan-out cap bites, and
     * acknowledgement broadcasts follow it, so an implementation backed by a
     * {@code HashSet} would make forwarding unreproducible between runs.
     */
    java.util.Set<String> connectedPeerIds();

    /**
     * Registers the callback the transport invokes when a payload arrives from a
     * peer. Implementations must deliver on a thread of their choosing; the
     * router is thread safe.
     */
    void setInboundHandler(InboundHandler handler);

    /** Listener for connection lifecycle changes. */
    void setConnectionListener(ConnectionListener listener);

    /** Starts the radio stack. Idempotent. */
    void start();

    /** Stops the radio stack and drops all sessions. Idempotent. */
    void stop();

    /** True while the radio stack is running. */
    boolean isStarted();

    /** Receives raw payloads from directly connected peers. */
    @FunctionalInterface
    interface InboundHandler {
        /**
         * @param fromPeerDeviceId stable id of the peer that delivered the frame
         * @param payload raw canonical envelope bytes
         */
        void onPayload(String fromPeerDeviceId, byte[] payload);
    }

    /** Reports peer connection lifecycle so the router can drain queues. */
    interface ConnectionListener {
        default void onPeerConnected(String peerDeviceId) {
        }

        default void onPeerDisconnected(String peerDeviceId) {
        }
    }
}