package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.crypto.SignatureVerifier;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.protocol.ProtocolException;
import dev.resqnet.mesh.protocol.RejectReason;

/**
 * Adversarial tests. Each one describes a concrete way an attacker with a radio
 * within range could abuse the mesh, and asserts the specific defence that
 * stops it. A mesh router that only handles the happy path is the single
 * largest risk in this project.
 */
@DisplayName("Security")
class SecurityTest {

    private MeshHarness mesh;

    @BeforeEach
    void setUp() {
        mesh = new MeshHarness(1_700_000_000L);
    }

    private static byte[] body(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("drops traffic from an origin whose key was never confirmed")
    void dropsUnknownOrigin() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node attacker = mesh.addNode("Attacker");
        MeshHarness.Node victim = mesh.addNode("Victim");
        mesh.link(attacker, victim);
        mesh.trustAll().startAll();

        // The attacker signs correctly, but the victim has no key for it.
        victim.trustStore.forget(attacker.id());
        attacker.router.originate(victim.id(), MessageType.SOS, Priority.SOS,
                body("trust me, send the helicopter"), 0L);
        mesh.settle();

        assertThat(victim.inbox).isEmpty();
        assertThat(victim.router.metrics().untrustedDropCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("drops a replayed capture of a previously valid message")
    void dropsReplay() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        MessageEnvelope genuine = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("send help"), 0L);
        mesh.settle();
        assertThat(b.inbox).hasSize(1);

        byte[] captured = EnvelopeCodec.encode(genuine);
        RoutingOutcome replay = b.router.receive(a.id(), captured);

        assertThat(replay).isEqualTo(RoutingOutcome.DUPLICATE);
        assertThat(b.inbox)
                .as("a replay must never produce a second alert in the user's inbox")
                .hasSize(1);
        assertThat(b.router.metrics().duplicateSuppressedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("drops a message whose payload was edited after signing")
    void dropsTamperedPayload() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        MessageEnvelope genuine = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("injured, send help"), 0L);

        MessageEnvelope tampered = genuine.toBuilder()
                .payload(body("no injuries, stand down"))
                .build();
        byte[] forged = EnvelopeCodec.encode(tampered);
        RoutingOutcome outcome = b.router.receive(a.id(), forged);

        assertThat(outcome).isEqualTo(RoutingOutcome.DROPPED_UNTRUSTED);
        assertThat(b.inbox).isEmpty();
    }

    @Test
    @DisplayName("drops a message re-labelled to come from a different device")
    void dropsOriginSubstitution() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        MeshHarness.Node c = mesh.addNode("C");
        mesh.link(a, b);
        mesh.link(c, b);
        mesh.trustAll().startAll();

        MessageEnvelope fromA = a.router.originate(b.id(), MessageType.SOS, Priority.SOS,
                body("from A"), 0L);

        // Take A's perfectly valid signed frame and claim it was sent by C.
        MessageEnvelope impersonatingC = fromA.toBuilder()
                .origin(c.id())
                .originKeyFingerprint(c.identity.keyFingerprint())
                .build();
        RoutingOutcome outcome = b.router.receive(a.id(), EnvelopeCodec.encode(impersonatingC));

        assertThat(outcome).isEqualTo(RoutingOutcome.DROPPED_UNTRUSTED);
        assertThat(b.inbox).isEmpty();
    }

    @Test
    @DisplayName("drops everything from a blocked origin")
    void dropsBlockedOrigin() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        a.router.originate(b.id(), MessageType.TEXT, Priority.NORMAL, body("hello"), 0L);
        mesh.settle();
        assertThat(b.inbox).hasSize(1);

        b.trustStore.block(a.id());
        // Fresh message so deduplication is not what stops it.
        a.router.originate(b.id(), MessageType.TEXT, Priority.NORMAL, body("again"), 0L);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(b.router.metrics().untrustedDropCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a device id cannot be claimed with a different key")
    void deviceIdIsBoundToItsKey() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        InMemorySigningIdentity impostor = InMemorySigningIdentity.generate("A impostor");

        assertThat(dev.resqnet.mesh.crypto.DeviceIdentity.deviceId(impostor.publicKey()))
                .as("identity is a hash of the key, so it is unforgeable by construction")
                .isNotEqualTo(a.id());

        a.trustStore.rememberUnverified("A", a.identity.publicKey(), java.time.Instant.EPOCH);
        a.trustStore.rememberUnverified("A", a.identity.publicKey(), java.time.Instant.EPOCH);

        assertThat(a.trustStore.size()).isEqualTo(1);
        assertThat(a.trustStore.entry(a.id()).orElseThrow().keyFingerprint())
                .isEqualTo(a.identity.keyFingerprint());
    }

    @Test
    @DisplayName("rejects a payload that claims to be larger than the radio allows")
    void rejectsOversizedPayload() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        byte[] huge = new byte[MessageEnvelope.MAX_PAYLOAD_BYTES + 1];
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                a.router.originate(b.id(), MessageType.TEXT, Priority.LOW, huge, 0L))
                .isInstanceOf(ProtocolException.class);

        assertThat(b.inbox).isEmpty();
    }

    @Test
    @DisplayName("rejects a malformed frame without throwing at the transport")
    void rejectsMalformedFrame() throws Exception {
        MeshHarness.Node b = mesh.addNode("B");
        mesh.trustAll().startAll();

        RoutingOutcome outcome = b.router.receive("rqn1_" + "a".repeat(64),
                new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 });

        assertThat(outcome).isEqualTo(RoutingOutcome.DROPPED_MALFORMED);
        assertThat(b.router.metrics().malformedDropCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("throttles a peer flooding with distinct messages from one origin")
    void throttlesFlooding() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        for (int i = 0; i < 400; i++) {
            a.router.originate(b.id(), MessageType.TEXT, Priority.LOW,
                    body("flood " + i), 0L);
        }
        mesh.settle();

        // The throttle binds at the injection point too, which is where the
        // damage actually happens: a device that composes 400 messages must not
        // be able to spend every neighbour's battery on all of them.
        assertThat(a.router.metrics().rateLimitDropCount()).isGreaterThan(0);
        assertThat(b.router.metrics().acceptedCount())
                .as("the per-origin forwarding allowance must bound inbound work")
                .isLessThan(400);
    }

    @Test
    @DisplayName("never throttles life-safety traffic from the same peer")
    void doesNotThrottleLifeSafety() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        for (int i = 0; i < 60; i++) {
            a.router.originate(b.id(), MessageType.SOS, Priority.SOS, body("SOS " + i), 0L);
        }
        mesh.settle();

        assertThat(b.router.metrics().acceptedCount())
                .as("suppressing SOS traffic is strictly worse than extra work")
                .isEqualTo(60);
        assertThat(b.router.metrics().rateLimitDropCount()).isZero();
    }

    @Test
    @DisplayName("a signature only verifies for the key it was made with")
    void signatureIsBoundToKey() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        InMemorySigningIdentity other = InMemorySigningIdentity.generate("Other");
        MessageEnvelope envelope = a.router.originate(null, MessageType.TEXT, Priority.LOW,
                body("content"), 0L);

        assertThat(SignatureVerifier.verifyEnvelope(other, envelope)).isFalse();
        assertThat(SignatureVerifier.verifyEnvelope(a.identity, envelope)).isTrue();
    }

    @Test
    @DisplayName("device identity is derived from the key, not from a radio handle")
    void identityIsKeyDerived() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node restarted = mesh.addRestartedNode(a);

        assertThat(restarted.id()).isEqualTo(a.id());
        assertThat(restarted.id()).startsWith("rqn1_").hasSize(69);
        assertThat(a.id())
                .as("a Nearby endpoint id is a bare 16 hex digits; identity must not look like one")
                .doesNotMatch("^[0-9a-f]{16}$");
    }

    @Test
    @DisplayName("an unsigned identity claim cannot deliver anything else")
    void unsignedIdentityClaimGrantsNothing() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        java.util.List<IdentityAnnouncement> seen = new java.util.ArrayList<>();
        b.router.setIdentityAnnouncementListener((envelope, announcement, from) ->
                seen.add(announcement));

        b.trustStore.forget(a.id());
        a.router.originate(null, MessageType.IDENTITY, Priority.LOW,
                IdentityAnnouncement.of(a.identity).encode(), 0L);
        mesh.settle();

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).label()).isEqualTo("A");
        assertThat(b.trustStore.entry(a.id()))
                .as("a key claim is not trust; a human must confirm it")
                .isEmpty();
    }

    @Test
    @DisplayName("accepting a key unlocks that origin and records the fingerprint")
    void acceptingKeyUnlocksOrigin() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();
        b.trustStore.forget(a.id());

        b.router.acceptPeerKey("A", a.identity.publicKey());
        a.router.originate(b.id(), MessageType.TEXT, Priority.NORMAL, body("now you know me"), 0L);
        mesh.settle();

        assertThat(b.inbox).hasSize(1);
        assertThat(b.trustStore.entry(a.id()).orElseThrow().trust())
                .isEqualTo(dev.resqnet.mesh.crypto.TrustStore.Trust.UNVERIFIED);
    }

    @Test
    @DisplayName("drop reasons are reported as structured events, not only logs")
    void dropReasonsAreObservable() throws Exception {
        MeshHarness.Node a = mesh.addNode("A");
        MeshHarness.Node b = mesh.addNode("B");
        mesh.link(a, b);
        mesh.trustAll().startAll();

        b.trustStore.block(a.id());
        a.router.originate(b.id(), MessageType.TEXT, Priority.LOW, body("blocked"), 0L);
        mesh.settle();

        assertThat(b.events)
                .filteredOn(e -> e instanceof MeshEvent.Rejected rejected
                        && rejected.reason() == RejectReason.ORIGIN_BLOCKED)
                .hasSize(1);
        // The radio handover genuinely succeeded, so the sender is told exactly
        // that much and nothing more. SENT_TO_PEER is not a delivery claim.
        assertThat(a.store.find(a.store.pending().get(0).messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.SENT_TO_PEER);
        assertThat(a.router.metrics().acknowledgementReceivedCount()).isZero();
    }
}