package dev.resqnet.mesh.protocol;

/**
 * End-to-end delivery lifecycle of a message originated by this device.
 *
 * <p>The distinction between {@link #SENT_TO_PEER} and {@link #ACKNOWLEDGED}
 * is the single most important one in the system: a successful Nearby
 * Connections payload transfer only proves one radio hop succeeded, never that
 * the intended human received the message.
 */
public enum DeliveryState {

    /** Built locally, not yet handed to the routing engine. */
    CREATED,
    /** Persisted and waiting for an eligible peer or a retry window. */
    QUEUED,
    /** Handed to a directly connected neighbour. Not proof of delivery. */
    SENT_TO_PEER,
    /** Handed to the next hop on behalf of someone else's message. */
    RELAYED,
    /** Arrived at the intended destination device (relay devices also do this). */
    DELIVERED,
    /** Destination explicitly acknowledged receipt. End-to-end confirmed. */
    ACKNOWLEDGED,
    /** Lifetime elapsed before delivery. */
    EXPIRED,
    /** Permanently given up on (peer permanently gone, retries exhausted). */
    FAILED;

    /** True once the message can no longer change state. */
    public boolean terminal() {
        return this == ACKNOWLEDGED || this == EXPIRED || this == FAILED;
    }

    /** True when the sender may still make progress. */
    public boolean pending() {
        return this == CREATED || this == QUEUED || this == SENT_TO_PEER || this == RELAYED;
    }
}