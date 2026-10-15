package dev.resqnet.mesh.routing;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;

/**
 * One message as the local device owes it to the mesh.
 *
 * <p>Records are immutable; state transitions produce new records. That keeps
 * the SQLite adapter and the in-memory adapter interchangeable and makes retry
 * logic easy to reason about under concurrency.
 *
 * @param messageId stable message identifier
 * @param envelope canonical encoded bytes; kept encoded so a restart replays
 *     byte-identical content and signature checks still succeed
 * @param state current delivery state
 * @param originDeviceId key-derived origin, {@code null} for relayed traffic
 * @param destinationDeviceId intended recipient, {@code null} for broadcast
 * @param type application message type
 * @param priority queue eviction class
 * @param createdAt when the human composed it
 * @param expiresAt hard deadline after which the message is worthless
 * @param attemptCount number of send attempts so far
 * @param nextAttemptAt earliest time the next attempt may run, after backoff
 * @param lastDetail short human-readable note about the latest transition
 * @param localId stable row id assigned by the store, {@code null} until saved
 */
public record StoredMessage(
        String messageId,
        byte[] envelope,
        DeliveryState state,
        String originDeviceId,
        String destinationDeviceId,
        MessageType type,
        Priority priority,
        long createdAt,
        long expiresAt,
        int attemptCount,
        long nextAttemptAt,
        String lastDetail,
        long localId
) {

    public StoredMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(lastDetail, "lastDetail");
    }

    /** Creates a fresh record for a locally originated message. */
    public static StoredMessage of(dev.resqnet.mesh.protocol.MessageEnvelope envelope,
                                   byte[] encodedEnvelope,
                                   long nowMillis) {
        return new StoredMessage(
                envelope.messageId(),
                encodedEnvelope.clone(),
                DeliveryState.QUEUED,
                envelope.originDeviceId(),
                envelope.destinationDeviceId().orElse(null),
                envelope.type(),
                envelope.priority(),
                envelope.createdAtEpochMillis(),
                envelope.expiresAtEpochMillis(),
                0,
                nowMillis,
                "queued for delivery",
                -1L);
    }

    /** True when the deadline has passed. */
    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAt;
    }

    /** True when the store should attempt to send or forward this record now. */
    public boolean readyToAttempt(long nowMillis) {
        return !state.terminal() && !expired(nowMillis) && nextAttemptAt <= nowMillis;
    }

    public Optional<Long> localRowId() {
        return localId < 0 ? Optional.empty() : Optional.of(localId);
    }

    /** Returns a copy with a new state and a note for the audit trail. */
    public StoredMessage withState(DeliveryState newState, String detail) {
        return new StoredMessage(messageId, envelope, newState, originDeviceId,
                destinationDeviceId, type, priority, createdAt, expiresAt, attemptCount,
                nextAttemptAt, detail, localId);
    }

    /** Returns a copy with a new state, a note, and a row id from the store. */
    public StoredMessage withIdentity(DeliveryState newState, String detail, long rowId) {
        return new StoredMessage(messageId, envelope, newState, originDeviceId,
                destinationDeviceId, type, priority, createdAt, expiresAt, attemptCount,
                nextAttemptAt, detail, rowId);
    }

    /** Returns a copy with retry bookkeeping applied. */
    public StoredMessage withAttempt(int newAttemptCount, long newNextAttemptAt,
                                     DeliveryState newState, String detail) {
        return new StoredMessage(messageId, envelope, newState, originDeviceId,
                destinationDeviceId, type, priority, createdAt, expiresAt, newAttemptCount,
                newNextAttemptAt, detail, localId);
    }

    /**
     * Updates only the retry schedule, leaving the state and the audit note
     * untouched.
     *
     * <p>A routine retry is not a state transition. Overwriting the note with
     * "attempt 3" would destroy the explanation of why the message is in its
     * current state, which is exactly the information a responder or a bug report
     * needs.
     */
    public StoredMessage withRetry(int newAttemptCount, long newNextAttemptAt) {
        return new StoredMessage(messageId, envelope, state, originDeviceId,
                destinationDeviceId, type, priority, createdAt, expiresAt, newAttemptCount,
                newNextAttemptAt, lastDetail, localId);
    }

    public Instant createdAtInstant() {
        return Instant.ofEpochMilli(createdAt);
    }
}