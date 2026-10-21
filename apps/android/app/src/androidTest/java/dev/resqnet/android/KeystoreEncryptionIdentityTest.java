package dev.resqnet.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import dev.resqnet.android.identity.KeystoreEncryptionIdentity;
import dev.resqnet.mesh.crypto.EncryptionIdentity;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The HPKE keyset must be sealed at rest and survive a cold load.
 *
 * <p>Destroying the keyset here would invalidate the singleton engine used by
 * the other instrumentation tests, so this covers the persistence half. Reset
 * destruction is exercised manually through Settings and is fail-closed.
 */
@RunWith(AndroidJUnit4.class)
public class KeystoreEncryptionIdentityTest {

    @Test
    public void keysetReloadsWithTheSamePublicKey() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        EncryptionIdentity first = KeystoreEncryptionIdentity.loadOrCreate(context);
        EncryptionIdentity second = KeystoreEncryptionIdentity.loadOrCreate(context);

        assertArrayEquals(first.publicKeyBytes(), second.publicKeyBytes());
        assertEquals(first.keyId(), second.keyId());
        assertTrue(KeystoreEncryptionIdentity.exists(context));
    }
}
