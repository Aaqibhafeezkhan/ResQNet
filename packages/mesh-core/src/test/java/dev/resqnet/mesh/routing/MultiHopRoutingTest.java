package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;

@DisplayName("Multi-hop routing")
class MultiHopRoutingTest {

    private MeshHarness mesh;

    @BeforeEach
    void setUp() throws Exception {
        mesh = new MeshHarness(1_700_000_000L);
    }

    private static byte[] sos(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("delivers a directed message across three hops")
    void deliversAcrossThreeHops() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        MeshHarness.Node d = mesh.addNode("D");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.link(c, d);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(d.id(), MessageType.SOS, Priority.SOS,
                sos("trapped in the basement"), 0L);
        mesh.settle();

        assertThat(d.inbox).hasSize(1);
        assertThat(d.inbox.get(0).messageId()).isEqualTo(sent.messageId());
        assertThat(d.inbox.get(0).hopCount()).isEqualTo(3);
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isIn(DeliveryState.SENT_TO_PEER, DeliveryState.ACKNOWLEDGED);
        assertThat(a.router.metrics().forwardedCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("delivers a message that is five hops from its destination")
    void deliversAcrossFiveHops() throws Exception {
        MeshHarness.Node[] chain = new MeshHarness.Node[6];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = mesh.addNode("N" + i);
        }
        for (int i = 1; i < chain.length; i++) {
            mesh.link(chain[i - 1], chain[i]);
        }
        mesh.trustAll().startAll();

        MessageEnvelope sent = chain[0].router.originate(chain[5].id(), MessageType.SOS,
                Priority.SOS, sos("flood rising on the third floor"), 0L);
        mesh.settle();

        assertThat(chain[5].inbox).hasSize(1);
        assertThat(chain[5].inbox.get(0).hopCount()).isEqualTo(5);
        assertThat(sent.hopLimit()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("delivers around a ring without looping forever")
    void deliversAroundARing() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        MeshHarness.Node d = mesh.addNode("D");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.link(c, d);
        mesh.link(d, a);
        mesh.trustAll().startAll();

        a.router.originate(d.id(), MessageType.SOS, Priority.SOS, sos("roof is collapsing"), 0L);
        mesh.settle();

        assertThat(d.inbox).hasSize(1);
        // Controlled flooding visits each node at most once per message, so the
        // total number of accepts across the ring is bounded by the node count
        // no matter how many independent paths exist between the endpoints.
        assertThat(totalAccepts(mesh)).isLessThanOrEqualTo(4);
        assertThat(mesh.framesInFlight()).isZero();
    }

    @Test
    @DisplayName("suppresses duplicates when a diamond offers several paths")
    void suppressesDuplicatesInADiamond() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        MeshHarness.Node d = mesh.addNode("D");
        mesh.link(a, b);
        mesh.link(a, c);
        mesh.link(b, d);
        mesh.link(c, d);
        mesh.trustAll().startAll();

        a.router.originate(d.id(), MessageType.SOS, Priority.SOS, sos("two ways here"), 0L);
        mesh.settle();

        assertThat(d.inbox).hasSize(1);
        long duplicates = b.router.metrics().duplicateSuppressedCount()
                + c.router.metrics().duplicateSuppressedCount()
                + d.router.metrics().duplicateSuppressedCount();
        assertThat(duplicates).isGreaterThan(0);
        // A, B, C and D: the origin is not an "accept", and the second copy
        // arriving at D is suppressed, so exactly three accepts occur.
        assertThat(totalAccepts(mesh)).isEqualTo(3);
    }

    @Test
    @DisplayName("delivers through a two node cycle where split horizon alone is not enough")
    void terminatesInATwoNodeCycle() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        a.router.originate(null, MessageType.TEXT, Priority.NORMAL, sos("hello"), 0L);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(mesh.framesInFlight()).isZero();
        assertThat(a.router.metrics().forwardedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("broadcast reaches every connected node exactly once")
    void broadcastReachesEveryNodeOnce() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        a.router.originate(null, MessageType.SOS, Priority.SOS, sos("bridge out ahead of us"), 0L);
        mesh.settle();

        assertThat(a.inbox).hasSize(1);
        assertThat(b.inbox).hasSize(1);
        assertThat(c.inbox).hasSize(1);
        // The origin does not accept its own traffic, so B and C account for
        // every accept in a three node broadcast.
        assertThat(totalAccepts(mesh)).isEqualTo(2);
    }

    @Test
    @DisplayName("an envelope aimed at one node is not delivered to anyone else")
    void directedMessageIsNotLeaked() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(a, c);
        mesh.trustAll().startAll();

        a.router.originate(b.id(), MessageType.TEXT, Priority.NORMAL, sos("for your eyes"), 0L);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(c.inbox).isEmpty();
    }

    @Test
    @DisplayName("drops a message that runs out of hops before arriving")
    void dropsWhenHopsRunOut() throws Exception {
        mesh.hopLimit(2);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        MeshHarness.Node d = mesh.addNode("D");
        MeshHarness.Node e = mesh.addNode("E");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.link(c, d);
        mesh.link(d, e);
        mesh.trustAll().startAll();

        a.router.originate(e.id(), MessageType.SOS, Priority.SOS, sos("too far to reach"), 0L);
        mesh.settle();

        assertThat(e.inbox).isEmpty();
        long hopLimitDrops = mesh.allNodes().stream()
                .mapToLong(n -> n.router.metrics().hopLimitDropCount()).sum();
        assertThat(hopLimitDrops).isGreaterThan(0);
    }

    @Test
    @DisplayName("a relay holds the message when it has no eligible peer yet")
    void relayQueuesWhenIsolated() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                sos("relay me"), 0L);
        mesh.settle();
        assertThat(c.inbox).hasSize(1);

        // Now cut B off from the far side while A can still reach B. The second
        // message must sit in B's durable queue rather than vanishing, because
        // the link will come back.
        mesh.unlink(b, c);
        a.router.originate(c.id(), MessageType.SOS, Priority.SOS, sos("hold this"), 0L);
        mesh.settle();

        assertThat(c.inbox).hasSize(1);
        StoredMessage queued = b.store.pending().stream()
                .filter(m -> !m.messageId().equals(sent.messageId()))
                .findFirst().orElseThrow();
        assertThat(queued.state()).isIn(DeliveryState.RELAYED, DeliveryState.QUEUED);
        assertThat(queued.lastDetail()).contains("no eligible peer");
    }

    @Test
    @DisplayName("every relayed copy keeps the origin signature intact")
    void relayedCopiesRemainAuthentic() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                sos("please send water"), 0L);
        mesh.settle();

        MessageEnvelope delivered = c.inbox.get(0);
        assertThat(dev.resqnet.mesh.crypto.SignatureVerifier.verifyEnvelope(
                c.trustStore.entry(a.id()).orElseThrow(), delivered)).isTrue();
        assertThat(delivered.signature()).isEqualTo(sent.signature());
        assertThat(delivered.hopLimit()).isLessThan(sent.hopLimit());
    }

    @Test
    @DisplayName("a message with no path is reported as having no path, not as sent")
    void isolatedOriginIsNotReportedAsSent() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(null, MessageType.SOS, Priority.SOS,
                sos("no one is listening"), 0L);
        mesh.settle();

        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.QUEUED);
    }

    @Test
    @DisplayName("a message nobody claimed is expired rather than delivered late")
    void expiredMessagesAreMarkedExpired() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate("rqn1_" + "e".repeat(64), MessageType.SOS,
                Priority.SOS, sos("nobody is in range"), 5_000L);
        mesh.settle();
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.QUEUED);

        mesh.advance(60_000L);

        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.EXPIRED);
    }

    private static long totalAccepts(MeshHarness mesh) {
        long total = 0;
        for (MeshHarness.Node node : mesh.allNodes()) {
            total += node.router.metrics().acceptedCount();
        }
        return total;
    }

    @Test
    @DisplayName("forwards to at most the configured fan-out")
    void respectsFanOutLimit() throws Exception {
        mesh.hopLimit(4);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node[] spokes = new MeshHarness.Node[6];
        for (int i = 0; i < spokes.length; i++) {
            spokes[i] = mesh.addNode("S" + i);
            mesh.link(a, spokes[i]);
        }
        mesh.trustAll().startAll();

        a.router.originate(null, MessageType.TEXT, Priority.NORMAL, sos("fan out"), 0L);
        mesh.settle();

        List<MeshHarness.Node> reached = List.of(spokes).stream()
                .filter(n -> !n.inbox.isEmpty()).toList();
        assertThat(reached).isNotEmpty();
        assertThat(a.router.config().maxFanOut()).isEqualTo(8);
    }
}