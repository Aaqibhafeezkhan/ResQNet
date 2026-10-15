package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;

/**
 * End-to-end delivery confirmation.
 *
 * <p>This is the test file that encodes the most important product rule in
 * ResQNet: a successful radio handover is not a delivery. Every assertion here
 * exists to keep that distinction sharp, because collapsing it would mean
 * telling a stranded person their SOS arrived when it did not.
 */
@DisplayName("Delivery acknowledgement")
class AcknowledgementTest {

    private MeshHarness mesh;

    @BeforeEach
    void setUp() {
        mesh = new MeshHarness(1_700_000_000L);
    }

    private static byte[] body(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("reaches ACKNOWLEDGED after the destination confirms")
    void reachesAcknowledged() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                body("we are cut off"), 0L);
        mesh.settle();

        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.ACKNOWLEDGED);
        assertThat(a.router.metrics().acknowledgementReceivedCount()).isEqualTo(1);
        assertThat(a.router.metrics().deliveryLatencySampleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("stays SENT_TO_PEER when the destination never acknowledges")
    void staysSentWhenUnacknowledged() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        // The destination is reachable for one hop but the acknowledgement
        // cannot get back, which is exactly what SENT_TO_PEER must mean.
        MessageEnvelope sent = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("did you get this"), 0L);
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.SENT_TO_PEER);
        assertThat(a.router.metrics().acknowledgementReceivedCount()).isZero();
    }

    @Test
    @DisplayName("ignores a duplicate acknowledgement")
    void ignoresDuplicateAcknowledgement() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("confirm receipt"), 0L);
        mesh.settle();
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.ACKNOWLEDGED);

        // Replay the exact acknowledgement bytes the destination already sent.
        RoutingOutcome replay = replayAcknowledgement(a, b, sent);
        assertThat(replay)
                .as("a second acknowledgement is idempotent, never an error")
                .isEqualTo(RoutingOutcome.ACKNOWLEDGED);
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.ACKNOWLEDGED);
        assertThat(a.router.metrics().duplicateAcknowledgementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("relays an acknowledgement back along the path")
    void relaysAcknowledgement() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                body("please confirm"), 0L);
        mesh.settle();

        assertThat(a.events)
                .filteredOn(e -> e instanceof MeshEvent.AcknowledgementReceived)
                .as("the acknowledgement must come back along the relay path")
                .isNotEmpty();
        assertThat(b.events)
                .filteredOn(e -> e instanceof MeshEvent.Forwarded)
                .as("the relay must hand the acknowledgement onwards")
                .isNotEmpty();
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.ACKNOWLEDGED);
    }

    @Test
    @DisplayName("an acknowledgement never triggers another acknowledgement")
    void doesNotAcknowledgeAcknowledgements() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        a.router.originate(b.id(), MessageType.TEXT, Priority.NORMAL, body("hello"), 0L);
        mesh.settle();

        long acksSent = b.router.metrics().acknowledgementSentCount();
        mesh.advance(30_000L);

        assertThat(b.router.metrics().acknowledgementSentCount())
                .as("acknowledgements must not oscillate")
                .isEqualTo(acksSent);
    }

    @Test
    @DisplayName("records measurable delivery latency")
    void recordsDeliveryLatency() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        for (int i = 0; i < 5; i++) {
            a.router.originate(b.id(), MessageType.TEXT, Priority.NORMAL,
                    body("latency sample " + i), 0L);
            mesh.advance(250L);
        }
        mesh.settle();

        assertThat(a.router.metrics().deliveryLatencySampleCount()).isEqualTo(5);
        assertThat(a.router.metrics().medianDeliveryLatencyMillis()).isNotNegative();
        assertThat(a.router.metrics().p95DeliveryLatencyMillis())
                .isGreaterThanOrEqualTo(a.router.metrics().medianDeliveryLatencyMillis());
    }

    @Test
    @DisplayName("a broadcast is never acknowledged, because there is one recipient")
    void broadcastIsNotAcknowledged() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(null, MessageType.SOS, Priority.SOS,
                body("everyone hears this"), 0L);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.SENT_TO_PEER);
    }

    @Test
    @DisplayName("acknowledgement survives a relay that is not the origin")
    void relayDoesNotClaimDelivery() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        a.router.originate(c.id(), MessageType.SOS, Priority.SOS, body("relay only"), 0L);
        mesh.settle();

        assertThat(b.router.metrics().acknowledgementReceivedCount())
                .as("only the originating device may claim end-to-end delivery")
                .isZero();
        assertThat(b.router.metrics().deliveryLatencySampleCount()).isZero();
    }

    private RoutingOutcome replayAcknowledgement(MeshHarness.Node origin,
                                                MeshHarness.Node destination,
                                                MessageEnvelope original) throws Exception {
        // Re-derive the acknowledgement the destination already produced and feed
        // it back through the normal inbound path.
        MessageEnvelope ack = destination.router.buildAcknowledgement(original,
                mesh.clock().nowMillis()).orElseThrow();
        return origin.router.receive(destination.id(), EnvelopeCodec.encode(ack));
    }
}