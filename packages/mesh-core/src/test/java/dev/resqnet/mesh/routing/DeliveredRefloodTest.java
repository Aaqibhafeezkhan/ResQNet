package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;

/**
 * What a node does with a message it has already received.
 *
 * <p>These are regression tests for a defect found in the M9 audit, not a
 * description of intended behaviour written after the fact. Before the fix, a
 * delivered record stayed in the retry queue, so every five-second sweep
 * re-broadcast it to every neighbour; because the re-send passed no arrival
 * peer, split horizon was bypassed too. The same record was then relabelled
 * EXPIRED at end of lifetime, so a message that had been received was reported
 * as one that never was.
 */
@DisplayName("Delivered messages stop being forwarded")
class DeliveredRefloodTest {

    private MeshHarness mesh;

    @BeforeEach
    void setUp() throws Exception {
        mesh = new MeshHarness(1_700_000_000L);
    }

    @Test
    @DisplayName("a delivered message must not be re-broadcast on every retry sweep")
    void deliveredMessageIsNotReflooded() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.link(a, c);
        mesh.trustAll().startAll();

        var sent = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                "help".getBytes(StandardCharsets.UTF_8), 0L);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(b.store.find(sent.messageId()).orElseThrow().state())
                .as("B is the destination, so B's copy is DELIVERED")
                .isEqualTo(DeliveryState.DELIVERED);

        long forwardedBefore = b.router.metrics().forwardedCount();
        System.out.println("PROBE B forwardedCount after delivery = " + forwardedBefore);
        System.out.println("PROBE B pending after delivery = "
                + b.store.pending().size());

        // Pump the retry queue several times without advancing time.
        for (int i = 0; i < 5; i++) {
            mesh.settle();
        }

        long forwardedAfter = b.router.metrics().forwardedCount();
        System.out.println("PROBE B forwardedCount after 5 more sweeps = " + forwardedAfter);
        assertThat(forwardedAfter)
                .as("a message B has already received must never be re-broadcast")
                .isEqualTo(forwardedBefore);
    }

    @Test
    @DisplayName("a delivered message is not later relabelled as expired")
    void deliveredMessageIsNotRelabelledExpired() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        var sent = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                "help".getBytes(StandardCharsets.UTF_8), 0L);
        mesh.settle();
        assertThat(b.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.DELIVERED);

        mesh.advance(3_600_000L);

        assertThat(b.store.find(sent.messageId()).orElseThrow().state())
                .as("reporting an unarrived message as expired is the worst failure this"
                        + " system can make about its own reliability")
                .isEqualTo(DeliveryState.DELIVERED);
    }

    @Test
    @DisplayName("a relay still forwards other people's messages after it is done")
    void relayStillForwardsForOthers() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                "help".getBytes(StandardCharsets.UTF_8), 0L);
        mesh.settle();

        assertThat(c.inbox)
                .as("stopping the destination from re-flooding must not stop relays relaying")
                .hasSize(1);
        assertThat(b.store.pending())
                .as("the relay keeps its copy so it can retransmit if the link drops")
                .isNotEmpty();
    }
}
