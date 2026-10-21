package dev.resqnet.mesh.crypto;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.hybrid.HpkeParameters;
import com.google.crypto.tink.hybrid.HpkePrivateKey;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.util.Bytes;

import dev.resqnet.mesh.protocol.ProtectedPayload;

/**
 * Long-term HPKE identity used to encrypt directed mesh messages.
 *
 * <p>This is deliberately separate from the signing identity. The router signs
 * envelopes with a non-exportable Android Keystore P-256 key, while HPKE needs
 * X25519 operations that the platform Keystore cannot perform. Reusing one key
 * for both would also cross protocol domains, which is exactly the kind of
 * shortcut that turns two sound primitives into one unsound construction.
 *
 * <p>The suite is HPKE base mode with DHKEM(X25519, HKDF-SHA256),
 * HKDF-SHA256 and AES-256-GCM. Leaves are serialized by Tink rather than by
 * hand, so key parsing, length checks and algorithm identifiers are the
 * library's responsibility.
 */
public final class EncryptionIdentity {

    /** The only HPKE suite this protocol version accepts. */
    public static final HpkeParameters PARAMETERS = parameters();

    /** Raw X25519 public key length. */
    public static final int PUBLIC_KEY_BYTES = 32;

    /** Raw key identifier length. */
    public static final int KEY_ID_BYTES = 32;

    private static final Object REGISTER_LOCK = new Object();
    private static volatile boolean registered;

    private static HpkeParameters parameters() {
        try {
            return HpkeParameters.builder()
                    .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                    .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                    .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                    .setVariant(HpkeParameters.Variant.NO_PREFIX)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final KeysetHandle handle;
    private final byte[] publicKey;
    private final String keyId;

    private EncryptionIdentity(KeysetHandle handle, byte[] publicKey) {
        this.handle = handle;
        this.publicKey = publicKey.clone();
        this.keyId = keyId(publicKey);
    }

    /** Generates a fresh X25519 HPKE identity using the platform CSPRNG. */
    public static EncryptionIdentity generate() throws GeneralSecurityException {
        ensureRegistered();
        KeysetHandle handle = KeysetHandle.generateNew(PARAMETERS,
                RegistryConfiguration.get());
        return new EncryptionIdentity(handle, rawPublicKey(handle));
    }

    /**
     * Rehydrates an identity from a serialized Tink private keyset.
     *
     * <p>The caller is responsible for protecting these bytes at rest. On
     * Android they are sealed by an Android Keystore AES-GCM key before they
     * touch disk.
     */
    public static EncryptionIdentity fromPrivateKeyset(byte[] serialized)
            throws GeneralSecurityException {
        ensureRegistered();
        if (serialized == null || serialized.length == 0) {
            throw new GeneralSecurityException("Empty HPKE keyset");
        }
        KeysetHandle handle = TinkProtoKeysetFormat.parseKeyset(serialized,
                InsecureSecretKeyAccess.get(), RegistryConfiguration.get());
        if (!(handle.getPrimary().getKey() instanceof HpkePrivateKey privateKey)) {
            throw new GeneralSecurityException("Keyset does not contain an HPKE private key");
        }
        if (!PARAMETERS.equals(privateKey.getParameters())) {
            throw new GeneralSecurityException(
                    "Unsupported HPKE parameters in stored keyset");
        }
        return new EncryptionIdentity(handle, privateKey.getPublicKey()
                .getPublicKeyBytes().toByteArray());
    }

    /**
     * Serializes the private keyset for sealed storage.
     *
     * <p>Treat the returned array as secret material: zero it after use where
     * the platform allows it, and never log it.
     */
    public byte[] serializePrivateKeyset() throws GeneralSecurityException {
        return TinkProtoKeysetFormat.serializeKeyset(handle,
                InsecureSecretKeyAccess.get(), RegistryConfiguration.get());
    }

    /** Raw 32-byte X25519 public key published in identity announcements. */
    public byte[] publicKeyBytes() {
        return publicKey.clone();
    }

    /** Stable lowercase-hex SHA-256 identifier of the public key. */
    public String keyId() {
        return keyId;
    }

    /** Computes the key id for a raw public key. */
    public static String keyId(byte[] publicKey) {
        return DeviceIdentity.toHex(keyIdBytes(publicKey));
    }

    /** Raw SHA-256 identifier of an HPKE public key. */
    public static byte[] keyIdBytes(byte[] publicKey) {
        if (publicKey == null || publicKey.length != PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("HPKE public key must be "
                    + PUBLIC_KEY_BYTES + " bytes");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(publicKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Encrypts one plaintext to a recipient's raw public key.
     *
     * @param recipientPublicKey raw 32-byte X25519 key from a verified
     *     identity announcement
     * @param contextInfo HPKE application context; callers pass the canonical
     *     associated data from {@link ProtectedPayload}
     */
    public static byte[] encryptTo(byte[] recipientPublicKey, byte[] plaintext,
                                   byte[] contextInfo) throws GeneralSecurityException {
        ensureRegistered();
        if (recipientPublicKey == null || recipientPublicKey.length != PUBLIC_KEY_BYTES) {
            throw new GeneralSecurityException("Recipient HPKE key must be "
                    + PUBLIC_KEY_BYTES + " bytes");
        }
        if (plaintext == null || plaintext.length == 0) {
            throw new GeneralSecurityException("Refusing to encrypt an empty payload");
        }
        if (plaintext.length > ProtectedPayload.MAX_PLAINTEXT_BYTES) {
            throw new GeneralSecurityException("Plaintext exceeds protected payload limit");
        }
        KeysetHandle publicKeyset = publicKeyset(recipientPublicKey);
        HybridEncrypt hybrid = publicKeyset.getPrimitive(
                RegistryConfiguration.get(), HybridEncrypt.class);
        return hybrid.encrypt(plaintext,
                contextInfo == null ? new byte[0] : contextInfo);
    }

    /** Decrypts one ciphertext addressed to this identity. */
    public byte[] decrypt(byte[] ciphertext, byte[] contextInfo)
            throws GeneralSecurityException {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new GeneralSecurityException("Empty HPKE ciphertext");
        }
        ensureRegistered();
        HybridDecrypt hybrid = handle.getPrimitive(
                RegistryConfiguration.get(), HybridDecrypt.class);
        return hybrid.decrypt(ciphertext, contextInfo == null ? new byte[0] : contextInfo);
    }

    /** Builds a Tink public keyset handle from a raw X25519 public key. */
    private static KeysetHandle publicKeyset(byte[] rawPublicKey)
            throws GeneralSecurityException {
        HpkePublicKey publicKey = HpkePublicKey.create(PARAMETERS,
                Bytes.copyFrom(Arrays.copyOf(rawPublicKey, rawPublicKey.length)), null);
        return KeysetHandle.newBuilder()
                .addEntry(KeysetHandle.importKey(publicKey)
                        .withRandomId()
                        .makePrimary())
                .build();
    }

    private static byte[] rawPublicKey(KeysetHandle privateKeyset)
            throws GeneralSecurityException {
        if (!(privateKeyset.getPrimary().getKey() instanceof HpkePrivateKey privateKey)) {
            throw new GeneralSecurityException("Generated keyset is not HPKE");
        }
        return privateKey.getPublicKey().getPublicKeyBytes().toByteArray();
    }

    private static void ensureRegistered() throws GeneralSecurityException {
        if (registered) {
            return;
        }
        synchronized (REGISTER_LOCK) {
            if (!registered) {
                HybridConfig.register();
                registered = true;
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EncryptionIdentity identity
                && keyId.equals(identity.keyId);
    }

    @Override
    public int hashCode() {
        return keyId.hashCode();
    }

    @Override
    public String toString() {
        return "EncryptionIdentity{" + keyId.substring(0, 16) + "}";
    }
}
