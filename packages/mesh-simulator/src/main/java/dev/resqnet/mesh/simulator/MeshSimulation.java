package dev.resqnet.mesh.simulator;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.RoutingMetrics;
import dev.resqnet.mesh.triage.TriageClassifier;

/**
 * Deterministic discrete-event simulator for the ResQNet mesh.
 *
 * <p>What this class is: a harness that instantiates real
 * {@link dev.resqnet.mesh.routing.MeshRouter} instances, wires them together with
 * a synthetic radio, and drives them through a reproducible scenario.
 *
 * <p>What this class is not: a model that approximates the router. It contains no
 * routing logic of its own. Every accept, forward, duplicate suppression and
 * acknowledgement reported in the result was produced by the same class that runs
 * on a handset. That distinction is the entire reason this module exists: it turns
 * "we think multi-hop works" into a claim that can be re-checked from a seed.
 *
 * <p>Determinism comes from four places: a manual clock, a seeded RNG, FIFO frame
 * delivery, and a message schedule derived only from the seed. Two runs with the
 * same {@link SimulationConfig} produce byte-identical event streams.
 *
 * <pre>{@code
 * SimulationConfig config = SimulationConfig.defaults(
 *         Topology.of(Topology.Shape.RING, 8, 3, 42L), 42L);
 * SimulationReport report = new MeshSimulation(config).run();
 * System.out.println(report.toSummary());
 * }</pre>
 */
public final class MeshSimulation implements AutoCloseable {

    private final SimulationConfig config;
    private final TriageClassifier classifier;
    private final SimulationNetwork network;
    private final List<SimulatedNode> nodes = new ArrayList<>();
    private final Random schedule;
    private List<SimulationEvent> collectedEvents = new ArrayList<>();
    private final List<SimulationReport.MessageReport> originatedMessages = new ArrayList<>();

    /** Upper bound on events retained in a report. */
    public static final int MAX_RECORDED_EVENTS = 4_000;

    private boolean closed;
    private long totalEventCount;

    /** Uses the shipped offline triage classifier, which is what a handset runs. */
    public MeshSimulation(SimulationConfig config) {
        this(config, SimulatedNode.defaultClassifier());
    }

    /**
     * @param classifier triage model to attach; pass
     *     {@code TriageClassifier.disabled()} to isolate routing from advisory
     *     classification
     */
    public MeshSimulation(SimulationConfig config, TriageClassifier classifier) {
        this.config = config;
        this.classifier = classifier;
        this.network = new SimulationNetwork(config);
        this.schedule = new Random(config.seed() * 7919L);
    }

    /**
     * Executes the scenario and returns what was measured.
     *
     * @throws IllegalStateException if the simulation was already run or closed
     * @throws GeneralSecurityException if key generation fails
     */
    public SimulationReport run()
            throws GeneralSecurityException, dev.resqnet.mesh.protocol.ProtocolException {
        if (closed) {
            throw new IllegalStateException("Simulation already closed");
        }
        long cpuStart = System.nanoTime();

        buildNodes();
        network.bringOnline();
        applyInitialPartition();

        int messagesIssued = 0;
        int messagesPerRound = config.messageCount() == 0
                ? 0
                : Math.max(1, (int) Math.ceil((double) config.messageCount()
                        / (double) config.rounds()));

        for (int round = 0; round < config.rounds(); round++) {
            network.startRound(round);
            applyChurn(round);

            for (int i = 0; i < messagesPerRound && messagesIssued < config.messageCount(); i++) {
                originateMessage(messagesIssued++);
                network.advance(config.stepMillis() / 2);
            }

            network.advance(config.stepMillis());
            network.drain();
        }

        // Final settling pass so messages that are still retrying are given a
        // chance to complete before the report is taken.
        for (int i = 0; i < 8; i++) {
            network.advance(config.stepMillis());
            network.drain();
        }

        long cpuMillis = (System.nanoTime() - cpuStart) / 1_000_000L;
        return buildReport(cpuMillis);
    }

    // ------------------------------------------------------------------ setup

    private void buildNodes() throws GeneralSecurityException {
        int count = config.topology().nodeCount();
        for (int i = 0; i < count; i++) {
            SimulatedNode node = new SimulatedNode(i, config, network.clock(), network, classifier);
            nodes.add(node);
            network.register(node);
        }
        for (SimulatedNode a : nodes) {
            for (SimulatedNode b : nodes) {
                if (a != b) {
                    a.crossTrust(b);
                }
            }
        }
    }

    /**
     * Starts the run with the busiest relay offline.
     *
     * <p>Exercises the case that matters most in a real disaster: the person most
     * likely to be forwarding messages for everyone else is the first to lose
     * battery or walk away.
     */
    private void applyInitialPartition() {
        if (!config.startPartitioned() || nodes.size() < 3) {
            return;
        }
        List<Integer> byDegree = config.topology().nodesByDegreeDescending();
        SimulatedNode hub = nodes.get(byDegree.get(0));
        int isolated = byDegree.size() > 1 ? byDegree.get(1) : 1;
        for (SimulatedNode other : nodes) {
            if (other != hub && other.index() != isolated) {
                network.setLink(hub.index(), other.index(), false);
            }
        }
    }

    /** Cuts and restores one link on the configured cadence. */
    private void applyChurn(int round) {
        int interval = config.churnEveryRounds();
        if (interval == 0 || round == 0 || round % interval != 0) {
            return;
        }
        List<int[]> edges = config.topology().edges();
        if (edges.isEmpty()) {
            return;
        }
        int[] edge = edges.get(schedule.nextInt(edges.size()));
        boolean currentlyUp = network.isLinked(edge[0], edge[1]);
        network.setLink(edge[0], edge[1], !currentlyUp);
    }

    // ------------------------------------------------------------- workload

    private void originateMessage(int index)
            throws GeneralSecurityException, dev.resqnet.mesh.protocol.ProtocolException {
        if (nodes.isEmpty() || config.messageCount() == 0) {
            return;
        }
        SimulatedNode source = nodes.get(schedule.nextInt(nodes.size()));

        boolean broadcast = schedule.nextDouble() < config.broadcastRatio();
        String destination = broadcast
                ? null
                : nodes.get(schedule.nextInt(nodes.size())).deviceId();
        if (!broadcast && destination.equals(source.deviceId())) {
            destination = nodes.get((source.index() + 1) % nodes.size()).deviceId();
        }

        String body = SAMPLE_MESSAGES[index % SAMPLE_MESSAGES.length];
        dev.resqnet.mesh.protocol.MessageEnvelope envelope = source.router().originate(destination,
                MessageType.SOS, Priority.SOS,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0L);

        // Recorded so a report can say which node composed a message and which
        // node it was addressed to. Without this, a message id in the event
        // stream is a label with no journey attached to it.
        originatedMessages.add(new SimulationReport.MessageReport(envelope.messageId(),
                source.index(), broadcast ? -1 : network.indexOf(destination), broadcast,
                envelope.createdAtEpochMillis()));
    }

    /**
     * Representative emergency texts.
     *
     * <p>Real message bodies, not {@code "test 1"}. Payload length affects radio
     * cost, and the text length of an actual SOS is part of what makes a link
     * expensive.
     */
    private static final String[] SAMPLE_MESSAGES = {
            "We are trapped on the second floor and the stairwell is blocked.",
            "Water is rising fast in the basement, we need help getting out.",
            "There is heavy smoke coming from the third floor of our building.",
            "My father collapsed and is not breathing, please send someone.",
            "We have no food or clean water since yesterday morning.",
            "A section of the bridge ahead of us has collapsed into the river.",
            "Cannot find my daughter since the storm, she was on her way home.",
            "We are sheltering, everyone accounted for, no injuries.",
    };

    // --------------------------------------------------------------- results

    private SimulationReport buildReport(long cpuMillis) {
        Map<Integer, Long> hopDistribution = new LinkedHashMap<>();
        List<SimulationReport.NodeReport> nodeReports = new ArrayList<>();

        RoutingMetrics aggregate = new RoutingMetrics();
        long[] allLatencies = new long[0];

        for (SimulatedNode node : nodes) {
            RoutingMetrics metrics = node.router().metrics();
            aggregate.accumulate(metrics);
            mergeCounts(hopDistribution, metrics.hopDistribution());
            nodeReports.add(SimulationReport.NodeReport.of(node,
                    network.neighboursOf(node.index()).size()));
            allLatencies = concat(allLatencies, metrics.deliveryLatencySamples());
            collectedEvents.addAll(node.events());
            totalEventCount += node.events().size();
        }

        // A long run under heavy churn produces hundreds of thousands of
        // routing events. Keeping them all would produce a report nobody can
        // diff, so the tail is retained: it is the part that explains how the
        // run ended.
        if (collectedEvents.size() > MAX_RECORDED_EVENTS) {
            collectedEvents = new ArrayList<>(collectedEvents.subList(
                    collectedEvents.size() - MAX_RECORDED_EVENTS, collectedEvents.size()));
        }

        int partitions = countPartitions();
        long duration = config.rounds() * config.stepMillis();

        List<SimulationReport.LinkChangeReport> linkChangeRecords = new ArrayList<>();
        for (SimulationNetwork.LinkChange change : network.linkChanges()) {
            linkChangeRecords.add(new SimulationReport.LinkChangeReport(change.atEpochMillis(),
                    change.round(), change.nodeA(), change.nodeB(), change.up()));
        }

        return new SimulationReport(config, nodeReports, collectedEvents,
                network.framesSent(), network.framesDelivered(), network.framesDropped(),
                network.linkChanges().size(), partitions, duration, cpuMillis, allLatencies,
                aggregate, hopDistribution, Map.of(), originatedMessages, linkChangeRecords,
                totalEventCount);
    }

    private static Map<Integer, Long> mergeCounts(Map<Integer, Long> target,
                                                   Map<Integer, Long> addition) {
        addition.forEach((key, value) -> target.merge(key, value, Long::sum));
        return target;
    }

    private static long[] concat(long[] a, long[] b) {
        long[] out = new long[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * Counts connected components in the *final* topology.
     *
     * <p>One partition means the run ended fully connected. More than one means
     * the network is still fractured, which is worth surfacing rather than
     * hiding: it usually means messages were legitimately undeliverable.
     */
    private int countPartitions() {
        java.util.Set<Integer> remaining = new java.util.LinkedHashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            remaining.add(i);
        }
        int components = 0;
        while (!remaining.isEmpty()) {
            components++;
            int start = remaining.iterator().next();
            java.util.Deque<Integer> frontier = new java.util.ArrayDeque<>();
            frontier.add(start);
            remaining.remove(start);
            while (!frontier.isEmpty()) {
                int current = frontier.poll();
                for (int peer : network.neighboursOf(current)) {
                    if (remaining.remove(peer)) {
                        frontier.add(peer);
                    }
                }
            }
        }
        return components;
    }

    // ------------------------------------------------------------- accessors

    /** Direct access to a node, for tests and for interactive exploration. */
    public SimulatedNode node(int index) {
        return nodes.get(index);
    }

    public List<SimulatedNode> nodes() {
        return List.copyOf(nodes);
    }

    public SimulationNetwork network() {
        return network;
    }

    public SimulationConfig config() {
        return config;
    }

    /** Events emitted so far, in emission order. */
    public List<SimulationEvent> events() {
        return List.copyOf(collectedEvents);
    }

    /** Total events observed, including any dropped from the retained window. */
    public long totalEventCount() {
        return totalEventCount;
    }

    /** Convenience: run and return only the summary text. */
    public String runForSummary()
            throws GeneralSecurityException, dev.resqnet.mesh.protocol.ProtocolException {
        return run().toSummary();
    }

    @Override
    public void close() {
        closed = true;
        nodes.forEach(SimulatedNode::close);
        nodes.clear();
    }
}