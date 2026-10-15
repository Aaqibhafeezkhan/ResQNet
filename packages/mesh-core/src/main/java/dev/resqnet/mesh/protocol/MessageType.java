package dev.resqnet.mesh.protocol;

/** Application-level kind of a mesh envelope. */
public enum MessageType {

    /** End user emergency (SOS) content. */
    SOS(10, false),
    /** Plain informational message between people. */
    TEXT(20, false),
    /** Application level delivery confirmation for an earlier envelope. */
    ACK(30, false),
    /** Relay announcement, useful for topology awareness and debugging. */
    PRESENCE(40, false),
    /** Gateway synchronisation batch heading towards the internet. */
    GATEWAY_SYNC(50, true),
    /** Identity key announcement used for out-of-band verification. */
    IDENTITY(60, true);

    private final int wireId;
    private final boolean infrastructure;

    MessageType(int wireId, boolean infrastructure) {
        this.wireId = wireId;
        this.infrastructure = infrastructure;
    }

    /** Stable numeric identifier used on the wire. Never reuse a value. */
    public int wireId() {
        return wireId;
    }

    /**
     * True for machine generated control traffic. Infrastructure traffic is
     * rate limited more aggressively and can be dropped without user impact.
     */
    public boolean infrastructure() {
        return infrastructure;
    }

    /**
     * Resolves a wire id.
     *
     * @throws ProtocolException when the id is unknown, forcing callers to
     *     reject rather than silently downgrade unrecognised traffic
     */
    public static MessageType fromWireId(int wireId) throws ProtocolException {
        for (MessageType type : values()) {
            if (type.wireId == wireId) {
                return type;
            }
        }
        throw new ProtocolException(RejectReason.UNKNOWN_MESSAGE_TYPE,
                "Unknown message type id " + wireId);
    }
}