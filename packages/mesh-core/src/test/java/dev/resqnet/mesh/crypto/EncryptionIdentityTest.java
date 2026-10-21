package dev.resqnet.mesh.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.protocol.ProtectedPayload;

@DisplayName("EncryptionIdentity")
class EncryptionIdentityTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("generates a 32 byte X25519 key and a stable SHA-256 key id")
    void generatesKeys() throws Exception {
        EncryptionIdentity identity = EncryptionIdentity.generate();

        assertThat(identity.publicKeyBytes()).hasSize(EncryptionIdentity.PUBLIC_KEY_BYTES);
        assertThat(identity.keyId()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(EncryptionIdentity.keyId(identity.publicKeyBytes()))
                .isEqualTo(identity.keyId());
        assertThat(EncryptionIdentity.keyIdBytes(identity.publicKeyBytes()))
                .hasSize(EncryptionIdentity.KEY_ID_BYTES);
    }

    @Test
    @DisplayName("restores the same public key from a serialized private keyset")
    void restoresKeyset() throws Exception {
        EncryptionIdentity original = EncryptionIdentity.generate();

        EncryptionIdentity restored = EncryptionIdentity.fromPrivateKeyset(
                original.serializePrivateKeyset());

        assertThat(restored.keyId()).isEqualTo(original.keyId());
        assertThat(restored.publicKeyBytes()).isEqualTo(original.publicKeyBytes());
    }

    @Test
    @DisplayName("encrypts to a recipient and authenticates the associated data")
    void roundTripsCiphertext() throws Exception {
        EncryptionIdentity alice = EncryptionIdentity.generate();
        EncryptionIdentity bob = EncryptionIdentity.generate();
        byte[] plaintext = bytes("responder at grid 41, two casualties");
        byte[] aad = bytes("message-header-context");

        byte[] ciphertext = EncryptionIdentity.encryptTo(
                bob.publicKeyBytes(), plaintext, aad);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(bob.decrypt(ciphertext, aad)).isEqualTo(plaintext);
        assertThatThrownBy(() -> alice.decrypt(ciphertext, aad))
                .as("a different recipient key must not open the ciphertext")
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> bob.decrypt(ciphertext, bytes("different-context")))
                .as("the AEAD tag must cover the envelope header")
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("rejects a modified ciphertext")
    void rejectsTamperedCiphertext() throws Exception {
        EncryptionIdentity bob = EncryptionIdentity.generate();
        byte[] aad = bytes("context");
        byte[] ciphertext = EncryptionIdentity.encryptTo(
                bob.publicKeyBytes(), bytes("trusted content"), aad);
        ciphertext[ciphertext.length - 1] ^= 0x01;

        assertThatThrownBy(() -> bob.decrypt(ciphertext, aad))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("never emits the same ciphertext twice for the same input")
    void usesFreshEphemeralKeys() throws Exception {
        EncryptionIdentity bob = EncryptionIdentity.generate();
        EncryptionIdentity alice = EncryptionIdentity.generate();
        byte[] aad = bytes("context");

        byte[] first = EncryptionIdentity.encryptTo(
                bob.publicKeyBytes(), bytes("same message"), aad);
        byte[] second = EncryptionIdentity.encryptTo(
                bob.publicKeyBytes(), bytes("same message"), aad);

        assertThat(first).isNotEqualTo(second);
        assertThat(bob.decrypt(first, aad)).isEqualTo(bytes("same message"));
        assertThat(bob.decrypt(second, aad)).isEqualTo(bytes("same message"));
        assertThatThrownBy(() -> alice.decrypt(first, aad))
                .as("the sender's own key is not the recipient")
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("rejects wrong key sizes, empty plaintext and oversized plaintext")
    void enforcesBounds() throws Exception {
        EncryptionIdentity bob = EncryptionIdentity.generate();
        byte[] aad = bytes("context");

        assertThatThrownBy(() -> EncryptionIdentity.encryptTo(
                new byte[31], bytes("x"), aad))
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> EncryptionIdentity.encryptTo(
                bob.publicKeyBytes(), new byte[0], aad))
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> EncryptionIdentity.encryptTo(
                bob.publicKeyBytes(),
                new byte[ProtectedPayload.MAX_PLAINTEXT_BYTES + 1], aad))
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> EncryptionIdentity.fromPrivateKeyset(new byte[0]))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("ciphertext fits inside the mesh payload ceiling")
    void fitsEnvelope() throws Exception {
        EncryptionIdentity bob = EncryptionIdentity.generate();
        byte[] plaintext = new byte[ProtectedPayload.MAX_PLAINTEXT_BYTES];
        Arrays.fill(plaintext, (byte) 'x');

        byte[] ciphertext = EncryptionIdentity.encryptTo(
                bob.publicKeyBytes(), plaintext, bytes("context"));

        assertThat(ciphertext.length).isLessThanOrEqualTo(
                ProtectedPayload.MAX_CIPHERTEXT_BYTES);
        assertThat(ProtectedPayload.HEADER_BYTES + ciphertext.length)
                .isLessThanOrEqualTo(dev.resqnet.mesh.protocol.MessageEnvelope.MAX_PAYLOAD_BYTES);
    }
}
