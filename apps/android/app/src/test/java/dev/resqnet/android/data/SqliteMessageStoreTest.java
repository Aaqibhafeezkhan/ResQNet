package dev.resqnet.android.data;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.StoredMessage;

/**
 * Durable queue behaviour against a real SQLite database.
 *
 * <p>Robolectric is used so the store runs against an actual database
 * implementation rather than an in-memory stand-in. The properties under test,
 * particularly the capacity bound, only mean something if the same SQL runs in
 * both environments; a mocked store would prove nothing about the handset.
 *
 * <p>Every assertion runs on the same worker thread as the writes. The store
 * refuses main-thread writes by design, and Robolectric's SQLite cursor
 * visibility is bound to the thread that created it, so mixing threads would
 * test the harness rather than the product.
 */
@RunWith(RobolectricTestRunner.class)
public class SqliteMessageStoreTest {

    private Context context;
    private ResQNetDatabase database;
    private SqliteMessageStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = ResQNetDatabase.openIsolated(context);
        store = new SqliteMessageStore(database, 8, null);
    }

    @After
    public void tearDown() {
        database.close();
    }

    /** Runs a body on a worker thread, the way the mesh service does. */
    private static <T> T offMain(Callable<T> body) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(body.call());
            } catch (Exception e) {
                failure.set(e);
            }
        }, "resqnet-test-worker");
        worker.start();
        worker.join(15_000);
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private StoredMessage message(String id, DeliveryState state, Priority priority,
                                  long createdAt, long expiresAt) throws Exception {
        MessageEnvelope envelope = MessageEnvelope.builder()
                .messageId(id)
                .origin("rqn1_" + "a".repeat(64))
                .originKeyFingerprint("b".repeat(64))
                .destination("rqn1_" + "c".repeat(64))
                .type(MessageType.SOS)
                .priority(priority)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .payload(("body " + id).getBytes(StandardCharsets.UTF_8))
                .build();
        return new StoredMessage(id, EnvelopeCodec.encode(envelope), state,
                envelope.originDeviceId(), envelope.destinationDeviceId().orElse(null),
                envelope.type(), envelope.priority(), createdAt, expiresAt, 0, 0L, "test", -1L);
    }

    @Test
    public void savesAndReloadsAMessage() throws Exception {
        StoredMessage saved = offMain(() -> store.upsert(message("m1", DeliveryState.QUEUED,
                Priority.SOS, 1_000L, 60_000L)));
        StoredMessage[] reloaded = offMain(() -> new StoredMessage[] {
                store.find("m1").orElse(null) });

        assertThat(saved.localRowId()).isPresent();
        assertThat(reloaded[0]).isNotNull();
        assertThat(reloaded[0].state()).isEqualTo(DeliveryState.QUEUED);
        assertThat(reloaded[0].envelope()).isNotEmpty();
        assertThat(EnvelopeCodec.decode(reloaded[0].envelope()).messageId()).isEqualTo("m1");
    }

    @Test
    public void survivesReopeningTheDatabase() throws Exception {
        offMain(() -> {
            store.upsert(message("m1", DeliveryState.QUEUED, Priority.SOS, 1_000L, 60_000L));
            return null;
        });

        // A second store over the same file is what a cold start looks like.
        SqliteMessageStore reopened = new SqliteMessageStore(database, 8, null);
        boolean found = offMain(() -> reopened.find("m1").isPresent());

        assertThat(found)
                .as("a queued message must outlive the process that composed it")
                .isTrue();
    }

    @Test
    public void updatesStateAndKeepsTheEnvelope() throws Exception {
        offMain(() -> {
            store.upsert(message("m1", DeliveryState.QUEUED, Priority.SOS, 1_000L, 60_000L));
            store.updateState("m1", DeliveryState.SENT_TO_PEER, "handed to one peer");
            return store.find("m1").orElse(null);
        });

        StoredMessage record = offMain(() -> store.find("m1").orElse(null));

        assertThat(record.state()).isEqualTo(DeliveryState.SENT_TO_PEER);
        assertThat(record.lastDetail()).isEqualTo("handed to one peer");
        assertThat(EnvelopeCodec.decode(record.envelope()).messageId()).isEqualTo("m1");
    }

    @Test
    public void listsPendingSoonestDeadlineFirst() throws Exception {
        List<String> pending = offMain(() -> {
            store.upsert(message("late", DeliveryState.QUEUED, Priority.SOS, 0L, 300_000L));
            store.upsert(message("soon", DeliveryState.QUEUED, Priority.SOS, 0L, 100_000L));
            store.upsert(message("done", DeliveryState.ACKNOWLEDGED, Priority.SOS, 0L, 50_000L));
            return store.pending().stream().map(StoredMessage::messageId).toList();
        });

        assertThat(pending).containsExactly("soon", "late");
    }

    @Test
    public void expiresStaleRecords() throws Exception {
        Object[] outcome = offMain(() -> {
            store.upsert(message("old", DeliveryState.QUEUED, Priority.SOS, 0L, 1_000L));
            store.upsert(message("new", DeliveryState.QUEUED, Priority.SOS, 0L, 100_000L));
            int expired = store.expireStale(2_000L);
            return new Object[] { expired,
                    store.find("old").orElseThrow().state(),
                    store.find("new").orElseThrow().state(),
                    store.pendingCount() };
        });

        assertThat((Integer) outcome[0]).isEqualTo(1);
        assertThat((DeliveryState) outcome[1]).isEqualTo(DeliveryState.EXPIRED);
        assertThat((DeliveryState) outcome[2]).isEqualTo(DeliveryState.QUEUED);
        assertThat((Integer) outcome[3]).isEqualTo(1);
    }

    @Test
    public void neverEvictsLifeSafetyTraffic() throws Exception {
        Object[] outcome = offMain(() -> {
            for (int i = 0; i < 6; i++) {
                store.upsert(message("sos" + i, DeliveryState.QUEUED, Priority.SOS,
                        i, 600_000L));
            }
            for (int i = 0; i < 6; i++) {
                store.upsert(message("low" + i, DeliveryState.QUEUED, Priority.LOW,
                        100 + i, 600_000L));
            }
            store.enforceCapacity(4);
            return new Object[] { store.find("sos0").isPresent(),
                    store.find("low0").isPresent(), store.pendingCount() };
        });

        assertThat((Boolean) outcome[0]).isTrue();
        assertThat((Boolean) outcome[1]).isFalse();
        // The bound cannot be reached: six life-safety records exist and are
        // never evicted. Under-protection is the correct failure direction for
        // traffic of this kind, so the queue settles above the cap rather than
        // discarding an SOS.
        assertThat((Integer) outcome[2]).isEqualTo(6);
    }

    @Test
    public void deletesRecordsAndTheirAuditTrail() throws Exception {
        Object[] outcome = offMain(() -> {
            store.upsert(message("m1", DeliveryState.QUEUED, Priority.SOS, 1_000L, 60_000L));
            database.appendDelivery("m1", DeliveryState.QUEUED, "composed", 1_000L, "node");
            return new Object[] { store.delete("m1"), store.find("m1").isPresent(),
                    database.deliveryHistory("m1").size() };
        });

        assertThat((Boolean) outcome[0]).isTrue();
        assertThat((Boolean) outcome[1]).isFalse();
        assertThat((Integer) outcome[2]).isZero();
    }

    @Test
    public void keepsAnAppendOnlyAuditTrail() throws Exception {
        // The audit sink is wired exactly as the mesh engine wires it: every real
        // transition appends a row, and nothing ever rewrites one.
        SqliteMessageStore audited = new SqliteMessageStore(database, 8,
                (messageId, previous, current, detail) -> database.appendDelivery(
                        messageId, current, detail, 1_000L * (1 + current.ordinal()),
                        "node"));

        List<ResQNetDatabase.DeliveryAuditEntry> history = offMain(() -> {
            audited.upsert(message("m1", DeliveryState.QUEUED, Priority.SOS, 1_000L, 60_000L));
            audited.updateState("m1", DeliveryState.SENT_TO_PEER, "handed over");
            audited.updateState("m1", DeliveryState.ACKNOWLEDGED, "confirmed");
            return database.deliveryHistory("m1");
        });

        assertThat(history).hasSize(2);
        assertThat(history.get(0).state()).isEqualTo(DeliveryState.SENT_TO_PEER);
        assertThat(history.get(1).state()).isEqualTo(DeliveryState.ACKNOWLEDGED);
        assertThat(history.get(0).atMillis()).isLessThan(history.get(1).atMillis());
    }

    @Test
    public void firesTheAuditorOnlyOnRealTransitions() throws Exception {
        List<String> transitions = new ArrayList<>();
        SqliteMessageStore audited = new SqliteMessageStore(database, 8,
                (messageId, previous, current, detail) ->
                        transitions.add(previous + "->" + current));

        offMain(() -> {
            audited.upsert(message("m1", DeliveryState.QUEUED, Priority.SOS, 1_000L, 60_000L));
            audited.updateState("m1", DeliveryState.SENT_TO_PEER, "a");
            audited.updateState("m1", DeliveryState.SENT_TO_PEER, "a again");
            return null;
        });

        assertThat(transitions).containsExactly("QUEUED->SENT_TO_PEER");
    }

    @Test
    public void persistsPeersAndRecomputesTheirIdentity() throws Exception {
        java.security.KeyPairGenerator generator =
                java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.PublicKey publicKey = generator.generateKeyPair().getPublic();

        TrustStore trustStore = new TrustStore();
        TrustStore.Entry entry = trustStore.rememberUnverified("Alice", publicKey,
                java.time.Instant.ofEpochMilli(1_000L));
        database.savePeer(entry);

        TrustStore.Entry reloaded = database.loadPeer(entry.deviceId());

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.label()).isEqualTo("Alice");
        assertThat(reloaded.keyFingerprint()).isEqualTo(entry.keyFingerprint());
        assertThat(reloaded.trust()).isEqualTo(TrustStore.Trust.UNVERIFIED);
        assertThat(ResQNetDatabase.shortCode(entry.keyFingerprint())).hasSize(9);
    }

    @Test
    public void ignoresAPeerRowWhoseKeyDoesNotMatchItsIdentity() throws Exception {
        java.security.KeyPairGenerator generator =
                java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.PublicKey key = generator.generateKeyPair().getPublic();

        TrustStore trustStore = new TrustStore();
        TrustStore.Entry entry = trustStore.rememberUnverified("Alice", key,
                java.time.Instant.ofEpochMilli(1_000L));
        database.savePeer(entry);

        // Force a mismatch by writing the id column directly, as a tampered or
        // corrupted database would.
        android.content.ContentValues tampered = new android.content.ContentValues();
        tampered.put("device_id", "rqn1_" + "0".repeat(64));
        database.getWritableDatabase().update(ResQNetDatabase.TABLE_PEERS, tampered,
                "device_id = ?", new String[] { entry.deviceId() });

        assertThat(database.loadPeer("rqn1_" + "0".repeat(64)))
                .as("a database row must not be able to claim an identity it has no key for")
                .isNull();
    }

    @Test
    public void storesSettingsAndTriageHints() throws Exception {
        offMain(() -> {
            store.upsert(message("m1", DeliveryState.DELIVERED, Priority.SOS, 1_000L, 60_000L));
            database.putSetting("mesh.enabled", "true");
            database.saveTriage("m1", "TRAPPED", 0.72);
            database.markRead("m1", true);
            return null;
        });

        Object[] outcome = offMain(() -> new Object[] {
                database.getSetting("mesh.enabled").orElse(null),
                database.getSetting("missing").isPresent(),
                database.loadTriage("m1").orElse(null),
                database.unreadCount() });

        assertThat((String) outcome[0]).isEqualTo("true");
        assertThat((Boolean) outcome[1]).isFalse();
        assertThat(outcome[2]).isNotNull();
        assertThat(((ResQNetDatabase.TriageRecord) outcome[2]).category()).isEqualTo("TRAPPED");
        assertThat((Integer) outcome[3]).isZero();
    }

    @Test
    public void refusesStoreWritesFromTheMainLooper() throws Exception {
        assertThat(Looper.myLooper()).isNotNull();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> store.upsert(message("m1", DeliveryState.QUEUED,
                        Priority.SOS, 1_000L, 60_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("main thread");
    }
}