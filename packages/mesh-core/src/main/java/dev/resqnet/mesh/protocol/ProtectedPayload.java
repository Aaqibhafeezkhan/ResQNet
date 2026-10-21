package dev.resqnet.mesh.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Outer container for an end-to-end encrypted directed payload.
 *
 * <p>The envelope payload is the only field a relay cannot see. Keeping the
 * recipients and message identifiers in the signed envelope header means the
 * mesh can route ciphertext without decrypting it, while HPKE's associated data
 * binds the ciphertext to those exact header fields. A relay that rewrites a
 * destination, type or timestamp invalidates both the envelope signature and
 * the AEAD tag.
 *
 * <p>Wire layout:
 *
 * <pre>
 *   magic "RQE1"        4 bytes
 *   version             1 byte
 *   suite               1 byte   (1 = X25519 / HKDF-SHA256 / AES-256-GCM)
 *   recipient key id   32 bytes  (SHA-256 of the raw X25519 public key)
 *   ciphertext length  int32
 *   ciphertext         n bytes
 * </pre>
 */
public final class ProtectedPayload {

    /** Magic prefix distinguishing an encrypted payload. */
    public static final byte[] MAGIC = { 0x52, 0x51, 0x45, 0x31 };

    /** Current protected payload version. */
    public static final int VERSION = 1;

    /** HPKE suite identifier: X25519 + HKDF-SHA256 + AES-256-GCM. */
    public static final int SUITE_X25519_HKDF_SHA256_AES_256_GCM = 1;

    /** Raw recipient key identifier length. */
    public static final int KEY_ID_BYTES = 32;

    /** Fixed bytes before the ciphertext. */
    public static final int HEADER_BYTES = MAGIC.length + 2 + KEY_ID_BYTES + 4;

    /** HPKE authentication overhead for the supported suite. */
    public static final int CIPHERTEXT_OVERHEAD_BYTES = 48;

    /** Largest ciphertext that still fits inside one mesh payload. */
    public static final int MAX_CIPHERTEXT_BYTES =
            MessageEnvelope.MAX_PAYLOAD_BYTES - HEADER_BYTES;

    /** Largest plaintext accepted by this version. */
    public static final int MAX_PLAINTEXT_BYTES =
            MAX_CIPHERTEXT_BYTES - CIPHERTEXT_OVERHEAD_BYTES;

    private static final byte[] DOMAIN =
            "ResQNet-E2EE-ProtectedPayload-v1".getBytes(StandardCharsets.US_ASCII);

    private final byte[] keyId;
    private final byte[] ciphertext;

    private ProtectedPayload(byte[] keyId, byte[] ciphertext) {
        this.keyId = keyId.clone();
        this.ciphertext = ciphertext.clone();
    }

    /** Builds a container from an HPKE output. */
    public static ProtectedPayload of(byte[] recipientKeyId, byte[] ciphertext)
            throws ProtocolException {
        if (recipientKeyId == null || recipientKeyId.length != KEY_ID_BYTES) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Recipient key id must be " + KEY_ID_BYTES + " bytes");
        }
        if (ciphertext == null || ciphertext.length == 0) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Protected ciphertext must not be empty");
        }
        if (ciphertext.length > MAX_CIPHERTEXT_BYTES) {
            throw new ProtocolException(RejectReason.PAYLOAD_TOO_LARGE,
                    "Protected ciphertext " + ciphertext.length + "B exceeds limit");
        }
        return new ProtectedPayload(recipientKeyId, ciphertext);
    }

    /** Parses and structural-validates an encrypted payload. */
    public static ProtectedPayload decode(byte[] data) throws ProtocolException {
        if (data == null || data.length < HEADER_BYTES) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Protected payload is truncated");
        }
        if (data.length > MessageEnvelope.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException(RejectReason.PAYLOAD_TOO_LARGE,
                    "Protected payload exceeds envelope limit");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                throw new ProtocolException(RejectReason.MALFORMED,
                        "Bad protected payload magic");
            }
        }
        int p = MAGIC.length;
        int version = data[p++] & 0xFF;
        int suite = data[p++] & 0xFF;
        if (version != VERSION) {
            throw new ProtocolException(RejectReason.UNSUPPORTED_VERSION,
                    "Unsupported protected payload version " + version);
        }
        if (suite != SUITE_X25519_HKDF_SHA256_AES_256_GCM) {
            throw new ProtocolException(RejectReason.UNSUPPORTED_VERSION,
                    "Unsupported protected payload suite " + suite);
        }
        byte[] keyId = Arrays.copyOfRange(data, p, p + KEY_ID_BYTES);
        p += KEY_ID_BYTES;
        int length = ((data[p] & 0xFF) << 24) | ((data[p + 1] & 0xFF) << 16)
                | ((data[p + 2] & 0xFF) << 8) | (data[p + 3] & 0xFF);
        p += 4;
        if (length <= 0 || length != data.length - p) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    "Protected payload length " + length + " does not match "
                            + (data.length - p) + " remaining bytes");
        }
        if (length > MAX_CIPHERTEXT_BYTES) {
            throw new ProtocolException(RejectReason.PAYLOAD_TOO_LARGE,
                    "Protected ciphertext exceeds limit");
        }
        return new ProtectedPayload(keyId, Arrays.copyOfRange(data, p, data.length));
    }

    /** Serializes this container. */
    public byte[] encode() {
        byte[] out = new byte[HEADER_BYTES + ciphertext.length];
        int p = 0;
        System.arraycopy(MAGIC, 0, out, p, MAGIC.length);
        p += MAGIC.length;
        out[p++] = (byte) VERSION;
        out[p++] = (byte) SUITE_X25519_HKDF_SHA256_AES_256_GCM;
        System.arraycopy(keyId, 0, out, p, KEY_ID_BYTES);
        p += KEY_ID_BYTES;
        out[p++] = (byte) (ciphertext.length >>> 24);
        out[p++] = (byte) (ciphertext.length >>> 16);
        out[p++] = (byte) (ciphertext.length >>> 8);
        out[p++] = (byte) ciphertext.length;
        System.arraycopy(ciphertext, 0, out, p, ciphertext.length);
        return out;
    }

    /** Raw recipient key identifier. */
    public byte[] keyId() {
        return keyId.clone();
    }

    /** Ciphertext bytes. */
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    /** True when a payload begins with the protected-payload magic. */
    public static boolean looksLikeProtected(byte[] data) {
        return data != null && data.length >= MAGIC.length
                && Arrays.equals(Arrays.copyOf(data, MAGIC.length), MAGIC);
    }

    /**
     * Canonical HPKE associated data for one envelope.
     *
     * <p>The unsigned header is built with an empty payload, so the associated
     * data binds every routable field without circularly covering the
     * ciphertext. Hop count and hop limit are intentionally excluded because
     * relays must be able to change them without holding any key.
     */
    public static byte[] associatedData(MessageEnvelope unsignedHeader,
                                        byte[] recipientKeyId) {
        Objects.requireNonNull(unsignedHeader, "unsignedHeader");
        if (recipientKeyId == null || recipientKeyId.length != KEY_ID_BYTES) {
            throw new IllegalArgumentException("Recipient key id must be "
                    + KEY_ID_BYTES + " bytes");
        }
        byte[] signingBytes = EnvelopeCodec.signingBytes(unsignedHeader);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(
                    DOMAIN.length + 2 + KEY_ID_BYTES + 4 + signingBytes.length);
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(DOMAIN);
            out.writeByte(VERSION);
            out.writeByte(SUITE_X25519_HKDF_SHA256_AES_256_GCM);
            out.writeShort(KEY_ID_BYTES);
            out.write(recipientKeyId);
            out.writeInt(signingBytes.length);
            out.write(signingBytes);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("In-memory associated data failed", e);
        }
    }

    /** Maps a clear message type to its encrypted variant. */
    public static MessageType protectedTypeFor(MessageType type) {
        return switch (type) {
            case SOS -> MessageType.SOS_PROTECTED;
            case TEXT -> MessageType.TEXT_PROTECTED;
            default -> throw new IllegalArgumentException(
                    "Message type " + type + " has no protected variant");
        };
    }

    /** True for message types that must carry a protected payload. */
    public static boolean isProtected(MessageType type) {
        return type == MessageType.SOS_PROTECTED || type == MessageType.TEXT_PROTECTED;
    }
}
