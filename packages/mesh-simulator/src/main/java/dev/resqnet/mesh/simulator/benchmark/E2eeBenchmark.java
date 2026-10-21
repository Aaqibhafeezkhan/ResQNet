package dev.resqnet.mesh.simulator.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import dev.resqnet.mesh.crypto.EncryptionIdentity;
import dev.resqnet.mesh.protocol.ProtectedPayload;

/**
 * Measures the CPU cost and wire overhead of the directed-message HPKE path.
 *
 * <p>This is a microbenchmark of the cryptographic operations the router calls,
 * not a radio measurement. It reports warmed medians on the host JVM so a
 * reviewer can see what encryption adds to a handset-class payload without
 * having to infer it from an end-to-end simulation. Android Keystore signing
 * and radio time are not included.
 */
public final class E2eeBenchmark {

    private static final int WARMUP = 1_000;
    private static final int ITERATIONS = 5_000;
    private static final int[] PAYLOAD_SIZES = {64, 256, 4_096};

    private E2eeBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        EncryptionIdentity recipient = EncryptionIdentity.generate();
        EncryptionIdentity sender = EncryptionIdentity.generate();
        byte[] recipientKeyId = EncryptionIdentity.keyIdBytes(recipient.publicKeyBytes());
        byte[] associatedData = ProtectedPayload.associatedData(
                dev.resqnet.mesh.protocol.MessageEnvelope.builder()
                        .messageId("benchmark-message")
                        .origin(sender.keyId())
                        .originKeyFingerprint("0".repeat(64))
                        .destination(recipient.keyId())
                        .type(dev.resqnet.mesh.protocol.MessageType.SOS_PROTECTED)
                        .priority(dev.resqnet.mesh.protocol.Priority.SOS)
                        .createdAt(1_000L)
                        .expiresAt(60_000L)
                        .payload(new byte[0])
                        .build(),
                recipientKeyId);

        System.out.println("# ResQNet E2EE microbenchmark");
        System.out.println();
        System.out.printf(Locale.ROOT, "Host: %s, Java %s%n",
                System.getProperty("os.name"), System.getProperty("java.version"));
        System.out.printf(Locale.ROOT, "Warmup: %d, measured iterations: %d per size%n%n",
                WARMUP, ITERATIONS);
        System.out.println("| Plaintext | Encrypted payload | Wire overhead | Encrypt | Decrypt |");
        System.out.println("|---:|---:|---:|---:|---:|");

        for (int size : PAYLOAD_SIZES) {
            byte[] plaintext = ("x".repeat(size)).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < WARMUP; i++) {
                roundTrip(recipient, plaintext, associatedData);
            }
            long encryptNanos = 0;
            long decryptNanos = 0;
            int encodedLength = 0;
            for (int i = 0; i < ITERATIONS; i++) {
                long start = System.nanoTime();
                byte[] ciphertext = EncryptionIdentity.encryptTo(
                        recipient.publicKeyBytes(), plaintext, associatedData);
                long mid = System.nanoTime();
                byte[] encoded = ProtectedPayload.of(recipientKeyId, ciphertext).encode();
                encryptNanos += mid - start;
                encodedLength = encoded.length;

                ProtectedPayload decoded = ProtectedPayload.decode(encoded);
                long beforeDecrypt = System.nanoTime();
                recipient.decrypt(decoded.ciphertext(), associatedData);
                decryptNanos += System.nanoTime() - beforeDecrypt;
            }
            System.out.printf(Locale.ROOT, "| %d B | %d B | %d B | %.1f us | %.1f us |%n",
                    size, encodedLength, encodedLength - size,
                    encryptNanos / 1_000.0 / ITERATIONS,
                    decryptNanos / 1_000.0 / ITERATIONS);
        }
        System.out.println();
        System.out.println("Wire overhead is the fixed ProtectedPayload header (42 B) plus");
        System.out.println("HPKE's X25519 encapsulation and AES-GCM tag (48 B).");
    }

    private static void roundTrip(EncryptionIdentity recipient, byte[] plaintext,
                                  byte[] associatedData) throws Exception {
        byte[] ciphertext = EncryptionIdentity.encryptTo(
                recipient.publicKeyBytes(), plaintext, associatedData);
        byte[] encoded = ProtectedPayload.of(
                EncryptionIdentity.keyIdBytes(recipient.publicKeyBytes()), ciphertext).encode();
        ProtectedPayload decoded = ProtectedPayload.decode(encoded);
        recipient.decrypt(decoded.ciphertext(), associatedData);
    }
}
