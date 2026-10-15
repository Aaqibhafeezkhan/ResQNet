package dev.resqnet.mesh.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Binary wire codec for {@link MessageEnvelope}.
 *
 * <p>The encoding is intentionally boring: big-endian fixed-width integers and
 * length-prefixed UTF-8 / byte fields. Boring matters here because the payload
 * travels through a Nearby Connections radio link where every byte costs battery
 * and range, and because a byte-stable encoding is what makes signatures
 * verifiable by a third party.
 *
 * <p>Layout:
 *
 * <pre>
 *   magic "RQN1"           4 bytes
 *   protocolVersion       int32
 *   type                  int32
 *   priority              int32
 *   hopLimit              int32
 *   hopCount              int32
 *   createdAtEpochMillis  int64
 *   expiresAtEpochMillis  int64
 *   messageId             len16 + utf8
 *   originDeviceId        len16 + utf8
 *   originKeyFingerprint  len16 + utf8   (len 0 == unsigned)
 *   destinationDeviceId   len16 + utf8   (len 0 == broadcast)
 *   correlationId         len16 + utf8   (len 0 == none)
 *   payload               int32 + bytes
 *   signatureAlgorithm    len16 + utf8
 *   signature             int32 + bytes
 * </pre>
 *
 * <p>Every declared length is validated against the remaining buffer before any
 * allocation, so a hostile peer cannot trigger an oversized array by lying in
 * the header.
 */
public final class EnvelopeCodec {

    /** ASCII magic prefix identifying a ResQNet envelope. */
    public static final byte[] MAGIC = { 0x52, 0x51, 0x4E, 0x31 };

    /** Maximum characters accepted in a single UTF-8 identifier field. */
    public static final int MAX_IDENTIFIER_CHARS = 128;

    private EnvelopeCodec() {
    }

    /** Serialises an envelope to its canonical binary form. */
    public static byte[] encode(MessageEnvelope envelope) throws ProtocolException {
        if (envelope == null) {
            throw new ProtocolException(RejectReason.MALFORMED, "envelope is null");
        }
        if (envelope.payloadLength() > MessageEnvelope.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException(RejectReason.PAYLOAD_TOO_LARGE,
                    "Payload " + envelope.payloadLength() + "B exceeds "
                            + MessageEnvelope.MAX_PAYLOAD_BYTES + "B");
        }
        Writer w = new Writer(512);
        w.bytes(MAGIC);
        w.int32(envelope.protocolVersion());
        w.int32(envelope.type().wireId());
        w.int32(envelope.priority().level());
        w.int32(envelope.hopLimit());
        w.int32(envelope.hopCount());
        w.int64(envelope.createdAtEpochMillis());
        w.int64(envelope.expiresAtEpochMillis());
        w.text(envelope.messageId());
        w.text(envelope.originDeviceId());
        w.text(envelope.originKeyFingerprint());
        w.text(envelope.destinationDeviceId().orElse(null));
        w.text(envelope.correlationId().orElse(null));
        w.binary(envelope.payload());
        w.text(envelope.signatureAlgorithm().isEmpty() ? null : envelope.signatureAlgorithm());
        w.binary(envelope.signature());

        byte[] out = w.toByteArray();
        if (out.length > MessageEnvelope.MAX_ENCODED_BYTES) {
            throw new ProtocolException(RejectReason.PAYLOAD_TOO_LARGE,
                    "Encoded envelope " + out.length + "B exceeds "
                            + MessageEnvelope.MAX_ENCODED_BYTES + "B");
        }
        return out;
    }

    /**
     * Parses and structurally validates a binary envelope.
     *
     * <p>Validation here is purely structural. Semantic checks that need local
     * state (deduplication, hop budget, clock) happen in
     * {@code EnvelopeValidator}.
     *
     * @throws ProtocolException when the buffer is truncated, has a bad magic
     *     prefix, an unknown type id or an inconsistent length prefix
     */
    public static MessageEnvelope decode(byte[] data) throws ProtocolException {
        if (data == null || data.length < MAGIC.length + 8) {
            throw new ProtocolException(RejectReason.MALFORMED, "Envelope too short");
        }
        if (data.length > MessageEnvelope.MAX_ENCODED_BYTES) {
            throw new ProtocolException(RejectReason.PAYLOAD_TOO_LARGE,
                    "Envelope " + data.length + "B exceeds hard limit");
        }
        Reader r = new Reader(data);

        byte[] magic = r.bytes(MAGIC.length);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new ProtocolException(RejectReason.MALFORMED, "Bad magic prefix");
        }

        int protocolVersion = r.int32();
        if (!ProtocolVersion.isSupported(protocolVersion)) {
            throw new ProtocolException(RejectReason.UNSUPPORTED_VERSION,
                    "Protocol version " + Integer.toHexString(protocolVersion)
                            + " is not supported by this engine");
        }
        MessageType type = MessageType.fromWireId(r.int32());
        Priority priority = Priority.fromLevel(r.int32());
        int hopLimit = r.int32();
        int hopCount = r.int32();
        long createdAt = r.int64();
        long expiresAt = r.int64();
        String messageId = r.text();
        String originDeviceId = r.text();
        String fingerprint = r.text();
        String destination = r.text();
        String correlationId = r.text();
        byte[] payload = r.binary();
        String signatureAlgorithm = r.text();
        byte[] signature = r.binary();

        if (r.remaining() != 0) {
            throw new ProtocolException(RejectReason.MALFORMED,
                    r.remaining() + " trailing bytes after envelope");
        }

        MessageEnvelope.Builder builder = MessageEnvelope.builder()
                .type(type)
                .priority(priority)
                .hopLimit(hopLimit)
                .hopCount(hopCount)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .payload(payload);
        builder.messageId(messageId)
                .origin(originDeviceId)
                .originKeyFingerprint(fingerprint);
        if (destination != null) {
            builder.destination(destination);
        }
        if (correlationId != null) {
            builder.correlationId(correlationId);
        }

        if (signature.length == 0) {
            return builder.build();
        }
        return builder.buildSigned(signature, signatureAlgorithm);
    }

    /**
     * Canonical byte string covered by an envelope signature.
     *
     * <p>Routing metadata that a relay is expected to change (hop count, hop
     * limit) is deliberately excluded so a relay can forward an envelope without
     * holding the origin's private key. Everything that describes content or
     * identity is included.
     */
    public static byte[] signingBytes(MessageEnvelope e) {
        Writer w = new Writer(256);
        w.bytes(MAGIC);
        w.int32(e.protocolVersion());
        w.text(e.messageId());
        w.text(e.originDeviceId());
        w.text(e.originKeyFingerprint());
        w.text(e.destinationDeviceId().orElse(null));
        w.int32(e.type().wireId());
        w.int32(e.priority().level());
        w.int64(e.createdAtEpochMillis());
        w.int64(e.expiresAtEpochMillis());
        w.text(e.correlationId().orElse(null));
        w.binary(e.payload());
        return w.toByteArray();
    }

    private static final class Writer {
        private byte[] buf;
        private int pos;

        Writer(int capacity) {
            this.buf = new byte[Math.max(32, capacity)];
        }

        private void ensure(int extra) {
            if (pos + extra > buf.length) {
                int next = buf.length;
                while (next < pos + extra) {
                    next <<= 1;
                }
                buf = Arrays.copyOf(buf, next);
            }
        }

        void bytes(byte[] src) {
            ensure(src.length);
            System.arraycopy(src, 0, buf, pos, src.length);
            pos += src.length;
        }

        void int32(int v) {
            ensure(4);
            buf[pos] = (byte) (v >>> 24);
            buf[pos + 1] = (byte) (v >>> 16);
            buf[pos + 2] = (byte) (v >>> 8);
            buf[pos + 3] = (byte) v;
            pos += 4;
        }

        void int64(long v) {
            ensure(8);
            for (int i = 0; i < 8; i++) {
                buf[pos + i] = (byte) (v >>> (56 - 8 * i));
            }
            pos += 8;
        }

        void text(String s) {
            if (s == null || s.isEmpty()) {
                int16(0);
                return;
            }
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            if (utf8.length > MAX_IDENTIFIER_CHARS * 4) {
                throw new IllegalArgumentException("Identifier field too long: " + utf8.length + "B");
            }
            int16(utf8.length);
            bytes(utf8);
        }

        void binary(byte[] src) {
            int32(src.length);
            bytes(src);
        }

        private void int16(int v) {
            ensure(2);
            buf[pos] = (byte) (v >>> 8);
            buf[pos + 1] = (byte) v;
            pos += 2;
        }

        byte[] toByteArray() {
            return Arrays.copyOf(buf, pos);
        }
    }

    private static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) {
            this.buf = buf;
        }

        int remaining() {
            return buf.length - pos;
        }

        private void require(int n) throws ProtocolException {
            if (remaining() < n) {
                throw new ProtocolException(RejectReason.MALFORMED,
                        "Truncated envelope: need " + n + "B, have " + remaining() + "B");
            }
        }

        byte[] bytes(int n) throws ProtocolException {
            require(n);
            byte[] out = Arrays.copyOfRange(buf, pos, pos + n);
            pos += n;
            return out;
        }

        int int32() throws ProtocolException {
            require(4);
            int v = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                    | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        long int64() throws ProtocolException {
            require(8);
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (buf[pos + i] & 0xFFL);
            }
            pos += 8;
            return v;
        }

        String text() throws ProtocolException {
            int len = length16();
            if (len == 0) {
                return "";
            }
            byte[] raw = bytes(len);
            return new String(raw, StandardCharsets.UTF_8);
        }

        byte[] binary() throws ProtocolException {
            int len = int32();
            if (len < 0 || len > remaining()) {
                throw new ProtocolException(RejectReason.MALFORMED,
                        "Declared length " + len + " exceeds remaining " + remaining());
            }
            return bytes(len);
        }

        private int length16() throws ProtocolException {
            require(2);
            int len = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
            pos += 2;
            return len;
        }
    }
}