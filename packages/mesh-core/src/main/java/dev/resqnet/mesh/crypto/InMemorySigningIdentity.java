package dev.resqnet.mesh.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Objects;

/**
 * {@link SigningIdentity} backed by an ordinary in-memory key pair.
 *
 * <p>Used by the deterministic simulator, by tests, and as the fallback on
 * Android when hardware-backed key generation is unavailable. The Android app
 * substitutes a Keystore-backed implementation; the routing engine cannot tell
 * the difference.
 */
public final class InMemorySigningIdentity implements SigningIdentity {

    private static final String CURVE = "secp256r1";

    private final String deviceId;
    private final String displayName;
    private final KeyPair keyPair;
    private final String keyFingerprint;
    private final String publicKeyBase64;
    private final boolean hardwareBacked;

    private InMemorySigningIdentity(KeyPair keyPair, String displayName, boolean hardwareBacked) {
        this.keyPair = keyPair;
        this.displayName = displayName;
        this.hardwareBacked = hardwareBacked;
        this.keyFingerprint = DeviceIdentity.fingerprint(keyPair.getPublic());
        this.deviceId = DeviceIdentity.deviceId(keyPair.getPublic());
        this.publicKeyBase64 = DeviceIdentity.encodePublicKey(keyPair.getPublic());
    }

    /** Generates a fresh P-256 identity. */
    public static InMemorySigningIdentity generate(String displayName)
            throws GeneralSecurityException {
        return generate(displayName, false);
    }

    /** Generates a fresh P-256 identity, optionally flagged hardware-backed. */
    public static InMemorySigningIdentity generate(String displayName, boolean hardwareBacked)
            throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(DeviceIdentity.KEY_ALGORITHM);
        generator.initialize(new ECGenParameterSpec(CURVE));
        return new InMemorySigningIdentity(generator.generateKeyPair(),
                Objects.requireNonNullElse(displayName, "resqnet-node"), hardwareBacked);
    }

    /**
     * Rehydrates an identity from a persisted PKCS#8 private key plus its
     * matching X.509 public key.
     *
     * <p>Both halves are stored rather than deriving the public point from the
     * private scalar: no JCA API exposes that derivation, and reimplementing
     * elliptic curve arithmetic for a convenience path is not a trade worth
     * making in safety-relevant code.
     */
    public static InMemorySigningIdentity fromKeyPair(String displayName,
                                                       byte[] pkcs8PrivateKey,
                                                       byte[] x509PublicKey)
            throws GeneralSecurityException {
        PrivateKey privateKey = java.security.KeyFactory
                .getInstance(DeviceIdentity.KEY_ALGORITHM)
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pkcs8PrivateKey));
        PublicKey publicKey = java.security.KeyFactory
                .getInstance(DeviceIdentity.KEY_ALGORITHM)
                .generatePublic(new java.security.spec.X509EncodedKeySpec(x509PublicKey));
        return new InMemorySigningIdentity(new KeyPair(publicKey, privateKey),
                displayName, false);
    }

    @Override
    public String deviceId() {
        return deviceId;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String keyFingerprint() {
        return keyFingerprint;
    }

    @Override
    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    @Override
    public String publicKeyBase64() {
        return publicKeyBase64;
    }

    @Override
    public byte[] sign(byte[] data) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(DeviceIdentity.SIGNATURE_ALGORITHM);
        signature.initSign(keyPair.getPrivate());
        signature.update(data);
        return signature.sign();
    }

    @Override
    public boolean hardwareBacked() {
        return hardwareBacked;
    }

    @Override
    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    @Override
    public String toString() {
        return displayName + "[" + DeviceIdentity.shortDeviceId(deviceId) + "]";
    }
}