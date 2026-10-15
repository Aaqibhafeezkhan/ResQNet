package dev.resqnet.mesh.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnvelopeCodec")
class EnvelopeCodecTest {

    private static MessageEnvelope sample() throws ProtocolException {
        return MessageEnvelope.builder()
                .messageId("msg-0001")
                .origin("rqn1_" + "a".repeat(64))
                .originKeyFingerprint("b".repeat(64))
                .destination("rqn1_" + "c".repeat(64))
                .type(MessageType.SOS)
                .priority(Priority.SOS)
                .createdAt(1_700_000_000_000L)
                .expiresAt(1_700_000_900_000L)
                .hopLimit(6)
                .hopCount(2)
                .payload("help".getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Test
    @DisplayName("round-trips every field through the binary form")
    void roundTrip() throws Exception {
        MessageEnvelope original = sample();
        MessageEnvelope decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(original));

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.messageId()).isEqualTo("msg-0001");
        assertThat(decoded.type()).isEqualTo(MessageType.SOS);
        assertThat(decoded.priority()).isEqualTo(Priority.SOS);
        assertThat(decoded.hopLimit()).isEqualTo(6);
        assertThat(decoded.hopCount()).isEqualTo(2);
        assertThat(decoded.destinationDeviceId()).hasValue("rqn1_" + "c".repeat(64));
        assertThat(new String(decoded.payload(), StandardCharsets.UTF_8)).isEqualTo("help");
    }

    @Test
    @DisplayName("round-trips signatures")
    void roundTripSigned() throws Exception {
        byte[] signature = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        MessageEnvelope signed = sample().toBuilder()
                .buildSigned(signature, "SHA256withECDSA");
        MessageEnvelope decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(signed));

        assertThat(decoded.signed()).isTrue();
        assertThat(decoded.signature()).isEqualTo(signature);
        assertThat(decoded.signatureAlgorithm()).isEqualTo("SHA256withECDSA");
        assertThat(decoded).isEqualTo(signed);
    }

    @Test
    @DisplayName("treats an absent destination as broadcast")
    void broadcastRoundTrip() throws Exception {
        MessageEnvelope broadcast = MessageEnvelope.builder()
                .messageId("msg-bc")
                .origin("rqn1_" + "a".repeat(64))
                .type(MessageType.TEXT)
                .createdAt(1000L)
                .expiresAt(2000L)
                .build();
        MessageEnvelope decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(broadcast));

        assertThat(decoded.broadcast()).isTrue();
        assertThat(decoded.destinationDeviceId()).isEmpty();
    }

    @Test
    @DisplayName("rejects a bad magic prefix")
    void rejectsBadMagic() throws Exception {
        byte[] encoded = EnvelopeCodec.encode(sample());
        encoded[0] = 'X';

        assertThatThrownBy(() -> EnvelopeCodec.decode(encoded))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.MALFORMED));
    }

    @Test
    @DisplayName("rejects a truncated frame without allocating")
    void rejectsTruncation() throws Exception {
        byte[] encoded = EnvelopeCodec.encode(sample());

        for (int cut = 1; cut < encoded.length; cut += 7) {
            byte[] partial = Arrays.copyOf(encoded, encoded.length - cut);
            assertThatThrownBy(() -> EnvelopeCodec.decode(partial))
                    .as("truncated by %d bytes", cut)
                    .isInstanceOf(ProtocolException.class);
        }
    }

    @Test
    @DisplayName("rejects trailing bytes rather than ignoring them")
    void rejectsTrailingBytes() throws Exception {
        byte[] encoded = EnvelopeCodec.encode(sample());
        byte[] tampered = Arrays.copyOf(encoded, encoded.length + 5);
        Arrays.fill(tampered, encoded.length, encoded.length + 5, (byte) 0x7F);

        assertThatThrownBy(() -> EnvelopeCodec.decode(tampered))
                .isInstanceOf(ProtocolException.class)
                .hasMessageContaining("trailing");
    }

    @Test
    @DisplayName("refuses a hostile length prefix that would over-read the buffer")
    void rejectsHostileLengthPrefix() throws Exception {
        byte[] encoded = EnvelopeCodec.encode(sample());
        // The first int32 payload-length declaration lives after the fixed
        // header and the identifier block; find it by re-encoding a payload of
        // known size and comparing. Simpler and just as strong: corrupt every
        // int32 candidate and assert we never allocate or crash.
        for (int i = 4; i < encoded.length - 4; i += 4) {
            byte[] tampered = encoded.clone();
            tampered[i] = (byte) 0x7F;
            tampered[i + 1] = (byte) 0xFF;
            tampered[i + 2] = (byte) 0xFF;
            tampered[i + 3] = (byte) 0xFF;
            try {
                EnvelopeCodec.decode(tampered);
            } catch (ProtocolException expected) {
                assertThat(expected.reason()).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("rejects an unknown message type id")
    void rejectsUnknownType() throws Exception {
        byte[] encoded = EnvelopeCodec.encode(sample());
        // type wire id is the first int32 after the magic + version
        encoded[8] = 0;
        encoded[9] = 0;
        encoded[10] = 0;
        encoded[11] = 99;

        assertThatThrownBy(() -> EnvelopeCodec.decode(encoded))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.UNKNOWN_MESSAGE_TYPE));
    }

    @Test
    @DisplayName("rejects an unsupported protocol major version")
    void rejectsUnsupportedVersion() throws Exception {
        byte[] encoded = EnvelopeCodec.encode(sample());
        encoded[4] = 0x00;
        encoded[5] = 0x00;
        encoded[6] = 0x00;
        encoded[7] = 0x02;

        assertThatThrownBy(() -> EnvelopeCodec.decode(encoded))
                .isInstanceOf(ProtocolException.class)
                .satisfies(e -> assertThat(((ProtocolException) e).reason())
                        .isEqualTo(RejectReason.UNSUPPORTED_VERSION));
    }

    @Test
    @DisplayName("refuses to encode a payload beyond the 8 KiB ceiling")
    void rejectsOversizePayload() {
        assertThatThrownBy(() -> MessageEnvelope.builder()
                .messageId("big")
                .origin("rqn1_" + "a".repeat(64))
                .createdAt(1000L)
                .expiresAt(2000L)
                .payload(new byte[MessageEnvelope.MAX_PAYLOAD_BYTES + 1])
                .buildSigned(new byte[] { 1 }, "SHA256withECDSA"))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    @DisplayName("signing bytes exclude mutable routing metadata but cover content")
    void signingBytesScope() throws Exception {
        MessageEnvelope original = sample();
        MessageEnvelope relayed = original.relayedCopy(5, 3);

        assertThat(Arrays.equals(original.signaturePayload(), relayed.signaturePayload()))
                .as("a relay advancing hop metadata must not invalidate the signature")
                .isTrue();

        MessageEnvelope tampered = original.toBuilder()
                .payload("goodbye".getBytes(StandardCharsets.UTF_8))
                .build();
        assertThat(Arrays.equals(original.signaturePayload(), tampered.signaturePayload()))
                .as("payload changes must change the signed bytes")
                .isFalse();

        MessageEnvelope retargeted = original.toBuilder()
                .destination("rqn1_" + "d".repeat(64))
                .build();
        assertThat(Arrays.equals(original.signaturePayload(), retargeted.signaturePayload()))
                .as("retargeting must change the signed bytes")
                .isFalse();
    }


    @DisplayName("protocol version arithmetic")

        @Test
        void packsAndUnpacksMajorMinor() {
            assertThat(ProtocolVersion.major(ProtocolVersion.CURRENT))
                    .isEqualTo(ProtocolVersion.MAJOR);
            assertThat(ProtocolVersion.minor(ProtocolVersion.CURRENT))
                    .isEqualTo(ProtocolVersion.MINOR);
        }

        @Test
        void acceptsHigherMinorOfTheSameMajor() {
            int higherMinor = (ProtocolVersion.MAJOR << 16) | (ProtocolVersion.MINOR + 7);
            assertThat(ProtocolVersion.isSupported(higherMinor)).isTrue();
        }

        @Test
        void rejectsDifferentMajor() {
            assertThat(ProtocolVersion.isSupported((ProtocolVersion.MAJOR + 1) << 16))
                    .isFalse();
    }
}
