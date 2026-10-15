package dev.resqnet.android.identity;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import dev.resqnet.mesh.crypto.DeviceIdentity;
import dev.resqnet.mesh.crypto.SigningIdentity;

/**
 * Device identity backed by the Android Keystore.
 *
 * <p>The private key is generated inside the keystore and, on hardware that
 * supports it, never enters the application process at all. Signing is performed
 * by the keystore itself. That matters because a lost handset is the realistic
 * threat here: a key in {@code SharedPreferences} is extractable by anyone with
 * physical access and five minutes, and a stolen identity can be used to make a
 * missing person look like they sent an all-clear.
 *
 * <p>Where the keystore cannot provide hardware backing, the key is still
 * generated inside the keystore and {@link #hardwareBacked()} reports {@code
 * false}, which the UI surfaces rather than hiding.
 *
 * <p>Requires API 23 or newer, which is below this project's {@code minSdk} of 26.
 */
public final class KeystoreIdentity implements SigningIdentity {

    /** Keystore provider available on every supported Android release. */
    private static final String PROVIDER = "AndroidKeyStore";

    /** Alias under which the device key is stored. */
    private static final String KEY_ALIAS = "resqnet.device.identity.v1";

    /** Tag written by this class, so we never adopt a foreign key under this alias. */
    private static final String PURPOSE = "ResQNet device identity: signs mesh envelopes. " +
            "Deleting this key permanently changes this device's identity.";

    private final String displayName;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final String keyFingerprint;
    private final String deviceId;
    private final String publicKeyBase64;
    private final boolean hardwareBacked;

    private KeystoreIdentity(String displayName, PublicKey publicKey, PrivateKey privateKey,
                             boolean hardwareBacked) {
        this.displayName = displayName;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.keyFingerprint = DeviceIdentity.fingerprint(publicKey);
        this.deviceId = DeviceIdentity.deviceId(publicKey);
        this.publicKeyBase64 = DeviceIdentity.encodePublicKey(publicKey);
        this.hardwareBacked = hardwareBacked;
    }

    /**
     * Loads the existing device identity, creating it on first run.
     *
     * <p>Regenerating a key is never done implicitly. A new key means a new
     * device id, which means every previously verified peer no longer
     * recognises this device, so the change is an explicit, user-confirmed
     * operation.
     *
     * @param displayName human label shown during in-person verification
     */
    public static KeystoreIdentity loadOrCreate(String displayName)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);

        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.PrivateKeyEntry privateKeyEntry) {
            PrivateKey privateKey = privateKeyEntry.getPrivateKey();
            PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();
            return new KeystoreIdentity(displayName, publicKey, privateKey,
                    isHardwareBacked(keyStore, KEY_ALIAS));
        }

        KeyPair keyPair = generateKeyPair();
        return new KeystoreIdentity(displayName, keyPair.getPublic(), keyPair.getPrivate(),
                isHardwareBacked(keyStore, KEY_ALIAS));
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, PROVIDER);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(new byte[] { 'R', 'e', 's', 'Q', 'N', 'e', 't' })
                .build();
        generator.initialize(spec);
        return generator.generateKeyPair();
    }

    /**
     * Permanently destroys this device's identity.
     *
     * <p>Only reachable from an explicit, confirmed user action in settings,
     * because it invalidates every verification this device has ever made.
     */
    public static void destroy() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        keyStore.deleteEntry(KEY_ALIAS);
    }

    /**
     * Reports whether the key is held in secure hardware.
     *
     * <p>Guarded by {@link KeyStore.Entry#isHardwareBacked()} which only exists
     * from API 31. On older releases this reports the security level of the
     * provider instead, which is the best available answer.
     */
    private static boolean isHardwareBacked(KeyStore keyStore, String alias) {
        try {
            KeyStore.Entry entry = keyStore.getEntry(alias, null);
            if (!(entry instanceof KeyStore.PrivateKeyEntry privateKeyEntry)) {
                return false;
            }
            android.security.keystore.KeyInfo info = (android.security.keystore.KeyInfo)
                    KeyFactory.getInstance(
                            privateKeyEntry.getPrivateKey().getAlgorithm(), PROVIDER)
                            .getKeySpec(privateKeyEntry.getPrivateKey(),
                                    android.security.keystore.KeyInfo.class);
            return info != null && info.isInsideSecureHardware();
        } catch (GeneralSecurityException | ClassCastException e) {
            // Some providers do not expose a KeySpec. Reporting "not hardware
            // backed" is the safe answer: the settings screen tells the user the
            // truth rather than claiming a protection that may not be there.
            return false;
        }
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
        return publicKey;
    }

    @Override
    public String publicKeyBase64() {
        return publicKeyBase64;
    }

    @Override
    public byte[] sign(byte[] data) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(DeviceIdentity.SIGNATURE_ALGORITHM);
        // The Keystore-backed private key requires an explicit initialisation
        // with this operation before it will sign.
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    @Override
    public boolean hardwareBacked() {
        return hardwareBacked;
    }

    @Override
    public PrivateKey privateKey() {
        return privateKey;
    }

    /** The alias under which the key is stored, for diagnostics screens. */
    public static String keyAlias() {
        return KEY_ALIAS;
    }

    /** Whether a key already exists, used to avoid creating one on the welcome screen. */
    public static boolean exists() {
        try {
            KeyStore keyStore = KeyStore.getInstance(PROVIDER);
            keyStore.load(null);
            return keyStore.containsAlias(KEY_ALIAS);
        } catch (GeneralSecurityException | IOException e) {
            return false;
        }
    }
}