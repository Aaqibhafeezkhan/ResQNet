package dev.resqnet.mesh.crypto;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * Stable device identifiers derived from public keys.
 *
 * <p>This is the crux of ResQNet identity. A Nearby Connections endpoint id is
 * a per-session radio handle that changes when a device reboots, moves between
 * radios, or when the OS decides it has. It must never be used as an identity,
 * for routing, or for authorisation. The device id here is instead the SHA-256
 * fingerprint of the long-term public key, so it survives reinstalls of the
 * transport stack and is verifiable by anyone who has seen the key once.
 */
public final class DeviceIdentity {

    /** Signature algorithm this engine produces and verifies. */
    public static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /** Curve used for device keys. Supported by the JDK and by Android API 23+. */
    public static final String KEY_ALGORITHM = "EC";

    private DeviceIdentity() {
    }

    /**
     * Computes the device id: lowercase hex SHA-256 over the X.509 encoding of
     * the public key.
     *
     * <p>Prefixing with {@code rqn1_} makes the value self-describing in logs
     * and prevents accidental confusion with a Nearby endpoint id, which is a
     * bare 16-hex-digit string.
     */
    public static String deviceId(PublicKey publicKey) {
        requireEc(publicKey);
        return "rqn1_" + fingerprint(publicKey);
    }

    /** Returns the bare 64 character lowercase hex SHA-256 fingerprint. */
    public static String fingerprint(PublicKey publicKey) {
        requireEc(publicKey);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(publicKey.getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Short human-comparable form, e.g. {@code rqn1_a1b2c3d4}. */
    public static String shortDeviceId(String deviceId) {
        if (deviceId == null) {
            return "unknown";
        }
        if (deviceId.length() <= 13) {
            return deviceId;
        }
        return deviceId.substring(0, 13);
    }

    /** Base64 (no padding line breaks) of the X.509 public key, for transport. */
    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Decodes a Base64 X.509 public key.
     *
     * @throws GeneralSecurityException when the bytes are not a valid EC key
     */
    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(base64);
        return java.security.KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(new java.security.spec.X509EncodedKeySpec(der));
    }

    /**
     * Lowercase hex encoding.
     *
     * <p>Hand-rolled because {@code java.util.HexFormat} is a Java 17 API that the
     * Android platform does not ship.
     */
    static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0F];
        }
        return new String(out);
    }

    private static void requireEc(PublicKey publicKey) {
        if (!(publicKey instanceof ECPublicKey)) {
            throw new IllegalArgumentException(
                    "ResQNet device keys must be EC P-256, got " + publicKey.getAlgorithm());
        }
    }
}