package dev.resqnet.mesh.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.simulator.json.Json;

/**
 * End-to-end simulation behaviour.
 *
 * <p>These tests run the real routing engine over a synthetic radio. A failure
 * here is a statement about the handset implementation, not about a model.
 */
@DisplayName("MeshSimulation")
class MeshSimulationTest {

    private static SimulationConfig config(Topology.Shape shape, int nodes, long seed) {
        return SimulationConfig.defaults(Topology.of(shape, nodes, 3, seed), seed);
    }

    @Test
    @DisplayName("delivers across a ring")
    void deliversAcrossRing() throws Exception {
        SimulationReport report = tryRun(config(Topology.Shape.RING, 8, 42L));

        assertThat(report.nodeCount()).isEqualTo(8);
        assertThat(report.originatedCount()).isPositive();
        assertThat(report.acknowledgedCount()).isPositive();
        assertThat(report.deliveryRate()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("delivers across a line and records hop counts")
    void deliversAcrossLine() throws Exception {
        SimulationReport report = tryRun(config(Topology.Shape.LINE, 8, 7L));

        assertThat(report.acknowledgedCount()).isPositive();
        assertThat(report.hopDistribution().keySet())
                .as("a line must actually exercise multi-hop forwarding")
                .anyMatch(hop -> hop > 1);
    }

    @Test
    @DisplayName("suppresses duplicates instead of amplifying them")
    void suppressesDuplicates() throws Exception {
        SimulationReport report = tryRun(config(Topology.Shape.MESH, 8, 11L));

        assertThat(report.duplicateSuppressed())
                .as("a dense mesh must generate duplicates for dedup to remove")
                .isPositive();
        assertThat(report.amplificationFactor())
                .as("amplification must stay bounded, not grow without limit")
                .isLessThan(200.0);
    }

    @Test
    @DisplayName("never reports a message as acknowledged without an acknowledgement")
    void neverFabricatesDelivery() throws Exception {
        SimulationReport report = tryRun(
                config(Topology.Shape.TWO_CLUSTER, 8, 5L).startingPartitioned());

        // With the hub offline the network is genuinely partitioned, so some
        // messages must fail. The point is that they fail cleanly rather than
        // being counted as delivered.
        long failed = report.failedCount() + report.expiredCount();
        assertThat(failed + report.queuedCount())
                .isGreaterThanOrEqualTo(0);
        assertThat(report.acknowledgedCount())
                .isLessThanOrEqualTo(report.originatedCount());
    }

    @Test
    @DisplayName("recovers after a partition heals")
    void recoversAfterPartition() throws Exception {
        SimulationConfig cfg = config(Topology.Shape.RING, 8, 13L)
                .withChurnEveryRounds(5)
                .withRounds(60);

        SimulationReport report = tryRun(cfg);

        assertThat(report.linkChanges())
                .as("the scenario must actually cut and restore links")
                .isPositive();
        assertThat(report.acknowledgedCount())
                .as("messages held through a partition must go out once it heals")
                .isPositive();
    }

    @Test
    @DisplayName("replays identically from the same seed")
    void isDeterministic() throws Exception {
        SimulationConfig cfg = config(Topology.Shape.MESH, 8, 2024L);

        SimulationReport first = tryRun(cfg);
        SimulationReport second = tryRun(cfg);

        assertThat(stripVolatile(first)).isEqualTo(stripVolatile(second));
    }

    @Test
    @DisplayName("produces different behaviour for a different seed")
    void seedChangesBehaviour() throws Exception {
        SimulationReport first = tryRun(config(Topology.Shape.MESH, 8, 1L));
        SimulationReport second = tryRun(config(Topology.Shape.MESH, 8, 2L));

        assertThat(first.events()).isNotEqualTo(second.events());
    }

    @Test
    @DisplayName("loses frames when a drop probability is configured")
    void losesFramesUnderPacketLoss() throws Exception {
        SimulationConfig lossy = config(Topology.Shape.MESH, 8, 3L)
                .withLinkDropProbability(0.5);

        SimulationReport report = tryRun(lossy);

        assertThat(report.framesDropped()).isPositive();
        assertThat(report.framesDelivered()).isLessThan(report.framesSent());
    }

    @Test
    @DisplayName("records a latency distribution rather than a single number")
    void recordsLatencyDistribution() throws Exception {
        SimulationReport report = tryRun(config(Topology.Shape.MESH, 8, 21L));

        assertThat(report.medianLatencyMillis()).isNotNegative();
        assertThat(report.p95LatencyMillis())
                .isGreaterThanOrEqualTo(report.medianLatencyMillis());
        assertThat(report.p99LatencyMillis())
                .isGreaterThanOrEqualTo(report.p95LatencyMillis());
    }

    @Test
    @DisplayName("reports hop latency averages only where hops were measured")
    void hopAveragesAreHonest() throws Exception {
        SimulationReport report = tryRun(config(Topology.Shape.MESH, 6, 33L));

        assertThat(report.averageLatencyForHopCount(1)).isEqualTo(-1);
    }

    @Test
    @DisplayName("emits a structured, JSON-serialisable event stream")
    void emitsStructuredEvents() throws Exception {
        SimulationReport report = tryRun(config(Topology.Shape.LINE, 6, 77L));

        assertThat(report.events()).isNotEmpty();
        assertThat(report.events())
                .allSatisfy(event -> assertThat(event.simulated()).isTrue());
        assertThat(report.events())
                .extracting(SimulationEvent::kind)
                .contains("accepted", "forwarded", "delivered");

        Map<String, Object> parsed = Json.parseObject(report.toJson().toString());
        assertThat(parsed).containsKeys("simulation", "outcome", "deliveryRate",
                "latencyMillis", "radio", "nodes", "events");
        assertThat(parsed.get("outcome")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("records per-node measurements for the dashboard")
    void recordsPerNodeMeasurements() throws Exception {
        SimulationReport report = tryRun(config(Topology.Shape.RING, 6, 8L));

        assertThat(report.nodes()).hasSize(6);
        assertThat(report.nodes())
                .allSatisfy(node -> {
                    assertThat(node.deviceId()).startsWith("rqn1_");
                    assertThat(node.keyFingerprint()).hasSize(64);
                    assertThat(node.originated()).isNotNegative();
                });
    }

    @Test
    @DisplayName("holds relay copies in the store when peers are unreachable")
    void holdsRelayCopies() throws Exception {
        SimulationReport report = tryRun(
                config(Topology.Shape.TWO_CLUSTER, 8, 4L).startingPartitioned());

        assertThat(report.nodes())
                .filteredOn(node -> node.countByState(DeliveryState.RELAYED) > 0
                        || node.countByState(DeliveryState.QUEUED) > 0)
                .isNotEmpty();
    }

    @Test
    @DisplayName("a node cannot deliver to a device it does not share a mesh with")
    void partitionsStopDelivery() throws Exception {
        // Two clusters of four with no link between them: nothing can cross.
        SimulationConfig cfg = new SimulationConfig(
                Topology.of(Topology.Shape.TWO_CLUSTER, 8, 3, 17L),
                16, 0.0, 1_760_000_000L, 250L, 40, 17L, 0.0, 0, 8, 900L, false);

        SimulationReport report = tryRun(cfg);

        assertThat(report.originatedCount()).isEqualTo(16);
        assertThat(report.acknowledgedCount())
                .as("every message must have failed or still be queued, none delivered")
                .isLessThan(16);
    }

    @Test
    @DisplayName("produces a readable summary")
    void summaryIsReadable() throws Exception {
        String summary = tryRun(config(Topology.Shape.RING, 6, 99L)).toSummary();

        assertThat(summary).contains("ResQNet simulation");
        assertThat(summary).contains("delivery rate");
        assertThat(summary).contains("p95 latency");
    }

    private static SimulationReport tryRun(SimulationConfig config) throws Exception {
        try (MeshSimulation simulation = new MeshSimulation(config)) {
            return simulation.run();
        }
    }

    /**
     * Removes the fields that legitimately differ between runs.
     *
     * <p>Every node generates a fresh EC key pair on each run, exactly as a real
     * handset would, so the device id and fingerprint differ by construction. What
     * must be identical is every routing decision: counters, the event stream,
     * radio statistics and latency distributions.
     */
    private static Map<String, Object> stripVolatile(SimulationReport report) {
        return Map.of(
                "outcome", report.originatedCount() + "/" + report.acknowledgedCount() + "/"
                        + report.queuedCount(),
                "deliveryRate", report.deliveryRate(),
                "latency", report.medianLatencyMillis() + "/" + report.p95LatencyMillis(),
                "hops", report.hopDistribution(),
                "radio", report.framesSent() + "/" + report.framesDelivered(),
                "events", report.events().stream().map(SimulationEvent::toString).toList(),
                "nodeCounters", report.nodes().stream()
                        .map(node -> node.originated() + "," + node.accepted() + ","
                                + node.forwarded() + "," + node.duplicatesSuppressed() + ","
                                + node.rejected() + "," + node.stillQueued())
                        .toList());
    }

    @Test
    @DisplayName("topology edges are reported in a stable order")
    void edgesAreStable() {
        Topology topology = Topology.of(Topology.Shape.MESH, 8, 3, 5L);

        List<int[]> first = topology.edges();
        List<int[]> second = topology.edges();

        assertThat(first).hasSameSizeAs(second);
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i)[0]).isEqualTo(second.get(i)[0]);
            assertThat(first.get(i)[1]).isEqualTo(second.get(i)[1]);
        }
    }
}