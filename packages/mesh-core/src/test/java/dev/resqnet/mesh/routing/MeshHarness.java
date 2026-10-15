package dev.resqnet.mesh.routing;

import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.triage.TriageClassifier;

/**
 * In-process mesh of real {@link MeshRouter} instances wired through a
 * deterministic simulated radio.
 *
 * <p>The point of this harness is that it does not mock the routing engine. Each
 * node here is the production {@code MeshRouter} with real EC keys, real
 * signatures, real validation and the real store. Only the radio is synthetic,
 * because a radio needs hardware. A test that passes here is testing the same
 * code that runs on the handset.
 *
 * <p>Determinism: frames are queued and drained in FIFO order by
 * {@link #drain()}, never by a background thread. Two runs with the same
 * topology and the same message order produce identical event streams.
 */
final class MeshHarness {

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Set<String> disabledLinks = new LinkedHashSet<>();
    private final Deque<Frame> inFlight = new ArrayDeque<>();
    private final List<MeshEvent> globalEvents = new ArrayList<>();
    private final MeshClock.ManualClock clock;

    private double dropProbability = 0.0;
    private int hopLimit = 6;
    private long lifetimeMillis = 15 * 60 * 1000L;
    private TriageClassifier classifier = TriageClassifier.disabled();
    private boolean trustEveryone = true;

    MeshHarness(long startEpochMillis) {
        this.clock = MeshClock.ManualClock.atEpochSecond(startEpochMillis);
    }

    /** One simulated node: a real router plus the radio that feeds it. */
    final class Node {
        final InMemorySigningIdentity identity;
        final TrustStore trustStore;
        final InMemoryMessageStore store;
        final SimulatedTransport transport;
        final MeshRouter router;
        final List<MeshEvent> events = new ArrayList<>();
        final List<MessageEnvelope> inbox = new ArrayList<>();

        Node(InMemorySigningIdentity identity) {
            this.identity = identity;
            this.trustStore = new TrustStore();
            this.store = new InMemoryMessageStore();
            this.transport = new SimulatedTransport(identity.deviceId());
            this.router = new MeshRouter(identity, trustStore, store, transport, clock,
                    RouterConfig.builder()
                            .hopLimit(hopLimit)
                            .lifetimeMillis(lifetimeMillis)
                            .build(),
                    classifier,
                    new DuplicateCache(4096, lifetimeMillis, 4 * 1024 * 1024),
                    RetryPolicy.deterministic(1000, 30_000, 10, 42));
            router.addEventListener(events::add);
            router.setDeliveryListener((envelope, via, hops) -> inbox.add(envelope));
        }

        String id() {
            return identity.deviceId();
        }
    }

    MeshHarness classifier(TriageClassifier value) {
        this.classifier = value;
        return this;
    }

    MeshHarness hopLimit(int value) {
        this.hopLimit = value;
        return this;
    }

    MeshHarness lifetimeMillis(long value) {
        this.lifetimeMillis = value;
        return this;
    }

    /** Probability in [0,1] that a single frame is lost in transit. */
    MeshHarness dropProbability(double value) {
        this.dropProbability = value;
        return this;
    }

    /** When false, nodes must explicitly accept each other's keys. */
    MeshHarness trustEveryone(boolean value) {
        this.trustEveryone = value;
        return this;
    }

    Node addNode(String label) throws GeneralSecurityException {
        Node node = new Node(InMemorySigningIdentity.generate(label));
        nodes.put(node.id(), node);
        return node;
    }

    /**
     * Adds a node that reuses an existing identity, modelling a device restart.
     * Its trust store and message store start empty, exactly as they would after
     * a cold boot with a rehydrated key.
     */
    Node addRestartedNode(Node original) throws GeneralSecurityException {
        InMemorySigningIdentity rehydrated = InMemorySigningIdentity.fromKeyPair(
                original.identity.displayName(),
                original.identity.privateKey().getEncoded(),
                original.identity.publicKey().getEncoded());
        Node node = new Node(rehydrated);
        nodes.put(node.id(), node);
        return node;
    }

    Node node(String label) {
        for (Node node : nodes.values()) {
            if (node.identity.displayName().equals(label)) {
                return node;
            }
        }
        throw new IllegalArgumentException("No node named " + label + "; have "
                + nodes.values().stream().map(n -> n.identity.displayName()).toList());
    }

    Collection<Node> allNodes() {
        return nodes.values();
    }

    MeshClock.ManualClock clock() {
        return clock;
    }

    List<MeshEvent> globalEvents() {
        return globalEvents;
    }

    /** Cross-signs every pair so each node can verify the others' signatures. */
    MeshHarness trustAll() throws GeneralSecurityException {
        if (!trustEveryone) {
            return this;
        }
        for (Node a : nodes.values()) {
            for (Node b : nodes.values()) {
                if (a != b) {
                    a.trustStore.rememberUnverified(b.identity.displayName(),
                            b.identity.publicKey(), java.time.Instant.EPOCH);
                }
            }
        }
        return this;
    }

    /** Creates a bidirectional radio link and notifies both routers. */
    void link(Node a, Node b) {
        String key = edgeKey(a.id(), b.id());
        if (disabledLinks.remove(key)) {
            a.transport.peers.add(b.id());
            b.transport.peers.add(a.id());
            a.transport.listener.onPeerConnected(b.id());
            b.transport.listener.onPeerConnected(a.id());
            return;
        }
        a.transport.peers.add(b.id());
        b.transport.peers.add(a.id());
        a.transport.listener.onPeerConnected(b.id());
        b.transport.listener.onPeerConnected(a.id());
    }

    /** Creates an undirected radio link between two nodes. */
    void linkByLabel(String a, String b) {
        link(node(a), node(b));
    }

    /** Severs a radio link, simulating a device walking out of range. */
    void unlink(Node a, Node b) {
        disabledLinks.add(edgeKey(a.id(), b.id()));
        a.transport.peers.remove(b.id());
        b.transport.peers.remove(a.id());
        a.transport.listener.onPeerDisconnected(b.id());
        b.transport.listener.onPeerDisconnected(a.id());
    }

    /** Severs every link touching a node, simulating the device going offline. */
    void isolate(Node a) {
        for (Node b : nodes.values()) {
            if (b != a) {
                unlink(a, b);
            }
        }
    }

    boolean isLinked(Node a, Node b) {
        return !disabledLinks.contains(edgeKey(a.id(), b.id()));
    }

    /**
     * Delivers every queued frame, including frames queued while draining.
     *
     * @return number of frames delivered
     */
    int drain() {
        int delivered = 0;
        int guard = 0;
        while (!inFlight.isEmpty() && guard++ < 100_000) {
            Frame frame = inFlight.poll();
            if (dropProbability > 0 && hash(frame) % 1000 < dropProbability * 1000) {
                continue;
            }
            Node receiver = nodes.get(frame.to);
            if (receiver == null) {
                continue;
            }
            receiver.transport.inbound.onPayload(frame.from, frame.payload);
            globalEvents.addAll(receiver.events);
            delivered++;
        }
        return delivered;
    }

    /** Advances simulated time and then lets the mesh settle. */
    void advance(long millis) {
        clock.advance(millis);
        for (Node node : nodes.values()) {
            node.router.attemptPending();
        }
        drain();
    }

    /** Pumps every node's retry queue and drains the radio, without moving time. */
    void settle() {
        for (Node node : nodes.values()) {
            node.router.attemptPending();
        }
        drain();
    }

    int framesInFlight() {
        return inFlight.size();
    }

    private int hash(Frame frame) {
        return Math.abs((frame.from + frame.to + frame.payload.length).hashCode() % 997);
    }

    private static String edgeKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** One frame queued on the simulated radio. */
    private record Frame(String from, String to, byte[] payload) {
    }

    /** Radio adapter bound to a single node. */
    final class SimulatedTransport implements MeshTransport {
        private final String selfId;
        private final Set<String> peers = new LinkedHashSet<>();
        private InboundHandler inbound = (from, payload) -> {
        };
        private MeshTransport.ConnectionListener listener = new MeshTransport.ConnectionListener() {
        };
        private boolean started;

        SimulatedTransport(String selfId) {
            this.selfId = selfId;
        }

        @Override
        public java.util.Optional<Long> send(String peerDeviceId, byte[] encodedPayload) {
            if (!started || !peers.contains(peerDeviceId)) {
                return java.util.Optional.empty();
            }
            inFlight.add(new Frame(selfId, peerDeviceId, encodedPayload.clone()));
            return java.util.Optional.of((long) inFlight.size());
        }

        @Override
        public Set<String> connectedPeerIds() {
            return Set.copyOf(peers);
        }

        @Override
        public void setInboundHandler(InboundHandler handler) {
            this.inbound = handler;
        }

        @Override
        public void setConnectionListener(ConnectionListener connectionListener) {
            this.listener = connectionListener;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            started = false;
            peers.clear();
        }

        @Override
        public boolean isStarted() {
            return started;
        }
    }

    /** Starts every radio, which routing requires before it will send anything. */
    MeshHarness startAll() {
        for (Node node : nodes.values()) {
            node.transport.start();
        }
        return this;
    }
}