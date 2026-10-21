package dev.resqnet.mesh.protocol;

/**
 * Semantic validation applied to every envelope that enters the routing engine.
 *
 * <p>The {@link EnvelopeCodec} only guarantees that bytes are well formed. This
 * validator is the trust boundary: nothing reaches the dedup cache, the relay
 * queue or a user inbox until it has passed here. A mesh is a hostile
 * environment, and an unauthenticated peer within radio range can hand this
 * device arbitrary bytes.
 */
public final class EnvelopeValidator {

    /** Tolerance for clock skew between independently provisioned devices. */
    public static final long MAX_CLOCK_SKEW_MILLIS = 5L * 60 * 1000;

    /** SHA-256 fingerprint length in lowercase hex. */
    public static final int FINGERPRINT_HEX_LENGTH = 64;

    private final boolean requireSignature;
    private final long maxLifetimeMillis;
    private final int maxHopLimit;

    /** Default policy: signatures mandatory, protocol maxima respected. */
    public EnvelopeValidator() {
        this(true, MessageEnvelope.MAX_LIFETIME_MILLIS, MessageEnvelope.MAX_HOP_LIMIT);
    }

    /**
     * @param requireSignature when false, unsigned envelopes are accepted but
     *     treated as untrusted by the router (used only by tests and by the
     *     pairing bootstrap message, which cannot be signed before keys are
     *     exchanged)
     * @param maxLifetimeMillis caps how long any peer may keep a message alive
     * @param maxHopLimit caps the flooding radius of any peer
     */
    public EnvelopeValidator(boolean requireSignature, long maxLifetimeMillis, int maxHopLimit) {
        this.requireSignature = requireSignature;
        this.maxLifetimeMillis = Math.min(maxLifetimeMillis, MessageEnvelope.MAX_LIFETIME_MILLIS);
        this.maxHopLimit = Math.min(maxHopLimit, MessageEnvelope.MAX_HOP_LIMIT);
    }

    /**
     * Validates structure, identity and resource bounds.
     *
     * @param nowEpochMillis local clock, used for skew checks
     * @throws ProtocolException with a specific {@link RejectReason} on failure
     */
    public void validate(MessageEnvelope e, long nowEpochMillis) throws ProtocolException {
        if (e == null) {
            throw new ProtocolException(RejectReason.MALFORMED, "envelope is null");
        }
        if (!ProtocolVersion.isSupported(e.protocolVersion())) {
            throw new ProtocolException(RejectReason.UNSUPPORTED_VERSION,
                    "Unsupported protocol version " + e.protocolVersion());
        }

        requireIdentifier(e.messageId(), "messageId");
        requireIdentifier(e.originDeviceId(), "originDeviceId");

        String destination = e.destinationDeviceId().orElse(null);
        if (destination != null) {
            requireIdentifier(destination, "destinationDeviceId");
            if (destination.equals(e.originDeviceId())) {
                throw new ProtocolException(RejectReason.MALFORMED,
                        "Destination equals origin; use local delivery instead");
            }
        }

        if (e.payloadLength() > MessageEnvelope.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException(RejectReason.PAYLOAD_TOO_LARGE,
                    "Payload " + e.payloadLength() + "B exceeds "
                            + MessageEnvelope.MAX_PAYLOAD_BYTES + "B");
        }

        if (e.expiresAtEpochMillis() <= e.createdAtEpochMillis()) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Expiry " + e.expiresAtEpochMillis() + " must be after creation "
                            + e.createdAtEpochMillis());
        }
        if (e.lifetimeMillis() > maxLifetimeMillis) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Requested lifetime " + e.lifetimeMillis() + "ms exceeds local maximum "
                            + maxLifetimeMillis + "ms");
        }
        if (e.createdAtEpochMillis() > nowEpochMillis + MAX_CLOCK_SKEW_MILLIS) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Creation timestamp is more than " + MAX_CLOCK_SKEW_MILLIS
                            + "ms in the future; refusing to trust the sender clock");
        }

        if (e.hopLimit() < 0 || e.hopLimit() > maxHopLimit) {
            throw new ProtocolException(RejectReason.HOP_LIMIT_EXCEEDED,
                    "Hop limit " + e.hopLimit() + " outside [0, " + maxHopLimit + "]");
        }
        if (e.hopCount() < 0 || e.hopCount() > MessageEnvelope.MAX_HOP_LIMIT) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Hop count " + e.hopCount() + " is impossible");
        }

        if (e.type().protectedContent()) {
            if (destination == null) {
                throw new ProtocolException(RejectReason.MALFORMED,
                        "Encrypted content must be addressed to exactly one device");
            }
            if (!e.signed()) {
                throw new ProtocolException(RejectReason.INVALID_SIGNATURE,
                        "Encrypted content must be signed by its origin");
            }
            ProtectedPayload.decode(e.payload());
        }

        if (e.type() == MessageType.ACK) {
            if (e.correlationId().isEmpty()) {
                throw new ProtocolException(RejectReason.MALFORMED,
                        "ACK envelopes must reference the message they acknowledge");
            }
            if (e.correlationId().get().equals(e.messageId())) {
                throw new ProtocolException(RejectReason.MALFORMED,
                        "ACK cannot reference itself");
            }
        }

        if (!e.signed()) {
            if (requireSignature) {
                throw new ProtocolException(RejectReason.INVALID_SIGNATURE,
                        "Unsigned envelope rejected; sign your messages");
            }
        } else {
            if (!isHex(e.originKeyFingerprint(), FINGERPRINT_HEX_LENGTH)) {
                throw new ProtocolException(RejectReason.UNTRUSTED_ORIGIN,
                        "Key fingerprint must be " + FINGERPRINT_HEX_LENGTH + " hex characters");
            }
            if (e.signatureAlgorithm().isBlank()) {
                throw new ProtocolException(RejectReason.INVALID_SIGNATURE,
                        "Signed envelope must declare its signature algorithm");
            }
        }
    }

    private static void requireIdentifier(String value, String field) throws ProtocolException {
        if (value == null || value.isBlank()) {
            throw new ProtocolException(RejectReason.MALFORMED, field + " is required");
        }
        if (value.length() > EnvelopeCodec.MAX_IDENTIFIER_CHARS) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    field + " exceeds " + EnvelopeCodec.MAX_IDENTIFIER_CHARS + " characters");
        }
    }

    private static boolean isHex(String value, int expectedLength) {
        if (value.length() != expectedLength) {
            return false;
        }
        for (int i = 0; i < expectedLength; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}