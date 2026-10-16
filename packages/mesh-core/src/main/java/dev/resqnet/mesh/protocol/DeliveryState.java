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
        return this == DELIVERED || this == ACKNOWLEDGED || this == EXPIRED || this == FAILED;
    }

    /**
     * The states that end a record, in the order the stores filter on.
     *
     * <p>Shared so the in-memory store and the Android SQLite store cannot drift
     * apart on which rows are finished. They previously each hardcoded the
     * list, and the two copies had already diverged once.
     *
     * <p>{@link #DELIVERED} is terminal here, which is the one judgement call in
     * this enum. Delivery is not the same as confirmation, but a node that has
     * the message has nothing left to send for it: treating DELIVERED as live
     * put the record back in the retry queue and re-broadcast a duplicate on
     * every sweep. End-to-end confirmation is unaffected, because it depends on
     * the <em>origin's</em> record, which is SENT_TO_PEER and stays live until an
     * acknowledgement arrives. The store still permits the DELIVERED to
     * ACKNOWLEDGED transition, so a late acknowledgement is still recorded.
     */
    public static java.util.List<String> terminalNames() {
        return java.util.List.of(DELIVERED.name(), ACKNOWLEDGED.name(), EXPIRED.name(),
                FAILED.name());
    }

    /** True when the sender may still make progress. */
    public boolean pending() {
        return this == CREATED || this == QUEUED || this == SENT_TO_PEER || this == RELAYED;
    }
}