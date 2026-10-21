package dev.resqnet.android.identity;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import dev.resqnet.mesh.crypto.EncryptionIdentity;

/**
 * Loads and seals the long-term HPKE keyset on Android.
 *
 * <p>The platform Keystore does not expose X25519 key agreement, so the HPKE
 * private key cannot live inside secure hardware the way the P-256 signing key
 * does. Instead the Tink keyset is serialized and encrypted with an
 * Android-Keystore AES-256-GCM key before it is written to private
 * {@code SharedPreferences}. On hardware with a secure Keystore the wrapping
 * key does not leave secure hardware, but the unwrapped HPKE keyset necessarily
 * exists in this process while messages are being encrypted or decrypted.
 *
 * <p>This is a local-at-rest boundary, not a claim that the private key is
 * hardware-isolated. The threat model says so explicitly.
 *
 * <p>The stored blob is:
 *
 * <pre>
 *   format version   1 byte
 *   IV length        1 byte
 *   IV               n bytes
 *   AES-GCM output   remaining bytes
 * </pre>
 */
public final class KeystoreEncryptionIdentity {

    private static final String PREFS = "resqnet.crypto";
    private static final String PREF_KEYSET = "hpke.private.keyset.v1";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "resqnet.hpke.wrap.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte FORMAT_VERSION = 1;
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int MIN_IV_BYTES = 12;
    private static final byte[] AAD =
            "ResQNet HPKE private keyset v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private KeystoreEncryptionIdentity() {
    }

    /**
     * Returns the persisted identity, creating and sealing one on first run.
     *
     * <p>Failure to unseal is fatal rather than a reason to generate a new key:
     * silently rotating would make every peer reject encrypted messages and
     * would look like an attack from their side.
     */
    public static synchronized EncryptionIdentity loadOrCreate(Context context)
            throws GeneralSecurityException, IOException {
        Context app = context.getApplicationContext();
        SharedPreferences preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = preferences.getString(PREF_KEYSET, null);
        if (stored != null) {
            byte[] sealed;
            try {
                sealed = Base64.decode(stored, Base64.NO_WRAP);
            } catch (IllegalArgumentException e) {
                throw new GeneralSecurityException("Stored HPKE keyset is not valid Base64", e);
            }
            byte[] keyset = open(sealed);
            try {
                return EncryptionIdentity.fromPrivateKeyset(keyset);
            } finally {
                Arrays.fill(keyset, (byte) 0);
            }
        }

        EncryptionIdentity identity = EncryptionIdentity.generate();
        byte[] keyset = identity.serializePrivateKeyset();
        byte[] sealed;
        try {
            sealed = seal(keyset);
        } finally {
            Arrays.fill(keyset, (byte) 0);
        }
        boolean saved = preferences.edit()
                .putString(PREF_KEYSET, Base64.encodeToString(sealed, Base64.NO_WRAP))
                .commit();
        if (!saved) {
            throw new IOException("Could not persist the HPKE keyset");
        }
        return identity;
    }

    /** True when a sealed keyset has been written by this install. */
    public static boolean exists(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(PREF_KEYSET);
    }

    /** Deletes the HPKE identity and its wrapping key. Used by identity reset. */
    public static synchronized void destroy(Context context) throws GeneralSecurityException,
            IOException {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(PREF_KEYSET).commit();
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS);
        }
    }

    private static byte[] seal(byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey());
        cipher.updateAAD(AAD);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length < MIN_IV_BYTES) {
            throw new GeneralSecurityException("Keystore returned no AES-GCM IV");
        }
        byte[] ciphertext = cipher.doFinal(plaintext);
        ByteArrayOutputStream out = new ByteArrayOutputStream(2 + iv.length + ciphertext.length);
        out.write(FORMAT_VERSION);
        out.write(iv.length);
        out.write(iv, 0, iv.length);
        out.write(ciphertext, 0, ciphertext.length);
        return out.toByteArray();
    }

    private static byte[] open(byte[] sealed) throws GeneralSecurityException {
        if (sealed == null || sealed.length < 2 + MIN_IV_BYTES + GCM_TAG_BITS / 8) {
            throw new GeneralSecurityException("Sealed HPKE keyset is truncated");
        }
        if (sealed[0] != FORMAT_VERSION) {
            throw new GeneralSecurityException("Unsupported sealed keyset format");
        }
        int ivLength = sealed[1] & 0xFF;
        if (ivLength < MIN_IV_BYTES || 2 + ivLength + GCM_TAG_BITS / 8 > sealed.length) {
            throw new GeneralSecurityException("Sealed HPKE keyset has an invalid IV length");
        }
        byte[] iv = Arrays.copyOfRange(sealed, 2, 2 + ivLength);
        byte[] ciphertext = Arrays.copyOfRange(sealed, 2 + ivLength, sealed.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(AAD);
        return cipher.doFinal(ciphertext);
    }

    private static SecretKey wrappingKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        try {
            keyStore.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException("Android Keystore unavailable", e);
        }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry secret) {
                return secret.getSecretKey();
            }
            throw new GeneralSecurityException("HPKE wrapping alias is not a secret key");
        }
        try {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build();
            generator.init(spec);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new GeneralSecurityException("Could not create the HPKE wrapping key", e);
        }
    }

    /** Exposed for diagnostics: alias of the AES key that seals the HPKE keyset. */
    public static String wrappingKeyAlias() {
        return KEY_ALIAS;
    }
}
