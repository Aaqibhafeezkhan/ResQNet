package dev.resqnet.mesh.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnvelopeValidator")
class EnvelopeValidatorTest {

    private static final String ORIGIN = "rqn1_" + "a".repeat(64);
    private static final String DESTINATION = "rqn1_" + "b".repeat(64);
    private static final String FINGERPRINT = "c".repeat(64);
    private static final long NOW = 1_700_000_000_000L;

    private final EnvelopeValidator validator = new EnvelopeValidator();

    private static MessageEnvelope.Builder valid() {
        return MessageEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .origin(ORIGIN)
                .originKeyFingerprint(FINGERPRINT)
                .destination(DESTINATION)
                .type(MessageType.SOS)
                .priority(Priority.SOS)
                .createdAt(NOW)
                .expiresAt(NOW + 60_000L)
                .payload("help".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("accepts a well-formed signed envelope")
    void acceptsValid() throws Exception {
        assertThatCode(() -> validator.validate(valid().buildSigned(new byte[] { 1 }, "x"), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects an unsigned envelope under the default policy")
    void rejectsUnsigned() throws Exception {
        MessageEnvelope unsigned = MessageEnvelope.builder()
                .messageId("m").origin(ORIGIN).destination(DESTINATION)
                .createdAt(NOW).expiresAt(NOW + 1000)
                .build();

        assertThatThrownBy(() -> validator.validate(unsigned, NOW))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.INVALID_SIGNATURE));
    }

    @Test
    @DisplayName("accepts an unsigned envelope only when the policy allows it")
    void allowsUnsignedWhenConfigured() throws Exception {
        EnvelopeValidator lenient = new EnvelopeValidator(false, 60_000L, 8);
        MessageEnvelope unsigned = MessageEnvelope.builder()
                .messageId("m").origin(ORIGIN).destination(DESTINATION)
                .createdAt(NOW).expiresAt(NOW + 1000).build();

        assertThatCode(() -> lenient.validate(unsigned, NOW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a creation timestamp far in the future")
    void rejectsFutureTimestamp() throws Exception {
        MessageEnvelope skewed = valid().createdAt(NOW + 30 * 60 * 1000L)
                .expiresAt(NOW + 40 * 60 * 1000L)
                .buildSigned(new byte[] { 9 }, "SHA256withECDSA");

        assertThatThrownBy(() -> validator.validate(skewed, NOW))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("rejects an expiry that precedes creation")
    void rejectsInvertedTimestamps() throws Exception {
        MessageEnvelope inverted = valid().createdAt(NOW).expiresAt(NOW - 1)
                .buildSigned(new byte[] { 9 }, "SHA256withECDSA");

        assertThatThrownBy(() -> validator.validate(inverted, NOW))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.MALFORMED));
    }

    @Test
    @DisplayName("rejects a lifetime beyond the local ceiling")
    void rejectsExcessiveLifetime() throws Exception {
        EnvelopeValidator strict = new EnvelopeValidator(true, 60_000L, 8);
        MessageEnvelope greedy = valid().expiresAt(NOW + 86_400_000L)
                .buildSigned(new byte[] { 9 }, "SHA256withECDSA");

        assertThatThrownBy(() -> strict.validate(greedy, NOW))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("exceeds local maximum");
    }

    @Test
    @DisplayName("rejects a message addressed to its own origin")
    void rejectsSelfAddressed() throws Exception {
        MessageEnvelope selfish = valid().destination(ORIGIN)
                .buildSigned(new byte[] { 9 }, "SHA256withECDSA");

        assertThatThrownBy(() -> validator.validate(selfish, NOW))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("Destination equals origin");
    }

    @Test
    @DisplayName("rejects a hop limit beyond the local ceiling")
    void rejectsExcessiveHopLimit() throws Exception {
        EnvelopeValidator strict = new EnvelopeValidator(true, 600_000L, 4);
        MessageEnvelope greedy = valid().hopLimit(31)
                .buildSigned(new byte[] { 9 }, "SHA256withECDSA");

        assertThatThrownBy(() -> strict.validate(greedy, NOW))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.HOP_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("rejects an ACK with nothing to acknowledge")
    void rejectsBareAck() throws Exception {
        MessageEnvelope ack = MessageEnvelope.builder()
                .messageId("ack-1").origin(ORIGIN).originKeyFingerprint(FINGERPRINT).destination(DESTINATION)
                .type(MessageType.ACK).createdAt(NOW).expiresAt(NOW + 1000)
                .buildSigned(new byte[] { 5 }, "SHA256withECDSA");

        assertThatThrownBy(() -> validator.validate(ack, NOW))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("must reference the message");
    }

    @Test
    @DisplayName("rejects an ACK that references itself")
    void rejectsSelfReferentialAck() throws Exception {
        MessageEnvelope ack = MessageEnvelope.builder()
                .messageId("ack-1").origin(ORIGIN).originKeyFingerprint(FINGERPRINT).destination(DESTINATION)
                .type(MessageType.ACK).correlationId("ack-1")
                .createdAt(NOW).expiresAt(NOW + 1000)
                .buildSigned(new byte[] { 5 }, "SHA256withECDSA");

        assertThatThrownBy(() -> validator.validate(ack, NOW))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("cannot reference itself");
    }

    @Test
    @DisplayName("rejects a non-hexadecimal key fingerprint")
    void rejectsMalformedFingerprint() throws Exception {
        MessageEnvelope bad = valid().originKeyFingerprint("Z".repeat(64))
                .buildSigned(new byte[] { 9 }, "SHA256withECDSA");

        assertThatThrownBy(() -> validator.validate(bad, NOW))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.UNTRUSTED_ORIGIN));
    }

    @Test
    @DisplayName("rejects an empty identifier")
    void rejectsBlankIdentifier() throws Exception {
        MessageEnvelope bad = valid().messageId("  ")
                .buildSigned(new byte[] { 9 }, "SHA256withECDSA");

        assertThatThrownBy(() -> validator.validate(bad, NOW))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("messageId is required");
    }

    @Test
    @DisplayName("rejects a null envelope")
    void rejectsNull() {
        assertThatThrownBy(() -> validator.validate(null, NOW))
                .isInstanceOf(ProtocolException.class);
    }
}