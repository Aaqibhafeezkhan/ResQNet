package dev.resqnet.android.transport;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.concurrent.atomic.AtomicLong;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;

/** A public identity announcement must not authorize a Nearby endpoint. */
public class LinkIdentityProofTest {

    private static final class Pair {
        final AtomicLong time = new AtomicLong(1000);
        final InMemorySigningIdentity local;
        final InMemorySigningIdentity peer;
        final LinkIdentityProof verifier;
        final LinkIdentityProof claimant;

        Pair() throws Exception {
            local = InMemorySigningIdentity.generate("local");
            peer = InMemorySigningIdentity.generate("peer");
            verifier = new LinkIdentityProof(local, time::get);
            claimant = new LinkIdentityProof(peer, time::get);
            verifier.connected("endpoint-A");
            claimant.connected("endpoint-local");
        }

        byte[] challenge() {
            return verifier.challenge("endpoint-A", peer.deviceId(), peer.publicKey());
        }

        byte[] response(byte[] challenge) {
            return claimant.receive("endpoint-local", challenge).reply();
        }
    }

    @Test
    public void presentingAKnownPublicKeyDoesNotRebindWithoutPrivateKey() throws Exception {
        InMemorySigningIdentity local = InMemorySigningIdentity.generate("local");
        InMemorySigningIdentity victim = InMemorySigningIdentity.generate("victim");
        LinkIdentityProof proof = new LinkIdentityProof(local, System::currentTimeMillis);
        PeerBindings bindings = new PeerBindings();
        bindings.bind("attacker-endpoint");
        proof.connected("attacker-endpoint");

        byte[] challenge = proof.challenge("attacker-endpoint", victim.deviceId(),
                victim.publicKey());
        assertNotNull(challenge);
        assertNull(bindings.endpointFor(victim.deviceId()));
        assertEquals("attacker-endpoint", bindings.resolve("attacker-endpoint"));
    }

    @Test
    public void validFreshProofBindsOnlyTheChallengedIdentity() throws Exception {
        Pair pair = new Pair();
        byte[] response = pair.response(pair.challenge());
        assertNotNull(response);
        assertEquals(pair.peer.deviceId(),
                pair.verifier.receive("endpoint-A", response).verifiedDeviceId());
    }

    @Test
    public void replayCannotRebindAfterUseOrAcrossFreshChallenges() throws Exception {
        Pair pair = new Pair();
        byte[] first = pair.response(pair.challenge());
        assertEquals(pair.peer.deviceId(),
                pair.verifier.receive("endpoint-A", first).verifiedDeviceId());
        assertNull(pair.verifier.receive("endpoint-A", first).verifiedDeviceId());
        pair.time.incrementAndGet();
        byte[] second = pair.challenge();
        assertNotNull(second);
        assertFalse(java.util.Arrays.equals(first, pair.response(second)));
        assertNull(pair.verifier.receive("endpoint-A", first).verifiedDeviceId());
    }

    @Test
    public void wrongKeyResponseConsumesChallenge() throws Exception {
        Pair pair = new Pair();
        InMemorySigningIdentity attacker = InMemorySigningIdentity.generate("attacker");
        byte[] challenge = pair.challenge();
        byte[] legitimate = pair.response(challenge);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(legitimate));
        in.skipBytes(6);
        int claimLength = in.readUnsignedShort();
        byte[] claim = new byte[claimLength];
        in.readFully(claim);
        byte[] wrongSignature = attacker.sign(claim);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.write(legitimate, 0, 8 + claimLength);
        data.writeShort(wrongSignature.length);
        data.write(wrongSignature);
        byte[] forged = out.toByteArray();
        assertNull(pair.verifier.receive("endpoint-A", forged).verifiedDeviceId());
        assertNull(pair.verifier.receive("endpoint-A", legitimate).verifiedDeviceId());
    }

    @Test
    public void malformedResponseConsumesChallenge() throws Exception {
        Pair pair = new Pair();
        byte[] legitimate = pair.response(pair.challenge());
        byte[] truncated = java.util.Arrays.copyOf(legitimate, 10);
        assertNull(pair.verifier.receive("endpoint-A", truncated).verifiedDeviceId());
        assertNull(pair.verifier.receive("endpoint-A", legitimate).verifiedDeviceId());
    }

    @Test
    public void swappedEndpointCannotCompleteAnotherEndpointsChallenge() throws Exception {
        Pair pair = new Pair();
        pair.verifier.connected("endpoint-B");
        byte[] response = pair.response(pair.challenge());
        assertNull(pair.verifier.receive("endpoint-B", response).verifiedDeviceId());
        assertEquals(pair.peer.deviceId(),
                pair.verifier.receive("endpoint-A", response).verifiedDeviceId());
    }

    @Test
    public void staleChallengeAndInterruptedConnectionFailClosed() throws Exception {
        Pair pair = new Pair();
        byte[] response = pair.response(pair.challenge());
        pair.time.addAndGet(LinkIdentityProof.TIMEOUT_MILLIS);
        assertNull(pair.verifier.receive("endpoint-A", response).verifiedDeviceId());
        pair.time.incrementAndGet();
        response = pair.response(pair.challenge());
        pair.verifier.disconnected("endpoint-A");
        pair.verifier.connected("endpoint-A");
        assertNull(pair.verifier.receive("endpoint-A", response).verifiedDeviceId());
    }
}
