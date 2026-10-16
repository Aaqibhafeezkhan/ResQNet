package dev.resqnet.mesh.simulator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Network shapes the simulator can build.
 *
 * <p>Each topology exists because it stresses a different property of the
 * routing engine:
 *
 * <ul>
 *   <li>{@link #LINE} — the only topology where hop budget is the only thing
 *       standing between a message and an unbounded walk. Tests hop limits.</li>
 *   <li>{@link #RING} — two independent paths between most pairs. Tests that
 *       duplicates are suppressed rather than amplified.</li>
 *   <li>{@link #STAR} — a single relay is the only route. Tests relay load and
 *       what happens when the hub leaves.</li>
 *   <li>{@link #MESH} — a circulant band: every node joined to its +/-1 and
 *       +/-2 neighbours. <b>Its diameter grows linearly with node count</b>
 *       (n/4), so it is a sparse chain-like graph, not a well-connected mesh,
 *       and delivery falls with size for that reason alone. Tests amplification
 *       bounds and, just as usefully, what a hop budget cannot buy.</li>
 *   <li>{@link #TWO_CLUSTER} — a partition. Tests that delivery fails cleanly
 *       rather than reporting a false success.</li>
 *   <li>{@link #RANDOM} — seeded, so an interesting shape can be captured by
 *       its seed alone and replayed.</li>
 * </ul>
 *
 * <p>Every topology is undirected: a radio link is bidirectional in practice.
 */
public final class Topology {

    /** Available shapes. */
    public enum Shape {
        LINE, RING, STAR, MESH, TWO_CLUSTER, RANDOM
    }

    private final Shape shape;
    private final int nodeCount;
    private final int degree;
    private final long seed;
    private final Map<Integer, Set<Integer>> adjacency;

    private Topology(Shape shape, int nodeCount, int degree, long seed,
                     Map<Integer, Set<Integer>> adjacency) {
        this.shape = shape;
        this.nodeCount = nodeCount;
        this.degree = degree;
        this.seed = seed;
        this.adjacency = adjacency;
    }

    public Shape shape() {
        return shape;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int degree() {
        return degree;
    }

    public long seed() {
        return seed;
    }

    /** Neighbouring node indices for a node, as an unmodifiable set. */
    public Set<Integer> neighbours(int node) {
        return adjacency.getOrDefault(node, Set.of());
    }

    /** Number of undirected links. */
    public int linkCount() {
        int total = 0;
        for (Set<Integer> peers : adjacency.values()) {
            total += peers.size();
        }
        return total / 2;
    }

    /** All edges as ordered pairs with {@code from < to}. */
    public List<int[]> edges() {
        List<int[]> edges = new ArrayList<>();
        adjacency.forEach((node, peers) -> peers.forEach(peer -> {
            if (node < peer) {
                edges.add(new int[] { node, peer });
            }
        }));
        edges.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0])
                : Integer.compare(a[1], b[1]));
        return edges;
    }

    public Map<Integer, Set<Integer>> adjacency() {
        return Map.copyOf(adjacency);
    }

    /** Node indices sorted by degree, highest first. Used to pick the busiest relay. */
    public List<Integer> nodesByDegreeDescending() {
        List<Integer> nodes = new ArrayList<>(adjacency.keySet());
        nodes.sort((a, b) -> {
            int byDegree = Integer.compare(adjacency.get(b).size(), adjacency.get(a).size());
            return byDegree != 0 ? byDegree : Integer.compare(a, b);
        });
        return nodes;
    }

    /** Nodes reachable from {@code start}, including itself. */
    public Set<Integer> componentOf(int start) {
        Set<Integer> seen = new LinkedHashSet<>();
        List<Integer> frontier = new ArrayList<>();
        frontier.add(start);
        seen.add(start);
        while (!frontier.isEmpty()) {
            int current = frontier.remove(frontier.size() - 1);
            for (int peer : adjacency.getOrDefault(current, Set.of())) {
                if (seen.add(peer)) {
                    frontier.add(peer);
                }
            }
        }
        return seen;
    }

    // ------------------------------------------------------------- factories

    /** Builds the requested shape. */
    public static Topology of(Shape shape, int nodeCount, int degree, long seed) {
        if (nodeCount < 2) {
            throw new IllegalArgumentException("A mesh needs at least two nodes");
        }
        Map<Integer, Set<Integer>> adjacency = new LinkedHashMap<>();
        for (int i = 0; i < nodeCount; i++) {
            adjacency.put(i, new LinkedHashSet<>());
        }

        switch (shape) {
            case LINE -> {
                for (int i = 0; i < nodeCount - 1; i++) {
                    connect(adjacency, i, i + 1);
                }
            }
            case RING -> {
                for (int i = 0; i < nodeCount - 1; i++) {
                    connect(adjacency, i, i + 1);
                }
                connect(adjacency, nodeCount - 1, 0);
            }
            case STAR -> {
                for (int i = 1; i < nodeCount; i++) {
                    connect(adjacency, 0, i);
                }
            }
            case MESH -> {
                // A circulant graph on the node index: each node is joined to
                // the nodes one, two and optionally three positions away on
                // either side. Redundancy is local, so the diameter is about
                // nodeCount / 4 and grows without bound.
                //
                // This is NOT a well-mixed mesh despite the name, and the
                // distinction matters: at 500 nodes its diameter is 125, so
                // only a quarter of node pairs are within a 32-hop budget. A
                // run that reports falling delivery at that size is reporting
                // the graph, not the router. Use RANDOM for a small-diameter
                // graph of the same size, or read the structural ceiling from
                // TopologyDiagnostics before comparing delivery rates.
                for (int i = 0; i < nodeCount; i++) {
                    connect(adjacency, i, (i + 1) % nodeCount);
                    connect(adjacency, i, (i + 2) % nodeCount);
                    if (degree >= 4) {
                        connect(adjacency, i, (i + 3) % nodeCount);
                    }
                }
            }
            case TWO_CLUSTER -> {
                int split = Math.max(1, nodeCount / 2);
                for (int i = 0; i < split - 1; i++) {
                    connect(adjacency, i, i + 1);
                }
                for (int i = split; i < nodeCount - 1; i++) {
                    connect(adjacency, i, i + 1);
                }
            }
            case RANDOM -> buildRandom(adjacency, nodeCount, Math.max(1, degree), seed);
            default -> throw new IllegalArgumentException("Unhandled shape " + shape);
        }

        return new Topology(shape, nodeCount, degree, seed, adjacency);
    }

    private static void buildRandom(Map<Integer, Set<Integer>> adjacency, int nodeCount,
                                    int degree, long seed) {
        // Seeded Erdős–Rényi-style growth: each node in turn picks its neighbours
        // from those already placed, which guarantees connectivity while still
        // producing irregular, interesting shapes.
        Random random = new Random(seed);
        for (int i = 1; i < nodeCount; i++) {
            int wanted = Math.min(degree, i);
            List<Integer> candidates = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                candidates.add(j);
            }
            java.util.Collections.shuffle(candidates, random);
            for (int k = 0; k < wanted; k++) {
                connect(adjacency, i, candidates.get(k));
            }
        }
    }

    private static void connect(Map<Integer, Set<Integer>> adjacency, int a, int b) {
        adjacency.get(a).add(b);
        adjacency.get(b).add(a);
    }

    @Override
    public String toString() {
        return "Topology{" + shape + ", nodes=" + nodeCount + ", links=" + linkCount() + "}";
    }
}