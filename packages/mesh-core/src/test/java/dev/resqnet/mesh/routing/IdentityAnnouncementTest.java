package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;

@DisplayName("IdentityAnnouncement")
class IdentityAnnouncementTest {

    @Test
    @DisplayName("round-trips a label, key and comparison code")
    void roundTrip() throws Exception {
        InMemorySigningIdentity identity = InMemorySigningIdentity.generate("Responder Two");
        IdentityAnnouncement announcement = IdentityAnnouncement.of(identity);

        byte[] encoded = announcement.encode();
        IdentityAnnouncement decoded = IdentityAnnouncement.decode(encoded);

        assertThat(decoded.label()).isEqualTo("Responder Two");
        assertThat(decoded.publicKeyBase64()).isEqualTo(identity.publicKeyBase64());
        assertThat(decoded.shortCode()).isEqualTo(announcement.shortCode());
        assertThat(IdentityAnnouncement.looksLikeIdentity(encoded)).isTrue();
    }

    @Test
    @DisplayName("produces an eight character code that two people can read aloud")
    void producesReadableCode() throws Exception {
        InMemorySigningIdentity identity = InMemorySigningIdentity.generate("Alice");

        String code = IdentityAnnouncement.of(identity).shortCode();

        assertThat(code.replace("-", "")).hasSize(IdentityAnnouncement.SHORT_CODE_LENGTH);
        assertThat(code).matches("[0-9a-f]{4}-[0-9a-f]{4}");
        assertThat(code.substring(0, 4))
                .isEqualTo(identity.keyFingerprint().substring(0, 4));
    }

    @Test
    @DisplayName("refuses a payload that is not an announcement")
    void rejectsForeignPayload() {
        byte[] notIdentity = "hello world".getBytes(StandardCharsets.UTF_8);

        assertThat(IdentityAnnouncement.looksLikeIdentity(notIdentity)).isFalse();
        assertThatThrownBy(() -> IdentityAnnouncement.decode(notIdentity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("magic");
    }

    @Test
    @DisplayName("refuses a truncated payload rather than reading past the end")
    void rejectsTruncatedPayload() throws Exception {
        byte[] encoded = IdentityAnnouncement.of(
                InMemorySigningIdentity.generate("Alice")).encode();

        for (int cut = 1; cut < encoded.length; cut++) {
            byte[] partial = Arrays.copyOf(encoded, encoded.length - cut);
            assertThatThrownBy(() -> IdentityAnnouncement.decode(partial))
                    .as("truncated by %d bytes", cut)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("refuses a length prefix that overstates the payload")
    void rejectsHostileLengthPrefix() throws Exception {
        byte[] encoded = IdentityAnnouncement.of(
                InMemorySigningIdentity.generate("Alice")).encode();
        // First int16 after the magic is the label length.
        encoded[4] = (byte) 0x7F;
        encoded[5] = (byte) 0xFF;

        assertThatThrownBy(() -> IdentityAnnouncement.decode(encoded))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refuses an oversized payload")
    void rejectsOversizedPayload() {
        assertThatThrownBy(() -> IdentityAnnouncement.decode(
                new byte[IdentityAnnouncement.MAX_PAYLOAD_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("handles a non-ASCII display name")
    void handlesUnicodeLabel() throws Exception {
        InMemorySigningIdentity identity = InMemorySigningIdentity.generate("Rédacteur");

        IdentityAnnouncement decoded =
                IdentityAnnouncement.decode(IdentityAnnouncement.of(identity).encode());

        assertThat(decoded.label()).isEqualTo("Rédacteur");
    }
}