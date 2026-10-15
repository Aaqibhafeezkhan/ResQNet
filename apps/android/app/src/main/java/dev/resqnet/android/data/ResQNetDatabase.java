package dev.resqnet.android.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.resqnet.mesh.crypto.DeviceIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.DeliveryState;

/**
 * Durable storage for the ResQNet handset.
 *
 * <p>Plain {@code SQLiteOpenHelper} rather than Room. The schema is four small
 * tables, the queries are hand-written and indexable, and the routing engine
 * needs a synchronous {@code MessageStore} on a background thread rather than a
 * generated async DAO. Adding an annotation processor to save a dozen
 * {@code Cursor} loops would be the wrong trade for a safety-adjacent component
 * that must behave predictably under an OS that is actively trying to kill it.
 *
 * <p>Every write is a single transaction. A partially written queue entry during
 * a process kill would lose a message the user believes they sent.
 */
public final class ResQNetDatabase extends SQLiteOpenHelper {

    /** Schema version. Bumping this requires a migration, never a destructive drop. */
    public static final int SCHEMA_VERSION = 1;

    public static final String TABLE_MESSAGES = "messages";
    public static final String TABLE_PEERS = "peers";
    public static final String TABLE_DELIVERIES = "deliveries";
    public static final String TABLE_SETTINGS = "settings";

    private static final String DB_NAME = "resqnet.db";

    private static volatile ResQNetDatabase instance;

    private ResQNetDatabase(@NonNull Context context, String databaseName) {
        super(context.getApplicationContext(), databaseName, null, SCHEMA_VERSION);
    }

    private ResQNetDatabase(@NonNull Context context) {
        this(context, DB_NAME);
    }

    /**
     * Opens a separate database file for one test.
     *
     * <p>Not part of the shipped surface. The singleton is cached per process,
     * so a test that needs a genuinely empty database cannot use
     * {@link #get}: deleting the file would leave the cached helper holding an
     * open handle to the old one.
     */
    static ResQNetDatabase openIsolated(@NonNull Context context) {
        return new ResQNetDatabase(context.getApplicationContext(),
                "resqnet-test-" + java.util.UUID.randomUUID() + ".db");
    }

    /** Process-wide singleton; the database handle is expensive and thread safe. */
    public static ResQNetDatabase get(@NonNull Context context) {
        ResQNetDatabase local = instance;
        if (local == null) {
            synchronized (ResQNetDatabase.class) {
                local = instance;
                if (local == null) {
                    local = new ResQNetDatabase(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // Required for the ON CONFLICT clauses used by the peer upsert.
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // local_id is the rowid alias, not an ordinary column. An earlier draft
        // declared it as a nullable column and read back "rowid", which
        // getColumnIndexOrThrow cannot resolve, so every read silently produced
        // an unreadable record. Making it the actual primary key removes the
        // ambiguity entirely.
        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + "message_id TEXT NOT NULL UNIQUE,"
                + "local_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "envelope BLOB NOT NULL,"
                + "state TEXT NOT NULL,"
                + "origin_device_id TEXT,"
                + "destination_device_id TEXT,"
                + "message_type TEXT NOT NULL,"
                + "priority TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "expires_at INTEGER NOT NULL,"
                + "attempt_count INTEGER NOT NULL DEFAULT 0,"
                + "next_attempt_at INTEGER NOT NULL DEFAULT 0,"
                + "last_detail TEXT NOT NULL DEFAULT '',"
                + "triage_category TEXT,"
                + "triage_confidence REAL,"
                + "read INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE INDEX idx_messages_state ON " + TABLE_MESSAGES + " (state)");
        db.execSQL("CREATE INDEX idx_messages_expires ON " + TABLE_MESSAGES + " (expires_at)");
        db.execSQL("CREATE INDEX idx_messages_origin ON " + TABLE_MESSAGES
                + " (origin_device_id)");

        db.execSQL("CREATE TABLE " + TABLE_PEERS + " ("
                + "device_id TEXT PRIMARY KEY NOT NULL,"
                + "public_key BLOB NOT NULL,"
                + "label TEXT NOT NULL,"
                + "trust TEXT NOT NULL,"
                + "short_code TEXT NOT NULL,"
                + "first_seen INTEGER NOT NULL,"
                + "last_seen INTEGER NOT NULL,"
                + "endpoint_hint TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_DELIVERIES + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "message_id TEXT NOT NULL,"
                + "state TEXT NOT NULL,"
                + "detail TEXT NOT NULL,"
                + "at_millis INTEGER NOT NULL,"
                + "node_id TEXT)");
        db.execSQL("CREATE INDEX idx_deliveries_message ON " + TABLE_DELIVERIES
                + " (message_id, at_millis)");

        db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " ("
                + "key TEXT PRIMARY KEY NOT NULL,"
                + "value TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first release. Future migrations are added here as
        // explicit ALTER statements; a destructive drop is never acceptable
        // because it would discard a queue of unsent emergency messages.
        throw new IllegalStateException(
                "No migration defined from schema " + oldVersion + " to " + newVersion);
    }

    // ------------------------------------------------------------------ peers

    /**
     * Persists a trust store entry.
     *
     * <p>The public key is stored as raw X.509 bytes rather than as a trust
     * record, so the trust store can be rebuilt in memory on every launch and
     * the database holds no derived state that could drift from the key.
     */
    public void savePeer(TrustStore.Entry entry) {
        ContentValues values = new ContentValues();
        values.put("device_id", entry.deviceId());
        values.put("public_key", entry.publicKey().getEncoded());
        values.put("label", entry.label());
        values.put("trust", entry.trust().name());
        values.put("short_code", shortCode(entry.keyFingerprint()));
        values.put("first_seen", entry.firstSeen().toEpochMilli());
        values.put("last_seen", entry.lastSeen().toEpochMilli());
        getWritableDatabase().insertWithOnConflict(TABLE_PEERS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Loads every known peer as a trust store entry. */
    public List<TrustStore.Entry> loadPeers() {
        List<TrustStore.Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_PEERS, null, null, null,
                null, null, "last_seen DESC")) {
            while (cursor.moveToNext()) {
                TrustStore.Entry entry = readPeer(cursor);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    /** Loads one peer by device id. */
    @Nullable
    public TrustStore.Entry loadPeer(String deviceId) {
        // Every column is projected: readPeer parses the whole row, so a
        // narrowed projection would fail at the first getColumnIndexOrThrow.
        try (Cursor cursor = getReadableDatabase().query(TABLE_PEERS,
                null, "device_id = ?", new String[] { deviceId }, null, null, null)) {
            return cursor.moveToFirst() ? readPeer(cursor) : null;
        }
    }

    public void deletePeer(String deviceId) {
        getWritableDatabase().delete(TABLE_PEERS, "device_id = ?", new String[] { deviceId });
    }

    /** Number of verified peers, shown on the nearby devices screen. */
    public int verifiedPeerCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_PEERS + " WHERE trust = ?",
                new String[] { TrustStore.Trust.VERIFIED.name() })) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Nullable
    private TrustStore.Entry readPeer(Cursor cursor) {
        byte[] keyBytes = cursor.getBlob(cursor.getColumnIndexOrThrow("public_key"));
        String deviceId = cursor.getString(cursor.getColumnIndexOrThrow("device_id"));
        String label = cursor.getString(cursor.getColumnIndexOrThrow("label"));
        String trustName = cursor.getString(cursor.getColumnIndexOrThrow("trust"));
        long firstSeen = cursor.getLong(cursor.getColumnIndexOrThrow("first_seen"));
        long lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow("last_seen"));
        try {
            PublicKey publicKey = KeyFactory.getInstance(DeviceIdentity.KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            // The device id is recomputed rather than trusted from the row: a
            // tampered database must not be able to claim an identity it has no
            // key for.
            if (!DeviceIdentity.deviceId(publicKey).equals(deviceId)) {
                return null;
            }
            TrustStore.Trust trust;
            try {
                trust = TrustStore.Trust.valueOf(trustName);
            } catch (IllegalArgumentException e) {
                trust = TrustStore.Trust.UNVERIFIED;
            }
            return TrustStore.entryOf(publicKey, label, trust,
                    Instant.ofEpochMilli(firstSeen), Instant.ofEpochMilli(lastSeen));
        } catch (java.security.GeneralSecurityException e) {
            return null;
        }
    }

    // -------------------------------------------------------------- deliveries

    /**
     * Appends an audit row for a delivery state transition.
     *
     * <p>Append only. There is no update or delete path by design: the audit
     * trail is what makes "we told the user it was sent" auditable.
     */
    public void appendDelivery(String messageId, DeliveryState state, String detail,
                               long atMillis, String nodeId) {
        ContentValues values = new ContentValues();
        values.put("message_id", messageId);
        values.put("state", state.name());
        values.put("detail", detail);
        values.put("at_millis", atMillis);
        values.put("node_id", nodeId);
        getWritableDatabase().insert(TABLE_DELIVERIES, null, values);
    }

    /** Full timestamp history for one message, oldest first. */
    public List<DeliveryAuditEntry> deliveryHistory(String messageId) {
        List<DeliveryAuditEntry> history = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_DELIVERIES,
                new String[] { "state", "detail", "at_millis", "node_id" },
                "message_id = ?", new String[] { messageId }, null, null, "at_millis ASC")) {
            while (cursor.moveToNext()) {
                DeliveryState state;
                try {
                    state = DeliveryState.valueOf(cursor.getString(0));
                } catch (IllegalArgumentException e) {
                    state = DeliveryState.FAILED;
                }
                history.add(new DeliveryAuditEntry(state, cursor.getString(1),
                        cursor.getLong(2), cursor.getString(3)));
            }
        }
        return history;
    }

    // --------------------------------------------------------------- settings

    public void putSetting(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        getWritableDatabase().insertWithOnConflict(TABLE_SETTINGS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Optional<String> getSetting(String key) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_SETTINGS,
                new String[] { "value" }, "key = ?", new String[] { key }, null, null, null)) {
            return cursor.moveToFirst()
                    ? Optional.ofNullable(cursor.getString(0))
                    : Optional.empty();
        }
    }

    // ----------------------------------------------------------------- triage

    /** Records the advisory classification shown alongside a message. */
    public void saveTriage(String messageId, String category, double confidence) {
        ContentValues values = new ContentValues();
        values.put("triage_category", category);
        values.put("triage_confidence", confidence);
        getWritableDatabase().update(TABLE_MESSAGES, values, "message_id = ?",
                new String[] { messageId });
    }

    /** Advisory classification for a message, if one was produced. */
    public Optional<TriageRecord> loadTriage(String messageId) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_MESSAGES,
                new String[] { "triage_category", "triage_confidence" },
                "message_id = ?", new String[] { messageId }, null, null, null)) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return Optional.empty();
            }
            return Optional.of(new TriageRecord(cursor.getString(0), cursor.getDouble(1)));
        }
    }

    public void markRead(String messageId, boolean read) {
        ContentValues values = new ContentValues();
        values.put("read", read ? 1 : 0);
        getWritableDatabase().update(TABLE_MESSAGES, values, "message_id = ?",
                new String[] { messageId });
    }

    public int unreadCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_MESSAGES
                        + " WHERE read = 0 AND origin_device_id IS NOT NULL", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** One row of the append-only delivery audit trail. */
    public record DeliveryAuditEntry(DeliveryState state, String detail, long atMillis,
                                     String nodeId) {
    }

    /** Stored advisory classification. */
    public record TriageRecord(String category, double confidence) {
    }

    /** Collection view helper used by the adapter layer. */
    public Collection<TrustStore.Entry> peerSnapshot() {
        return loadPeers();
    }

    /** Eight character comparison code derived from a fingerprint. */
    public static String shortCode(String fullFingerprint) {
        return dev.resqnet.mesh.routing.IdentityAnnouncement.shortCode(fullFingerprint);
    }
}