package dev.resqnet.mesh.routing;

/** Receives messages that reached this device because it was the destination. */
@FunctionalInterface
public interface LocalDeliveryListener {

    /**
     * A message arrived for this device.
     *
     * @param envelope the validated, locally delivered envelope
     * @param viaPeerDeviceId the directly connected neighbour that handed it
     *     over, or {@code null} for a message this device composed
     * @param hopCount how many radio hops it travelled
     */
    void onDelivered(dev.resqnet.mesh.protocol.MessageEnvelope envelope,
                     String viaPeerDeviceId, int hopCount);
}