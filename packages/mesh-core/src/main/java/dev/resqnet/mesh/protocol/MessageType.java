package dev.resqnet.mesh.protocol;

/** Application-level kind of a mesh envelope. */
public enum MessageType {

    /** End user emergency (SOS) content. Public broadcast, not encrypted. */
    SOS(10, false),
    /** End-to-end encrypted SOS addressed to exactly one verified device. */
    SOS_PROTECTED(11, false),
    /** Plain informational message between people. */
    TEXT(20, false),
    /** End-to-end encrypted text addressed to exactly one verified device. */
    TEXT_PROTECTED(21, false),
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
     * True for types whose payload is an end-to-end encrypted container.
     *
     * <p>These are separate wire types rather than a flag on SOS/TEXT so an
     * older client rejects them as unknown instead of rendering ciphertext as
     * if it were human text. An explicit refusal is the safe failure mode.
     */
    public boolean protectedContent() {
        return this == SOS_PROTECTED || this == TEXT_PROTECTED;
    }

    /** True when the type carries user-visible content in a readable form. */
    public boolean cleartextContent() {
        return this == SOS || this == TEXT;
    }

    /** Encrypted variant of a content type. */
    public MessageType protectedVariant() {
        return switch (this) {
            case SOS -> SOS_PROTECTED;
            case TEXT -> TEXT_PROTECTED;
            default -> throw new IllegalStateException(this + " has no protected variant");
        };
    }

    /** Cleartext variant of an encrypted content type. */
    public MessageType cleartextVariant() {
        return switch (this) {
            case SOS_PROTECTED -> SOS;
            case TEXT_PROTECTED -> TEXT;
            default -> throw new IllegalStateException(this + " is not protected content");
        };
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