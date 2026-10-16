package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;

/**
 * The two topologies the hardware test can actually produce, pinned.
 *
 * <h2>Why this exists</h2>
 *
 * <p>M10 documented a "same-cluster delivery limitation" on the strength of a
 * code-reading audit that claimed a {@code P2P_CLUSTER} client connects only to
 * the cluster owner. That is false, and it is false in the one way that
 * mattered. Google's own documentation for the strategy says:
 *
 * <blockquote>
 * The cluster strategy is a peer-to-peer strategy that supports an M-to-N, or
 * cluster-shaped, connection topology. In other words, this enables connecting
 * amorphous clusters of devices within radio range (~100m), where each device
 * can both initiate outgoing connections to M other devices and accept incoming
 * connections from N other devices.
 * </blockquote>
 *
 * <p>That is the STAR topology being described. Cluster is a full mesh.
 *
 * <p>The consequence is not a bug but a property the demonstration has to
 * respect: with three phones in range of each other, A to C is one hop and no
 * relay is involved, so a demo arranged that way shows nothing about relaying
 * and will look broken to anyone expecting a hop count of two.
 *
 * <p>These tests pin both arrangements so the distinction is executable rather
 * than a paragraph someone can misread.
 *
 * <h2>What they do not do</h2>
 *
 * <p>They run the router over a simulated radio with the exact peer sets that
 * {@code P2P_CLUSTER} produces. They say nothing about whether three real
 * handsets form that peer set, which needs the procedure in
 * {@code apps/android/DEVICE_TESTING.md}.
 */
@DisplayName("P2P_CLUSTER topologies")
class ClusterTopologyTest {

    @Test
    @DisplayName("three phones in range form a full mesh and A reaches C directly")
    void fullClusterDeliversInOneHop() throws Exception {
        MeshHarness mesh = new MeshHarness(1_700_000_000L);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");

        // What P2P_CLUSTER produces when all three are within ~100 m: every
        // device both initiates to and accepts from every other.
        mesh.link(a, b);
        mesh.link(a, c);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        var sent = a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                "help".getBytes(StandardCharsets.UTF_8), 0L);
        mesh.settle();

        assertThat(c.inbox).hasSize(1);
        assertThat(c.inbox.get(0).messageId()).isEqualTo(sent.messageId());
        assertThat(c.inbox.get(0).hopCount())
                .as("in a full cluster A and C are direct neighbours, so this is one hop"
                        + " and no relay is involved. A demo that expects two here has the"
                        + " phones too close together, not a broken mesh.")
                .isEqualTo(1);

        // B may still forward once, and it is worth saying what for. C builds
        // the acknowledgement and floods it to everyone except the peer it
        // arrived from, which is A, so the ACK goes to B and B carries it on.
        // Harmless, because split horizon stops it going straight back to A,
        // but it means "the relay forwarded nothing" would be a false claim.
        assertThat(b.inbox)
                .as("B is not on the path for the message itself and never displays it")
                .isEmpty();
        assertThat(b.router.metrics().forwardedCount())
                .as("at most one, and only if B is carrying the acknowledgement back")
                .isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a chain delivers in two hops through a relay that never sees the body")
    void chainDeliversInTwoHops() throws Exception {
        MeshHarness mesh = new MeshHarness(1_700_000_000L);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");

        // What P2P_CLUSTER produces when B is the only thing bridging A and C:
        // A sees B, B sees both, C sees B.
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        assertThat(a.transport.connectedPeerIds())
                .as("A cannot see C, which is the whole point of this arrangement")
                .doesNotContain(c.id());

        var sent = a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                "help".getBytes(StandardCharsets.UTF_8), 0L);
        mesh.settle();

        assertThat(c.inbox).hasSize(1);
        assertThat(c.inbox.get(0).hopCount())
                .as("A to B to C is two hops")
                .isEqualTo(2);

        assertThat(b.inbox)
                .as("the relay carried a message it had no business reading")
                .isEmpty();
        assertThat(b.router.metrics().forwardedCount())
                .as("and it did forward it")
                .isPositive();
    }

    @Test
    @DisplayName("a directed message in a full cluster goes straight to its addressee")
    void directedMessagePrefersTheDestination() throws Exception {
        MeshHarness mesh = new MeshHarness(1_700_000_000L);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(a, c);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                "for c only".getBytes(StandardCharsets.UTF_8), 0L);
        mesh.settle();

        assertThat(c.inbox).hasSize(1);
        assertThat(b.inbox)
                .as("B is a peer of both, and still must not see a message addressed to C")
                .isEmpty();
    }

    @Test
    @DisplayName("a peer that leaves the cluster stops receiving immediately")
    void leavingTheClusterStopsDelivery() throws Exception {
        MeshHarness mesh = new MeshHarness(1_700_000_000L);
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, c);
        mesh.trustAll().startAll();

        mesh.unlink(a, c);
        a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                "too late".getBytes(StandardCharsets.UTF_8), 0L);
        mesh.settle();

        assertThat(c.inbox).isEmpty();
        assertThat(a.store.find(firstMessageId(a)))
                .get()
                .extracting(StoredMessage::state)
                .as("queued, not dropped: the handset is out of range, not gone")
                .isIn(DeliveryState.QUEUED, DeliveryState.SENT_TO_PEER);
    }

    private static String firstMessageId(MeshHarness.Node node) {
        return node.store.pending().get(0).messageId();
    }
}