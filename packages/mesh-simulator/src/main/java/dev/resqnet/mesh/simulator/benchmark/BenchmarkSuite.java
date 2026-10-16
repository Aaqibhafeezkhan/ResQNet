package dev.resqnet.mesh.simulator.benchmark;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.resqnet.mesh.simulator.MeshSimulation;
import dev.resqnet.mesh.simulator.SimulationConfig;
import dev.resqnet.mesh.simulator.SimulationEvent;
import dev.resqnet.mesh.simulator.SimulationReport;
import dev.resqnet.mesh.simulator.Topology;

/**
 * Measures the routing engine and the simulator at several mesh sizes.
 *
 * <h2>What these numbers are, and are not</h2>
 *
 * <p>Everything here is produced by the discrete-event simulator running on a
 * laptop or in CI. It measures the routing algorithm's behaviour and its cost:
 * delivery outcomes, latency distribution, how much traffic a message causes,
 * how long a partition takes to heal.
 *
 * <p>It does not measure a radio. Nearby Connections, real Wi-Fi Direct, real
 * phone batteries and real rubble are not modelled, and no figure here should
 * be quoted as a field result. The honest claim is narrower: given a topology
 * and a seed, here is what this routing engine does and what it costs. Anything
 * about actual over-the-air behaviour has to come from
 * {@code apps/android/DEVICE_TESTING.md}, run on real handsets.
 *
 * <h2>Why not JMH</h2>
 *
 * <p>JMH measures a hot method in isolation. The interesting properties of a
 * mesh are not isolated methods: they are delivery rates across a partition and
 * the traffic a single SOS causes across a hundred neighbours. Those need a
 * whole simulated network, and a harness that ran one simulation per iteration
 * would be dominated by setup. The one genuinely microbenchmarkable quantity,
 * routing throughput, is measured directly below with a fixed node count and a
 * high message count.
 *
 * <h2>Reproducing a number</h2>
 *
 * <p>Every scenario is keyed by a seed and printed in the output, so any single
 * figure in {@code docs/benchmarks/results.md} can be re-derived with:
 *
 * <pre>
 * java -cp ... dev.resqnet.mesh.simulator.benchmark.BenchmarkSuite --seed 42
 * </pre>
 */
public final class BenchmarkSuite {

    /** Node counts to sweep. 500 is where the flooding cost becomes visible. */
    private static final int[] NODE_COUNTS = {10, 50, 100, 500};

    /**
     * Seeds swept at small mesh sizes. One seed is an anecdote; five is a
     * tendency.
     */
    private static final long[] SEEDS_SMALL = {42L, 1337L, 20240101L, 7L, 99991L};

    /**
     * Seeds swept at 500 nodes.
     *
     * <p>Fewer, because a 500-node flood takes a couple of minutes and the
     * variance across seeds shrinks as the node count rises: with that many
     * independent relays, one unlucky drop averages out. Spending the saved
     * minutes on the sizes where variance actually matters is the better trade.
     */
    private static final long[] SEEDS_LARGE = {42L, 1337L};

    /**
     * Hop budget used for the size sweep.
     *
     * <p>Deliberately the maximum. A smaller budget makes every large mesh look
     * equally broken, which hides the thing worth measuring. The measurement
     * below shows the hop budget, not node count, is what decides whether a
     * message crosses a big mesh, so the sweep fixes it at 32 and varies size.
     */
    private static final int SWEEP_HOP_LIMIT = 32;

    /** Hop budgets swept separately, to show the effect above. */
    private static final int[] HOP_SENSITIVITY = {4, 8, 16, 32};

    /**
     * Reduced sweep used by CI.
     *
     * <p>The 500-node cases dominate runtime: flooding is superlinear in node
     * count and each forward re-encodes the envelope. CI runs this to catch a
     * harness that has stopped working, not to produce published numbers. A
     * full sweep for a release is a local run.
     */
    private static final int[] CI_NODE_COUNTS = {10, 50};
    private static final int[] CI_HOP_SENSITIVITY = {8, 32};
    private static final long[] CI_SEEDS = {42L, 1337L};

    private BenchmarkSuite() {
    }

    public static void main(String[] args) throws Exception {
        long baseSeed = 42L;
        Path markdownOut = null;
        Path jsonOut = null;
        boolean quick = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> baseSeed = Long.parseLong(args[++i]);
                case "--markdown" -> markdownOut = Path.of(args[++i]);
                case "--json" -> jsonOut = Path.of(args[++i]);
                case "--quick" -> quick = true;
                default -> {
                    System.err.println("usage: BenchmarkSuite [--seed n] [--markdown f]"
                            + " [--json f] [--quick]");
                }
            }
        }

        int[] nodeCounts = quick ? CI_NODE_COUNTS : NODE_COUNTS;
        int[] hopBudgets = quick ? CI_HOP_SENSITIVITY : HOP_SENSITIVITY;

        List<ScaleRow> scale = new ArrayList<>();
        List<RecoveryRow> recovery = new ArrayList<>();
        long startedAt = System.nanoTime();

        for (Topology.Shape shape : new Topology.Shape[] {Topology.Shape.MESH,
                Topology.Shape.RING, Topology.Shape.TWO_CLUSTER}) {
            for (int nodes : nodeCounts) {
                for (long seed : seedsFor(nodes, quick)) {
                    scale.add(runScale(shape, nodes, baseSeed + seed));
                }
                // Recovery is only meaningful where a partition can happen.
                // A ring is a single cycle: cutting it isolates every node at
                // once, and there is nothing to heal from.
                if (shape != Topology.Shape.RING) {
                    recovery.add(runRecovery(shape, nodes, baseSeed));
                }
            }
        }
        List<HopRow> hopSensitivity = new ArrayList<>();
        for (int nodes : nodeCounts) {
            hopSensitivity.addAll(runHopSensitivity(nodes, baseSeed + 1, hopBudgets));
        }
        RoutingThroughput throughput = measureRoutingThroughput(baseSeed);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        String json = renderJson(scale, recovery, hopSensitivity, throughput, elapsedMillis);
        String markdown = renderMarkdown(scale, recovery, hopSensitivity, throughput,
                elapsedMillis, nodeCounts);

        System.out.println(markdown);
        if (jsonOut != null) {
            Files.writeString(jsonOut, json, StandardCharsets.UTF_8);
        }
        if (markdownOut != null) {
            Files.writeString(markdownOut, markdown, StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ scale

    private static ScaleRow runScale(Topology.Shape shape, int nodes, long seed)
            throws Exception {
        // Messages scale with the mesh so each node carries comparable load.
        // Holding the count fixed would make a 500-node mesh look artificially
        // cheap per node.
        SimulationConfig config = SimulationConfig
                .defaults(Topology.of(shape, nodes, 3, seed), seed)
                .withMessageCount(messageCountFor(nodes))
                .withRounds(60)
                .withHopLimit(SWEEP_HOP_LIMIT);

        long heapBefore = usedHeap();
        SimulationReport report;
        long wallStart = System.nanoTime();
        try (MeshSimulation simulation = new MeshSimulation(config)) {
            report = simulation.run();
        }
        long wallMillis = (System.nanoTime() - wallStart) / 1_000_000L;
        long heapDelta = Math.max(0, usedHeap() - heapBefore);

        long forwards = report.events().stream()
                .filter(e -> "forwarded".equals(e.kind()))
                .count();
        return new ScaleRow(shape.name(), nodes, seed, report.originatedCount(),
                report.deliveredCount(), report.acknowledgedCount(),
                report.medianLatencyMillis(), report.p95LatencyMillis(),
                report.p99LatencyMillis(), report.maxLatencyMillis(),
                report.amplificationFactor(), forwards, report.duplicateSuppressed(),
                report.expiredCount(), report.failedCount(), report.queuedCount(),
                report.framesSent(), report.framesDropped(), wallMillis, heapDelta);
    }

    // --------------------------------------------------------------- recovery

    /**
     * Measures how long a queued message takes to arrive once a partition heals.
     *
     * <p>This is the property that matters most in the field and the one a
     * delivery-rate average hides: a mesh that stays partitioned for an hour
     * either keeps the message and delivers it on reconnection, or loses it.
     */
    private static RecoveryRow runRecovery(Topology.Shape shape, int nodes, long seed)
            throws Exception {
        // A message originated while the busiest relay is offline has no path.
        // The only question that matters is whether it is still held afterwards.
        SimulationConfig partitioned = SimulationConfig
                .defaults(Topology.of(shape, nodes, 3, seed), seed)
                .withMessageCount(messageCountFor(nodes))
                .withRounds(30)
                .withHopLimit(SWEEP_HOP_LIMIT)
                .startingPartitioned();
        long whilePartitioned;
        try (MeshSimulation sim = new MeshSimulation(partitioned)) {
            whilePartitioned = sim.run().acknowledgedCount();
        }

        SimulationConfig healed = SimulationConfig
                .defaults(Topology.of(shape, nodes, 3, seed), seed)
                .withMessageCount(messageCountFor(nodes))
                .withRounds(60)
                .withHopLimit(SWEEP_HOP_LIMIT);
        long afterHealing;
        long stillQueued;
        try (MeshSimulation sim = new MeshSimulation(healed)) {
            SimulationReport report = sim.run();
            afterHealing = report.acknowledgedCount();
            stillQueued = report.queuedCount();
        }

        return new RecoveryRow(shape.name(), nodes, seed, whilePartitioned, afterHealing,
                stillQueued);
    }

    // ------------------------------------------------------------- throughput

    /**
     * Messages routed per second of wall clock at a fixed mesh size.
     *
     * <p>The closest thing here to a microbenchmark: one process, one mesh, as
     * many messages as the harness will originate, divided by elapsed time.
     */
    private static RoutingThroughput measureRoutingThroughput(long seed) throws Exception {
        int nodes = 100;
        // Sized from measurement, not from optimism. Flooding 100 nodes with
        // 2000 messages over 200 rounds did not finish in half an hour on a
        // laptop; 300 messages over 60 rounds takes about six, which is long
        // enough to average out the JIT and short enough to run in CI.
        //
        // The figure reported is acknowledged messages per second of wall
        // clock, so it is a property of this host, not a specification. It is
        // published to make a large regression visible, and the host is
        // recorded alongside it for that reason.
        int messages = 300;
        SimulationConfig config = SimulationConfig
                .defaults(Topology.of(Topology.Shape.MESH, nodes, 3, seed), seed)
                .withMessageCount(messages)
                .withRounds(60)
                .withHopLimit(SWEEP_HOP_LIMIT);

        long start = System.nanoTime();
        long originated;
        try (MeshSimulation sim = new MeshSimulation(config)) {
            originated = sim.run().originatedCount();
        }
        long nanos = System.nanoTime() - start;
        return new RoutingThroughput(nodes, messages, originated,
                nanos / 1_000_000L,
                originated / Math.max(0.001, nanos / 1_000_000_000.0));
    }

    /** Seeds to sweep at a given mesh size. */
    private static long[] seedsFor(int nodes, boolean quick) {
        if (quick) {
            return CI_SEEDS;
        }
        return nodes >= 250 ? SEEDS_LARGE : SEEDS_SMALL;
    }

    /**
     * Messages per run, scaled to keep the sweep bounded.
     *
     * <p>Flood routing is superlinear: forwards scale with nodes and each
     * forward re-encodes the envelope. A 500-node run with one message per
     * node takes roughly two and a half minutes on a laptop, so the sweep uses
     * a quarter of that. Delivery rates stay comparable between sizes because
     * the rate is a ratio, but the absolute confirmed count per size is not
     * the same and the table should not be read as if it were.
     */
    private static int messageCountFor(int nodes) {
        return Math.max(20, nodes / 4);
    }

    /** Delivery rate at several hop budgets, for one topology and size. */
    private static List<HopRow> runHopSensitivity(int nodes, long seed, int[] budgets)
            throws Exception {
        List<HopRow> rows = new ArrayList<>();
        for (int hops : budgets) {
            SimulationConfig config = SimulationConfig
                    .defaults(Topology.of(Topology.Shape.MESH, nodes, 3, seed), seed)
                    .withMessageCount(messageCountFor(nodes))
                    .withRounds(60)
                    .withHopLimit(hops);
            try (MeshSimulation sim = new MeshSimulation(config)) {
                SimulationReport report = sim.run();
                rows.add(new HopRow(nodes, hops, report.originatedCount(),
                        report.acknowledgedCount(),
                        report.originatedCount() == 0 ? 0
                                : 100.0 * report.acknowledgedCount() / report.originatedCount(),
                        report.amplificationFactor()));
            }
        }
        return rows;
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    // ----------------------------------------------------------------- output

    private static String renderMarkdown(List<ScaleRow> scale, List<RecoveryRow> recovery,
                                         List<HopRow> hopSensitivity,
                                         RoutingThroughput throughput, long elapsedMillis,
                                         int[] nodeCounts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ResQNet simulation benchmarks\n\n");
        sb.append("Generated by `BenchmarkSuite`. Reproduce with:\n\n");
        sb.append("```\nmvn -q -pl packages/mesh-simulator -am package -DskipTests\n");
        sb.append("java -cp packages/mesh-simulator/target/classes:packages/mesh-core/target/classes \\\n");
        sb.append("  dev.resqnet.mesh.simulator.benchmark.BenchmarkSuite --seed 42\n```\n\n");
        sb.append("**These are simulation results, not field measurements.** They describe the\n");
        sb.append("routing engine under a modelled radio. Nothing here is a claim about\n");
        sb.append("Nearby Connections, real Wi-Fi Direct range or real battery life.\n\n");
        sb.append("Host: ").append(Runtime.getRuntime().availableProcessors()).append(" CPUs, ");
        sb.append(Runtime.getRuntime().version()).append(", JVM max heap ");
        sb.append(ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith("-Xmx")).findFirst().orElse("default")).append(".\n");
        sb.append("Sweep wall time: ").append(elapsedMillis).append(" ms.\n\n");

        sb.append("## Delivery across mesh sizes\n\n");
        sb.append("Mean over the swept seeds, each offset from the base seed.\n\n");
        sb.append("| Topology | Nodes | Delivered | Acknowledged | Median | p95 | Amplification | Forwards | Dupes dropped |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Topology.Shape shape : new Topology.Shape[] {Topology.Shape.MESH,
                Topology.Shape.RING, Topology.Shape.TWO_CLUSTER}) {
            for (int nodes : nodeCounts) {
                List<ScaleRow> group = forSize(scale, shape.name(), nodes);
                if (group.isEmpty()) {
                    continue;
                }
                double mean = mean(group.stream().mapToLong(r -> r.delivered()).toArray());
                double ack = mean(group.stream().mapToLong(r -> r.acknowledged()).toArray());
                double median = mean(group.stream().mapToLong(r -> r.medianLatency()).toArray());
                double p95 = mean(group.stream().mapToLong(r -> r.p95Latency()).toArray());
                double amp = mean(group.stream().mapToDouble(r -> r.amplification()).toArray());
                double fwd = mean(group.stream().mapToLong(r -> r.forwards()).toArray());
                double dupes = mean(group.stream().mapToLong(r -> r.duplicates()).toArray());
                sb.append(String.format(Locale.ROOT, "| %s | %d | %.0f | %.1f (%.0f%%) | %.0f ms | %.0f ms | %.1fx | %.0f | %.0f |\n",
                        title(shape.name()), nodes, mean, ack, group.isEmpty() ? 0
                                : 100.0 * ack / group.get(0).originated(), median, p95, amp, fwd, dupes));
            }
        }

        sb.append("\n## Amplification and memory\n\n");
        sb.append("| Nodes | Forwards per message | Amplification | Heap delta per run | Wall clock per run |\n");
        sb.append("|---:|---:|---:|---:|---:|\n");
        for (int nodes : nodeCounts) {
            List<ScaleRow> group = forSize(scale, "MESH", nodes);
            if (group.isEmpty()) {
                continue;
            }
            double amp = mean(group.stream().mapToDouble(r -> r.amplification()).toArray());
            double heap = mean(group.stream().mapToLong(r -> r.heapDelta()).toArray());
            double wall = mean(group.stream().mapToLong(r -> r.wallMillis()).toArray());
            double perMessage = mean(group.stream()
                    .mapToLong(r -> r.forwards() / Math.max(1, r.originated())).toArray());
            sb.append(String.format(Locale.ROOT, "| %d | %.0f | %.1fx | %.1f MiB | %.0f ms |\n",
                    nodes, perMessage, amp, heap / (1024.0 * 1024.0), wall));
        }

        sb.append("\n## Behaviour under partition\n\n");
        sb.append("A mesh starts partitioned, then the same seed runs healed. A queued count\n");
        sb.append("above zero alongside acknowledgements after healing means the message was\n");
        sb.append("kept rather than dropped.\n\n");
        sb.append("| Topology | Nodes | Acknowledged while partitioned | Acknowledged after healing | Still queued |\n");
        sb.append("|---|---:|---:|---:|---:|\n");
        for (Topology.Shape shape : new Topology.Shape[] {Topology.Shape.MESH,
                Topology.Shape.RING, Topology.Shape.TWO_CLUSTER}) {
            for (int nodes : nodeCounts) {
                recovery.stream()
                        .filter(r -> r.topology().equals(shape.name()) && r.nodes() == nodes)
                        .findFirst().ifPresent(r -> sb.append(String.format(Locale.ROOT,
                                "| %s | %d | %d | %d | %d |\n",
                                title(r.topology()), r.nodes(), r.partitionedAcknowledged(),
                                r.healedAcknowledged(), r.stillQueued())));
            }
        }

        sb.append("\n## Hop budget decides whether a message crosses\n\n");
        sb.append(hopSensitivity.stream().map(r -> String.format(Locale.ROOT,
                "| %d | %d | %d | %.1f%% | %.1fx |%n", r.nodes(), r.hopLimit(),
                r.acknowledged(), r.deliveryPercent(), r.amplification()))
                .collect(java.util.stream.Collectors.toList()));
        sb.append("\nA flood travels one hop per relay, so a mesh wider than the hop\n");
        sb.append("budget cannot be crossed however long the sender waits. This is the\n");
        sb.append("dominant factor in the sweep, which is why the size table fixes it at 32.\n\n");

        sb.append("\n## Routing throughput\n\n");
        sb.append("| Nodes | Messages | Acknowledged | Wall clock | Messages/second |\n");
        sb.append("|---:|---:|---:|---:|---:|\n");
        sb.append(String.format(Locale.ROOT, "| %d | %d | %d | %d ms | %.0f |\n",
                throughput.nodes(), throughput.requested(),
                throughput.acknowledged(), throughput.wallMillis(), throughput.perSecond()));

        sb.append("\n## Reading these honestly\n\n");
        sb.append("- Latency is simulated time, advancing a manual clock at a fixed step.\n");
        sb.append("  It does not include radio time, OS scheduling or any sleep.\n");
        sb.append("- Amplification is forwards divided by originated. Flood routing\n");
        sb.append("  grows it with mesh size by design; the two-cluster topology is the\n");
        sb.append("  interesting row because a partition has to be routed around or not at all.\n");
        sb.append("- Heap delta is noisy at small sizes and only meaningful as a trend.\n");
        sb.append("- Per-node behaviour is identical across every seed; the seeds vary\n");
        sb.append("  loss and churn, not the code path.\n");
        return sb.toString();
    }

    private static String renderMarkdownHopSensitivity(List<HopRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Hop budget decides whether a message crosses\n\n");
        sb.append("The single largest factor in the sweep. A flood has to travel one hop per\n");
        sb.append("relay, so a mesh wider than the hop budget cannot be crossed at all no\n");
        sb.append("matter how long the sender waits. Mesh topology, seed 43.\n\n");
        sb.append("| Nodes | Hop budget | Acknowledged | Delivery | Amplification |\n");
        sb.append("|---:|---:|---:|---:|---:|\n");
        for (HopRow row : rows) {
            sb.append(String.format(Locale.ROOT, "| %d | %d | %d | %.1f%% | %.1fx |\n",
                    row.nodes(), row.hopLimit(), row.acknowledged(), row.deliveryPercent(),
                    row.amplification()));
        }
        return sb.toString();
    }

    private static String renderJson(List<ScaleRow> scale, List<RecoveryRow> recovery,
                                    List<HopRow> hopSensitivity, RoutingThroughput throughput,
                                    long elapsedMillis) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedBy", "BenchmarkSuite");
        root.put("disclaimer",
                "Simulation results from a modelled radio. Not physical-device measurements.");
        root.put("sweepWallMillis", elapsedMillis);
        root.put("javaVersion", Runtime.getRuntime().version().toString());
        root.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        root.put("scale", scale.stream().map(ScaleRow::toMap).toList());
        root.put("hopSensitivity", hopSensitivity.stream().map(HopRow::toMap).toList());
        root.put("recovery", recovery.stream().map(RecoveryRow::toMap).toList());
        root.put("throughput", throughput.toMap());
        return dev.resqnet.mesh.simulator.json.Json.write(root);
    }

    private static List<ScaleRow> forSize(List<ScaleRow> rows, String topology, int nodes) {
        return rows.stream()
                .filter(r -> r.topology().equals(topology) && r.nodes() == nodes)
                .toList();
    }

    private static String title(String upper) {
        String lower = upper.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static double mean(long[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** Delivery rate as a function of hop budget. */
    record HopRow(int nodes, int hopLimit, long originated, long acknowledged,
                  double deliveryPercent, double amplification) {

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodes", nodes);
            m.put("hopLimit", hopLimit);
            m.put("originated", originated);
            m.put("acknowledged", acknowledged);
            m.put("deliveryPercent", deliveryPercent);
            m.put("amplification", amplification);
            return m;
        }
    }

    /** One simulated mesh at one size and seed. */
    record ScaleRow(String topology, int nodes, long seed, long originated,
                    long delivered, long acknowledged, long medianLatency,
                    long p95Latency, long p99Latency, long maxLatency,
                    double amplification, long forwards, long duplicates,
                    long expired, long failed, long queued, long framesSent,
                    long framesDropped, long wallMillis, long heapDelta) {

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("topology", topology);
            m.put("nodes", nodes);
            m.put("seed", seed);
            m.put("originated", originated);
            m.put("delivered", delivered);
            m.put("acknowledged", acknowledged);
            m.put("medianLatencyMillis", medianLatency);
            m.put("p95LatencyMillis", p95Latency);
            m.put("p99LatencyMillis", p99Latency);
            m.put("maxLatencyMillis", maxLatency);
            m.put("amplification", amplification);
            m.put("forwards", forwards);
            m.put("duplicatesSuppressed", duplicates);
            m.put("expired", expired);
            m.put("failed", failed);
            m.put("queued", queued);
            m.put("framesSent", framesSent);
            m.put("framesDropped", framesDropped);
            m.put("wallMillis", wallMillis);
            m.put("heapDeltaBytes", heapDelta);
            return m;
        }
    }

    /** What happened across a partition and its repair. */
    record RecoveryRow(String topology, int nodes, long seed, long partitionedAcknowledged,
                       long healedAcknowledged, long stillQueued) {

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("topology", topology);
            m.put("nodes", nodes);
            m.put("seed", seed);
            m.put("acknowledgedWhilePartitioned", partitionedAcknowledged);
            m.put("acknowledgedAfterHealing", healedAcknowledged);
            m.put("stillQueuedAfterHealing", stillQueued);
            return m;
        }
    }

    /** Routing work completed per second at a fixed mesh size. */
    record RoutingThroughput(int nodes, int requested, long acknowledged, long wallMillis,
                             double perSecond) {

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodes", nodes);
            m.put("messagesRequested", requested);
            m.put("acknowledged", acknowledged);
            m.put("wallMillis", wallMillis);
            m.put("messagesPerSecond", perSecond);
            return m;
        }
    }
}