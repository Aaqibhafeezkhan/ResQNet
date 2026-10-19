package dev.resqnet.android;

import static dev.resqnet.android.TestSupport.engine;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.routing.InMemoryMessageStore;
import dev.resqnet.mesh.routing.MeshClock;
import dev.resqnet.mesh.routing.MeshRouter;
import dev.resqnet.mesh.routing.MeshTransport;
import dev.resqnet.mesh.routing.RouterConfig;

/**
 * Reconnection under a rotated Nearby endpoint id.
 *
 * <p>Nearby Connections hands out a fresh endpoint id every time a link is
 * re-established. The device behind it has not changed, only the radio handle,
 * so the engine must re-point its binding on every claim, not only on the
 * first one.
 *
 * <p>What went wrong: {@code MeshEngine.handleIdentityAnnouncement} returned
 * early as soon as it already held the device's key, which is true from the
 * first meeting onwards. The call that would have rebound the new endpoint
 * therefore never ran. Nothing appeared wrong: the peer stayed listed, the
 * trust store still had the key, and the diagnostics screen still showed it.
 * The only thing missing was the mapping from the device id to a live radio
 * handle, so every directed message addressed to that peer went to an endpoint
 * that no longer existed.
 */
@RunWith(AndroidJUnit4.class)
public class IdentityRebindingTest {

    /**
     * A second phone, built in-process: real identity, real signature, real
     * identity announcement. Only its radio is a stub, because a radio needs
     * hardware.
     */
    private static final class PeerPhone {
        final InMemorySigningIdentity identity;
        final MeshRouter router;

        PeerPhone() throws java.security.GeneralSecurityException {
            this.identity = InMemorySigningIdentity.generate("Reconnector");
            this.router = new MeshRouter(identity, new TrustStore(), new InMemoryMessageStore(),
                    new MeshTransport() {
                        @Override
                        public Optional<Long> send(String peerDeviceId, byte[] encodedPayload) {
                            return Optional.empty();
                        }

                        @Override
                        public Set<String> connectedPeerIds() {
                            return Set.of();
                        }

                        @Override
                        public void setInboundHandler(InboundHandler handler) {
                        }

                        @Override
                        public void setConnectionListener(ConnectionListener listener) {
                        }

                        @Override
                        public void start() {
                        }

                        @Override
                        public void stop() {
                        }

                        @Override
                        public boolean isStarted() {
                            return true;
                        }
                    },
                    MeshClock.system(), RouterConfig.builder().build());
        }

        byte[] announcement() throws Exception {
            Optional<MessageEnvelope> claim = router.publishIdentity();
            assertTrue("a peer must be able to announce itself", claim.isPresent());
            return EnvelopeCodec.encode(claim.get());
        }
    }

    @Test
    public void rebindsAKnownPeerWhenItReturnsUnderANewEndpointId() throws Exception {
        PeerPhone phone = new PeerPhone();
        String deviceId = phone.identity.deviceId();
        byte[] claim = phone.announcement();

        assertTrue("the first claim must be accepted",
                engine().router().receive("endpoint-1", claim).productive());
        assertEquals("the first claim must map the device to its live endpoint",
                "endpoint-1", engine().transport().bindings().get(deviceId));

        // The link dropped and Nearby reconnected the same phone under a new
        // endpoint id. The peer re-announces, exactly as it does in production.
        assertTrue("a re-announced peer must still be accepted",
                engine().router().receive("endpoint-2", claim).productive());

        Map<String, String> bindings = engine().transport().bindings();
        assertNotNull("the device must still be bound after a reconnect",
                bindings.get(deviceId));
        assertEquals("the binding must follow the peer to its new endpoint",
                "endpoint-2", bindings.get(deviceId));
    }

    @Test
    public void doesNotRebindAPeerTheUserBlocked() throws Exception {
        PeerPhone phone = new PeerPhone();
        String deviceId = phone.identity.deviceId();
        byte[] claim = phone.announcement();

        engine().router().receive("endpoint-1", claim);
        assertTrue("the peer must be blocked before the reconnect",
                engine().trustStore().block(deviceId));

        engine().router().receive("endpoint-2", claim);

        assertEquals("a blocked peer must not be handed back a live radio handle",
                "endpoint-1", engine().transport().bindings().get(deviceId));
    }

    @Test
    public void anUnrelatedPeerDoesNotDisturbAnExistingBinding() throws Exception {
        PeerPhone phone = new PeerPhone();
        String deviceId = phone.identity.deviceId();
        byte[] claim = phone.announcement();
        engine().router().receive("endpoint-1", claim);

        PeerPhone stranger = new PeerPhone();
        engine().router().receive("endpoint-9", stranger.announcement());

        Map<String, String> bindings = engine().transport().bindings();
        assertEquals("an unrelated peer must not move someone else's binding",
                "endpoint-1", bindings.get(deviceId));
        assertEquals("the unrelated peer must still be bound to its own endpoint",
                "endpoint-9", bindings.get(stranger.identity.deviceId()));
    }
}
