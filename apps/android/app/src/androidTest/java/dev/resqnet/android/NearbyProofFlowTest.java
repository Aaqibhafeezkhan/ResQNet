package dev.resqnet.android;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.tasks.Tasks;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.transport.LinkIdentityProof;
import dev.resqnet.android.transport.NearbyTransport;
import dev.resqnet.android.transport.PeerBindings;
import dev.resqnet.mesh.crypto.InMemorySigningIdentity;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.protocol.MessageEnvelope;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.InMemoryMessageStore;
import dev.resqnet.mesh.routing.MeshClock;
import dev.resqnet.mesh.routing.MeshEvent;
import dev.resqnet.mesh.routing.MeshRouter;
import dev.resqnet.mesh.routing.RouterConfig;

/** Engine, Nearby adapter and router over an in-process byte pipe. */
@RunWith(AndroidJUnit4.class)
public class NearbyProofFlowTest {

    @Test
    public void concurrentProofAndDisconnectCannotLeaveAStableBinding() throws Exception {
        InMemorySigningIdentity local = InMemorySigningIdentity.generate("local");
        InMemorySigningIdentity peer = InMemorySigningIdentity.generate("peer");
        NearbyTransport transport = new NearbyTransport(
                ApplicationProvider.getApplicationContext(), local, "local",
                new NearbyTransport.Listener() {
                    @Override
                    public void onTransportError(String operation, String message) { }

                    @Override
                    public void onPeerStateChanged(String deviceId, boolean connected) { }
                });
        transport.setBindingApproval(id -> true);
        byte[][] challenge = {null};
        ConnectionsClient client = (ConnectionsClient) Proxy.newProxyInstance(
                ConnectionsClient.class.getClassLoader(),
                new Class<?>[] {ConnectionsClient.class}, (proxy, method, args) -> {
                    if ("sendPayload".equals(method.getName()) && args.length == 2
                            && args[1] instanceof Payload payload) {
                        challenge[0] = payload.asBytes();
                    }
                    return method.getReturnType() == void.class ? null : Tasks.forResult(null);
                });
        field(transport, "client", client);
        field(transport, "started", true);
        ConnectionLifecycleCallback lifecycle =
                (ConnectionLifecycleCallback) field(transport, "lifecycleCallback");
        PayloadCallback inbound = (PayloadCallback) field(transport, "payloadCallback");
        LinkIdentityProof peerProof = new LinkIdentityProof(peer, System::currentTimeMillis);
        peerProof.connected("local");
        lifecycle.onConnectionResult("endpoint-1",
                new ConnectionResolution(new Status(ConnectionsStatusCodes.STATUS_OK)));
        transport.requestBindingProof("endpoint-1", peer.deviceId(), peer.publicKey());
        byte[] response = peerProof.receive("local", challenge[0]).reply();
        assertNotNull(response);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> proof = workers.submit(() -> {
                await(start);
                inbound.onPayloadReceived("endpoint-1", Payload.fromBytes(response));
            });
            Future<?> disconnect = workers.submit(() -> {
                await(start);
                lifecycle.onDisconnected("endpoint-1");
            });
            start.countDown();
            proof.get(5, TimeUnit.SECONDS);
            disconnect.get(5, TimeUnit.SECONDS);
            assertNull(transport.bindings().get(peer.deviceId()));
        } finally {
            workers.shutdownNow();
            transport.stop();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    public void failedConnectionCanBeRequestedAgainAndLateSuccessAfterStopIsIgnored()
            throws Exception {
        InMemorySigningIdentity identity = InMemorySigningIdentity.generate("local");
        NearbyTransport transport = new NearbyTransport(
                ApplicationProvider.getApplicationContext(), identity, "local",
                new NearbyTransport.Listener() {
                    @Override
                    public void onTransportError(String operation, String message) { }

                    @Override
                    public void onPeerStateChanged(String deviceId, boolean connected) { }
                });
        int[] requests = {0};
        ConnectionsClient client = (ConnectionsClient) Proxy.newProxyInstance(
                ConnectionsClient.class.getClassLoader(),
                new Class<?>[] {ConnectionsClient.class}, (proxy, method, args) -> {
                    if ("requestConnection".equals(method.getName())) {
                        requests[0]++;
                    }
                    if (method.getReturnType() == void.class) {
                        return null;
                    }
                    return Tasks.forResult(null);
                });
        field(transport, "client", client);
        field(transport, "started", true);
        Method request = NearbyTransport.class.getDeclaredMethod("requestConnection", String.class);
        request.setAccessible(true);
        ConnectionLifecycleCallback lifecycle =
                (ConnectionLifecycleCallback) field(transport, "lifecycleCallback");

        request.invoke(transport, "endpoint-1");
        request.invoke(transport, "endpoint-1");
        assertEquals(1, requests[0]);
        lifecycle.onConnectionResult("endpoint-1", new ConnectionResolution(
                new Status(ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED)));
        request.invoke(transport, "endpoint-1");
        assertEquals("a failed result must release the requested flag", 2, requests[0]);

        ((PeerBindings) field(transport, "bindings")).markDiscovered("endpoint-1");
        lifecycle.onConnectionResult("endpoint-1", new ConnectionResolution(
                new Status(ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED)));
        TestSupport.eventually("failed discovered connection is retried", 20_000L,
                () -> requests[0] == 3);

        transport.stop();
        lifecycle.onConnectionResult("endpoint-1",
                new ConnectionResolution(new Status(ConnectionsStatusCodes.STATUS_OK)));
        assertTrue("late callbacks must not resurrect a stopped link",
                transport.bindings().isEmpty());
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void announcementProofDirectedDeliveryDisconnectAndRebind() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        MeshEngine engine = new MeshEngine(context, "Proof-flow local");
        InMemorySigningIdentity peer = InMemorySigningIdentity.generate("Proof-flow peer");
        LinkIdentityProof peerProof = new LinkIdentityProof(peer, System::currentTimeMillis);
        MeshRouter peerRouter = new MeshRouter(peer, new TrustStore(),
                new InMemoryMessageStore(), null, MeshClock.system(),
                RouterConfig.builder().acknowledgementsEnabled(false).build());
        peerRouter.acceptPeerKey(engine.deviceId(), engine.identity().publicKey());
        List<MessageEnvelope> delivered = new ArrayList<>();
        peerRouter.addEventListener(event -> {
            if (event instanceof MeshEvent.Delivered received) {
                delivered.add(received.envelope());
            }
        });
        NearbyTransport transport = engine.transport();
        ConnectionLifecycleCallback lifecycle =
                (ConnectionLifecycleCallback) field(transport, "lifecycleCallback");
        PayloadCallback inbound = (PayloadCallback) field(transport, "payloadCallback");
        String[] liveEndpoint = {"endpoint-1"};
        boolean[] failNextProof = {false};

        ConnectionsClient client = (ConnectionsClient) Proxy.newProxyInstance(
                ConnectionsClient.class.getClassLoader(),
                new Class<?>[] {ConnectionsClient.class}, (proxy, method, args) -> {
                    if ("sendPayload".equals(method.getName()) && args.length == 2
                            && args[0] instanceof String endpoint) {
                        byte[] bytes = ((Payload) args[1]).asBytes();
                        if (endpoint.equals(liveEndpoint[0]) && bytes != null) {
                            if (LinkIdentityProof.isProofFrame(bytes)) {
                                if (failNextProof[0]) {
                                    failNextProof[0] = false;
                                    return Tasks.forException(new IllegalStateException(
                                            "simulated challenge delivery failure"));
                                }
                                byte[] response = peerProof.receive("local", bytes).reply();
                                if (response != null) {
                                    inbound.onPayloadReceived(endpoint,
                                            Payload.fromBytes(response));
                                }
                            } else {
                                MessageEnvelope envelope = EnvelopeCodec.decode(bytes);
                                if (envelope.type() == MessageType.TEXT) {
                                    peerRouter.receive(engine.deviceId(), bytes);
                                }
                            }
                        }
                        return Tasks.forResult(null);
                    }
                    if (method.getReturnType() == void.class) {
                        return null;
                    }
                    return Tasks.forResult(null);
                });
        field(transport, "client", client);
        field(transport, "started", true);

        try {
            peerProof.connected("local");
            byte[] announcement = EnvelopeCodec.encode(peerRouter.publishIdentity().orElseThrow());
            lifecycle.onConnectionResult(liveEndpoint[0],
                    new ConnectionResolution(new Status(ConnectionsStatusCodes.STATUS_OK)));
            assertEquals(liveEndpoint[0], transport.bindings().get(liveEndpoint[0]));
            assertNull(transport.bindings().get(peer.deviceId()));

            // The public claim is accepted, then a signed response promotes it.
            inbound.onPayloadReceived(liveEndpoint[0], Payload.fromBytes(announcement));
            assertEquals(liveEndpoint[0], transport.bindings().get(peer.deviceId()));
            assertEquals(TrustStore.Trust.UNVERIFIED,
                    engine.trustStore().entry(peer.deviceId()).orElseThrow().trust());

            engine.router().originate(peer.deviceId(), MessageType.TEXT, Priority.NORMAL,
                    "first directed message".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    0L);
            assertEquals(1, delivered.size());
            assertEquals(peer.deviceId(), delivered.get(0)
                    .destinationDeviceId().orElseThrow());

            lifecycle.onDisconnected(liveEndpoint[0]);
            peerProof.disconnected("local");
            assertNull(transport.bindings().get(peer.deviceId()));
            liveEndpoint[0] = "endpoint-2";
            peerProof.connected("local");
            lifecycle.onConnectionResult(liveEndpoint[0],
                    new ConnectionResolution(new Status(ConnectionsStatusCodes.STATUS_OK)));
            assertNull(transport.bindings().get(peer.deviceId()));
            inbound.onPayloadReceived(liveEndpoint[0], Payload.fromBytes(announcement));
            assertEquals(liveEndpoint[0], transport.bindings().get(peer.deviceId()));
            engine.router().originate(peer.deviceId(), MessageType.TEXT, Priority.NORMAL,
                    "second directed message".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    0L);
            assertEquals(2, delivered.size());
            assertEquals(peer.deviceId(), delivered.get(1)
                    .destinationDeviceId().orElseThrow());

            // A failed challenge send must retry with a new nonce on the same
            // connection, without waiting for a second announcement.
            lifecycle.onDisconnected(liveEndpoint[0]);
            peerProof.disconnected("local");
            liveEndpoint[0] = "endpoint-3";
            peerProof.connected("local");
            lifecycle.onConnectionResult(liveEndpoint[0],
                    new ConnectionResolution(new Status(ConnectionsStatusCodes.STATUS_OK)));
            failNextProof[0] = true;
            inbound.onPayloadReceived(liveEndpoint[0], Payload.fromBytes(announcement));
            assertNull(transport.bindings().get(peer.deviceId()));
            TestSupport.eventually("failed challenge is retried", 22_000L,
                    () -> liveEndpoint[0].equals(transport.bindings().get(peer.deviceId())));

            // Blocking also removes an existing stable route before disconnect.
            engine.blockPeer(peer.deviceId());
            TestSupport.eventually("blocked peer loses its live stable route", 5_000L,
                    () -> transport.bindings().get(peer.deviceId()) == null);
            assertEquals(liveEndpoint[0], transport.bindings().get(liveEndpoint[0]));
            lifecycle.onDisconnected(liveEndpoint[0]);
            peerProof.disconnected("local");
            liveEndpoint[0] = "endpoint-4";
            peerProof.connected("local");
            lifecycle.onConnectionResult(liveEndpoint[0],
                    new ConnectionResolution(new Status(ConnectionsStatusCodes.STATUS_OK)));
            inbound.onPayloadReceived(liveEndpoint[0], Payload.fromBytes(announcement));
            assertNull(transport.bindings().get(peer.deviceId()));
        } finally {
            engine.close();
            peerRouter.close();
        }
    }
}
