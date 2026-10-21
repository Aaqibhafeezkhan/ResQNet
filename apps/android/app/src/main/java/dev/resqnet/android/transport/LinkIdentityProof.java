package dev.resqnet.android.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import dev.resqnet.mesh.crypto.DeviceIdentity;
import dev.resqnet.mesh.crypto.SigningIdentity;

/** One-use proof of possession for a connected Nearby endpoint. */
public final class LinkIdentityProof {
    private static final int MAGIC = 0x52514c50; // RQLP, separate from mesh envelopes
    private static final byte VERSION = 1;
    private static final byte CHALLENGE = 1;
    private static final byte RESPONSE = 2;
    private static final int NONCE_SIZE = 32;
    private static final int MAX_FRAME = 4096;
    public static final long TIMEOUT_MILLIS = 15_000L;

    private final SigningIdentity self;
    private final LongSupplier clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, Boolean> connected = new HashMap<>();

    private record Pending(String deviceId, PublicKey key, byte[] challenge,
                           long startedAt) { }

    public record Result(byte[] reply, String verifiedDeviceId) { }

    public LinkIdentityProof(SigningIdentity self, LongSupplier clock) {
        this.self = java.util.Objects.requireNonNull(self);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized void connected(String endpointId) {
        pending.remove(endpointId);
        connected.put(endpointId, true);
    }

    public synchronized void disconnected(String endpointId) {
        pending.remove(endpointId);
        connected.remove(endpointId);
    }

    public synchronized void clear() {
        pending.clear();
        connected.clear();
    }

    /** Returns null unless this is a currently connected, provisional endpoint. */
    public synchronized byte[] challenge(String endpointId, String deviceId, PublicKey key) {
        if (!connected.containsKey(endpointId) || key == null
                || !DeviceIdentity.deviceId(key).equals(deviceId)) {
            return null;
        }
        Pending existing = pending.get(endpointId);
        if (existing != null) {
            long elapsed = clock.getAsLong() - existing.startedAt;
            if (elapsed >= 0 && elapsed < TIMEOUT_MILLIS) {
                return null;
            }
        }
        byte[] nonce = new byte[NONCE_SIZE];
        random.nextBytes(nonce);
        byte[] claim = claim(self.deviceId(), deviceId, nonce);
        pending.put(endpointId, new Pending(deviceId, key, claim, clock.getAsLong()));
        return frame(CHALLENGE, claim, new byte[0]);
    }

    /** Control frames never enter the mesh router. Invalid frames fail closed. */
    public static boolean isProofFrame(byte[] bytes) {
        return bytes != null && bytes.length >= 4
                && bytes[0] == 'R' && bytes[1] == 'Q' && bytes[2] == 'L' && bytes[3] == 'P';
    }

    public synchronized Result receive(String endpointId, byte[] bytes) {
        if (!connected.containsKey(endpointId) || bytes == null || bytes.length > MAX_FRAME) {
            return new Result(null, null);
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC || in.readByte() != VERSION) {
                return new Result(null, null);
            }
            byte type = in.readByte();
            // A purported response gets exactly one verification attempt, even
            // when its frame is malformed.
            Pending expected = type == RESPONSE ? pending.remove(endpointId) : null;
            int claimLength = in.readUnsignedShort();
            if (claimLength < 1 || claimLength > 1024 || claimLength > in.available()) {
                return new Result(null, null);
            }
            byte[] claim = new byte[claimLength];
            in.readFully(claim);
            int signatureLength = in.readUnsignedShort();
            if (signatureLength > 1024 || signatureLength != in.available()) {
                return new Result(null, null);
            }
            byte[] signature = new byte[signatureLength];
            in.readFully(signature);
            if (type == CHALLENGE && signatureLength == 0) {
                if (claimant(claim).equals(self.deviceId())) {
                    return new Result(frame(RESPONSE, claim, self.sign(claim)), null);
                }
            } else if (type == RESPONSE) {
                // Consume before verification: a bad response cannot be retried with
                // another signature, and captured responses are one-use.
                long elapsed = expected == null ? -1 : clock.getAsLong() - expected.startedAt;
                if (expected != null && elapsed >= 0 && elapsed < TIMEOUT_MILLIS
                        && Arrays.equals(expected.challenge, claim)
                        && SigningIdentity.verify(expected.key, claim, signature)) {
                    return new Result(null, expected.deviceId);
                }
            }
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            pending.remove(endpointId);
        }
        return new Result(null, null);
    }

    private static byte[] claim(String challenger, String claimant, byte[] nonce) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF("ResQNet Nearby link proof v1");
            data.writeUTF(challenger);
            data.writeUTF(claimant);
            data.write(nonce);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String claimant(byte[] claim) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(claim));
        if (!"ResQNet Nearby link proof v1".equals(in.readUTF())) {
            throw new IOException("wrong proof domain");
        }
        in.readUTF(); // challenger
        String claimant = in.readUTF();
        if (in.available() != NONCE_SIZE) {
            throw new IOException("wrong nonce length");
        }
        return claimant;
    }

    private static byte[] frame(byte type, byte[] claim, byte[] signature) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeInt(MAGIC);
            data.writeByte(VERSION);
            data.writeByte(type);
            data.writeShort(claim.length);
            data.write(claim);
            data.writeShort(signature.length);
            data.write(signature);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
