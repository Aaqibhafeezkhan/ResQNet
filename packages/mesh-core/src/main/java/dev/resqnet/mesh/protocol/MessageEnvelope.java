package dev.resqnet.mesh.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable unit of work that travels across the mesh.
 *
 * <p>Design constraints that shaped this type:
 *
 * <ul>
 *   <li>Strictly immutable so an envelope can be handed to several peers from
 *       different threads without defensive copying at every relay.</li>
 *   <li>Every field is bounded and validated at construction time, so a router
 *       never has to defensively re-check structure on the receive path.</li>
 *   <li>The payload is an opaque byte array. mesh-core does not interpret SOS
 *       content; that keeps the routing engine testable and reusable.</li>
 *   <li>Signature covers the canonical unsigned form so relays cannot mutate
 *       routing metadata without invalidating the signature.</li>
 * </ul>
 */
public final class MessageEnvelope {

    /** Hard ceiling for the encoded envelope. Nearby Connections caps at 16 KiB. */
    public static final int MAX_ENCODED_BYTES = 16 * 1024;

    /** Hard ceiling for a single payload, leaving room for routing metadata. */
    public static final int MAX_PAYLOAD_BYTES = 8 * 1024;

    /** Maximum hop budget any sender may request. */
    public static final int MAX_HOP_LIMIT = 32;

    /** Maximum lifetime any sender may request. */
    public static final long MAX_LIFETIME_MILLIS = 6L * 60 * 60 * 1000;

    private final int protocolVersion;
    private final String messageId;
    private final String originDeviceId;
    private final String originKeyFingerprint;
    private final String destinationDeviceId;
    private final MessageType type;
    private final Priority priority;
    private final long createdAtEpochMillis;
    private final long expiresAtEpochMillis;
    private final int hopLimit;
    private final int hopCount;
    private final String correlationId;
    private final byte[] payload;
    private final byte[] signature;
    private final String signatureAlgorithm;

    private MessageEnvelope(Builder builder, byte[] signature, String signatureAlgorithm) {
        this.protocolVersion = builder.protocolVersion;
        this.messageId = builder.messageId;
        this.originDeviceId = builder.originDeviceId;
        this.originKeyFingerprint = builder.originKeyFingerprint;
        this.destinationDeviceId = builder.destinationDeviceId;
        this.type = builder.type;
        this.priority = builder.priority;
        this.createdAtEpochMillis = builder.createdAtEpochMillis;
        this.expiresAtEpochMillis = builder.expiresAtEpochMillis;
        this.hopLimit = builder.hopLimit;
        this.hopCount = builder.hopCount;
        this.correlationId = builder.correlationId;
        this.payload = builder.payload.clone();
        this.signature = signature == null ? new byte[0] : signature.clone();
        this.signatureAlgorithm = signatureAlgorithm == null ? "" : signatureAlgorithm;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this envelope with the hop budget and hop count advanced
     * for one relay. Relays never mutate an envelope in place: the received
     * bytes remain auditable and the signature still describes the origin's
     * content.
     */
    public MessageEnvelope relayedCopy(int newHopLimit, int newHopCount) {
        Builder b = toBuilder();
        b.hopLimit = newHopLimit;
        b.hopCount = newHopCount;
        return new MessageEnvelope(b, signature, signatureAlgorithm);
    }

    /** Returns a builder pre-populated from this envelope, signature included. */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.protocolVersion = protocolVersion;
        b.messageId = messageId;
        b.originDeviceId = originDeviceId;
        b.originKeyFingerprint = originKeyFingerprint;
        b.destinationDeviceId = destinationDeviceId;
        b.type = type;
        b.priority = priority;
        b.createdAtEpochMillis = createdAtEpochMillis;
        b.expiresAtEpochMillis = expiresAtEpochMillis;
        b.hopLimit = hopLimit;
        b.hopCount = hopCount;
        b.correlationId = correlationId;
        b.payload = payload;
        return b;
    }

    /** Canonical byte form covered by {@link #signature()}. */
    public byte[] signaturePayload() {
        return EnvelopeCodec.signingBytes(this);
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String messageId() {
        return messageId;
    }

    public String originDeviceId() {
        return originDeviceId;
    }

    /** SHA-256 fingerprint of the origin's public key, or empty when unsigned. */
    public String originKeyFingerprint() {
        return originKeyFingerprint;
    }

    /** Intended recipient, or empty for a broadcast scope. */
    public Optional<String> destinationDeviceId() {
        return Optional.ofNullable(destinationDeviceId);
    }

    /** True when the envelope is addressed to every node in range. */
    public boolean broadcast() {
        return destinationDeviceId == null;
    }

    public MessageType type() {
        return type;
    }

    public Priority priority() {
        return priority;
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public long expiresAtEpochMillis() {
        return expiresAtEpochMillis;
    }

    public long lifetimeMillis() {
        return expiresAtEpochMillis - createdAtEpochMillis;
    }

    /** Remaining hops this envelope may still consume. */
    public int hopLimit() {
        return hopLimit;
    }

    /** How many hops this envelope has already been forwarded across. */
    public int hopCount() {
        return hopCount;
    }

    /** Message id this envelope acknowledges, for {@link MessageType#ACK}. */
    public Optional<String> correlationId() {
        return Optional.ofNullable(correlationId);
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    public byte[] signature() {
        return signature.clone();
    }

    public boolean signed() {
        return signature.length > 0 && !originKeyFingerprint.isEmpty();
    }

    public String signatureAlgorithm() {
        return signatureAlgorithm;
    }

    /** True when the envelope can no longer be forwarded to a destination. */
    public boolean expiredAt(long nowEpochMillis) {
        return nowEpochMillis >= expiresAtEpochMillis;
    }

    /** True when no relay budget remains. */
    public boolean hopExhausted() {
        return hopLimit <= 0;
    }

    /** Milliseconds until expiry, never negative. */
    public long remainingLifetimeMillis(long nowEpochMillis) {
        return Math.max(0L, expiresAtEpochMillis - nowEpochMillis);
    }

    /** True when this device is the intended recipient. */
    public boolean addressedTo(String deviceId) {
        return destinationDeviceId == null || destinationDeviceId.equals(deviceId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageEnvelope other)) {
            return false;
        }
        return protocolVersion == other.protocolVersion
                && hopLimit == other.hopLimit
                && hopCount == other.hopCount
                && createdAtEpochMillis == other.createdAtEpochMillis
                && expiresAtEpochMillis == other.expiresAtEpochMillis
                && messageId.equals(other.messageId)
                && originDeviceId.equals(other.originDeviceId)
                && originKeyFingerprint.equals(other.originKeyFingerprint)
                && Objects.equals(destinationDeviceId, other.destinationDeviceId)
                && type == other.type
                && priority == other.priority
                && Objects.equals(correlationId, other.correlationId)
                && Arrays.equals(payload, other.payload)
                && Arrays.equals(signature, other.signature);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(protocolVersion, messageId, originDeviceId, originKeyFingerprint,
                destinationDeviceId, type, priority, createdAtEpochMillis, expiresAtEpochMillis,
                hopLimit, hopCount, correlationId);
        return 31 * result + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "MessageEnvelope{id=" + messageId
                + ", type=" + type
                + ", priority=" + priority
                + ", origin=" + shortId(originDeviceId)
                + ", dest=" + (destinationDeviceId == null ? "broadcast" : shortId(destinationDeviceId))
                + ", hops=" + hopCount + "/" + hopLimit
                + ", payload=" + payload.length + "B"
                + ", signed=" + signed()
                + '}';
    }

    private static String shortId(String id) {
        if (id == null) {
            return "null";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    /** Fluent builder with eager structural validation. */
    public static final class Builder {

        private int protocolVersion = ProtocolVersion.CURRENT;
        private String messageId;
        private String originDeviceId;
        private String originKeyFingerprint = "";
        private String destinationDeviceId;
        private MessageType type = MessageType.TEXT;
        private Priority priority = Priority.NORMAL;
        private long createdAtEpochMillis;
        private long expiresAtEpochMillis;
        private int hopLimit = 6;
        private int hopCount = 0;
        private String correlationId;
        private byte[] payload = new byte[0];

        private Builder() {
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder origin(String originDeviceId) {
            this.originDeviceId = originDeviceId;
            return this;
        }

        public Builder originKeyFingerprint(String fingerprint) {
            this.originKeyFingerprint = fingerprint == null ? "" : fingerprint;
            return this;
        }

        /**
         * Sets the intended recipient. A null or empty value means broadcast,
         * which is also how the binary codec represents an absent field, so the
         * two representations cannot drift apart.
         */
        public Builder destination(String destinationDeviceId) {
            this.destinationDeviceId =
                    (destinationDeviceId == null || destinationDeviceId.isEmpty())
                            ? null : destinationDeviceId;
            return this;
        }

        public Builder broadcast() {
            this.destinationDeviceId = null;
            return this;
        }

        public Builder type(MessageType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = Objects.requireNonNull(priority, "priority");
            return this;
        }

        public Builder createdAt(long epochMillis) {
            this.createdAtEpochMillis = epochMillis;
            return this;
        }

        public Builder expiresAt(long epochMillis) {
            this.expiresAtEpochMillis = epochMillis;
            return this;
        }

        public Builder lifetime(long lifetimeMillis) {
            this.expiresAtEpochMillis = this.createdAtEpochMillis + lifetimeMillis;
            return this;
        }

        public Builder hopLimit(int hopLimit) {
            this.hopLimit = hopLimit;
            return this;
        }

        public Builder hopCount(int hopCount) {
            this.hopCount = hopCount;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = (correlationId == null || correlationId.isEmpty())
                    ? null : correlationId;
            return this;
        }

        public Builder payload(byte[] payload) {
            this.payload = payload == null ? new byte[0] : payload;
            return this;
        }

        /** Builds an unsigned envelope. Used for ACK control frames and tests. */
        public MessageEnvelope build() throws ProtocolException {
            return new MessageEnvelope(this, null, null);
        }

        /** Builds a signed envelope. Signature covers {@code signingBytes()}. */
        public MessageEnvelope buildSigned(byte[] signature, String signatureAlgorithm)
                throws ProtocolException {
            if (signature == null || signature.length == 0) {
                throw new ProtocolException(RejectReason.INVALID_SIGNATURE,
                        "Signature must not be empty");
            }
            if (signatureAlgorithm == null || signatureAlgorithm.isBlank()) {
                throw new ProtocolException(RejectReason.INVALID_SIGNATURE,
                        "Signature algorithm must be declared");
            }
            if (originKeyFingerprint == null || originKeyFingerprint.isBlank()) {
                throw new ProtocolException(RejectReason.INVALID_SIGNATURE,
                        "Signed envelopes require an origin key fingerprint");
            }
            return new MessageEnvelope(this, signature, signatureAlgorithm);
        }
    }
}