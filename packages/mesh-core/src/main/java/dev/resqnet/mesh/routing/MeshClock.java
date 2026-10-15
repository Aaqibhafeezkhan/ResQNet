package dev.resqnet.mesh.routing;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Time source for the routing engine.
 *
 * <p>Every expiry check, backoff calculation and latency measurement in
 * mesh-core goes through this interface rather than {@code System.currentTimeMillis()}.
 * That is what makes the simulator reproducible: with a {@link ManualClock} and
 * a fixed random seed, a run produces byte-identical event streams on any
 * machine, and a failure found at 10^6 simulated seconds can be replayed.
 */
@FunctionalInterface
public interface MeshClock {

    /** Current wall-clock time in epoch milliseconds. */
    long nowMillis();

    /** Production clock backed by the system time. */
    static MeshClock system() {
        return System::currentTimeMillis;
    }

    /**
     * Test and simulation clock that only moves when told to.
     *
     * <p>Using a monotonic counter rather than a fixed step keeps assertions in
     * tests readable: the test decides exactly when time passes and by how much.
     */
    final class ManualClock implements MeshClock {
        private final AtomicLong now;

        public ManualClock(long startEpochMillis) {
            this.now = new AtomicLong(startEpochMillis);
        }

        public static ManualClock atEpochSecond(long epochSeconds) {
            return new ManualClock(epochSeconds * 1000L);
        }

        @Override
        public long nowMillis() {
            return now.get();
        }

        /** Advances the clock and returns the new time. */
        public long advance(long millis) {
            if (millis < 0) {
                throw new IllegalArgumentException("Manual clocks never move backwards");
            }
            return now.addAndGet(millis);
        }
    }
}