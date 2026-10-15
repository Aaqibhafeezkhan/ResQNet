package dev.resqnet.mesh.routing;

/**
 * Exponential backoff with jitter, bounded in both delay and attempts.
 *
 * <p>Two failure modes drive the design. Synchronised retries from a dozen
 * devices that all lost connectivity at the same instant would produce a burst
 * of radio traffic the instant the network comes back, which in a real disaster
 * is precisely when the spectrum is most contended. And a phone with a dead
 * battery-friendly duty cycle must not be forced to wake every 2 seconds all
 * night. So: exponential growth, full jitter, a hard cap, and an attempt limit
 * that ends the retry rather than looping forever.
 *
 * <p>Deterministic when given a deterministic {@link java.util.random.RandomGenerator},
 * which is what makes simulator runs reproducible.
 */
public final class RetryPolicy {

    /** First retry delay. */
    private final long baseDelayMillis;
    /** Ceiling for any single delay. */
    private final long maxDelayMillis;
    /** Attempts after which the message is marked {@code FAILED}. */
    private final int maxAttempts;
    /** Randomness source for jitter. */
    private final java.util.random.RandomGenerator random;

    /** Sensible defaults for a battery-powered handset. */
    public static RetryPolicy defaultMobile() {
        return new RetryPolicy(2_000L, 5L * 60_000L, 12,
                java.util.random.RandomGenerator.getDefault());
    }

    /** Deterministic policy for simulation. */
    public static RetryPolicy deterministic(long baseDelayMillis, long maxDelayMillis,
                                            int maxAttempts, long seed) {
        return new RetryPolicy(baseDelayMillis, maxDelayMillis, maxAttempts,
                new java.util.Random(seed));
    }

    /**
     * @param baseDelayMillis delay before the first retry
     * @param maxDelayMillis ceiling applied to every computed delay
     * @param maxAttempts total attempts, including the first
     */
    public RetryPolicy(long baseDelayMillis, long maxDelayMillis, int maxAttempts,
                       java.util.random.RandomGenerator random) {
        if (baseDelayMillis <= 0 || maxDelayMillis < baseDelayMillis || maxAttempts <= 0) {
            throw new IllegalArgumentException("Invalid retry policy bounds");
        }
        this.baseDelayMillis = baseDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.maxAttempts = maxAttempts;
        this.random = random;
    }

    /**
     * Delay before attempt number {@code attemptCount} (1-based: after one failed
     * attempt, pass 1).
     *
     * <p>Uses "full jitter": the delay is uniform in {@code [0, computed]}. The
     * alternative, fixed delay plus small jitter, still lets large groups
     * converge on the same retry tick.
     */
    public long delayBeforeAttempt(int attemptCount, long nowMillis) {
        if (attemptCount <= 0) {
            return 0L;
        }
        int exponent = Math.min(attemptCount - 1, 32);
        long growth = 1L << exponent;
        long computed = baseDelayMillis > Long.MAX_VALUE / growth
                ? maxDelayMillis
                : Math.min(maxDelayMillis, baseDelayMillis * growth);
        long jittered = computed <= 0 ? 0 : random.nextLong(computed + 1);
        return Math.max(0L, nowMillis + jittered);
    }

    /** True once {@code attemptCount} has used up the retry budget. */
    public boolean exhausted(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public long baseDelayMillis() {
        return baseDelayMillis;
    }

    public long maxDelayMillis() {
        return maxDelayMillis;
    }
}