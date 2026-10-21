package dev.resqnet.mesh.routing;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Aggregated routing measurements.
 *
 * <p>
 * Every number here is a measurement of something the engine actually did.
 * There is deliberately no modelled or estimated metric: a dashboard that shows
 * simulated figures as if they were field results is worse than no dashboard at
 * all when the product is safety-critical.
 *
 * <p>
 * Latency percentiles are computed from a bounded reservoir rather than a
 * full history so the router's memory use does not grow with uptime.
 */
public final class RoutingMetrics {

    /** Default reservoir size. Large enough for a meaningful p95 in the field. */
    public static final int DEFAULT_LATENCY_SAMPLES = 4096;

    private final LongAdder originated = new LongAdder();
    private final LongAdder directedOriginated = new LongAdder();
    private final LongAdder accepted = new LongAdder();
    private final LongAdder delivered = new LongAdder();
    private final LongAdder forwarded = new LongAdder();
    private final LongAdder duplicateSuppressed = new LongAdder();
    private final LongAdder expired = new LongAdder();
    private final LongAdder hopLimitDrops = new LongAdder();
    private final LongAdder rateLimitDrops = new LongAdder();
    private final LongAdder untrustedDrops = new LongAdder();
    private final LongAdder malformedDrops = new LongAdder();
    private final LongAdder otherDrops = new LongAdder();
    private final LongAdder acknowledgementsSent = new LongAdder();
    private final LongAdder acknowledgementsReceived = new LongAdder();
    private final LongAdder duplicateAcknowledgements = new LongAdder();
    private final LongAdder relayQueueEvictions = new LongAdder();
    private final LongAdder hopTotal = new LongAdder();

    private final long[] hopHistogram = new long[MessageHopLimits.MAX + 1];
    private final LatencyReservoir latency;
    private final LatencyReservoir acknowledgementLatency;

    public RoutingMetrics() {
        this(DEFAULT_LATENCY_SAMPLES);
    }

    public RoutingMetrics(int latencySamples) {
        this.latency = new LatencyReservoir(latencySamples);
        this.acknowledgementLatency = new LatencyReservoir(latencySamples);
    }

    void recordOriginated(boolean directed) {
        originated.increment();
        if (directed) {
            directedOriginated.increment();
        }
    }

    void recordAccepted() {
        accepted.increment();
    }

    void recordDelivered() {
        delivered.increment();
    }

    void recordForwarded(int peers, int hopsTaken) {
        forwarded.increment();
        hopTotal.add(hopsTaken);
    }

    void recordHopCount(int hops) {
        if (hops >= 0 && hops <= MessageHopLimits.MAX) {
            hopHistogram[hops]++;
        }
    }

    void recordDuplicate() {
        duplicateSuppressed.increment();
    }

    void recordExpired() {
        expired.increment();
    }

    void recordHopLimitDrop() {
        hopLimitDrops.increment();
    }

    void recordRateLimitDrop() {
        rateLimitDrops.increment();
    }

    void recordUntrustedDrop() {
        untrustedDrops.increment();
    }

    void recordMalformedDrop() {
        malformedDrops.increment();
    }

    void recordOtherDrop() {
        otherDrops.increment();
    }

    void recordAcknowledgementSent() {
        acknowledgementsSent.increment();
    }

    void recordAcknowledgementReceived() {
        acknowledgementsReceived.increment();
    }

    void recordDuplicateAcknowledgement() {
        duplicateAcknowledgements.increment();
    }

    void recordRelayQueueEviction() {
        relayQueueEvictions.increment();
    }

    void recordDeliveryLatency(long millis) {
        latency.add(millis);
    }

    void recordAcknowledgementLatency(long millis) {
        acknowledgementLatency.add(millis);
    }

    /** Messages created on this device. */
    /**
     * Messages that named a destination and so can be confirmed end to end.
     *
     * <p>
     * A broadcast is never acknowledged: {@code MeshRouter} returns empty
     * from {@code buildAcknowledgement} when there is no destination, because
     * there is nobody to acknowledge to. Mixing the two into one denominator
     * caps the reported delivery rate below 100% however well the mesh works.
     */
    public long directedOriginatedCount() {
        return directedOriginated.sum();
    }

    public long originatedCount() {
        return originated.sum();
    }

    /** Envelopes that passed full validation at some node. */
    public long acceptedCount() {
        return accepted.sum();
    }

    public long deliveredCount() {
        return delivered.sum();
    }

    /** Envelope forwards performed by this node. */
    public long forwardedCount() {
        return forwarded.sum();
    }

    public long duplicateSuppressedCount() {
        return duplicateSuppressed.sum();
    }

    public long expiredCount() {
        return expired.sum();
    }

    public long hopLimitDropCount() {
        return hopLimitDrops.sum();
    }

    public long rateLimitDropCount() {
        return rateLimitDrops.sum();
    }

    public long untrustedDropCount() {
        return untrustedDrops.sum();
    }

    public long malformedDropCount() {
        return malformedDrops.sum();
    }

    public long otherDropCount() {
        return otherDrops.sum();
    }

    public long acknowledgementSentCount() {
        return acknowledgementsSent.sum();
    }

    public long acknowledgementReceivedCount() {
        return acknowledgementsReceived.sum();
    }

    public long duplicateAcknowledgementCount() {
        return duplicateAcknowledgements.sum();
    }

    public long relayQueueEvictionCount() {
        return relayQueueEvictions.sum();
    }

    public long totalDropCount() {
        return duplicateSuppressed.sum() + expired.sum() + hopLimitDrops.sum()
                + rateLimitDrops.sum() + untrustedDrops.sum() + malformedDrops.sum()
                + otherDrops.sum();
    }

    /**
     * Median end-to-end delivery latency in milliseconds, or -1 without samples.
     */
    public long medianDeliveryLatencyMillis() {
        return latency.percentile(50);
    }

    /** 95th percentile end-to-end delivery latency, or -1 without samples. */
    public long p95DeliveryLatencyMillis() {
        return latency.percentile(95);
    }

    public long p99DeliveryLatencyMillis() {
        return latency.percentile(99);
    }

    public long maxDeliveryLatencyMillis() {
        return latency.max();
    }

    /**
     * Raw end-to-end delivery latency samples collected by this node.
     *
     * <p>
     * Exposed so the simulator and the benchmark harness can aggregate
     * percentiles across the whole mesh instead of only within one node. The
     * reservoir is bounded, so this never grows with uptime.
     */
    public long[] deliveryLatencySamples() {
        return latency.snapshot();
    }

    public int deliveryLatencySampleCount() {
        return latency.size();
    }

    public long medianAcknowledgementLatencyMillis() {
        return acknowledgementLatency.percentile(50);
    }

    /** Distribution of hop counts observed across the mesh, index == hops taken. */
    public Map<Integer, Long> hopDistribution() {
        Map<Integer, Long> histogram = new LinkedHashMap<>();
        for (int i = 0; i <= MessageHopLimits.MAX; i++) {
            if (hopHistogram[i] > 0) {
                histogram.put(i, hopHistogram[i]);
            }
        }
        return histogram;
    }

    /**
     * Ratio of forwarding work to distinct useful messages accepted.
     *
     * <p>
     * This is the flooding amplification number. A healthy mesh should sit
     * near the average hop count of the topology. A value that grows without
     * bound signals a deduplication failure.
     */
    public double amplificationFactor() {
        long acceptedTotal = accepted.sum();
        return acceptedTotal == 0 ? 0.0 : (double) forwarded.sum() / acceptedTotal;
    }

    /** Fraction of accepted envelopes that were refused for any reason. */
    public double dropRatio() {
        long total = accepted.sum() + totalDropCount();
        return total == 0 ? 0.0 : (double) totalDropCount() / total;
    }

    /**
     * Folds another instance's counters into this one.
     *
     * <p>
     * Used by the simulator and the benchmark harness to aggregate a
     * mesh-wide view from per-node instances without inventing a second
     * accounting path.
     */
    public void accumulate(RoutingMetrics other) {
        if (other == null || other == this) {
            return;
        }
        originated.add(other.originated.sum());
        directedOriginated.add(other.directedOriginated.sum());
        accepted.add(other.accepted.sum());
        delivered.add(other.delivered.sum());
        forwarded.add(other.forwarded.sum());
        duplicateSuppressed.add(other.duplicateSuppressed.sum());
        expired.add(other.expired.sum());
        hopLimitDrops.add(other.hopLimitDrops.sum());
        rateLimitDrops.add(other.rateLimitDrops.sum());
        untrustedDrops.add(other.untrustedDrops.sum());
        malformedDrops.add(other.malformedDrops.sum());
        otherDrops.add(other.otherDrops.sum());
        acknowledgementsSent.add(other.acknowledgementsSent.sum());
        acknowledgementsReceived.add(other.acknowledgementsReceived.sum());
        duplicateAcknowledgements.add(other.duplicateAcknowledgements.sum());
        relayQueueEvictions.add(other.relayQueueEvictions.sum());
        hopTotal.add(other.hopTotal.sum());
        synchronized (hopHistogram) {
            for (int i = 0; i < hopHistogram.length; i++) {
                hopHistogram[i] += other.hopHistogram[i];
            }
        }
        latency.mergeFrom(other.latency);
        acknowledgementLatency.mergeFrom(other.acknowledgementLatency);
    }

    /** Resets every counter. Used between simulation runs. */
    public void reset() {
        originated.reset();
        directedOriginated.reset();
        accepted.reset();
        delivered.reset();
        forwarded.reset();
        duplicateSuppressed.reset();
        expired.reset();
        hopLimitDrops.reset();
        rateLimitDrops.reset();
        untrustedDrops.reset();
        malformedDrops.reset();
        otherDrops.reset();
        acknowledgementsSent.reset();
        acknowledgementsReceived.reset();
        duplicateAcknowledgements.reset();
        relayQueueEvictions.reset();
        hopTotal.reset();
        Arrays.fill(hopHistogram, 0L);
        latency.reset();
        acknowledgementLatency.reset();
    }

    /** Fixed-capacity circular buffer of latency samples. */
    private static final class LatencyReservoir {
        private final long[] samples;
        private int cursor;
        private int count;
        private long max = Long.MIN_VALUE;

        LatencyReservoir(int capacity) {
            this.samples = new long[Math.max(16, capacity)];
        }

        synchronized void add(long value) {
            if (value < 0) {
                return;
            }
            samples[cursor] = value;
            cursor = (cursor + 1) % samples.length;
            if (count < samples.length) {
                count++;
            }
            if (value > max) {
                max = value;
            }
        }

        synchronized int size() {
            return count;
        }

        synchronized long max() {
            return count == 0 ? -1 : max;
        }

        synchronized long percentile(int percentile) {
            if (count == 0) {
                return -1;
            }
            long[] sorted = Arrays.copyOf(samples, count);
            Arrays.sort(sorted);
            int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }

        synchronized void reset() {
            Arrays.fill(samples, 0L);
            cursor = 0;
            count = 0;
            max = Long.MIN_VALUE;
        }

        synchronized long[] snapshot() {
            return count == 0 ? new long[0] : Arrays.copyOf(samples, count);
        }

        synchronized void mergeFrom(LatencyReservoir other) {
            long[] incoming = other.snapshot();
            for (long value : incoming) {
                add(value);
            }
        }
    }

    /** Local routing limits, exposed for configuration and for event formatting. */
    public static final class MessageHopLimits {
        /** Default hop budget applied to locally created messages. */
        public static final int DEFAULT = 6;

        /** Absolute ceiling, mirroring the protocol limit. */
        public static final int MAX = dev.resqnet.mesh.protocol.MessageEnvelope.MAX_HOP_LIMIT;

        private MessageHopLimits() {
        }
    }
}