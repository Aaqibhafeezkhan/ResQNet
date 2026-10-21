package dev.resqnet.android.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * The endpoint to identity mapping, which is where a silent defect lived.
 *
 * <p>Nothing ever promoted a binding, so a peer was known only by its Nearby
 * endpoint id: a per-session radio handle that rotates on reboot and whenever
 * the OS switches between Bluetooth and Wi-Fi Direct.
 *
 * <p>{@code MeshRouter.selectForwardTargets} asks the transport which peers it
 * can reach and checks whether the message's destination is among them. With
 * radio handles in that set and a key-derived device id in the envelope, the
 * comparison never matched, so every directed message flooded to every peer
 * instead of going to the one that could deliver it. Nothing crashed and the
 * message still arrived, one hop later and at the cost of every neighbour's
 * battery, which is why it went unnoticed until the code was read.
 */
public class PeerBindingsTest {

    @Test
    public void aNewEndpointIsBoundProvisionallyToItself() {
        PeerBindings bindings = new PeerBindings();

        assertEquals("endpoint-A", bindings.bind("endpoint-A"));
        assertEquals("endpoint-A", bindings.resolve("endpoint-A"));
        assertTrue(bindings.connectedPeerIds().contains("endpoint-A"));
    }

    @Test
    public void promotionReplacesTheRadioHandleWithTheDeviceIdentity() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");

        assertTrue(bindings.promote("endpoint-A", "rqn1_abc"));

        assertEquals("rqn1_abc", bindings.resolve("endpoint-A"));
        assertEquals("endpoint-A", bindings.endpointFor("rqn1_abc"));
        assertTrue(bindings.connectedPeerIds().contains("rqn1_abc"));
        assertFalse("the radio handle must not linger in the peer set, or the router"
                + " keeps seeing an id no envelope will ever name",
                bindings.connectedPeerIds().contains("endpoint-A"));
    }

    @Test
    public void aPromotedPeerResolvesInBothDirections() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_abc");

        // This is the exact lookup the transport depends on when the router
        // asks it to send: a device id from the envelope, and the radio handle
        // to send it through.
        assertEquals("endpoint-A", bindings.endpointFor("rqn1_abc"));
        assertTrue(bindings.connectedPeerIds().contains("rqn1_abc"));
    }

    @Test
    public void aProvenReplacementDemotesTheOldEndpoint() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("old");
        bindings.promote("old", "rqn1_abc");
        bindings.bind("new");
        bindings.promote("new", "rqn1_abc");

        assertEquals("new", bindings.endpointFor("rqn1_abc"));
        assertEquals("old", bindings.resolve("old"));
        assertEquals("rqn1_abc", bindings.resolve("new"));
        bindings.unbind("old");
        assertEquals("new", bindings.endpointFor("rqn1_abc"));
    }

    @Test
    public void blockingAConnectedPeerRemovesItsStableRouteImmediately() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_abc");

        assertEquals("endpoint-A", bindings.demote("rqn1_abc"));
        assertNull(bindings.endpointFor("rqn1_abc"));
        assertEquals("endpoint-A", bindings.resolve("endpoint-A"));
        assertTrue(bindings.connectedPeerIds().contains("endpoint-A"));
    }

    @Test
    public void promotingTwiceIsANoOp() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_abc");

        assertFalse("a repeated claim must not emit another disconnect and connect,"
                + " which would make the router sweep its retry queue for nothing",
                bindings.promote("endpoint-A", "rqn1_abc"));
    }

    @Test
    public void aNewConnectionUsingTheSameHandleRequiresFreshProof() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_abc");

        assertEquals("endpoint-A", bindings.bind("endpoint-A"));
        assertNull(bindings.endpointFor("rqn1_abc"));
    }

    /**
     * A reconnect arrives under a fresh endpoint id, which is what Nearby does
     * every time a link is re-established.
     *
     * <p>Nothing unbinds the old handle first: the OS simply stops mentioning
     * it, which is exactly what a stale handle looks like. The reverse mapping
     * has to follow the device to its new endpoint, or the only route to a peer
     * this device already trusts is one that no longer exists.
     */
    @Test
    public void aReconnectUnderAFreshEndpointMovesTheBinding() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_abc");
        bindings.bind("endpoint-B");

        assertTrue("the reconnect must be recognised as a change",
                bindings.promote("endpoint-B", "rqn1_abc"));

        assertEquals("the device must resolve to the live handle",
                "endpoint-B", bindings.snapshot().get("rqn1_abc"));
        assertEquals("endpoint-B", bindings.endpointFor("rqn1_abc"));
        assertEquals("the new handle must resolve back to the device",
                "rqn1_abc", bindings.resolve("endpoint-B"));
        assertEquals("a still-connected old handle must lose the stable identity",
                "endpoint-A", bindings.resolve("endpoint-A"));
        assertEquals("old and new links remain distinct until a disconnect arrives",
                2, bindings.connectedPeerIds().size());
    }

    @Test
    public void unbindReportsTheIdentityThatWasBound() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_abc");

        assertEquals("rqn1_abc", bindings.unbind("endpoint-A"));
        assertTrue(bindings.connectedPeerIds().isEmpty());
        assertNull(bindings.endpointFor("rqn1_abc"));
    }

    @Test
    public void unbindOfAnUnknownEndpointIsHarmless() {
        PeerBindings bindings = new PeerBindings();

        assertNull(bindings.unbind("never-seen"));
        assertEquals("an endpoint nothing is bound to still has to resolve, because a"
                + " payload can arrive before the binding is recorded",
                "never-seen", bindings.resolve("never-seen"));
    }

    @Test
    public void endpointIdsAndDeviceIdsDoNotCollide() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-A");
        bindings.bind("endpoint-B");
        bindings.promote("endpoint-A", "rqn1_abc");

        assertEquals("rqn1_abc", bindings.resolve("endpoint-A"));
        assertEquals("endpoint-B", bindings.resolve("endpoint-B"));
        assertEquals(2, bindings.size());
    }

    @Test
    public void aFailedConnectionCanBeRetried() {
        PeerBindings bindings = new PeerBindings();

        assertTrue(bindings.markRequested("endpoint-A"));
        assertFalse("a second request for the same endpoint is refused",
                bindings.markRequested("endpoint-A"));

        bindings.clearRequested("endpoint-A");
        assertTrue("after a failure the endpoint must be requestable again,"
                + " or one failed attempt disables it for the whole session",
                bindings.markRequested("endpoint-A"));
    }

    @Test
    public void discoveredEndpointsAreSortedForStableDisplay() {
        PeerBindings bindings = new PeerBindings();
        bindings.markDiscovered("endpoint-C");
        bindings.markDiscovered("endpoint-A");
        bindings.markDiscovered("endpoint-B");

        List<String> discovered = bindings.discoveredEndpoints();
        assertEquals(List.of("endpoint-A", "endpoint-B", "endpoint-C"), discovered);
    }

    @Test
    public void clearAllForgetsEverything() {
        PeerBindings bindings = new PeerBindings();
        bindings.markDiscovered("endpoint-A");
        bindings.markRequested("endpoint-A");
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_abc");

        bindings.clearAll();

        assertEquals(0, bindings.size());
        assertTrue(bindings.discoveredEndpoints().isEmpty());
        assertTrue("a stale requested flag would stop the radio ever retrying",
                bindings.markRequested("endpoint-A"));
    }

    @Test
    public void snapshotIsOrderedAndImmutable() {
        PeerBindings bindings = new PeerBindings();
        bindings.bind("endpoint-B");
        bindings.promote("endpoint-B", "rqn1_b");
        bindings.bind("endpoint-A");
        bindings.promote("endpoint-A", "rqn1_a");

        assertEquals(List.of("rqn1_a", "rqn1_b"),
                List.copyOf(bindings.snapshot().keySet()));
    }
}
