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
 * Hop accounting at a relay that queues instead of forwarding.
 *
 * <p>{@code hopCount} is a claim about the world: how many radio links this
 * frame has actually crossed. {@code hopLimit} is the budget it is allowed to
 * cross. Both are therefore spent at the moment a frame is handed to the radio,
 * and not before.
 *
 * <p>The relay queue sits between those two moments. A relay that has a
 * neighbour but finds the session gone holds the frame for a later pass, and
 * when it does so it must store the frame <em>as it arrived</em>. Storing the
 * frame it intended to send charges a hop the frame never used, and the next
 * pass then charges the same hop again: a two-hop path is reported to the
 * recipient as three, and a relay with a small budget can declare the frame
 * hop-exhausted without it having left the device once.
 */
@DisplayName("Hop accounting for a relayed frame")
class RelayHopAccountingTest {

    private static final byte[] BODY = "hold this for me".getBytes(StandardCharsets.UTF_8);

    private MeshHarness mesh;

    @BeforeEach
    void setUp() {
        mesh = new MeshHarness(1_700_000_000L);
    }

    private static MessageEnvelope storedEnvelope(MeshHarness.Node node, String messageId)
            throws Exception {
        return EnvelopeCodec.decode(node.store.find(messageId).orElseThrow().envelope());
    }

    @Test
    @DisplayName("charges no hop for a frame the radio never accepted")
    void aRefusedHandoffSpendsNoHop() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();
        // B can see C in its link table, but every copy it offers is refused.
        mesh.failSends(b, true);

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.TEXT, Priority.LOW,
                BODY, 0L);
        mesh.settle();

        MessageEnvelope held = storedEnvelope(b, sent.messageId());
        assertThat(held.hopCount())
                .as("only A->B has happened, and that hop is A's to charge")
                .isEqualTo(1);
        assertThat(held.hopLimit())
                .as("one hop was used, so one hop may be deducted")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("charges the hop once when the retry finally goes out")
    void theRetryChargesTheHopExactlyOnce() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();
        mesh.failSends(b, true);

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.TEXT, Priority.LOW,
                BODY, 0L);
        mesh.settle();
        assertThat(c.inbox).as("nothing may arrive while the radio refuses").isEmpty();

        mesh.failSends(b, false);
        mesh.settle();

        assertThat(c.inbox).hasSize(1);
        assertThat(c.inbox.get(0).hopCount())
                .as("A->B->C is two radio hops and must be reported as two")
                .isEqualTo(2);
        assertThat(c.inbox.get(0).hopLimit()).isEqualTo(4);
    }

    @Test
    @DisplayName("a clean forward still spends exactly one hop per link")
    void aCleanForwardSpendsOneHopPerLink() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.TEXT, Priority.LOW,
                BODY, 0L);
        mesh.settle();

        assertThat(c.inbox).hasSize(1);
        assertThat(c.inbox.get(0).hopCount()).isEqualTo(2);
        assertThat(c.inbox.get(0).hopLimit()).isEqualTo(4);
    }

    @Test
    @DisplayName("a dropped connection costs nothing either")
    void aDroppedConnectionSpendsNoHop() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();
        mesh.unlink(b, c);

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.TEXT, Priority.LOW,
                BODY, 0L);
        mesh.settle();

        MessageEnvelope held = storedEnvelope(b, sent.messageId());
        assertThat(held.hopCount())
                .as("B had nowhere to send it, so it spent nothing")
                .isEqualTo(1);
        assertThat(held.hopLimit()).isEqualTo(5);
    }

    @Test
    @DisplayName("repeated refusals never exhaust the hop budget")
    void repeatedRefusalsNeverExhaustTheBudget() throws Exception {
        mesh.hopLimit(2);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();
        mesh.failSends(b, true);

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.TEXT, Priority.LOW,
                BODY, 0L);
        mesh.settle();

        for (int i = 0; i < 20; i++) {
            mesh.advance(30_000L);
        }

        StoredMessage held = b.store.find(sent.messageId()).orElseThrow();
        assertThat(held.state())
                .as("a frame that never left the device cannot have run out of hops")
                .isNotEqualTo(DeliveryState.FAILED)
                .isNotEqualTo(DeliveryState.EXPIRED);
        assertThat(EnvelopeCodec.decode(held.envelope()).hopLimit())
                .as("the budget must survive twenty refusals untouched")
                .isPositive();
        assertThat(EnvelopeCodec.decode(held.envelope()).hopCount())
                .as("and no hop may be charged for twenty handoffs that never happened")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("expiry is expiry, not hop exhaustion")
    void aQueuedFrameThatExpiresIsNotCalledHopExhausted() throws Exception {
        mesh.hopLimit(2);
        mesh.lifetimeMillis(60_000L);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();
        mesh.failSends(b, true);

        MessageEnvelope sent = a.router.originate(c.id(), MessageType.TEXT, Priority.LOW,
                BODY, 0L);
        mesh.settle();
        for (int i = 0; i < 10; i++) {
            mesh.advance(30_000L);
        }

        StoredMessage held = b.store.find(sent.messageId()).orElseThrow();
        assertThat(held.state())
                .as("it aged out while isolated from a working radio, which is a"
                        + " different fact from spending its hop budget")
                .isEqualTo(DeliveryState.EXPIRED);
        assertThat(EnvelopeCodec.decode(held.envelope()).hopLimit())
                .as("an expired record still has to say how far it really got")
                .isEqualTo(1);
    }
}
