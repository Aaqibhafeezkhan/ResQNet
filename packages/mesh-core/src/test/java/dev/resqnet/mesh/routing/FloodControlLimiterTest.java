package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Flood control is what stops a single misbehaving node from being able to drain
 * every other phone in the area. These tests pin the behaviour that matters:
 * normal traffic is bounded, life-safety traffic never is, and state is released
 * when a peer leaves.
 */
@DisplayName("FloodControlLimiter")
class FloodControlLimiterTest {

    private final FloodControlLimiter limiter = new FloodControlLimiter();

    @Test
    @DisplayName("allows a burst then throttles")
    void allowsBurstThenThrottles() {
        long now = 1_000L;
        int allowed = 0;
        for (int i = 0; i < 50; i++) {
            if (limiter.tryAcquire("rqn1_a", false, 2.0, 10, now)) {
                allowed++;
            }
        }

        assertThat(allowed).isEqualTo(10);
    }

    @Test
    @DisplayName("refills over time")
    void refillsOverTime() {
        long t0 = 1_000L;
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("rqn1_a", false, 5.0, 5, t0)).isTrue();
        }
        assertThat(limiter.tryAcquire("rqn1_a", false, 5.0, 5, t0)).isFalse();

        // One second at 5/s restores 5 credits.
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("rqn1_a", false, 5.0, 5, t0 + 1_000L)).isTrue();
        }
    }

    @Test
    @DisplayName("tracks each origin independently")
    void tracksOriginsIndependently() {
        long now = 1_000L;
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("rqn1_a", false, 1.0, 10, now)).isTrue();
        }
        assertThat(limiter.tryAcquire("rqn1_a", false, 1.0, 10, now)).isFalse();
        assertThat(limiter.tryAcquire("rqn1_b", false, 1.0, 10, now))
                .as("one noisy device must not be able to starve the mesh")
                .isTrue();
    }

    @Test
    @DisplayName("never throttles life-safety traffic")
    void neverThrottlesLifeSafety() {
        long now = 1_000L;
        for (int i = 0; i < 10_000; i++) {
            assertThat(limiter.tryAcquire("rqn1_a", true, 0.1, 1, now)).isTrue();
        }
    }

    @Test
    @DisplayName("releases state when a peer disconnects")
    void forgetsOnDisconnect() {
        limiter.tryAcquire("rqn1_a", false, 1.0, 2, 1_000L);
        assertThat(limiter.trackedOrigins()).isEqualTo(1);

        limiter.forget("rqn1_a");

        assertThat(limiter.trackedOrigins()).isZero();
        assertThat(limiter.tryAcquire("rqn1_a", false, 1.0, 2, 1_000L)).isTrue();
    }

    @Test
    @DisplayName("refuses unknown origins once the tracking table is full")
    void boundsTrackedOrigins() {
        FloodControlLimiter bounded = new FloodControlLimiter(4);
        long now = 1_000L;
        for (int i = 0; i < 4; i++) {
            assertThat(bounded.tryAcquire("rqn1_" + i, false, 1.0, 1, now)).isTrue();
        }

        assertThat(bounded.tryAcquire("rqn1_new", false, 1.0, 1, now)).isFalse();
        assertThat(bounded.trackedOrigins()).isEqualTo(4);
    }
}