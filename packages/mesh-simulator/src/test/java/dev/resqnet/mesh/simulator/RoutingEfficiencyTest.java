package dev.resqnet.mesh.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * How close the router gets to what the graph actually permits.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>Reported delivery fell from 90% at 10 nodes to 21.6% at 500 and the
 * natural reading was that the router was losing messages. It was not. The
 * MESH shape the simulator builds is a circulant band with diameter n/4, so at
 * 500 nodes only a quarter of node pairs are within a 32-hop budget at all.
 * The router was already achieving almost all of the delivery the topology
 * permitted.
 *
 * <p>That distinction is easy to state and easy to lose, and once lost it sends
 * someone to change a working router. So this asserts it directly: for a range
 * of sizes and hop budgets, the router must reach a large fraction of the
 * structural ceiling. If a future change genuinely degrades routing, this
 * fails. If only the graph is unfavourable, it passes and the graph is the
 * thing to look at.
 *
 * <h2>What it does not claim</h2>
 *
 * <p>Anything about real radios. Every figure here is a discrete-event
 * simulation with a modelled radio and a manual clock.
 */
@DisplayName("Routing efficiency against the structural ceiling")
@Tag("slow")
class RoutingEfficiencyTest {

    /**
     * The router must reach at least this share of the graph's own limit.
     *
     * <p>Not 1.0. The ceiling assumes a message may take any shortest path,
     * while the router also spends a hop being refused by a node it cannot help,
     * re-queues a relay whose neighbours are busy, and competes with churn.
     * Some shortfall is real; a collapse is a regression.
     */
    private static final double MINIMUM_SHARE_OF_CEILING = 0.80;

    private static final long[] SEEDS = {11L, 22L};

    @Test
    @DisplayName("reaches most of the achievable delivery at every hop budget")
    void reachesMostOfTheCeiling() throws Exception {
        // Directed messages only. A broadcast has no destination and can never
        // be acknowledged, so including it measures the workload, not the
        // router.
        // Sized to stay inside a unit test budget. The sweep across every size
        // and budget is a benchmark, not a test; what a test needs is enough
        // representative points to catch a regression, and these three span
        // "budget far too small" through "budget more than sufficient".
        for (int nodes : new int[] {10, 25, 50}) {
            for (int hopLimit : new int[] {4, 32}) {
                double ceiling = fractionWithinBudget(Topology.Shape.MESH, nodes, hopLimit);
                double measured = meanDirectedDelivery(nodes, hopLimit);

                assertThat(measured)
                        .as("mesh/%d nodes/%d hops: the graph permits %.1f%% of pairs to be"
                                + " reached, and the router delivered %.1f%%. Falling short of"
                                + " the graph means a routing regression; matching it means the"
                                + " limit is the topology.",
                                nodes, hopLimit, ceiling * 100, measured * 100)
                        .isGreaterThanOrEqualTo(ceiling * MINIMUM_SHARE_OF_CEILING);
            }
        }
    }

    @Test
    @DisplayName("a small-diameter graph of the same size delivers nearly everything")
    void smallDiameterGraphDelivers() throws Exception {
        // The control for the test above. Same node count, same hop budget,
        // same router, a graph whose diameter is far below the budget. If this
        // also delivered poorly, the router would be at fault.
        double ceiling = fractionWithinBudget(Topology.Shape.RANDOM, 50, 8);
        double measured = meanDirectedDelivery(50, 8, Topology.Shape.RANDOM);

        assertThat(ceiling)
                .as("a 50-node random graph is within 8 hops for essentially every pair")
                .isGreaterThan(0.95);
        assertThat(measured)
                .as("with the topology no longer the constraint, delivery should be high")
                .isGreaterThan(0.85);
    }

    @Test
    @DisplayName("MESH has a diameter that grows linearly, which is the real explanation")
    void meshDiameterGrowsLinearly() {
        // No simulation, only breadth-first search, so the large sizes here are
        // cheap and are exactly the sizes that matter to the claim.
        // Recorded as an assertion rather than a comment so that changing the
        // MESH generator to something genuinely mesh-like is a deliberate,
        // visible act rather than a silent one.
        assertThat(diameter(Topology.Shape.MESH, 100)).isEqualTo(25);
        assertThat(diameter(Topology.Shape.MESH, 200)).isEqualTo(50);
        assertThat(diameter(Topology.Shape.MESH, 500)).isEqualTo(125);
        assertThat(diameter(Topology.Shape.RANDOM, 500))
                .as("the same size with a well-mixed graph")
                .isLessThan(20);
    }

    // ------------------------------------------------------------- helpers

    private static double meanDirectedDelivery(int nodes, int hopLimit) throws Exception {
        return meanDirectedDelivery(nodes, hopLimit, Topology.Shape.MESH);
    }

    private static double meanDirectedDelivery(int nodes, int hopLimit, Topology.Shape shape)
            throws Exception {
        double total = 0;
        for (long seed : SEEDS) {
            SimulationReport report = run(shape, nodes, hopLimit, seed,
                    shape == Topology.Shape.RANDOM ? CONTRAST_MESSAGES : 120);
            total += report.deliveryRate();
        }
        return total / SEEDS.length;
    }

    /**
     * Message count for the random contrast.
     *
     * <p>Lower than elsewhere because a random graph reaches degree 13 where
     * the circulant band reaches 4, so the same message count floods an order
     * of magnitude more frames. Measured: 150 messages takes 131 s against 12 s
     * for the band.
     */
    private static final int CONTRAST_MESSAGES = 50;

    private static SimulationReport run(Topology.Shape shape, int nodes, int hopLimit,
                                        long seed) throws Exception {
        return run(shape, nodes, hopLimit, seed, 120);
    }

    private static SimulationReport run(Topology.Shape shape, int nodes, int hopLimit,
                                        long seed, int messages) throws Exception {
        // Churn off, so the comparison is against the graph and not against a
        // link-cutting model. Enough messages that one acknowledgement is a
        // fraction of a point rather than three points.
        SimulationConfig config = new SimulationConfig(
                Topology.of(shape, nodes, 3, seed), messages, 0.0, 1_760_000_000L, 250L, 40,
                seed, 0.0, 0, hopLimit, 900L, false);
        try (MeshSimulation simulation = new MeshSimulation(config)) {
            return simulation.run();
        }
    }

    /**
     * Share of ordered node pairs whose shortest path fits inside the budget.
     *
     * <p>The last hop is reserved for arriving. A message that reaches a node
     * with its budget exhausted is rejected there: {@code MeshRouter.receive}
     * drops it with HOP_LIMIT_EXCEEDED even when that node is the destination.
     * So a budget of H reaches a maximum of H-1 hops, and a ceiling computed
     * against H is wrong by one hop at the margin, which is exactly where the
     * interesting measurements live.
     */
    private static double fractionWithinBudget(Topology.Shape shape, int nodes, int hopLimit) {
        return fractionWithinDistance(shape, nodes, hopLimit - 1);
    }

    private static double fractionWithinDistance(Topology.Shape shape, int nodes,
                                                 int maxDistance) {
        Topology topology = Topology.of(shape, nodes, 3, SEEDS[0]);
        int reachable = 0;
        int within = 0;
        int[] distance = new int[nodes];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int source = 0; source < nodes; source++) {
            Arrays.fill(distance, -1);
            distance[source] = 0;
            queue.add(source);
            while (!queue.isEmpty()) {
                int current = queue.poll();
                for (int next : topology.neighbours(current)) {
                    if (distance[next] < 0) {
                        distance[next] = distance[current] + 1;
                        queue.add(next);
                    }
                }
            }
            for (int target = 0; target < nodes; target++) {
                if (target == source || distance[target] < 0) {
                    continue;
                }
                reachable++;
                if (distance[target] <= maxDistance) {
                    within++;
                }
            }
            queue.clear();
        }
        return reachable == 0 ? 0 : (double) within / reachable;
    }

    private static int diameter(Topology.Shape shape, int nodes) {
        Topology topology = Topology.of(shape, nodes, 3, SEEDS[0]);
        int longest = 0;
        int[] distance = new int[nodes];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int source = 0; source < nodes; source++) {
            Arrays.fill(distance, -1);
            distance[source] = 0;
            queue.add(source);
            while (!queue.isEmpty()) {
                int current = queue.poll();
                for (int next : topology.neighbours(current)) {
                    if (distance[next] < 0) {
                        distance[next] = distance[current] + 1;
                        queue.add(next);
                    }
                }
            }
            for (int value : distance) {
                longest = Math.max(longest, value);
            }
            queue.clear();
        }
        return longest;
    }
}