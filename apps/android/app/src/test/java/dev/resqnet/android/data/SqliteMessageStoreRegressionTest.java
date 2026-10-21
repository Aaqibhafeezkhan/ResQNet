package dev.resqnet.android.data;

import static org.assertj.core.api.Assertions.assertThat;

import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.StoredMessage;

/**
 * Regressions for defects found while auditing the app for its first hardware
 * test. Each was found by reading, and each is the kind of bug a unit test
 * written alongside the original code would not have caught.
 */
@RunWith(RobolectricTestRunner.class)
public class SqliteMessageStoreRegressionTest {

    private ResQNetDatabase database;
    private SqliteMessageStore store;

    @Before
    public void setUp() {
        database = ResQNetDatabase.openIsolated(ApplicationProvider.getApplicationContext());
        store = new SqliteMessageStore(database, 16,
                (messageId, previous, current, detail) -> { });
    }

    @After
    public void tearDown() throws Exception {
        try {
            offMain(() -> {
                store.delete("msg-regression");
                store.delete("msg-regression-second");
                return null;
            });
        } finally {
            database.close();
        }
    }

    @Test
    public void upsertPreservesReadFlag() throws Exception {
        // Every retry sweep calls upsert on a record it already holds. Under
        // CONFLICT_REPLACE the row was deleted and reinserted, which reset
        // `read` to its column default, so a message the user had opened came
        // back as unread every five seconds for as long as it lived.
        upsert(message());
        assertThat(isRead())
                .as("the message starts unread")
                .isFalse();

        // The user opens it. Opening a message is a UI concern and writes
        // straight to the row, which is exactly the kind of column upsert has
        // no business resetting.
        markRead();
        assertThat(isRead()).as("the message is now read").isTrue();

        StoredMessage first = find("msg-regression");
        upsert(first.withRetry(1, System.currentTimeMillis() + 5000));

        assertThat(isRead())
                .as("a read message must stay read across an unrelated retry sweep")
                .isTrue();
    }

    @Test
    public void upsertKeepsRowIdentityStable() throws Exception {
        upsert(message());
        upsert(secondMessage());

        long originalId = find("msg-regression-second").localId();

        StoredMessage stored = find("msg-regression-second");
        StoredMessage updated = upsert(
                stored.withRetry(1, System.currentTimeMillis() + 5000));

        assertThat(updated.localId())
                .as("upsert must return the existing message's local_id")
                .isEqualTo(originalId);

        assertThat(find("msg-regression-second").localId())
                .as("upsert must preserve the message's row identity")
                .isEqualTo(originalId);
    }


    @Test
    public void upsertPreservesTriage() throws Exception {
        upsert(message());
        database.getWritableDatabase().execSQL(
                "UPDATE " + ResQNetDatabase.TABLE_MESSAGES
                        + " SET triage_category = ?, triage_confidence = ?"
                        + " WHERE message_id = ?",
                new Object[] { "medical", 0.85, "msg-regression" });

        StoredMessage stored = find("msg-regression");
        upsert(stored.withRetry(1, System.currentTimeMillis() + 5000));

        assertThat(triageOf("msg-regression"))
                .as("the handset classified this message offline; a retry sweep"
                        + " must not throw that classification away")
                .isEqualTo("medical");
    }

    @Test
    public void deliveredIsNotPending() throws Exception {
        // DELIVERED is terminal. Leaving it in the pending set meant the router
        // re-sent it on every sweep, which split horizon could not stop because
        // the retry path passes no arrival peer.
        upsert(message());
        updateState("msg-regression", DeliveryState.DELIVERED, "received");

        assertThat(pending()).isEmpty();
        assertThat(pendingCount()).isZero();
    }

    @Test
    public void deliveredIsNotExpiredLater() throws Exception {
        upsert(message());
        updateState("msg-regression", DeliveryState.DELIVERED, "received");

        expireStale(System.currentTimeMillis() + 3_600_000L);

        assertThat(find("msg-regression").state())
                .as("reporting an arrived message as expired is the worst failure this"
                        + " system can make about its own reliability")
                .isEqualTo(DeliveryState.DELIVERED);
    }

    // ------------------------------------------------------------- helpers

    /**
     * Runs a body on a worker thread.
     *
     * <p>The store refuses writes from the main looper, which is correct and is
     * enforced. Robolectric runs the test body on the main thread, so anything
     * that writes has to be marshalled exactly as the mesh service does it.
     */
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

    private StoredMessage upsert(StoredMessage message) throws Exception {
        return offMain(() -> store.upsert(message));
    }

    private StoredMessage find(String id) throws Exception {
        return offMain(() -> store.find(id).orElse(null));
    }

    private int pendingCount() throws Exception {
        return offMain(store::pendingCount);
    }

    private List<StoredMessage> pending() throws Exception {
        return offMain(store::pending);
    }

    private void updateState(String id, DeliveryState state, String detail) throws Exception {
        offMain(() -> store.updateState(id, state, detail));
    }

    private int expireStale(long now) throws Exception {
        return offMain(() -> store.expireStale(now));
    }

    /** Sets the read flag the way the message detail screen does. */
    private void markRead() {
        database.getWritableDatabase().execSQL(
                "UPDATE " + ResQNetDatabase.TABLE_MESSAGES
                        + " SET read = 1 WHERE message_id = ?",
                new Object[] { "msg-regression" });
    }

    private boolean isRead() {
        try (Cursor cursor = database.getReadableDatabase().query(
                ResQNetDatabase.TABLE_MESSAGES, new String[] { "read" },
                "message_id = ?", new String[] { "msg-regression" }, null, null, null)) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private String triageOf(String messageId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                ResQNetDatabase.TABLE_MESSAGES, new String[] { "triage_category" },
                "message_id = ?", new String[] { messageId }, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static StoredMessage message() throws Exception {
        long now = System.currentTimeMillis();
        MessageEnvelope envelope = MessageEnvelope.builder()
                .messageId("msg-regression")
                .origin("rqn1_" + "a".repeat(64))
                .originKeyFingerprint("b".repeat(64))
                .destination("rqn1_" + "c".repeat(64))
                .type(MessageType.SOS)
                .priority(Priority.SOS)
                .createdAt(now)
                .expiresAt(now + 900_000L)
                .payload("help".getBytes(StandardCharsets.UTF_8))
                .build();
        return StoredMessage.of(envelope, EnvelopeCodec.encode(envelope), now);
    }
    private static StoredMessage secondMessage() throws Exception {
        long now = System.currentTimeMillis();
        MessageEnvelope envelope = MessageEnvelope.builder()
                .messageId("msg-regression-second")
                .origin("rqn1_" + "a".repeat(64))
                .originKeyFingerprint("b".repeat(64))
                .destination("rqn1_" + "c".repeat(64))
                .type(MessageType.SOS)
                .priority(Priority.SOS)
                .createdAt(now)
                .expiresAt(now + 900_000L)
                .payload("help second".getBytes(StandardCharsets.UTF_8))
                .build();
        return StoredMessage.of(envelope, EnvelopeCodec.encode(envelope), now);
    }

}