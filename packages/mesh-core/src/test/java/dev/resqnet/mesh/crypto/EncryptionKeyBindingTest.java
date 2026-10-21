package dev.resqnet.mesh.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.routing.IdentityAnnouncement;

@DisplayName("EncryptionKeyBinding")
class EncryptionKeyBindingTest {

    @Test
    @DisplayName("proof verifies only for the signing identity that produced it")
    void proofBindsKeys() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        InMemorySigningIdentity bob = InMemorySigningIdentity.generate("Bob");
        EncryptionIdentity encryption = EncryptionIdentity.generate();

        byte[] proof = EncryptionKeyBinding.prove(alice, encryption.publicKeyBytes());

        assertThat(EncryptionKeyBinding.verify(alice.publicKey(),
                encryption.publicKeyBytes(), proof)).isTrue();
        assertThat(EncryptionKeyBinding.verify(bob.publicKey(),
                encryption.publicKeyBytes(), proof)).isFalse();
    }

    @Test
    @DisplayName("a substituted encryption key invalidates the proof")
    void substitutedKeyFails() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity real = EncryptionIdentity.generate();
        EncryptionIdentity attacker = EncryptionIdentity.generate();
        byte[] proof = EncryptionKeyBinding.prove(alice, real.publicKeyBytes());

        assertThat(EncryptionKeyBinding.verify(alice.publicKey(),
                attacker.publicKeyBytes(), proof)).isFalse();
        byte[] tampered = proof.clone();
        tampered[0] ^= 0x01;
        assertThat(EncryptionKeyBinding.verify(alice.publicKey(),
                real.publicKeyBytes(), tampered)).isFalse();
    }

    @Test
    @DisplayName("a proof cannot be moved to another signing identity")
    void domainSeparatesIdentity() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        InMemorySigningIdentity bob = InMemorySigningIdentity.generate("Bob");
        EncryptionIdentity encryption = EncryptionIdentity.generate();
        byte[] aliceProof = EncryptionKeyBinding.prove(alice,
                encryption.publicKeyBytes());

        InMemorySigningIdentity other = InMemorySigningIdentity.fromKeyPair("Alice copy",
                bob.privateKey().getEncoded(), bob.publicKey().getEncoded());
        assertThat(EncryptionKeyBinding.verify(other.publicKey(),
                encryption.publicKeyBytes(), aliceProof)).isFalse();
    }

    @Test
    @DisplayName("an identity announcement round-trips its proof-bound HPKE key")
    void announcementCarriesBinding() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity encryption = EncryptionIdentity.generate();

        IdentityAnnouncement decoded = IdentityAnnouncement.decode(
                IdentityAnnouncement.of(alice, encryption).encode());

        assertThat(decoded.hasEncryptionKey()).isTrue();
        assertThat(decoded.encryptionPublicKeyBase64())
                .isEqualTo(Base64.getEncoder().encodeToString(encryption.publicKeyBytes()));
        byte[] announcedKey = Base64.getDecoder()
                .decode(decoded.encryptionPublicKeyBase64());
        byte[] proof = Base64.getDecoder().decode(decoded.encryptionKeyProofBase64());
        assertThat(EncryptionKeyBinding.verify(alice.publicKey(), announcedKey, proof))
                .isTrue();
    }

    @Test
    @DisplayName("an announcement without encryption fields remains valid")
    void oldAnnouncementsStillParse() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");

        IdentityAnnouncement decoded = IdentityAnnouncement.decode(
                IdentityAnnouncement.of(alice).encode());

        assertThat(decoded.hasEncryptionKey()).isFalse();
        assertThat(decoded.encryptionPublicKeyBase64()).isEmpty();
        assertThat(decoded.encryptionKeyProofBase64()).isEmpty();
    }

    @Test
    @DisplayName("a truncated or trailing encryption tail is rejected")
    void hostileTailRejected() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity encryption = EncryptionIdentity.generate();
        byte[] encoded = IdentityAnnouncement.of(alice, encryption).encode();

        for (int cut = 1; cut <= 6; cut++) {
            byte[] partial = Arrays.copyOf(encoded, encoded.length - cut);
            assertThatThrownBy(() -> IdentityAnnouncement.decode(partial))
                    .as("truncated by %d bytes", cut)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThatThrownBy(() -> IdentityAnnouncement.decode(trailing))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
