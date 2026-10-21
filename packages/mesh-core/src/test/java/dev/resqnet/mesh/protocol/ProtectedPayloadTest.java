package dev.resqnet.mesh.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProtectedPayload")
class ProtectedPayloadTest {

    private static byte[] keyId() {
        return new byte[ProtectedPayload.KEY_ID_BYTES];
    }

    private static MessageEnvelope header() throws Exception {
        return header("message-1", "rqn1_alice", "rqn1_bob", MessageType.TEXT_PROTECTED,
                Priority.NORMAL, 1_000L, 2_000L, null);
    }

    private static MessageEnvelope header(String messageId, String origin, String destination,
                                          MessageType type, Priority priority,
                                          long createdAt, long expiresAt,
                                          String correlationId) throws Exception {
        return MessageEnvelope.builder()
                .messageId(messageId)
                .origin(origin)
                .originKeyFingerprint("a".repeat(64))
                .destination(destination)
                .type(type)
                .priority(priority)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .correlationId(correlationId)
                .payload(new byte[0])
                .build();
    }

    @Test
    @DisplayName("round-trips a key id and ciphertext")
    void roundTrips() throws Exception {
        byte[] keyId = keyId();
        byte[] ciphertext = "sealed bytes".getBytes(StandardCharsets.UTF_8);

        ProtectedPayload decoded = ProtectedPayload.decode(
                ProtectedPayload.of(keyId, ciphertext).encode());

        assertThat(decoded.keyId()).isEqualTo(keyId);
        assertThat(decoded.ciphertext()).isEqualTo(ciphertext);
    }

    @Test
    @DisplayName("recognises its own magic without accepting arbitrary bytes")
    void magic() throws Exception {
        byte[] encoded = ProtectedPayload.of(keyId(), new byte[] { 1 }).encode();

        assertThat(ProtectedPayload.looksLikeProtected(encoded)).isTrue();
        assertThat(ProtectedPayload.looksLikeProtected(
                "hello".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    @DisplayName("rejects a short, empty or oversized container")
    void rejectsBadSizes() {
        assertThatThrownBy(() -> ProtectedPayload.decode(new byte[4]))
                .isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> ProtectedPayload.of(keyId(), new byte[0]))
                .isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> ProtectedPayload.of(keyId(),
                new byte[ProtectedPayload.MAX_CIPHERTEXT_BYTES + 1]))
                .isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> ProtectedPayload.of(new byte[31], new byte[1]))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @DisplayName("rejects a length prefix that does not match the bytes")
    void rejectsHostileLength() throws Exception {
        byte[] encoded = ProtectedPayload.of(keyId(), new byte[16]).encode();
        encoded[encoded.length - 20] = 0x7F;

        assertThatThrownBy(() -> ProtectedPayload.decode(encoded))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @DisplayName("rejects an unsupported version or suite")
    void rejectsUnsupportedAlgorithms() throws Exception {
        byte[] version = ProtectedPayload.of(keyId(), new byte[] { 1 }).encode();
        version[4] = 99;
        assertThatThrownBy(() -> ProtectedPayload.decode(version))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("version");

        byte[] suite = ProtectedPayload.of(keyId(), new byte[] { 1 }).encode();
        suite[5] = 99;
        assertThatThrownBy(() -> ProtectedPayload.decode(suite))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("suite");
    }

    @Test
    @DisplayName("binds every routable header field and the recipient key into the AAD")
    void associatedDataBindsHeader() throws Exception {
        byte[] keyId = keyId();
        byte[] base = ProtectedPayload.associatedData(header(), keyId);

        assertThat(ProtectedPayload.associatedData(header(), keyId))
                .isEqualTo(base);
        assertThat(ProtectedPayload.associatedData(
                header("message-2", "rqn1_alice", "rqn1_bob", MessageType.TEXT_PROTECTED,
                        Priority.NORMAL, 1_000L, 2_000L, null), keyId))
                .isNotEqualTo(base);
        assertThat(ProtectedPayload.associatedData(
                header("message-1", "rqn1_alice", "rqn1_carol", MessageType.TEXT_PROTECTED,
                        Priority.NORMAL, 1_000L, 2_000L, null), keyId))
                .isNotEqualTo(base);
        assertThat(ProtectedPayload.associatedData(
                header("message-1", "rqn1_alice", "rqn1_bob", MessageType.SOS_PROTECTED,
                        Priority.NORMAL, 1_000L, 2_000L, null), keyId))
                .isNotEqualTo(base);
        assertThat(ProtectedPayload.associatedData(
                header("message-1", "rqn1_alice", "rqn1_bob", MessageType.TEXT_PROTECTED,
                        Priority.SOS, 1_000L, 2_000L, null), keyId))
                .isNotEqualTo(base);
        assertThat(ProtectedPayload.associatedData(
                header("message-1", "rqn1_alice", "rqn1_bob", MessageType.TEXT_PROTECTED,
                        Priority.NORMAL, 1_001L, 2_000L, null), keyId))
                .isNotEqualTo(base);
        assertThat(ProtectedPayload.associatedData(
                header("message-1", "rqn1_alice", "rqn1_bob", MessageType.TEXT_PROTECTED,
                        Priority.NORMAL, 1_000L, 2_001L, null), keyId))
                .isNotEqualTo(base);
        assertThat(ProtectedPayload.associatedData(
                header("message-1", "rqn1_alice", "rqn1_bob", MessageType.TEXT_PROTECTED,
                        Priority.NORMAL, 1_000L, 2_000L, "message-0"), keyId))
                .isNotEqualTo(base);
        byte[] otherKeyId = keyId();
        otherKeyId[0] = 1;
        assertThat(ProtectedPayload.associatedData(header(), otherKeyId))
                .isNotEqualTo(base);
    }

    @Test
    @DisplayName("maps only content types to protected variants")
    void mapsTypes() {
        assertThat(MessageType.SOS.protectedVariant()).isEqualTo(MessageType.SOS_PROTECTED);
        assertThat(MessageType.TEXT.protectedVariant()).isEqualTo(MessageType.TEXT_PROTECTED);
        assertThat(MessageType.SOS_PROTECTED.cleartextVariant()).isEqualTo(MessageType.SOS);
        assertThat(MessageType.SOS_PROTECTED.protectedContent()).isTrue();
        assertThat(MessageType.TEXT.protectedContent()).isFalse();
        assertThatThrownBy(() -> MessageType.ACK.protectedVariant())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects wrong key-id lengths and does not expose mutable arrays")
    void defensiveCopies() throws Exception {
        byte[] keyId = keyId();
        byte[] ciphertext = new byte[] { 1, 2, 3 };
        ProtectedPayload payload = ProtectedPayload.of(keyId, ciphertext);
        keyId[0] = 9;
        ciphertext[0] = 9;

        assertThat(payload.keyId()).isEqualTo(new byte[ProtectedPayload.KEY_ID_BYTES]);
        assertThat(payload.ciphertext()).isEqualTo(new byte[] { 1, 2, 3 });
        assertThatThrownBy(() -> ProtectedPayload.associatedData(header(), new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("uses distinct wire ids so older clients refuse instead of rendering")
    void stableWireIds() throws Exception {
        assertThat(MessageType.SOS_PROTECTED.wireId()).isEqualTo(11);
        assertThat(MessageType.TEXT_PROTECTED.wireId()).isEqualTo(21);
        assertThat(MessageType.fromWireId(11)).isEqualTo(MessageType.SOS_PROTECTED);
        assertThat(MessageType.fromWireId(21)).isEqualTo(MessageType.TEXT_PROTECTED);
        assertThat(Arrays.stream(MessageType.values()).map(MessageType::wireId).distinct().count())
                .isEqualTo(MessageType.values().length);
    }
}
