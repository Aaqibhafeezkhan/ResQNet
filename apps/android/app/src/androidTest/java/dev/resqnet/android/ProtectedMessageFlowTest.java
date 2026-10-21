package dev.resqnet.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import dev.resqnet.android.model.EmergencyPayload;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.mesh.crypto.EncryptionIdentity;
import dev.resqnet.mesh.crypto.EncryptionKeyBinding;
import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.IdentityAnnouncement;
import dev.resqnet.mesh.routing.InMemoryMessageStore;
import dev.resqnet.mesh.routing.MeshClock;
import dev.resqnet.mesh.routing.MeshRouter;
import dev.resqnet.mesh.routing.RouterConfig;
import dev.resqnet.mesh.routing.StoredMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** The Android wiring: directed sends encrypt, store no plaintext, and read back. */
@RunWith(AndroidJUnit4.class)
public class ProtectedMessageFlowTest {

    private MeshEngine engine;
    private InMemorySigningIdentity peer;
    private String peerId;

    @Before
    public void setUp() throws Exception {
        engine = TestSupport.engine();
        TestSupport.clearMessages();
        peer = InMemorySigningIdentity.generate("test peer");
        peerId = peer.deviceId();
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        engine.trustStore().rememberUnverified(peer.displayName(), peer.publicKey(), now);
        engine.trustStore().markVerified(peerId, now);
        engine.trustStore().rememberEncryptionKey(peerId,
                EncryptionIdentity.generate().publicKeyBytes(), now);
    }

    @After
    public void tearDown() {
        engine.trustStore().forget(peerId);
        TestSupport.clearMessages();
    }

    @Test
    public void identityClaimBindsTheKeystoreSealedHpkeKey() throws Exception {
        MessageEnvelope claim = engine.router().publishIdentity().orElseThrow();
        IdentityAnnouncement announcement = IdentityAnnouncement.decode(claim.payload());

        assertTrue(announcement.hasEncryptionKey());
        byte[] announcedKey = Base64.getDecoder()
                .decode(announcement.encryptionPublicKeyBase64());
        byte[] proof = Base64.getDecoder()
                .decode(announcement.encryptionKeyProofBase64());
        assertArrayEquals(engine.encryptionIdentity().publicKeyBytes(), announcedKey);
        assertTrue("the HPKE key must be bound by the hardware-backed signing key",
                EncryptionKeyBinding.verify(engine.identity().publicKey(), announcedKey, proof));
    }

    @Test
    public void outgoingDirectedTextEncryptsAndReadsFromLocalBody() throws Exception {
        String body = TestSupport.uniqueBody("protected-out");

        engine.sendText(body, peerId);

        StoredMessage stored = TestSupport.awaitBody(body, 20_000L);
        assertNotNull("the outgoing encrypted message must be readable", stored);
        assertEquals(MessageType.TEXT_PROTECTED, stored.type());
        assertEquals(peerId, stored.destinationDeviceId());
        assertTrue("the sender must see that this was encrypted",
                engine.readBody(stored).encrypted());

        byte[] localBody = engine.database().loadLocalBody(stored.messageId());
        assertArrayEquals(EmergencyPayload.encode(body, false, 0, 0), localBody);

        byte[] wirePayload = EnvelopeCodec.decode(stored.envelope()).payload();
        assertFalse("the envelope payload must not contain the plaintext",
                new String(wirePayload, StandardCharsets.UTF_8).contains(body));
    }

    @Test
    public void incomingProtectedMessageIsDecryptedInMemory() throws Exception {
        String body = TestSupport.uniqueBody("protected-in");
        InMemorySigningIdentity sender = InMemorySigningIdentity.generate("sender");
        EncryptionIdentity senderEncryption = EncryptionIdentity.generate();
        TrustStore senderTrust = new TrustStore();
        senderTrust.rememberUnverified("this device", engine.identity().publicKey(),
                Instant.now());
        senderTrust.markVerified(engine.deviceId(), Instant.now());
        senderTrust.rememberEncryptionKey(engine.deviceId(),
                engine.encryptionIdentity().publicKeyBytes(), Instant.now());
        MeshRouter senderRouter = new MeshRouter(sender, senderTrust,
                new InMemoryMessageStore(), null, MeshClock.system(),
                RouterConfig.defaults());
        senderRouter.setEncryptionIdentity(senderEncryption);
        MessageEnvelope envelope = senderRouter.originateProtected(engine.deviceId(),
                MessageType.TEXT, Priority.NORMAL,
                EmergencyPayload.encode(body, false, 0, 0), 0L);

        StoredMessage stored = StoredMessage.of(envelope, EnvelopeCodec.encode(envelope),
                System.currentTimeMillis());
        engine.store().upsert(stored);

        MeshEngine.BodyView view = engine.readBody(stored);
        assertTrue(view.readable());
        assertTrue(view.encrypted());
        assertEquals(body, view.body());
        assertEquals("incoming ciphertext must not be retained as plaintext",
                null, engine.database().loadLocalBody(stored.messageId()));
    }
}
