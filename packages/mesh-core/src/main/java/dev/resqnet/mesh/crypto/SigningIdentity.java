package dev.resqnet.mesh.crypto;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Signing capability of one device.
 *
 * <p>Kept as an interface so the Android app can back it with an Android
 * Keystore key that never leaves secure hardware, while the simulator and tests
 * use an in-memory key. The routing engine only ever sees this interface, so
 * signing behaviour is identical in both environments.
 */
public interface SigningIdentity extends PublicKeyHolder {

    /** Stable, key-derived device identifier. Never a radio endpoint id. */
    @Override
    String deviceId();

    /** Human readable label shown during out-of-band verification. */
    String displayName();

    /** SHA-256 fingerprint of the public key, lowercase hex. */
    @Override
    String keyFingerprint();

    @Override
    PublicKey publicKey();

    /** Base64 X.509 encoding of the public key, safe for JSON transport. */
    String publicKeyBase64();

    /**
     * Signs arbitrary bytes.
     *
     * @throws GeneralSecurityException when the secure key store refuses to sign
     */
    byte[] sign(byte[] data) throws GeneralSecurityException;

    /** Signature algorithm label recorded on every signed envelope. */
    default String signatureAlgorithm() {
        return DeviceIdentity.SIGNATURE_ALGORITHM;
    }

    /** True when the private key is held in hardware-backed secure storage. */
    boolean hardwareBacked();

    /**
     * Verifies a signature against an arbitrary public key.
     *
     * <p>Static helper duplicated here so callers holding only an identity do not
     * need to import the JCA directly.
     */
    static boolean verify(PublicKey key, byte[] data, byte[] signature)
            throws GeneralSecurityException {
        return SignatureVerifier.verify(key, data, signature);
    }

    /** Exposes the private key. Only for persistence inside trusted storage. */
    PrivateKey privateKey();
}