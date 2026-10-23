package dev.resqnet.mesh.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.simulator.json.Json;

/**
 * Provenance recorded in a simulation report.
 *
 * <p>The Replay Lab draws a network and replays a journey from these fields.
 * Each of them therefore has to be a fact about the run rather than an
 * inference, and each has to be absent when the run did not produce it.
 */
@DisplayName("Report provenance")
class ReportProvenanceTest {

    private static SimulationReport tryRun(SimulationConfig config) throws Exception {
        try (MeshSimulation simulation = new MeshSimulation(config)) {
            return simulation.run();
        }
    }

    private static SimulationConfig ring(int nodes, long seed) {
        return SimulationConfig.defaults(Topology.of(Topology.Shape.RING, nodes, 3, seed), seed);
    }

    @Test
    @DisplayName("records the topology edges it actually built")
    void recordsTopologyEdges() throws Exception {
        SimulationConfig config = ring(8, 42L);
        SimulationReport report = tryRun(config);

        Map<String, Object> json = Json.parseObject(report.toJson().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> simulation = (Map<String, Object>) json.get("simulation");
        @SuppressWarnings("unchecked")
        List<Object> edges = (List<Object>) simulation.get("edges");

        assertThat(edges)
                .as("the edges must be the topology the simulator built")
                .hasSize(config.topology().edges().size());
        assertThat(edges.get(0)).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("records the configuration needed to reproduce the run")
    void recordsReproducibleConfiguration() throws Exception {
        SimulationConfig config = ring(6, 42L).startingPartitioned();
        SimulationReport report = tryRun(config);

        Map<String, Object> json = Json.parseObject(report.toJson().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> simulation = (Map<String, Object>) json.get("simulation");

        assertThat(((Number) simulation.get("degree")).intValue())
                .isEqualTo(config.topology().degree());
        assertThat(((Number) simulation.get("churnEveryRounds")).intValue())
                .isEqualTo(config.churnEveryRounds());
        assertThat(((Number) simulation.get("messageLifetimeSeconds")).longValue())
                .isEqualTo(config.messageLifetimeSeconds());
        assertThat(((Number) simulation.get("startEpochSeconds")).longValue())
                .isEqualTo(config.startEpochSeconds());
        assertThat(simulation.get("startPartitioned"))
                .as("a run that started partitioned must say so")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("records every link cut and restore, not just a count")
    void recordsLinkChanges() throws Exception {
        SimulationReport report = tryRun(ring(8, 42L));

        assertThat(report.linkChangeRecords())
                .as("the initial radio handshake and the churn cuts must both be recorded")
                .isNotEmpty();
        assertThat(report.linkChangeRecords())
                .allSatisfy(change -> {
                    assertThat(change.fromIndex()).isNotNegative();
                    assertThat(change.toIndex()).isNotNegative();
                    assertThat(change.fromIndex()).isNotEqualTo(change.toIndex());
                });
        assertThat(report.linkChangeRecords())
                .as("a cut and its matching restore must both be present")
                .anyMatch(change -> !change.up())
                .anyMatch(change -> change.up());
    }

    @Test
    @DisplayName("records which node composed and addressed each message")
    void recordsMessageProvenance() throws Exception {
        SimulationReport report = tryRun(ring(6, 5L));

        assertThat(report.messages()).isNotEmpty();
        assertThat(report.messages())
                .allSatisfy(message -> {
                    assertThat(message.messageId()).isNotBlank();
                    assertThat(message.originIndex()).isBetween(0, report.nodeCount() - 1);
                    if (message.broadcast()) {
                        assertThat(message.destinationIndex())
                                .as("a broadcast names nobody")
                                .isEqualTo(-1);
                    } else {
                        assertThat(message.destinationIndex()).isBetween(0, report.nodeCount() - 1);
                    }
                });
        assertThat(report.messages().stream().filter(SimulationReport.MessageReport::broadcast))
                .as("the default scenario mixes broadcasts in, so they must be labelled")
                .isNotEmpty();
    }

    @Test
    @DisplayName("populates peer indexes on the events that name a peer")
    void populatesPeerIndexes() throws Exception {
        SimulationReport report = tryRun(ring(8, 42L));

        assertThat(report.events())
                .as("a forward event names the peers the frame was handed to")
                .filteredOn(event -> event.kind().equals("forwarded"))
                .allSatisfy(event -> event.peerIndexes()
                        .forEach(index -> assertThat(index).isBetween(0, report.nodeCount() - 1)));

        assertThat(report.events())
                .as("accepted events name the peer the frame arrived from")
                .filteredOn(event -> event.kind().equals("accepted"))
                .anySatisfy(event -> assertThat(event.peerIndexes()).isNotEmpty());
    }

    @Test
    @DisplayName("reports how much of the event history was retained")
    void reportsEventWindow() throws Exception {
        SimulationReport report = tryRun(ring(8, 42L));

        Map<String, Object> json = Json.parseObject(report.toJson().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> window = (Map<String, Object>) json.get("eventWindow");

        assertThat(window).containsKeys("retained", "total", "truncated");
        assertThat(((Number) window.get("retained")).longValue())
                .isEqualTo(report.events().size());
        assertThat(((Number) window.get("total")).longValue())
                .isEqualTo(report.totalEventCount());
        assertThat(report.eventsTruncated())
                .isEqualTo(report.totalEventCount() > report.events().size());
    }

    @Test
    @DisplayName("keeps every pre-existing report member intact")
    void staysBackwardCompatible() throws Exception {
        SimulationReport report = tryRun(ring(6, 8L));

        Map<String, Object> json = Json.parseObject(report.toJson().toString());

        assertThat(json).containsKeys("simulation", "outcome", "deliveryRate",
                "amplificationFactor", "latencyMillis", "hopDistribution", "radio", "timing",
                "nodes", "events");
        @SuppressWarnings("unchecked")
        Map<String, Object> radio = (Map<String, Object>) json.get("radio");
        assertThat(radio).containsKeys("framesSent", "framesDelivered", "framesDropped",
                "linkChanges", "partitions");
        assertThat(radio.get("linkChanges"))
                .as("radio.linkChanges stays the count it always was")
                .isInstanceOf(Number.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> outcome = (Map<String, Object>) json.get("outcome");
        assertThat(outcome).containsKeys("originated", "directedOriginated", "broadcastOriginated",
                "acknowledged", "duplicatesSuppressed", "forwards");
    }
}
