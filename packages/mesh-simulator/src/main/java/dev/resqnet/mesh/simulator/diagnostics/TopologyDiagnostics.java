package dev.resqnet.mesh.simulator.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.resqnet.mesh.simulator.Topology;

/**
 * Measures the graphs the simulator actually builds.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Reported delivery rates fall sharply with node count, and the usual first
 * guess is that the routing engine is at fault. Before changing any routing
 * code it is worth checking the thing the routing engine is being asked to
 * route across: a delivery rate is a property of the <em>pair</em>, and a graph
 * whose diameter exceeds the hop budget cannot be crossed by any router.
 *
 * <p>So this reports, for a given shape and size: the degree distribution, the
 * graph diameter, the number of connected components, and the fraction of
 * ordered node pairs whose shortest path fits inside a given hop budget. That
 * last number is the one that matters, because it is a hard upper bound on the
 * delivery rate that no amount of routing quality can beat.
 *
 * <h2>Interpreting the output</h2>
 *
 * <p>A diameter above the hop budget is not a defect in the routing engine. It
 * is the definition of the graph being too sparse for the budget, and the
 * honest response is to say so rather than to make the router burn more hops
 * hoping to compensate.
 */
public final class TopologyDiagnostics {

    private TopologyDiagnostics() {
    }

    public static void main(String[] args) {
        int[] sizes = {10, 50, 100, 500};
        long seed = 42;
        int[] budgets = {4, 8, 16, 32};

        for (String arg : args) {
            if (arg.startsWith("--seed=")) {
                seed = Long.parseLong(arg.substring(7));
            }
        }

        System.out.printf("%-13s %6s %8s %8s %10s %8s", "topology", "nodes", "minDeg",
                "maxDeg", "diameter", "parts");
        for (int budget : budgets) {
            System.out.printf(" %11s", "within" + budget);
        }
        System.out.println();
        System.out.println("-".repeat(13 + 6 + 8 + 8 + 10 + 8 + budgets.length * 12));

        for (Topology.Shape shape : Topology.Shape.values()) {
            for (int nodes : sizes) {
                Topology topology = Topology.of(shape, nodes, 3, seed);
                Stats stats = analyse(topology, budgets);
                System.out.printf("%-13s %6d %8d %8d %10d %8d",
                        shape.name().toLowerCase(java.util.Locale.ROOT), nodes,
                        stats.minDegree, stats.maxDegree, stats.diameter,
                        stats.components);
                for (double fraction : stats.withinBudget) {
                    System.out.printf(" %10.1f%%", fraction * 100);
                }
                System.out.println();
            }
        }
    }

    private static Stats analyse(Topology topology, int[] budgets) {
        int n = topology.nodeCount();
        Stats stats = new Stats(budgets.length);
        if (n == 0) {
            return stats;
        }

        // Degree distribution.
        for (int node = 0; node < n; node++) {
            int degree = topology.neighbours(node).size();
            if (node == 0 || degree < stats.minDegree) {
                stats.minDegree = degree;
            }
            if (degree > stats.maxDegree) {
                stats.maxDegree = degree;
            }
        }

        // All-pairs breadth-first search. O(V*E), which is fine up to the few
        // hundred nodes this is used at and is exact rather than sampled.
        Map<Integer, Integer> histogram = new HashMap<>();
        int reached = 0;
        int considered = 0;
        Deque<Integer> queue = new ArrayDeque<>();
        for (int source = 0; source < n; source++) {
            int[] distance = new int[n];
            java.util.Arrays.fill(distance, -1);
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
            for (int target = 0; target < n; target++) {
                if (target == source) {
                    continue;
                }
                considered++;
                int d = distance[target];
                if (d < 0) {
                    // Unreachable: a different component. Counted separately by
                    // the component check below and excluded from the histogram
                    // so it cannot masquerade as a very large diameter.
                    continue;
                }
                reached++;
                histogram.merge(d, 1, Integer::sum);
                if (d > stats.diameter) {
                    stats.diameter = d;
                }
            }
            queue.clear();
        }

        for (int i = 0; i < budgets.length; i++) {
            int within = 0;
            for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
                if (entry.getKey() <= budgets[i]) {
                    within += entry.getValue();
                }
            }
            stats.withinBudget[i] = considered == 0 ? 0 : (double) within / reached;
        }
        stats.reachableFraction = considered == 0 ? 0 : (double) reached / considered;
        stats.components = countComponents(topology);
        return stats;
    }

    private static int countComponents(Topology topology) {
        int n = topology.nodeCount();
        boolean[] seen = new boolean[n];
        int components = 0;
        Deque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < n; start++) {
            if (seen[start]) {
                continue;
            }
            components++;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int current = queue.poll();
                for (int next : topology.neighbours(current)) {
                    if (!seen[next]) {
                        seen[next] = true;
                        queue.add(next);
                    }
                }
            }
        }
        return components;
    }

    /** Per-graph measurements. */
    private static final class Stats {
        int minDegree;
        int maxDegree;
        int diameter;
        int components;
        double reachableFraction;
        final double[] withinBudget;

        Stats(int budgets) {
            withinBudget = new double[budgets];
            minDegree = Integer.MAX_VALUE;
        }
    }
}