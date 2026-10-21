package dev.resqnet.mesh.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Proof that a signing identity really controls an HPKE key.
 *
 * <p>An identity announcement is unsigned by necessity: it is how a peer first
 * learns the key that would verify a signature. Adding a second, independently
 * generated encryption key to that claim would otherwise let anyone append an
 * arbitrary HPKE key and receive messages intended for the named device.
 *
 * <p>The fix is a signature by the announcement's signing key over a
 * domain-separated encoding of both public keys. The verifier already has the
 * signing key from the claim (or from its trust store), so it can check the
 * binding before accepting the encryption key. The signing key is used only to
 * authorize the binding; it never performs key agreement or AEAD.
 */
public final class EncryptionKeyBinding {

    /** Domain separator, kept distinct from envelope signatures. */
    private static final byte[] DOMAIN =
            "ResQNet-EncryptionKeyBinding-v1".getBytes(StandardCharsets.US_ASCII);

    private EncryptionKeyBinding() {
    }

    /**
     * Builds the exact bytes signed by the proof.
     *
     * <p>Both keys are length-prefixed so no pair of distinct keys can produce
     * the same encoding by moving bytes across the boundary.
     */
    public static byte[] signedBytes(byte[] signingPublicKeyDer,
                                     byte[] encryptionPublicKey) {
        if (signingPublicKeyDer == null || signingPublicKeyDer.length == 0) {
            throw new IllegalArgumentException("Signing public key is required");
        }
        if (encryptionPublicKey == null
                || encryptionPublicKey.length != EncryptionIdentity.PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("HPKE public key must be "
                    + EncryptionIdentity.PUBLIC_KEY_BYTES + " bytes");
        }
        byte[] out = new byte[DOMAIN.length + 4 + signingPublicKeyDer.length
                + 4 + encryptionPublicKey.length];
        int p = 0;
        System.arraycopy(DOMAIN, 0, out, p, DOMAIN.length);
        p += DOMAIN.length;
        p = writeInt(out, p, signingPublicKeyDer.length);
        System.arraycopy(signingPublicKeyDer, 0, out, p, signingPublicKeyDer.length);
        p += signingPublicKeyDer.length;
        p = writeInt(out, p, encryptionPublicKey.length);
        System.arraycopy(encryptionPublicKey, 0, out, p, encryptionPublicKey.length);
        return out;
    }

    /** Produces a proof for an encryption key held by {@code signingIdentity}. */
    public static byte[] prove(SigningIdentity signingIdentity,
                               byte[] encryptionPublicKey)
            throws GeneralSecurityException {
        return signingIdentity.sign(signedBytes(signingIdentity.publicKey().getEncoded(),
                encryptionPublicKey));
    }

    /**
     * Verifies that {@code signingKey} authorized {@code encryptionPublicKey}.
     *
     * @return false for missing, malformed or cryptographically invalid proofs
     */
    public static boolean verify(PublicKey signingKey, byte[] encryptionPublicKey,
                                 byte[] proof) {
        if (signingKey == null || encryptionPublicKey == null || proof == null
                || proof.length == 0) {
            return false;
        }
        try {
            return SignatureVerifier.verify(signingKey,
                    signedBytes(signingKey.getEncoded(), encryptionPublicKey), proof);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks that the announced signing key decodes and derives the claimed
     * device id. Callers still verify the proof separately.
     */
    public static PublicKey decodeSigningKey(String publicKeyBase64)
            throws GeneralSecurityException {
        PublicKey key = DeviceIdentity.decodePublicKey(publicKeyBase64);
        if (!"EC".equals(key.getAlgorithm())) {
            throw new GeneralSecurityException("Signing key is not EC");
        }
        return key;
    }

    /**
     * Returns the public key fingerprint, used to make sure a proof is checked
     * against the same key the announcement names.
     */
    public static String fingerprint(PublicKey key) {
        return DeviceIdentity.fingerprint(key);
    }

    /** Constant-time comparison used for key identifiers. */
    public static boolean sameKeyId(byte[] left, byte[] right) {
        return left != null && right != null && left.length == right.length
                && MessageDigest.isEqual(left, right);
    }

    /** Convenience for diagnostics and tests. */
    public static boolean samePublicKey(byte[] left, byte[] right) {
        return left != null && right != null && Arrays.equals(left, right);
    }

    private static int writeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 24);
        out[offset + 1] = (byte) (value >>> 16);
        out[offset + 2] = (byte) (value >>> 8);
        out[offset + 3] = (byte) value;
        return offset + 4;
    }
}
