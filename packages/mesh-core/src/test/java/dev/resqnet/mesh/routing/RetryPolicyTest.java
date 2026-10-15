package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Backoff policy. The determinism assertions matter: the simulator's value
 * proposition is that a failing run replays identically.
 */
@DisplayName("RetryPolicy")
class RetryPolicyTest {

    @Test
    @DisplayName("grows exponentially and stays under the ceiling")
    void growsExponentially() {
        RetryPolicy policy = RetryPolicy.deterministic(1_000L, 60_000L, 20, 7L);

        long attempt1 = delayFor(policy, 1, 0L);
        long attempt10 = delayFor(policy, 10, 0L);

        assertThat(attempt1).isBetween(0L, 1_000L);
        assertThat(attempt10).isGreaterThan(attempt1);
        assertThat(delayFor(policy, 20, 0L)).isLessThanOrEqualTo(60_000L);
    }

    @Test
    @DisplayName("never returns a delay above the ceiling, even at huge attempt counts")
    void respectsCeiling() {
        RetryPolicy policy = RetryPolicy.deterministic(1_000L, 30_000L, 100, 11L);

        for (int attempt = 1; attempt <= 100; attempt++) {
            long delay = delayFor(policy, attempt, 0L);
            assertThat(delay).isBetween(0L, 30_000L);
        }
    }

    @Test
    @DisplayName("produces identical delays for identical seeds")
    void isDeterministicPerSeed() {
        RetryPolicy first = RetryPolicy.deterministic(1_000L, 60_000L, 20, 99L);
        RetryPolicy second = RetryPolicy.deterministic(1_000L, 60_000L, 20, 99L);

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(delayFor(first, attempt, 5_000L))
                    .isEqualTo(delayFor(second, attempt, 5_000L));
        }
    }

    @Test
    @DisplayName("adds jitter so a fleet does not retry in lockstep")
    void jittersDelays() {
        RetryPolicy policy = RetryPolicy.deterministic(10_000L, 60_000L, 50, 3L);

        var delays = new java.util.HashSet<Long>();
        for (int attempt = 1; attempt <= 20; attempt++) {
            delays.add(delayFor(policy, attempt, 0L));
        }

        assertThat(delays).as("full jitter must spread retries out").hasSizeGreaterThan(3);
    }

    @Test
    @DisplayName("first attempt is immediate")
    void firstAttemptIsImmediate() {
        RetryPolicy policy = RetryPolicy.deterministic(5_000L, 60_000L, 10, 1L);

        assertThat(policy.delayBeforeAttempt(0, 0L)).isZero();
        assertThat(policy.delayBeforeAttempt(1, 1_000L))
                .isBetween(1_000L, 6_000L);
    }

    @Test
    @DisplayName("reports exhaustion once the budget is spent")
    void reportsExhaustion() {
        RetryPolicy policy = RetryPolicy.deterministic(1_000L, 30_000L, 5, 1L);

        assertThat(policy.exhausted(4)).isFalse();
        assertThat(policy.exhausted(5)).isTrue();
        assertThat(policy.exhausted(6)).isTrue();
    }

    @Test
    @DisplayName("rejects nonsensical configuration")
    void rejectsBadConfiguration() {
        assertThat(catchThrowable(() -> new RetryPolicy(0L, 10L, 3, RetryPolicy.JitterSource.seededJitter(1L))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new RetryPolicy(100L, 10L, 3, RetryPolicy.JitterSource.seededJitter(1L))))
                .as("the ceiling may not be below the base delay")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new RetryPolicy(10L, 100L, 0, RetryPolicy.JitterSource.seededJitter(1L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static long delayFor(RetryPolicy policy, int attempt, long now) {
        return policy.delayBeforeAttempt(attempt, now) - now;
    }

    private static Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}