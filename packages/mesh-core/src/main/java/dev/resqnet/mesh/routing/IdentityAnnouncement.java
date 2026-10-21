package dev.resqnet.mesh.routing;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import dev.resqnet.mesh.protocol.RejectReason;

/**
 * Payload of a {@link dev.resqnet.mesh.protocol.MessageType#IDENTITY} envelope.
 *
 * <p>This is the one message that has to be unsigned, which creates a chicken
 * and egg problem: a device cannot sign to a peer whose key it does not have,
 * and it cannot have a peer's key without an announcement. Resolving it by
 * simply trusting announcements would let anyone claim any identity, so instead
 * the announcement is treated as an unverified <em>claim</em>. It is surfaced to
 * the device owner, who compares the eight character short fingerprint with the
 * person in front of them and taps accept. Only then does the key enter the
 * {@link dev.resqnet.mesh.crypto.TrustStore} and start being used for signature
 * verification.
 *
 * <p>Wire layout:
 *
 * <pre>
 *   magic "RQID"        4 bytes
 *   label               int16 length + UTF-8
 *   publicKeyBase64     int16 length + ASCII
 *   shortCode           int16 length + ASCII  (8 chars of the fingerprint)
 *   encryptionPublicKeyBase64  int16 length + ASCII  (optional, appended)
 *   encryptionKeyProofBase64   int16 length + ASCII  (optional, appended)
 * </pre>
 *
 * <p>The encryption fields are appended as a pair so an older client that
 * ignores everything after {@code shortCode} still reads the signing key
 * correctly. The proof is signed by that signing key, binding the HPKE key to
 * the identity without making the claim itself signed.
 */
public final class IdentityAnnouncement {

    /** Magic prefix distinguishing an identity announcement payload. */
    public static final byte[] MAGIC = { 0x52, 0x51, 0x49, 0x44 };

    /** Characters of the fingerprint shown for in-person comparison. */
    public static final int SHORT_CODE_LENGTH = 8;

    /** Maximum bytes allowed in the announcement payload. */
    public static final int MAX_PAYLOAD_BYTES = 1024;

    private final String label;
    private final String publicKeyBase64;
    private final String shortCode;
    private final String encryptionPublicKeyBase64;
    private final String encryptionKeyProofBase64;

    private IdentityAnnouncement(String label, String publicKeyBase64, String shortCode,
                                 String encryptionPublicKeyBase64,
                                 String encryptionKeyProofBase64) {
        this.label = label;
        this.publicKeyBase64 = publicKeyBase64;
        this.shortCode = shortCode;
        this.encryptionPublicKeyBase64 = encryptionPublicKeyBase64 == null
                ? "" : encryptionPublicKeyBase64;
        this.encryptionKeyProofBase64 = encryptionKeyProofBase64 == null
                ? "" : encryptionKeyProofBase64;
    }

    /** Builds an announcement for a locally held identity without encryption. */
    public static IdentityAnnouncement of(dev.resqnet.mesh.crypto.SigningIdentity identity) {
        String fingerprint = identity.keyFingerprint();
        return new IdentityAnnouncement(identity.displayName(),
                identity.publicKeyBase64(), shortCode(fingerprint), "", "");
    }

    /**
     * Builds an announcement that binds {@code encryption} to the signing
     * identity with a proof only that identity can produce.
     */
    public static IdentityAnnouncement of(dev.resqnet.mesh.crypto.SigningIdentity identity,
                                          dev.resqnet.mesh.crypto.EncryptionIdentity encryption)
            throws java.security.GeneralSecurityException {
        Objects.requireNonNull(encryption, "encryption");
        byte[] proof = dev.resqnet.mesh.crypto.EncryptionKeyBinding.prove(identity,
                encryption.publicKeyBytes());
        return new IdentityAnnouncement(identity.displayName(),
                identity.publicKeyBase64(), shortCode(identity.keyFingerprint()),
                java.util.Base64.getEncoder().encodeToString(encryption.publicKeyBytes()),
                java.util.Base64.getEncoder().encodeToString(proof));
    }

    /** Builds an announcement from raw parts, for the decode path and tests. */
    public static IdentityAnnouncement of(String label, String publicKeyBase64,
                                          String fullFingerprint) {
        return new IdentityAnnouncement(Objects.requireNonNullElse(label, "unknown"),
                Objects.requireNonNull(publicKeyBase64), shortCode(fullFingerprint), "", "");
    }

    /** Builds an announcement with an encryption-key binding, for tests. */
    public static IdentityAnnouncement of(String label, String publicKeyBase64,
                                          String fullFingerprint,
                                          String encryptionPublicKeyBase64,
                                          String encryptionKeyProofBase64) {
        return new IdentityAnnouncement(Objects.requireNonNullElse(label, "unknown"),
                Objects.requireNonNull(publicKeyBase64), shortCode(fullFingerprint),
                encryptionPublicKeyBase64, encryptionKeyProofBase64);
    }

    /** First eight characters of the fingerprint, grouped for legibility. */
    public static String shortCode(String fullFingerprint) {
        if (fullFingerprint == null || fullFingerprint.length() < SHORT_CODE_LENGTH) {
            return "????????";
        }
        String head = fullFingerprint.substring(0, SHORT_CODE_LENGTH);
        return head.substring(0, 4) + "-" + head.substring(4);
    }

    public String label() {
        return label;
    }

    public String publicKeyBase64() {
        return publicKeyBase64;
    }

    public String shortCode() {
        return shortCode;
    }

    /** Base64 raw X25519 public key, or empty when this claim carries none. */
    public String encryptionPublicKeyBase64() {
        return encryptionPublicKeyBase64;
    }

    /** Base64 signing-key proof over the HPKE key, or empty when absent. */
    public String encryptionKeyProofBase64() {
        return encryptionKeyProofBase64;
    }

    /** True when the claim carries an encryption key and its proof. */
    public boolean hasEncryptionKey() {
        return !encryptionPublicKeyBase64.isEmpty() && !encryptionKeyProofBase64.isEmpty();
    }

    public byte[] encode() {
        byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = publicKeyBase64.getBytes(StandardCharsets.US_ASCII);
        byte[] codeBytes = shortCode.getBytes(StandardCharsets.US_ASCII);
        byte[] encBytes = encryptionPublicKeyBase64.getBytes(StandardCharsets.US_ASCII);
        byte[] proofBytes = encryptionKeyProofBase64.getBytes(StandardCharsets.US_ASCII);
        int tail = hasEncryptionKey() ? 2 + encBytes.length + 2 + proofBytes.length : 0;
        byte[] out = new byte[4 + 2 + labelBytes.length + 2 + keyBytes.length
                + 2 + codeBytes.length + tail];
        int p = 0;
        System.arraycopy(MAGIC, 0, out, p, MAGIC.length);
        p += MAGIC.length;
        p = putShort(out, p, labelBytes, labelBytes);
        p = putShort(out, p, keyBytes, keyBytes);
        p = putShort(out, p, codeBytes, codeBytes);
        if (hasEncryptionKey()) {
            p = putShort(out, p, encBytes, encBytes);
            putShort(out, p, proofBytes, proofBytes);
        }
        return out;
    }

    /**
     * Parses an announcement.
     *
     * @throws IllegalArgumentException when the payload is truncated or lacks
     *     the magic prefix; the caller maps this to
     *     {@link RejectReason#MALFORMED}
     */
    public static IdentityAnnouncement decode(byte[] data) {
        if (data == null || data.length > MAX_PAYLOAD_BYTES || data.length < 4) {
            throw new IllegalArgumentException("Identity payload too small or too large");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                throw new IllegalArgumentException("Bad identity payload magic");
            }
        }
        int p = MAGIC.length;
        byte[] label = readShort(data, p, "label");
        p += 2 + label.length;
        byte[] key = readShort(data, p, "publicKey");
        p += 2 + key.length;
        byte[] code = readShort(data, p, "shortCode");
        p += 2 + code.length;
        String encryptionKey = "";
        String encryptionProof = "";
        if (p < data.length) {
            byte[] enc = readShort(data, p, "encryptionPublicKey");
            p += 2 + enc.length;
            byte[] proof = readShort(data, p, "encryptionKeyProof");
            p += 2 + proof.length;
            if (p != data.length) {
                throw new IllegalArgumentException("Identity payload has trailing bytes");
            }
            encryptionKey = new String(enc, StandardCharsets.US_ASCII);
            encryptionProof = new String(proof, StandardCharsets.US_ASCII);
            if (encryptionKey.isEmpty() || encryptionProof.isEmpty()) {
                throw new IllegalArgumentException(
                        "Encryption key fields must both be present");
            }
        }
        return new IdentityAnnouncement(
                new String(label, StandardCharsets.UTF_8),
                new String(key, StandardCharsets.US_ASCII),
                new String(code, StandardCharsets.US_ASCII),
                encryptionKey, encryptionProof);
    }

    /** True when the payload at least looks like an identity announcement. */
    public static boolean looksLikeIdentity(byte[] data) {
        return data != null && data.length >= MAGIC.length
                && Arrays.equals(Arrays.copyOf(data, MAGIC.length), MAGIC);
    }

    private static int putShort(byte[] out, int pos, byte[] value, byte[] encoded) {
        out[pos] = (byte) (encoded.length >>> 8);
        out[pos + 1] = (byte) encoded.length;
        System.arraycopy(value, 0, out, pos + 2, value.length);
        return pos + 2 + value.length;
    }

    private static byte[] readShort(byte[] data, int pos, String field) {
        if (pos + 2 > data.length) {
            throw new IllegalArgumentException("Identity payload truncated before " + field);
        }
        int len = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        if (pos + 2 + len > data.length) {
            throw new IllegalArgumentException("Identity payload overstates " + field
                    + " length " + len);
        }
        return Arrays.copyOfRange(data, pos + 2, pos + 2 + len);
    }

    @Override
    public String toString() {
        return "Identity{" + label + " key=" + shortCode + "}";
    }
}