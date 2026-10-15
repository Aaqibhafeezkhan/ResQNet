package dev.resqnet.mesh.simulator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import dev.resqnet.mesh.routing.MeshClock;

/**
 * The synthetic radio plane shared by every node in a run.
 *
 * <p>Holds the active link set, the frame queue and the failure model. Frames are
 * delivered strictly in FIFO order from {@link #drain()}; there is no background
 * thread and no wall-clock dependency anywhere in this class. Given the same
 * seed and the same message schedule, a run is bit-for-bit repeatable.
 */
final class SimulationNetwork {

    private final SimulationConfig config;
    private final MeshClock.ManualClock clock;
    private final Random random;
    private final Map<Integer, SimulatedNode> nodes = new LinkedHashMap<>();
    private final Map<Long, Boolean> activeLinks = new LinkedHashMap<>();
    private final Deque<Frame> inFlight = new ArrayDeque<>();
    private final List<LinkChange> linkChanges = new ArrayList<>();

    private long framesSent;
    private long framesDelivered;
    private long framesDropped;
    private int round = -1;

    SimulationNetwork(SimulationConfig config) {
        this.config = config;
        this.clock = MeshClock.ManualClock.atEpochSecond(config.startEpochSeconds());
        this.random = new Random(config.seed());
    }

    /** Human-readable label for a device identity, or a truncated form. */
    String labelOf(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return "?";
        }
        for (SimulatedNode node : nodes.values()) {
            if (node.deviceId().equals(deviceId)) {
                return "n" + node.index();
            }
        }
        return deviceId.length() <= 10 ? deviceId : deviceId.substring(0, 10);
    }

    MeshClock.ManualClock clock() {
        return clock;
    }

    void register(SimulatedNode node) {
        nodes.put(node.index(), node);
    }

    Map<Integer, SimulatedNode> nodes() {
        return Map.copyOf(nodes);
    }

    void startRound(int roundIndex) {
        this.round = roundIndex;
        nodes.values().forEach(node -> node.startRound(roundIndex));
    }

    /** Brings every radio up and establishes all topology links. */
    void bringOnline() {
        nodes.values().forEach(node -> node.radio().start());
        for (int[] edge : config.topology().edges()) {
            setLink(edge[0], edge[1], true);
        }
    }

    /** Queues a frame if the link is up and the destination radio is running. */
    Optional<Long> send(int fromIndex, int toIndex, byte[] payload) {
        if (!activeLinks.getOrDefault(linkKey(fromIndex, toIndex), Boolean.FALSE)) {
            return Optional.empty();
        }
        SimulatedNode target = nodes.get(toIndex);
        if (target == null || !target.radio().isStarted()) {
            return Optional.empty();
        }
        inFlight.add(new Frame(fromIndex, toIndex, payload.clone()));
        framesSent++;
        return Optional.of((long) inFlight.size());
    }

    /**
     * Delivers every queued frame, including frames queued during delivery.
     *
     * @return number of frames successfully handed to a receiver
     */
    int drain() {
        int delivered = 0;
        int guard = 0;
        while (!inFlight.isEmpty() && guard++ < 1_000_000) {
            Frame frame = inFlight.poll();
            if (random.nextDouble() < config.linkDropProbability()) {
                framesDropped++;
                continue;
            }
            SimulatedNode receiver = nodes.get(frame.toIndex());
            if (receiver == null) {
                continue;
            }
            receiver.radio().deliver(frame.fromIndex(), frame.payload());
            framesDelivered++;
            delivered++;
        }
        return delivered;
    }

    /** Advances simulated time and gives every node a chance to retry. */
    void advance(long millis) {
        clock.advance(millis);
        nodes.values().forEach(node -> node.router().attemptPending());
    }

    /** Cuts or restores one undirected link. */
    void setLink(int a, int b, boolean up) {
        long key = linkKey(a, b);
        boolean changed = !activeLinks.getOrDefault(key, Boolean.FALSE) == up;
        activeLinks.put(key, up);
        if (!changed) {
            return;
        }
        SimulatedNode nodeA = nodes.get(a);
        SimulatedNode nodeB = nodes.get(b);
        if (nodeA != null && nodeB != null) {
            if (up) {
                nodeA.radio().addPeer(b);
                nodeB.radio().addPeer(a);
            } else {
                nodeA.radio().removePeer(b);
                nodeB.radio().removePeer(a);
            }
        }
        linkChanges.add(new LinkChange(clock.nowMillis(), a, b, up, round));
    }

    boolean isLinked(int a, int b) {
        return activeLinks.getOrDefault(linkKey(a, b), Boolean.FALSE);
    }

    /** Currently connected neighbours of a node, by node index. */
    Set<Integer> neighboursOf(int index) {
        Set<Integer> peers = new LinkedHashSet<>();
        for (Map.Entry<Long, Boolean> entry : activeLinks.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            long key = entry.getKey();
            int a = (int) (key >>> 32);
            int b = (int) (key & 0xFFFFFFFFL);
            if (a == index) {
                peers.add(b);
            } else if (b == index) {
                peers.add(a);
            }
        }
        return peers;
    }

    List<LinkChange> linkChanges() {
        return List.copyOf(linkChanges);
    }

    long framesSent() {
        return framesSent;
    }

    long framesDelivered() {
        return framesDelivered;
    }

    long framesDropped() {
        return framesDropped;
    }

    int framesInFlight() {
        return inFlight.size();
    }

    private static long linkKey(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    /** One queued radio frame. */
    record Frame(int fromIndex, int toIndex, byte[] payload) {
    }

    /**
     * A recorded link transition.
     *
     * @param atEpochMillis simulated time of the change
     * @param nodeA first node index
     * @param nodeB second node index
     * @param up true for a restore, false for a cut
     * @param round simulation round the change happened in
     */
    record LinkChange(long atEpochMillis, int nodeA, int nodeB, boolean up, int round) {
    }
}