package dev.resqnet.mesh.routing;

/**
 * What the router decided to do with one envelope.
 *
 * <p>Every inbound frame maps to exactly one of these, which makes the routing
 * behaviour assertable in tests and renderable in the dashboard topology view
 * without guesswork.
 */
public enum RoutingOutcome {

    /** Handed to the local inbox because this device is the destination. */
    DELIVERED_LOCALLY(true),
    /** Stored locally because this device is not the destination but will relay. */
    RELAYED(true),
    /** A copy was queued for delivery or relay when a peer becomes reachable. */
    QUEUED_FOR_RELAY(true),
    /** A locally originated message was handed to at least one neighbour. */
    ORIGINATED_AND_SENT(true),
    /** End-to-end acknowledgement received for one of our messages. */
    ACKNOWLEDGED(true),
    /** Already seen; suppressed to stop flooding loops. */
    DUPLICATE(false),
    /** Lifetime elapsed. */
    DROPPED_EXPIRED(false),
    /** Hop budget exhausted before the destination was reached. */
    DROPPED_HOP_LIMIT(false),
    /** Throttled to bound flooding amplification. */
    DROPPED_RATE_LIMITED(false),
    /** Origin key unknown, blocked, or the signature did not verify. */
    DROPPED_UNTRUSTED(false),
    /** Failed structural or semantic validation. */
    DROPPED_MALFORMED(false),
    /** Origin and destination are the same device. */
    DROPPED_SELF_ADDRESSED(false),
    /** Valid, but no eligible peer and nothing left to try. */
    DROPPED_NO_PATH(false);

    private final boolean productive;

    RoutingOutcome(boolean productive) {
        this.productive = productive;
    }

    /** True when the message made real progress. */
    public boolean productive() {
        return productive;
    }

    /** True when the message will never move again without user action. */
    public boolean terminalDrop() {
        return !productive;
    }
}