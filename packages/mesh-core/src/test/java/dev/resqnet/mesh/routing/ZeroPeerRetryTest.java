package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;

/**
 * Retry accounting when the device is alone.
 *
 * <p>{@code attemptCount} counts transmissions, not loneliness. A phone that has
 * never found a neighbour has not failed to send anything; it has had nothing to
 * send to. Spending the retry budget on isolation turns "nobody is in range yet"
 * into a recorded, permanent failure, and the record that gets marked failed is
 * most likely somebody's SOS.
 */
@DisplayName("Retry budget while isolated")
class ZeroPeerRetryTest {

    private MeshHarness mesh;

    @BeforeEach
    void setUp() {
        mesh = new MeshHarness(1_700_000_000L);
    }

    private static byte[] body(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("does not spend the retry budget on isolation")
    void isolationDoesNotSpendTheRetryBudget() throws Exception {
        // The maximum lifetime, and an observation window strictly inside it,
        // so an expiry can never be mistaken for a retry decision.
        mesh.lifetimeMillis(6 * 60 * 60 * 1000L);
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate("rqn1_" + "b".repeat(64), MessageType.SOS,
                Priority.SOS, body("nobody in range"), 0L);

        // Five hours fifty of being alone, in two-minute steps.
        for (int i = 0; i < 175; i++) {
            mesh.advance(120_000L);
        }

        StoredMessage held = a.store.find(sent.messageId()).orElseThrow();
        assertThat(held.state())
                .as("an untransmitted message has not failed")
                .isEqualTo(DeliveryState.QUEUED);
        assertThat(held.attemptCount())
                .as("no transmission was attempted, so no attempt may be charged")
                .isZero();
        assertThat(a.store.pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("still delivers the held message when a peer finally appears")
    void deliversAfterTheLongSilence() throws Exception {
        mesh.lifetimeMillis(6 * 60 * 60 * 1000L);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(b.id(), MessageType.SOS,
                Priority.SOS, body("still out of range"), 0L);
        for (int i = 0; i < 175; i++) {
            mesh.advance(120_000L);
        }
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.QUEUED);

        mesh.link(a, b);
        mesh.settle();

        assertThat(b.inbox)
                .as("the whole point of the durable queue")
                .hasSize(1);
        assertThat(b.inbox.get(0).messageId()).isEqualTo(sent.messageId());
    }

    @Test
    @DisplayName("defers the next pass instead of hammering the queue every sweep")
    void isolationStillBacksOff() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate("rqn1_" + "b".repeat(64), MessageType.TEXT,
                Priority.LOW, body("ping nobody"), 0L);
        long firstPass = a.router.metrics().forwardedCount();

        mesh.advance(1);
        assertThat(a.router.metrics().forwardedCount())
                .as("backoff must still apply while isolated")
                .isEqualTo(firstPass);
        assertThat(a.store.find(sent.messageId()).orElseThrow().attemptCount()).isZero();
    }
}
