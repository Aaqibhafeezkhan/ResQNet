package dev.resqnet.mesh.routing;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded duplicate-suppression window.
 *
 * <p>This structure is what stops controlled flooding from becoming a broadcast
 * storm. A message id that has already been processed is never forwarded again,
 * so a two node cycle terminates after one traversal instead of looping until
 * the hop budget runs out. The cache is bounded on three axes because an
 * unbounded one is a denial-of-service target: maximum entries, maximum age, and
 * a per-message byte budget.
 *
 * <p>Eviction is LRU by first-seen time, which is the right choice here: a
 * message that just arrived has the longest time to live, so it is the one most
 * likely to be seen again by a late reconnecting peer.
 *
 * <p>Implementations are thread safe.
 */
public final class DuplicateCache {

    /** Default cap. 4096 ids is roughly an hour of a busy rescue scene. */
    public static final int DEFAULT_MAX_ENTRIES = 4096;

    /** Default retention. Matches the longest message lifetime the engine allows. */
    public static final long DEFAULT_MAX_AGE_MILLIS = 6L * 60 * 60 * 1000;

    private final int maxEntries;
    private final long maxAgeMillis;
    private final Map<String, Long> seenAt = new LinkedHashMap<>(256, 0.75f, false);
    private final Map<String, Integer> sizeById = new HashMap<>();
    private int bytesUsed;
    private final int maxBytes;

    /** Cache sized with the defaults above. */
    public DuplicateCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_AGE_MILLIS, 8 * 1024 * 1024);
    }

    public DuplicateCache(int maxEntries, long maxAgeMillis, int maxBytes) {
        if (maxEntries <= 0 || maxAgeMillis <= 0 || maxBytes <= 0) {
            throw new IllegalArgumentException("Duplicate cache bounds must be positive");
        }
        this.maxEntries = maxEntries;
        this.maxAgeMillis = maxAgeMillis;
        this.maxBytes = maxBytes;
    }

    /**
     * Records a message id as seen.
     *
     * @return true if this id had <em>not</em> been seen before, meaning the
     *     caller should process it; false when it is a duplicate
     */
    public synchronized boolean markIfFirstSeen(String messageId, long nowMillis, int sizeHintBytes) {
        purge(nowMillis);
        Long previous = seenAt.get(messageId);
        if (previous != null) {
            return false;
        }
        int cost = Math.max(1, sizeHintBytes);
        while ((seenAt.size() >= maxEntries || bytesUsed + cost > maxBytes) && !seenAt.isEmpty()) {
            evictOldest();
        }
        seenAt.put(messageId, nowMillis);
        sizeById.put(messageId, cost);
        bytesUsed += cost;
        return true;
    }

    /** True when the id is currently retained. Does not extend its lifetime. */
    public synchronized boolean isSeen(String messageId, long nowMillis) {
        purge(nowMillis);
        return seenAt.containsKey(messageId);
    }

    /**
     * Forgets a single id, for example after an end-to-end acknowledgement means
     * the message can never usefully arrive again.
     *
     * @return true when the id was present
     */
    public synchronized boolean forget(String messageId) {
        Integer cost = sizeById.remove(messageId);
        if (cost == null) {
            return false;
        }
        seenAt.remove(messageId);
        bytesUsed -= cost;
        return true;
    }

    /** Drops everything older than {@code maxAgeMillis} relative to now. */
    public synchronized void purge(long nowMillis) {
        long cutoff = nowMillis - maxAgeMillis;
        if (cutoff <= 0) {
            return;
        }
        var iterator = seenAt.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() <= cutoff) {
                iterator.remove();
                Integer cost = sizeById.remove(entry.getKey());
                if (cost != null) {
                    bytesUsed -= cost;
                }
            }
        }
    }

    public synchronized int size() {
        return seenAt.size();
    }

    public synchronized int bytesUsed() {
        return bytesUsed;
    }

    public synchronized long maxAgeMillis() {
        return maxAgeMillis;
    }

    private void evictOldest() {
        String oldest = null;
        for (String key : seenAt.keySet()) {
            oldest = key;
            break;
        }
        if (oldest == null) {
            return;
        }
        Integer cost = sizeById.remove(oldest);
        seenAt.remove(oldest);
        if (cost != null) {
            bytesUsed -= cost;
        }
    }

    /** Snapshot for debugging and for the responder dashboard topology panel. */
    public synchronized java.util.List<String> snapshot() {
        return java.util.List.copyOf(seenAt.keySet());
    }
}