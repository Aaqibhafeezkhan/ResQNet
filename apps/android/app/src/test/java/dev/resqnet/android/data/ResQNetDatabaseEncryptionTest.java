package dev.resqnet.android.data;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.ContentValues;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;

/** Schema v2 persistence: proof-bound peer HPKE keys and sender-local bodies. */
@RunWith(RobolectricTestRunner.class)
public class ResQNetDatabaseEncryptionTest {

    private ResQNetDatabase database;
    private String deviceId;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        database = ResQNetDatabase.openIsolated(context);
        InMemorySigningIdentity identity = InMemorySigningIdentity.generate("peer");
        deviceId = identity.deviceId();
        database.savePeer(TrustStore.entryOf(identity.publicKey(), "peer",
                TrustStore.Trust.VERIFIED, Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(2_000L)));
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void peerEncryptionKeyRoundTripsThroughSqlite() {
        byte[] publicKey = new byte[32];
        for (int i = 0; i < publicKey.length; i++) {
            publicKey[i] = (byte) (i + 1);
        }
        TrustStore.EncryptionKey key = TrustStore.encryptionKeyOf(deviceId, publicKey,
                Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(3_000L));

        database.saveEncryptionKey(key);
        List<TrustStore.EncryptionKey> loaded = database.loadEncryptionKeys();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).deviceId()).isEqualTo(deviceId);
        assertThat(loaded.get(0).publicKey()).isEqualTo(publicKey);
        assertThat(loaded.get(0).keyId()).isEqualTo(key.keyId());
        assertThat(loaded.get(0).lastSeen()).isEqualTo(Instant.ofEpochMilli(3_000L));
    }

    @Test
    public void refreshingAPeerPreservesItsEncryptionKey() throws Exception {
        byte[] publicKey = new byte[32];
        database.saveEncryptionKey(TrustStore.encryptionKeyOf(deviceId, publicKey,
                Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(2_000L)));
        TrustStore.Entry original = database.loadPeer(deviceId);

        database.savePeer(TrustStore.entryOf(original.publicKey(), original.label(),
                TrustStore.Trust.VERIFIED, original.firstSeen(),
                Instant.ofEpochMilli(9_000L)));

        assertThat(database.loadEncryptionKeys()).hasSize(1);
        assertThat(database.loadPeer(deviceId).trust()).isEqualTo(TrustStore.Trust.VERIFIED);
    }

    @Test
    public void deletingAPeerCascadesItsEncryptionKey() {
        database.saveEncryptionKey(TrustStore.encryptionKeyOf(deviceId, new byte[32],
                Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(2_000L)));

        database.deletePeer(deviceId);

        assertThat(database.loadEncryptionKeys()).isEmpty();
        assertThat(database.loadPeer(deviceId)).isNull();
    }

    @Test
    public void outgoingLocalBodyIsStoredSeparatelyFromTheEnvelope() {
        insertMessage("m1");

        database.saveLocalBody("m1", "private outgoing text".getBytes(StandardCharsets.UTF_8));

        assertThat(database.loadLocalBody("m1"))
                .isEqualTo("private outgoing text".getBytes(StandardCharsets.UTF_8));
        assertThat(database.loadLocalBody("missing")).isNull();
    }

    private void insertMessage(String messageId) {
        ContentValues values = new ContentValues();
        values.put("message_id", messageId);
        values.put("envelope", new byte[] {1, 2, 3});
        values.put("state", "QUEUED");
        values.put("message_type", "TEXT");
        values.put("priority", "NORMAL");
        values.put("created_at", 1_000L);
        values.put("expires_at", 60_000L);
        database.getWritableDatabase().insertOrThrow(ResQNetDatabase.TABLE_MESSAGES,
                null, values);
    }
}
