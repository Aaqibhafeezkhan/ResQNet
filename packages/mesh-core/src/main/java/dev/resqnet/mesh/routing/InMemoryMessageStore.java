package dev.resqnet.mesh.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.Priority;

/**
 * Thread-safe in-memory {@link MessageStore}.
 *
 * <p>Used by the simulator and by tests. It implements exactly the same
 * capacity, expiry and eviction rules as the Android SQLite implementation, so a
 * queue-overflow test here means the same thing on a handset.
 *
 * <p>Ordered by expiry rather than insertion: a message about to expire is the
 * one most likely to be useless, so it drains first.
 */
public final class InMemoryMessageStore implements MessageStore {

    private final ConcurrentSkipListMap<String, StoredMessage> byMessageId =
            new ConcurrentSkipListMap<>();
    private final AtomicLong rowIds = new AtomicLong(1);

    @Override
    public StoredMessage upsert(StoredMessage message) {
        long rowId = message.localRowId().orElseGet(rowIds::getAndIncrement);
        StoredMessage stored = message.localRowId().isPresent()
                ? message
                : message.withIdentity(message.state(), message.lastDetail(), rowId);
        byMessageId.put(stored.messageId(), stored);
        return stored;
    }

    @Override
    public Optional<StoredMessage> find(String messageId) {
        return Optional.ofNullable(byMessageId.get(messageId));
    }

    @Override
    public List<StoredMessage> pending() {
        List<StoredMessage> result = new ArrayList<>();
        for (StoredMessage m : byMessageId.values()) {
            if (!m.state().terminal()) {
                result.add(m);
            }
        }
        result.sort(Comparator.comparingLong(StoredMessage::expiresAt)
                .thenComparingInt(m -> -m.priority().level()));
        return result;
    }

    @Override
    public List<StoredMessage> recent(int limit) {
        List<StoredMessage> all = new ArrayList<>(byMessageId.values());
        all.sort(Comparator.comparingLong(StoredMessage::createdAt).reversed());
        return all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
    }

    @Override
    public StoredMessage updateState(String messageId, DeliveryState state, String detail) {
        return byMessageId.compute(messageId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.state() == state && existing.lastDetail().equals(detail)) {
                return existing;
            }
            return existing.withState(state, detail);
        });
    }

    @Override
    public boolean delete(String messageId) {
        return byMessageId.remove(messageId) != null;
    }

    @Override
    public int pendingCount() {
        int count = 0;
        for (StoredMessage m : byMessageId.values()) {
            if (!m.state().terminal()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int countByState(DeliveryState state) {
        int count = 0;
        for (StoredMessage m : byMessageId.values()) {
            if (m.state() == state) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int expireStale(long nowMillis) {
        int expired = 0;
        for (String messageId : List.copyOf(byMessageId.keySet())) {
            StoredMessage current = byMessageId.get(messageId);
            if (current == null || current.state().terminal() || !current.expired(nowMillis)) {
                continue;
            }
            byMessageId.replace(messageId, current.withState(DeliveryState.EXPIRED,
                    "lifetime elapsed before delivery"));
            expired++;
        }
        return expired;
    }

    @Override
    public int enforceCapacity(int maxRecords) {
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
        List<StoredMessage> nonTerminal = new ArrayList<>();
        for (StoredMessage m : byMessageId.values()) {
            if (!m.state().terminal()) {
                nonTerminal.add(m);
            }
        }
        if (nonTerminal.size() <= maxRecords) {
            return 0;
        }
        // Evict the oldest low-priority work first. SOS records are never evicted.
        nonTerminal.sort(Comparator
                .comparingInt((StoredMessage m) -> m.priority().level())
                .thenComparingLong(StoredMessage::createdAt));
        int evictCount = nonTerminal.size() - maxRecords;
        int evicted = 0;
        for (StoredMessage candidate : nonTerminal) {
            if (evicted >= evictCount) {
                break;
            }
            if (candidate.priority().protectedFromEviction()) {
                continue;
            }
            byMessageId.remove(candidate.messageId());
            evicted++;
        }
        return evicted;
    }

    @Override
    public Collection<StoredMessage> all() {
        return List.copyOf(byMessageId.values());
    }

    @Override
    public void close() {
        byMessageId.clear();
    }

    /** Convenience for tests: counts by priority, for eviction assertions. */
    public Map<Priority, Integer> priorityHistogram() {
        Map<Priority, Integer> histogram = new java.util.EnumMap<>(Priority.class);
        for (StoredMessage m : byMessageId.values()) {
            histogram.merge(m.priority(), 1, Integer::sum);
        }
        return histogram;
    }
}