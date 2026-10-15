package dev.resqnet.mesh.routing;

/**
 * Tunable routing limits.
 *
 * <p>Every bound in this class exists because mesh traffic is attacker-reachable.
 * An unbounded queue is a memory exhaustion vector; an unbounded fan-out is a
 * battery exhaustion vector; an unbounded lifetime is a storage exhaustion
 * vector. Defaults are chosen for a phone on a rescue scene, where a message
 * older than an hour is no longer useful but a battery that dies is fatal.
 */
public final class RouterConfig {

    /** Hop budget for locally created messages. */
    private final int hopLimit;
    /** Default lifetime for locally created messages. */
    private final long lifetimeMillis;
    /** Hard ceiling on non-terminal messages held in the store. */
    private final int maxPendingMessages;
    /** Hard ceiling on non-terminal messages held for relaying others' traffic. */
    private final int maxRelayMessages;
    /** Maximum directly connected peers one envelope is handed to. */
    private final int maxFanOut;
    /** Whether non-identity traffic must carry a valid signature. */
    private final boolean requireSignature;
    /** Whether delivering a message automatically generates an acknowledgement. */
    private final boolean acknowledgementsEnabled;
    /** Maximum lifetime any peer may request, enforced on the receive path. */
    private final long maxAcceptedLifetimeMillis;
    /** Maximum hop budget any peer may request. */
    private final int maxAcceptedHopLimit;
    /** Sustained forwarding rate allowed per origin, per second. */
    private final double perOriginForwardRatePerSecond;
    /** Burst allowance for the per-origin forward rate. */
    private final int perOriginBurst;

    private RouterConfig(Builder b) {
        this.hopLimit = b.hopLimit;
        this.lifetimeMillis = b.lifetimeMillis;
        this.maxPendingMessages = b.maxPendingMessages;
        this.maxRelayMessages = b.maxRelayMessages;
        this.maxFanOut = b.maxFanOut;
        this.requireSignature = b.requireSignature;
        this.acknowledgementsEnabled = b.acknowledgementsEnabled;
        this.maxAcceptedLifetimeMillis = b.maxAcceptedLifetimeMillis;
        this.maxAcceptedHopLimit = b.maxAcceptedHopLimit;
        this.perOriginForwardRatePerSecond = b.perOriginForwardRatePerSecond;
        this.perOriginBurst = b.perOriginBurst;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Balanced defaults suitable for a handset and for simulation. */
    public static RouterConfig defaults() {
        return builder().build();
    }

    public int hopLimit() {
        return hopLimit;
    }

    public long lifetimeMillis() {
        return lifetimeMillis;
    }

    public int maxPendingMessages() {
        return maxPendingMessages;
    }

    public int maxRelayMessages() {
        return maxRelayMessages;
    }

    public int maxFanOut() {
        return maxFanOut;
    }

    public boolean requireSignature() {
        return requireSignature;
    }

    public boolean acknowledgementsEnabled() {
        return acknowledgementsEnabled;
    }

    public long maxAcceptedLifetimeMillis() {
        return maxAcceptedLifetimeMillis;
    }

    public int maxAcceptedHopLimit() {
        return maxAcceptedHopLimit;
    }

    public double perOriginForwardRatePerSecond() {
        return perOriginForwardRatePerSecond;
    }

    public int perOriginBurst() {
        return perOriginBurst;
    }

    /** Builder with validation on every field. */
    public static final class Builder {
        private int hopLimit = RoutingMetrics.MessageHopLimits.DEFAULT;
        private long lifetimeMillis = 15L * 60 * 1000;
        private int maxPendingMessages = 512;
        private int maxRelayMessages = 512;
        private int maxFanOut = 8;
        private boolean requireSignature = true;
        private boolean acknowledgementsEnabled = true;
        private long maxAcceptedLifetimeMillis = dev.resqnet.mesh.protocol
                .MessageEnvelope.MAX_LIFETIME_MILLIS;
        private int maxAcceptedHopLimit = RoutingMetrics.MessageHopLimits.MAX;
        private double perOriginForwardRatePerSecond = 8.0;
        private int perOriginBurst = 16;

        public Builder hopLimit(int v) {
            this.hopLimit = v;
            return this;
        }

        public Builder lifetimeMillis(long v) {
            this.lifetimeMillis = v;
            return this;
        }

        public Builder maxPendingMessages(int v) {
            this.maxPendingMessages = v;
            return this;
        }

        public Builder maxRelayMessages(int v) {
            this.maxRelayMessages = v;
            return this;
        }

        public Builder maxFanOut(int v) {
            this.maxFanOut = v;
            return this;
        }

        public Builder requireSignature(boolean v) {
            this.requireSignature = v;
            return this;
        }

        public Builder acknowledgementsEnabled(boolean v) {
            this.acknowledgementsEnabled = v;
            return this;
        }

        public Builder maxAcceptedLifetimeMillis(long v) {
            this.maxAcceptedLifetimeMillis = v;
            return this;
        }

        public Builder maxAcceptedHopLimit(int v) {
            this.maxAcceptedHopLimit = v;
            return this;
        }

        public Builder perOriginForwardRate(double rate, int burst) {
            this.perOriginForwardRatePerSecond = rate;
            this.perOriginBurst = burst;
            return this;
        }

        public RouterConfig build() {
            if (hopLimit < 1 || hopLimit > maxAcceptedHopLimit) {
                throw new IllegalArgumentException(
                        "hopLimit must be within [1, " + maxAcceptedHopLimit + "]");
            }
            if (lifetimeMillis <= 0 || lifetimeMillis > maxAcceptedLifetimeMillis) {
                throw new IllegalArgumentException(
                        "lifetimeMillis must be within (0, " + maxAcceptedLifetimeMillis + "]");
            }
            if (maxPendingMessages <= 0 || maxRelayMessages <= 0 || maxFanOut <= 0) {
                throw new IllegalArgumentException("Queue and fan-out bounds must be positive");
            }
            if (perOriginForwardRatePerSecond <= 0 || perOriginBurst <= 0) {
                throw new IllegalArgumentException("Forward rate bounds must be positive");
            }
            return new RouterConfig(this);
        }
    }
}