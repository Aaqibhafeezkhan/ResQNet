package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;

/**
 * Durable queue semantics, independent of any routing.
 */
@DisplayName("InMemoryMessageStore")
class InMemoryMessageStoreTest {

    private InMemoryMessageStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryMessageStore();
    }

    private static StoredMessage message(String id, DeliveryState state, Priority priority,
                                         long createdAt, long expiresAt) {
        return new StoredMessage(id, ("payload-" + id).getBytes(StandardCharsets.UTF_8), state,
                "rqn1_origin", "rqn1_dest", MessageType.TEXT, priority, createdAt, expiresAt,
                0, 0L, "test", -1L);
    }

    @Test
    @DisplayName("assigns a stable row id on first save and keeps it")
    void assignsStableRowIds() {
        StoredMessage saved = store.upsert(message("m1", DeliveryState.QUEUED,
                Priority.NORMAL, 0L, 10_000L));

        assertThat(saved.localRowId()).isPresent();
        long rowId = saved.localRowId().orElseThrow();
        assertThat(store.upsert(saved).localRowId()).hasValue(rowId);
    }

    @Test
    @DisplayName("lists pending work soonest deadline first")
    void ordersPendingByDeadline() {
        store.upsert(message("late", DeliveryState.QUEUED, Priority.NORMAL, 0L, 30_000L));
        store.upsert(message("soon", DeliveryState.QUEUED, Priority.NORMAL, 0L, 10_000L));
        store.upsert(message("done", DeliveryState.ACKNOWLEDGED, Priority.NORMAL, 0L, 5_000L));

        assertThat(store.pending()).extracting(StoredMessage::messageId)
                .containsExactly("soon", "late");
    }

    @Test
    @DisplayName("counts terminal and non-terminal states separately")
    void countsByState() {
        store.upsert(message("a", DeliveryState.QUEUED, Priority.NORMAL, 0L, 10_000L));
        store.upsert(message("b", DeliveryState.ACKNOWLEDGED, Priority.NORMAL, 0L, 10_000L));
        store.upsert(message("c", DeliveryState.EXPIRED, Priority.NORMAL, 0L, 10_000L));

        assertThat(store.pendingCount()).isEqualTo(1);
        assertThat(store.countByState(DeliveryState.ACKNOWLEDGED)).isEqualTo(1);
        assertThat(store.countByState(DeliveryState.EXPIRED)).isEqualTo(1);
        assertThat(store.all()).hasSize(3);
    }

    @Test
    @DisplayName("marks stale records expired exactly once")
    void expiresStaleRecords() {
        store.upsert(message("old", DeliveryState.QUEUED, Priority.NORMAL, 0L, 1_000L));
        store.upsert(message("new", DeliveryState.QUEUED, Priority.NORMAL, 0L, 100_000L));

        assertThat(store.expireStale(2_000L)).isEqualTo(1);
        assertThat(store.expireStale(3_000L)).isEqualTo(0);
        assertThat(store.find("old").orElseThrow().state()).isEqualTo(DeliveryState.EXPIRED);
        assertThat(store.find("new").orElseThrow().state()).isEqualTo(DeliveryState.QUEUED);
    }

    @Test
    @DisplayName("evicts the oldest low priority work first")
    void evictsOldestLowPriorityFirst() {
        store.upsert(message("old-low", DeliveryState.QUEUED, Priority.LOW, 0L, 100_000L));
        store.upsert(message("new-low", DeliveryState.QUEUED, Priority.LOW, 50L, 100_000L));
        store.upsert(message("urgent", DeliveryState.QUEUED, Priority.URGENT, 60L, 100_000L));

        store.enforceCapacity(2);

        assertThat(store.find("old-low")).isEmpty();
        assertThat(store.find("new-low")).isPresent();
        assertThat(store.find("urgent")).isPresent();
    }

    @Test
    @DisplayName("never evicts SOS traffic, even when the queue is full of it")
    void neverEvictsSos() {
        for (int i = 0; i < 5; i++) {
            store.upsert(message("sos" + i, DeliveryState.QUEUED, Priority.SOS, i, 100_000L));
        }
        for (int i = 0; i < 5; i++) {
            store.upsert(message("low" + i, DeliveryState.QUEUED, Priority.LOW, 100 + i, 100_000L));
        }

        store.enforceCapacity(3);

        assertThat(store.pending()).extracting(StoredMessage::priority)
                .containsOnly(Priority.SOS);
        assertThat(store.find("sos0")).isPresent();
        assertThat(store.find("low0")).isEmpty();
    }

    @Test
    @DisplayName("deletes records permanently")
    void deletesRecords() {
        store.upsert(message("m1", DeliveryState.QUEUED, Priority.NORMAL, 0L, 10_000L));

        assertThat(store.delete("m1")).isTrue();
        assertThat(store.delete("m1")).isFalse();
        assertThat(store.find("m1")).isEmpty();
    }

    @Test
    @DisplayName("only attempts records whose backoff window has elapsed")
    void respectsBackoffWindow() {
        StoredMessage ready = new StoredMessage("ready", new byte[] { 1 }, DeliveryState.QUEUED,
                "rqn1_o", "rqn1_d", MessageType.TEXT, Priority.NORMAL, 0L, 10_000L, 1,
                5_000L, "waiting", -1L);
        StoredMessage waiting = new StoredMessage("waiting", new byte[] { 1 },
                DeliveryState.QUEUED, "rqn1_o", "rqn1_d", MessageType.TEXT, Priority.NORMAL,
                0L, 10_000L, 1, 50_000L, "waiting", -1L);

        assertThat(ready.readyToAttempt(6_000L)).isTrue();
        assertThat(waiting.readyToAttempt(6_000L)).isFalse();
        assertThat(ready.expired(20_000L)).isTrue();
    }

    @Test
    @DisplayName("updating an absent record is a no-op rather than an error")
    void updateOfAbsentRecordIsSafe() {
        assertThat(store.updateState("nope", DeliveryState.QUEUED, "x")).isNull();
        assertThat(store.all()).isEmpty();
    }
}