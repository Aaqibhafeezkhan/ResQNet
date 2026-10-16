package dev.resqnet.mesh.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the reported delivery rate actually measures.
 *
 * <p>Found during the M10 investigation into why reported delivery fell with
 * node count. It does not, on a directed-message basis. The reported rate had
 * been dividing acknowledged messages by <em>all</em> originated messages,
 * including broadcasts, and a broadcast can never be acknowledged by
 * construction: {@code MeshRouter.buildAcknowledgement} returns empty when the
 * envelope has no destination, because there is nobody to acknowledge to.
 *
 * <p>The consequence was that a run with a 25% broadcast ratio could not report
 * more than 75% however well the mesh performed, and a perfectly healthy large
 * mesh looked broken for a reason that had nothing to do with routing.
 */
@DisplayName("Delivery rate accounting")
class DeliveryRateAccountingTest {

    @Test
    @DisplayName("a broadcast is never acknowledged, by construction")
    void broadcastsCannotBeAcknowledged() throws Exception {
        SimulationReport report = run(config(Topology.Shape.MESH, 8, 77L, 1.0));

        assertThat(report.broadcastOriginatedCount())
                .as("every message in this run was a broadcast")
                .isPositive();
        assertThat(report.directedOriginatedCount())
                .as("no directed messages means the broadcast ratio worked")
                .isZero();
        assertThat(report.acknowledgedCount())
                .as("a broadcast has no destination, so nothing can acknowledge it")
                .isZero();
    }

    @Test
    @DisplayName("delivery rate is not capped by the broadcast ratio")
    void broadcastRatioDoesNotCapTheRate() throws Exception {
        // A mixed run used to report at most (1 - broadcastRatio) no matter how
        // well routing performed, purely because broadcasts can never be
        // confirmed. On a mesh small enough that everything is within one hop,
        // every directed message must be acknowledged.
        SimulationReport mixed = run(config(Topology.Shape.MESH, 8, 77L, 0.25));

        assertThat(mixed.broadcastOriginatedCount()).isPositive();
        assertThat(mixed.directedOriginatedCount()).isPositive();
        assertThat(mixed.deliveryRate())
                .as("the denominator is directed messages only")
                .isEqualTo((double) mixed.acknowledgedCount() / mixed.directedOriginatedCount());
    }

    @Test
    @DisplayName("a broadcast is still counted as originated, so it is not hidden")
    void broadcastsRemainVisibleInTheTotals() throws Exception {
        SimulationReport report = run(config(Topology.Shape.MESH, 10, 5L, 1.0));

        assertThat(report.originatedCount())
                .as("the total must still include broadcasts; folding them away"
                        + " would hide a real property of the workload")
                .isEqualTo(report.broadcastOriginatedCount());
        assertThat(report.deliveredCount())
                .as("a broadcast is still delivered to every node that receives it,"
                        + " it simply cannot be confirmed")
                .isPositive();
    }

    @Test
    @DisplayName("directed and broadcast counts sum to the total")
    void countsAreConsistent() throws Exception {
        SimulationReport report = run(config(Topology.Shape.MESH, 12, 31L, 0.5));

        assertThat(report.directedOriginatedCount() + report.broadcastOriginatedCount())
                .isEqualTo(report.originatedCount());
    }

    private static SimulationConfig config(Topology.Shape shape, int nodes, long seed,
                                           double broadcastRatio) {
        return new SimulationConfig(Topology.of(shape, nodes, 3, seed), 24, broadcastRatio,
                1_760_000_000L, 250L, 60, seed, 0.0, 12, 8, 900L, false);
    }

    private static SimulationReport run(SimulationConfig config) throws Exception {
        try (MeshSimulation simulation = new MeshSimulation(config)) {
            return simulation.run();
        }
    }
}