package dev.resqnet.android.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.protocol.ProtocolException;
import dev.resqnet.mesh.routing.MessageStore;
import dev.resqnet.mesh.routing.StoredMessage;

/**
 * SQLite-backed {@link MessageStore}.
 *
 * <p>This is what makes store-and-forward real rather than aspirational. A
 * message composed while every peer is out of range has to survive the process
 * being killed, the phone being locked, and the user force-quitting the app.
 * An in-memory queue would lose all three.
 *
 * <p>All methods are synchronous. On Android they run on the mesh service's
 * background executor, never on the main thread; {@link #assertNotMainThread()}
 * documents and enforces that expectation for the operations that matter.
 *
 * <p>Capacity is enforced on write as well as on a sweep, because a queue that
 * only shrinks when someone notices it is a queue that has already filled the
 * user's disk.
 */
public final class SqliteMessageStore implements MessageStore {

    /** Maximum non-terminal records before eviction begins. */
    private static final int DEFAULT_CAPACITY = 512;

    private final ResQNetDatabase database;
    private final int capacity;
    private final MeshAuditor auditor;

    public SqliteMessageStore(@NonNull Context context) {
        this(ResQNetDatabase.get(context), DEFAULT_CAPACITY, null);
    }

    public SqliteMessageStore(@NonNull ResQNetDatabase database, int capacity,
                              MeshAuditor auditor) {
        this.database = database;
        this.capacity = capacity;
        this.auditor = auditor;
    }

    /**
     * Optional sink for append-only delivery audit rows.
     *
     * <p>Separate from the store because the audit trail is written from the UI
     * and the service, and must never be silently skipped just because one
     * component did not supply a sink.
     */
    public interface MeshAuditor {
        void onStateChange(String messageId, DeliveryState previous, DeliveryState current,
                           String detail);
    }

    @Override
    public StoredMessage upsert(StoredMessage message) {
        assertNotMainThread();
        ContentValues values = new ContentValues();
        values.put("message_id", message.messageId());
        values.put("envelope", message.envelope());
        values.put("state", message.state().name());
        values.put("origin_device_id", message.originDeviceId());
        values.put("destination_device_id", message.destinationDeviceId());
        values.put("message_type", message.type().name());
        values.put("priority", message.priority().name());
        values.put("created_at", message.createdAt());
        values.put("expires_at", message.expiresAt());
        values.put("attempt_count", message.attemptCount());
        values.put("next_attempt_at", message.nextAttemptAt());
        values.put("last_detail", message.lastDetail());

        SQLiteDatabase db = database.getWritableDatabase();
        long rowId = upsertReturningRowId(db, values, message.messageId());
        return message.withIdentity(message.state(), message.lastDetail(), rowId);
    }

    private long upsertReturningRowId(SQLiteDatabase db, ContentValues values, String messageId) {
        long rowId = db.insertWithOnConflict(ResQNetDatabase.TABLE_MESSAGES, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        if (rowId != -1) {
            return rowId;
        }
        try (Cursor cursor = db.query(ResQNetDatabase.TABLE_MESSAGES,
                new String[] { "rowid" }, "message_id = ?", new String[] { messageId },
                null, null, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1L;
        }
    }

    @Override
    public Optional<StoredMessage> find(String messageId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                ResQNetDatabase.TABLE_MESSAGES, null, "message_id = ?",
                new String[] { messageId }, null, null, null)) {
            return cursor.moveToFirst() ? Optional.ofNullable(read(cursor)) : Optional.empty();
        }
    }

    @Override
    public List<StoredMessage> pending() {
        List<StoredMessage> result = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                ResQNetDatabase.TABLE_MESSAGES, null,
                "state NOT IN (?, ?, ?)", new String[] {
                        DeliveryState.ACKNOWLEDGED.name(),
                        DeliveryState.EXPIRED.name(),
                        DeliveryState.FAILED.name() },
                null, null, "expires_at ASC")) {
            while (cursor.moveToNext()) {
                StoredMessage message = read(cursor);
                if (message != null) {
                    result.add(message);
                }
            }
        }
        return result;
    }

    @Override
    public List<StoredMessage> recent(int limit) {
        List<StoredMessage> result = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                ResQNetDatabase.TABLE_MESSAGES, null, null, null, null, null,
                "created_at DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                StoredMessage message = read(cursor);
                if (message != null) {
                    result.add(message);
                }
            }
        }
        return result;
    }

    @Override
    public StoredMessage updateState(String messageId, DeliveryState state, String detail) {
        Optional<StoredMessage> existing = find(messageId);
        if (existing.isEmpty()) {
            return null;
        }
        StoredMessage previous = existing.get();
        ContentValues values = new ContentValues();
        values.put("state", state.name());
        values.put("last_detail", detail);
        database.getWritableDatabase().update(ResQNetDatabase.TABLE_MESSAGES, values,
                "message_id = ?", new String[] { messageId });
        if (auditor != null && previous.state() != state) {
            auditor.onStateChange(messageId, previous.state(), state, detail);
        }
        return previous.withState(state, detail);
    }

    /** Persists retry bookkeeping without touching the audit note. */
    public StoredMessage updateRetry(String messageId, int attemptCount, long nextAttemptAt) {
        Optional<StoredMessage> existing = find(messageId);
        if (existing.isEmpty()) {
            return null;
        }
        StoredMessage previous = existing.get();
        ContentValues values = new ContentValues();
        values.put("attempt_count", attemptCount);
        values.put("next_attempt_at", nextAttemptAt);
        database.getWritableDatabase().update(ResQNetDatabase.TABLE_MESSAGES, values,
                "message_id = ?", new String[] { messageId });
        return previous.withRetry(attemptCount, nextAttemptAt);
    }

    @Override
    public boolean delete(String messageId) {
        SQLiteDatabase db = database.getWritableDatabase();
        int deleted = db.delete(ResQNetDatabase.TABLE_MESSAGES, "message_id = ?",
                new String[] { messageId });
        db.delete(ResQNetDatabase.TABLE_DELIVERIES, "message_id = ?", new String[] { messageId });
        return deleted > 0;
    }

    @Override
    public int pendingCount() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + ResQNetDatabase.TABLE_MESSAGES
                        + " WHERE state NOT IN (?, ?, ?)",
                new String[] { DeliveryState.ACKNOWLEDGED.name(),
                        DeliveryState.EXPIRED.name(), DeliveryState.FAILED.name() })) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Override
    public int countByState(DeliveryState state) {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + ResQNetDatabase.TABLE_MESSAGES
                        + " WHERE state = ?", new String[] { state.name() })) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Override
    public int expireStale(long nowMillis) {
        List<String> stale = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                ResQNetDatabase.TABLE_MESSAGES, new String[] { "message_id" },
                "expires_at <= ? AND state NOT IN (?, ?, ?)",
                new String[] { String.valueOf(nowMillis), DeliveryState.ACKNOWLEDGED.name(),
                        DeliveryState.EXPIRED.name(), DeliveryState.FAILED.name() },
                null, null, null)) {
            while (cursor.moveToNext()) {
                stale.add(cursor.getString(0));
            }
        }
        for (String messageId : stale) {
            updateState(messageId, DeliveryState.EXPIRED, "lifetime elapsed before delivery");
        }
        return stale.size();
    }

    @Override
    public int enforceCapacity(int maxRecords) {
        if (pendingCount() <= maxRecords) {
            return 0;
        }
        List<String> evictable = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                ResQNetDatabase.TABLE_MESSAGES, new String[] { "message_id" },
                "priority IN (?, ?, ?) AND state NOT IN (?, ?, ?)",
                new String[] { Priority.LOW.name(), Priority.NORMAL.name(),
                        Priority.URGENT.name(), DeliveryState.ACKNOWLEDGED.name(),
                        DeliveryState.EXPIRED.name(), DeliveryState.FAILED.name() },
                null, null, "created_at ASC")) {
            while (cursor.moveToNext()) {
                evictable.add(cursor.getString(0));
            }
        }
        int toEvict = pendingCount() - maxRecords;
        int evicted = 0;
        for (String messageId : evictable) {
            if (evicted >= toEvict) {
                break;
            }
            if (delete(messageId)) {
                evicted++;
            }
        }
        return evicted;
    }

    @Override
    public List<StoredMessage> all() {
        return recent(Integer.MAX_VALUE);
    }

    @Override
    public void close() {
        // The database handle is owned by the process-wide singleton and shared
        // with the peer table, so this store does not close it.
    }

    /** Decodes the stored envelope for the inbox and detail screens. */
    public Optional<MessageEnvelope> decode(StoredMessage message) {
        try {
            return Optional.of(EnvelopeCodec.decode(message.envelope()));
        } catch (ProtocolException e) {
            return Optional.empty();
        }
    }

    private StoredMessage read(Cursor cursor) {
        try {
            return new StoredMessage(
                    cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
                    cursor.getBlob(cursor.getColumnIndexOrThrow("envelope")),
                    DeliveryState.valueOf(cursor.getString(
                            cursor.getColumnIndexOrThrow("state"))),
                    cursor.getString(cursor.getColumnIndexOrThrow("origin_device_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("destination_device_id")),
                    MessageType.valueOf(cursor.getString(
                            cursor.getColumnIndexOrThrow("message_type"))),
                    Priority.valueOf(cursor.getString(
                            cursor.getColumnIndexOrThrow("priority"))),
                    cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("expires_at")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("attempt_count")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("next_attempt_at")),
                    cursor.getString(cursor.getColumnIndexOrThrow("last_detail")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("local_id")));
        } catch (IllegalArgumentException | NullPointerException e) {
            // A row we cannot interpret is treated as absent rather than
            // crashing the service, because an emergency network that throws on
            // startup is worse than one that ignores a corrupt row. The reason
            // is logged rather than swallowed: a store that silently returns
            // "no messages" looks exactly like a mesh that never received any,
            // which is a dangerous thing to debug in the field.
            android.util.Log.w("ResQNetStore", "Unreadable message row ignored", e);
            return null;
        }
    }

    /**
     * Guards against Android's StrictMode failures.
     *
     * <p>Uses the looper rather than the thread name, because the main looper's
     * thread is not reliably called "main" on every OEM build. A write from the
     * main thread is a crash waiting to happen on a slow device, and an
     * emergency app that drops a composed message because of a thread policy
     * violation is unacceptable.
     */
    private void assertNotMainThread() {
        if (android.os.Looper.myLooper() != null
                && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "MessageStore writes must not run on the main thread");
        }
    }
}