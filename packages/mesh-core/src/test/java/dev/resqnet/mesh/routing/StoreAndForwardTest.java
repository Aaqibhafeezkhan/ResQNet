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
 * Store-and-forward behaviour: the property that makes ResQNet a network rather
 * than a walkie-talkie. A message composed out of range must still exist,
 * still be there after the process dies, and still go out when a link returns.
 */
@DisplayName("Store and forward")
class StoreAndForwardTest {

    private MeshHarness mesh;

    @BeforeEach
    void setUp() throws Exception {
        mesh = new MeshHarness(1_700_000_000L);
    }

    private static byte[] body(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("holds an undeliverable message instead of dropping it")
    void holdsUndeliverableMessage() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate("rqn1_" + "b".repeat(64), MessageType.SOS,
                Priority.SOS, body("nobody in range"), 0L);

        StoredMessage stored = a.store.find(sent.messageId()).orElseThrow();
        assertThat(stored.state()).isEqualTo(DeliveryState.QUEUED);
        assertThat(a.store.pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("sends the held message the moment a peer connects")
    void sendsOnReconnect() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("are you there"), 0L);
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.QUEUED);

        mesh.link(a, b);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(b.inbox.get(0).messageId()).isEqualTo(sent.messageId());
    }

    @Test
    @DisplayName("survives a relay going offline and coming back")
    void survivesRelayRestart() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(b, c);
        mesh.trustAll().startAll();

        // The link to the far side drops before anything is relayed.
        mesh.unlink(b, c);
        MessageEnvelope sent = a.router.originate(c.id(), MessageType.SOS, Priority.SOS,
                body("hold this for me"), 0L);
        mesh.settle();
        assertThat(c.inbox).isEmpty();
        assertThat(b.store.pending()).hasSize(1);

        mesh.link(b, c);
        mesh.settle();

        assertThat(c.inbox).hasSize(1);
        assertThat(c.inbox.get(0).messageId()).isEqualTo(sent.messageId());
    }

    @Test
    @DisplayName("restores a queue after a cold restart with the same key")
    void restoresQueueAfterRestart() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.trustAll().startAll();

        // b is offline when the message is composed.
        MessageEnvelope sent = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("persist me"), 0L);
        StoredMessage persisted = a.store.find(sent.messageId()).orElseThrow();

        // Model a cold start: the key is rehydrated from storage, and the queue
        // is reloaded from the database, exactly as the Android SQLite adapter
        // does on launch.
        MeshHarness.Node restarted = mesh.addRestartedNode(a);
        assertThat(restarted.identity.deviceId())
                .as("a key-derived identity must survive a reboot")
                .isEqualTo(a.id());
        restarted.store.upsert(persisted);
        restarted.trustStore.rememberUnverified(b.identity.displayName(),
                b.identity.publicKey(), java.time.Instant.EPOCH);
        restarted.transport.start();

        mesh.link(restarted, b);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(b.inbox.get(0).messageId()).isEqualTo(sent.messageId());
        assertThat(EnvelopeCodec.decode(persisted.envelope()).messageId())
                .isEqualTo(sent.messageId());
    }

    @Test
    @DisplayName("backs off between attempts instead of hammering the radio")
    void backsOffBetweenAttempts() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(b.id(), MessageType.TEXT, Priority.LOW,
                body("ping"), 0L);
        StoredMessage afterFirst = a.store.find(sent.messageId()).orElseThrow();
        assertThat(afterFirst.attemptCount()).isEqualTo(1);
        long scheduledNext = afterFirst.nextAttemptAt();
        assertThat(scheduledNext).isGreaterThanOrEqualTo(a.router.clock().nowMillis());

        long before = a.router.metrics().forwardedCount();
        mesh.advance(1);
        assertThat(a.router.metrics().forwardedCount())
                .as("no retry may happen before the backoff window elapses")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("gives up after the retry budget is exhausted")
    void givesUpAfterRetryBudget() throws Exception {
        mesh.lifetimeMillis(6 * 60 * 60 * 1000);
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate("rqn1_" + "b".repeat(64), MessageType.TEXT,
                Priority.LOW, body("nobody"), 0L);

        for (int i = 0; i < 40; i++) {
            mesh.advance(120_000L);
        }

        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.FAILED);
    }

    @Test
    @DisplayName("expires a held message once its lifetime elapses")
    void expiresHeldMessage() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate("rqn1_" + "b".repeat(64), MessageType.SOS,
                Priority.SOS, body("short lived"), 30_000L);

        mesh.advance(120_000L);

        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.EXPIRED);
        assertThat(a.store.pendingCount()).isZero();
    }

    @Test
    @DisplayName("a late peer does not resurrect an expired message")
    void latePeerDoesNotResurrectExpiredMessage() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sent = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("too late"), 10_000L);
        mesh.advance(60_000L);

        mesh.link(a, b);

        assertThat(b.inbox).isEmpty();
        assertThat(a.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.EXPIRED);
    }

    @Test
    @DisplayName("enforces queue capacity without ever evicting SOS traffic")
    void enforcesCapacityProtectingSos() throws Exception {
        mesh.lifetimeMillis(3_600_000);
        MeshHarness.Node a = mesh.addNode("A");
        mesh.addNode("B");
        mesh.trustAll().startAll();

        MessageEnvelope sos = a.router.originate("rqn1_" + "b".repeat(64), MessageType.SOS,
                Priority.SOS, body("keep me"), 0L);
        for (int i = 0; i < 600; i++) {
            a.router.originate("rqn1_" + "b".repeat(64), MessageType.TEXT, Priority.LOW,
                    body("noise " + i), 0L);
        }

        assertThat(a.store.find(sos.messageId()))
                .as("life-safety traffic must never be evicted to make room")
                .isPresent();
        assertThat(a.store.pendingCount()).isLessThanOrEqualTo(
                a.router.config().maxPendingMessages());
    }
}