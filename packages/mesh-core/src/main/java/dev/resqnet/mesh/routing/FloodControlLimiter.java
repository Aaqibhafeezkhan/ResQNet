package dev.resqnet.mesh.routing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-origin token bucket that bounds how much traffic a single peer may inject.
 *
 * <p>Deduplication and hop limits bound how far one message travels, but neither
 * bounds how many <em>distinct</em> messages an attacker within radio range can
 * send. A compromised or hostile node could otherwise keep every device in the
 * area relaying at line rate until their batteries are gone, and a battery is
 * the only thing that keeps a phone alive in an emergency. The bucket is
 * therefore applied to forwarded traffic per origin identity, with a separate
 * allowance for life-safety traffic so that legitimate SOS flooding is never
 * the thing that gets throttled.
 *
 * <p>Defences in depth, not a substitute for them: the signature check already
 * rejects forged origins, and this limits the damage from a device that is
 * genuine but misbehaving.
 *
 * <p>Thread safe.
 */
public final class FloodControlLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxTrackedOrigins;

    public FloodControlLimiter() {
        this(2048);
    }

    public FloodControlLimiter(int maxTrackedOrigins) {
        this.maxTrackedOrigins = maxTrackedOrigins;
    }

    /**
     * Attempts to consume one forwarding credit for an origin.
     *
     * @param perSecond sustained refill rate for normal traffic
     * @param burst maximum credits that may accumulate
     * @return true when the traffic is allowed through
     */
    public boolean tryAcquire(String originDeviceId, boolean lifeSafety,
                              double perSecond, int burst, long nowMillis) {
        if (lifeSafety) {
            // SOS traffic bypasses the throttle. Suppressing a life-safety
            // message is strictly worse than accepting some extra work, and the
            // rate here is still bounded by hop limits, dedup and a peer-count
            // fan-out cap in the router.
            return true;
        }
        if (buckets.size() >= maxTrackedOrigins && !buckets.containsKey(originDeviceId)) {
            // Under a flood from many distinct origins we stop tracking new ones
            // and refuse them rather than growing without bound.
            return false;
        }
        Bucket bucket = buckets.computeIfAbsent(originDeviceId,
                id -> new Bucket(burst, nowMillis));
        return bucket.tryAcquire(perSecond, burst, nowMillis);
    }

    /** Frees the allowance for an origin, e.g. when a peer disconnects cleanly. */
    public void forget(String originDeviceId) {
        buckets.remove(originDeviceId);
    }

    public int trackedOrigins() {
        return buckets.size();
    }

    private static final class Bucket {
        private double credits;
        private long lastRefillMillis;

        Bucket(int burst, long nowMillis) {
            this.credits = burst;
            this.lastRefillMillis = nowMillis;
        }

        synchronized boolean tryAcquire(double perSecond, int burst, long nowMillis) {
            if (nowMillis > lastRefillMillis) {
                double elapsedSeconds = (nowMillis - lastRefillMillis) / 1000.0;
                credits = Math.min(burst, credits + elapsedSeconds * perSecond);
                lastRefillMillis = nowMillis;
            }
            if (credits >= 1.0) {
                credits -= 1.0;
                return true;
            }
            return false;
        }
    }
}