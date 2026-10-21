package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.crypto.EncryptionIdentity;
import dev.resqnet.mesh.crypto.EncryptionKeyBinding;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.protocol.ProtectedPayload;
import dev.resqnet.mesh.protocol.ProtocolException;
import dev.resqnet.mesh.protocol.RejectReason;

@DisplayName("End-to-end encrypted routing")
class MeshE2eeTest {

    private MeshHarness mesh;
    private EncryptionIdentity aliceKey;
    private EncryptionIdentity bobKey;

    @BeforeEach
    void setUp() throws Exception {
        mesh = new MeshHarness(1_700_000_000L);
        aliceKey = EncryptionIdentity.generate();
        bobKey = EncryptionIdentity.generate();
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private void install(MeshHarness.Node node, EncryptionIdentity encryption) {
        node.router.setEncryptionIdentity(encryption);
    }

    /** Publishes the peer key material the router would learn from announcements. */
    private void learnKeys(MeshHarness.Node node, MeshHarness.Node peer,
                           EncryptionIdentity peerEncryption) throws Exception {
        node.trustStore.rememberUnverified(peer.identity.displayName(),
                peer.identity.publicKey(), Instant.EPOCH);
        node.trustStore.rememberEncryptionKey(peer.id(), peerEncryption.publicKeyBytes(),
                Instant.EPOCH);
        node.trustStore.markVerified(peer.id(), Instant.EPOCH);
    }

    private static byte[] associatedData(MessageEnvelope envelope) throws Exception {
        MessageEnvelope header = envelope.toBuilder().payload(new byte[0]).build();
        return ProtectedPayload.associatedData(header,
                ProtectedPayload.decode(envelope.payload()).keyId());
    }

    private static byte[] decrypt(MeshHarness.Node recipient,
                                  EncryptionIdentity encryption,
                                  MessageEnvelope envelope) throws Exception {
        ProtectedPayload protectedPayload = ProtectedPayload.decode(envelope.payload());
        assertThat(protectedPayload.keyId())
                .isEqualTo(EncryptionIdentity.keyIdBytes(encryption.publicKeyBytes()));
        return encryption.decrypt(protectedPayload.ciphertext(),
                associatedData(envelope));
    }

    @Test
    @DisplayName("a protected message reaches one verified peer and decrypts")
    void protectedMessageRoundTrip() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        install(bob, bobKey);
        mesh.link(alice, bob);
        mesh.trustAll().startAll();
        learnKeys(alice, bob, bobKey);
        learnKeys(bob, alice, aliceKey);

        MessageEnvelope sent = alice.router.originateProtected(bob.id(),
                MessageType.TEXT, Priority.NORMAL, text("meet at the north gate"), 0L);
        mesh.settle();

        assertThat(sent.type()).isEqualTo(MessageType.TEXT_PROTECTED);
        assertThat(sent.destinationDeviceId()).contains(bob.id());
        assertThat(bob.inbox).hasSize(1);
        MessageEnvelope received = bob.inbox.get(0);
        assertThat(decrypt(bob, bobKey, received)).isEqualTo(text("meet at the north gate"));
        assertThat(new String(received.payload(), StandardCharsets.ISO_8859_1))
                .doesNotContain("north gate");
        assertThat(bob.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.DELIVERED);
    }

    @Test
    @DisplayName("a relay can forward ciphertext without being able to read it")
    void relayCannotRead() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node relay = mesh.addNode("Relay");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        install(relay, EncryptionIdentity.generate());
        install(bob, bobKey);
        mesh.link(alice, relay);
        mesh.link(relay, bob);
        mesh.trustAll().startAll();
        learnKeys(alice, bob, bobKey);
        learnKeys(relay, alice, aliceKey);
        learnKeys(bob, alice, aliceKey);

        MessageEnvelope sent = alice.router.originateProtected(bob.id(),
                MessageType.SOS, Priority.SOS, text("cliff fall, need rope"), 0L);
        mesh.settle();

        assertThat(bob.inbox).hasSize(1);
        assertThat(relay.inbox).isEmpty();
        StoredMessage relayCopy = relay.store.find(sent.messageId()).orElseThrow();
        assertThat(relayCopy.state()).isEqualTo(DeliveryState.RELAYED);
        MessageEnvelope relayed = EnvelopeCodec.decode(relayCopy.envelope());
        assertThat(ProtectedPayload.looksLikeProtected(relayed.payload())).isTrue();
        assertThat(new String(relayed.payload(), StandardCharsets.ISO_8859_1))
                .doesNotContain("cliff fall");
        assertThat(decrypt(bob, bobKey, bob.inbox.get(0)))
                .isEqualTo(text("cliff fall, need rope"));
        assertThat(relay.router.metrics().acceptedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("store-and-forward keeps ciphertext until the recipient appears")
    void delayedDeliveryKeepsCiphertext() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        install(bob, bobKey);
        mesh.trustAll().startAll();
        learnKeys(alice, bob, bobKey);
        learnKeys(bob, alice, aliceKey);

        MessageEnvelope sent = alice.router.originateProtected(bob.id(),
                MessageType.TEXT, Priority.NORMAL, text("waiting for you"), 0L);
        alice.router.attemptPending();
        assertThat(bob.inbox).isEmpty();
        assertThat(alice.store.pending()).hasSize(1);
        assertThat(alice.store.find(sent.messageId()).orElseThrow().envelope())
                .isEqualTo(EnvelopeCodec.encode(sent));

        mesh.link(alice, bob);
        mesh.advance(2_000L);

        assertThat(bob.inbox).hasSize(1);
        assertThat(decrypt(bob, bobKey, bob.inbox.get(0))).isEqualTo(text("waiting for you"));
    }

    @Test
    @DisplayName("refuses to send to an unverified peer rather than falling back to plaintext")
    void refusesUnverifiedRecipient() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        install(bob, bobKey);
        mesh.link(alice, bob);
        mesh.trustAll().startAll();
        alice.trustStore.rememberUnverified("Bob", bob.identity.publicKey(), Instant.EPOCH);
        alice.trustStore.rememberEncryptionKey(bob.id(), bobKey.publicKeyBytes(),
                Instant.EPOCH);

        assertThatThrownBy(() -> alice.router.originateProtected(bob.id(),
                MessageType.SOS, Priority.SOS, text("help"), 0L))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.ENCRYPTION_UNAVAILABLE));

        mesh.settle();
        assertThat(alice.store.all()).isEmpty();
        assertThat(bob.inbox).isEmpty();
    }

    @Test
    @DisplayName("refuses when the recipient has published no encryption key")
    void refusesMissingEncryptionKey() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        mesh.link(alice, bob);
        mesh.trustAll().startAll();

        assertThatThrownBy(() -> alice.router.originateProtected(bob.id(),
                MessageType.TEXT, Priority.NORMAL, text("hello"), 0L))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.ENCRYPTION_UNAVAILABLE));
        assertThat(alice.store.all()).isEmpty();
    }

    @Test
    @DisplayName("refuses to encrypt when this device has no HPKE identity")
    void refusesWithoutLocalEncryption() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        mesh.trustAll().startAll();
        learnKeys(alice, bob, bobKey);

        assertThatThrownBy(() -> alice.router.originateProtected(bob.id(),
                MessageType.TEXT, Priority.NORMAL, text("hello"), 0L))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.ENCRYPTION_UNAVAILABLE));
    }

    @Test
    @DisplayName("an edited header makes the ciphertext fail authentication")
    void editedHeaderFailsAuthentication() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        install(bob, bobKey);
        mesh.link(alice, bob);
        mesh.trustAll().startAll();
        learnKeys(alice, bob, bobKey);
        learnKeys(bob, alice, aliceKey);

        MessageEnvelope sent = alice.router.originateProtected(bob.id(),
                MessageType.TEXT, Priority.NORMAL, text("trusted"), 0L);
        MessageEnvelope edited = sent.toBuilder()
                .createdAt(sent.createdAtEpochMillis() + 1)
                .build();

        assertThatThrownBy(() -> decrypt(bob, bobKey, edited))
                .isInstanceOf(java.security.GeneralSecurityException.class);
    }

    @Test
    @DisplayName("an encrypted envelope is still acknowledged end to end")
    void protectedMessagesAreAcknowledged() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        install(bob, bobKey);
        mesh.link(alice, bob);
        mesh.trustAll().startAll();
        learnKeys(alice, bob, bobKey);
        learnKeys(bob, alice, aliceKey);

        MessageEnvelope sent = alice.router.originateProtected(bob.id(),
                MessageType.TEXT, Priority.NORMAL, text("received?"), 0L);
        mesh.settle();

        assertThat(alice.store.find(sent.messageId()).orElseThrow().state())
                .isEqualTo(DeliveryState.ACKNOWLEDGED);
        assertThat(bob.inbox).hasSize(1);
    }

    @Test
    @DisplayName("public broadcast SOS stays cleartext and is labelled as such")
    void publicBroadcastRemainsCleartext() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        MeshHarness.Node bob = mesh.addNode("Bob");
        install(alice, aliceKey);
        mesh.link(alice, bob);
        mesh.trustAll().startAll();

        MessageEnvelope sent = alice.router.originateBroadcast(MessageType.SOS,
                Priority.SOS, text("public alert"));
        mesh.settle();

        assertThat(sent.type()).isEqualTo(MessageType.SOS);
        assertThat(sent.broadcast()).isTrue();
        assertThat(sent.payload()).isEqualTo(text("public alert"));
        assertThat(bob.inbox).hasSize(1);
    }

    @Test
    @DisplayName("identity claims carry a proof-bound key that the peer can verify")
    void identityClaimBindsEncryptionKey() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        install(alice, aliceKey);

        MessageEnvelope claim = alice.router.publishIdentity().orElseThrow();
        IdentityAnnouncement announcement = IdentityAnnouncement.decode(claim.payload());
        byte[] announcedKey = Base64.getDecoder()
                .decode(announcement.encryptionPublicKeyBase64());
        byte[] proof = Base64.getDecoder()
                .decode(announcement.encryptionKeyProofBase64());

        assertThat(announcement.hasEncryptionKey()).isTrue();
        assertThat(announcedKey).isEqualTo(aliceKey.publicKeyBytes());
        assertThat(EncryptionKeyBinding.verify(alice.identity.publicKey(), announcedKey, proof))
                .isTrue();
    }

    @Test
    @DisplayName("a protected broadcast is structurally rejected at the validator boundary")
    void protectedBroadcastRejected() throws Exception {
        MeshHarness.Node alice = mesh.addNode("Alice");
        install(alice, aliceKey);
        MessageEnvelope forged = MessageEnvelope.builder()
                .messageId("m1")
                .origin(alice.id())
                .originKeyFingerprint(alice.identity.keyFingerprint())
                .broadcast()
                .type(MessageType.SOS_PROTECTED)
                .priority(Priority.SOS)
                .createdAt(1_700_000_000_000L)
                .expiresAt(1_700_000_001_000L)
                .hopLimit(6)
                .payload(ProtectedPayload.of(new byte[32], new byte[48]).encode())
                .build();
        MessageEnvelope signed = alice.router.sign(forged);

        assertThatThrownBy(() -> new dev.resqnet.mesh.protocol.EnvelopeValidator()
                .validate(signed, 1_700_000_000_000L))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.MALFORMED));
    }
}
