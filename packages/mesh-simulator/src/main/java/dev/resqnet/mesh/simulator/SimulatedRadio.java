package dev.resqnet.mesh.simulator;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.resqnet.mesh.routing.MeshTransport;

/**
 * Radio adapter for one simulated node.
 *
 * <p>Implements the same {@link MeshTransport} contract the Android app
 * implements over Nearby Connections. The only difference is where the bytes go:
 * a queue inside the simulation instead of a Bluetooth or Wi-Fi Direct socket.
 */
final class SimulatedRadio implements MeshTransport {

    private final SimulationNetwork network;
    private final int selfIndex;
    private final Set<String> peers = new LinkedHashSet<>();

    private InboundHandler inbound = (from, payload) -> {
    };
    private MeshTransport.ConnectionListener listener = new MeshTransport.ConnectionListener() {
    };
    private boolean started;

    SimulatedRadio(SimulationNetwork network, int selfIndex) {
        this.network = network;
        this.selfIndex = selfIndex;
    }

    void addPeer(int peerIndex) {
        peers.add(nodeId(peerIndex));
        listener.onPeerConnected(nodeId(peerIndex));
    }

    void removePeer(int peerIndex) {
        peers.remove(nodeId(peerIndex));
        listener.onPeerDisconnected(nodeId(peerIndex));
    }

    void deliver(int fromIndex, byte[] payload) {
        inbound.onPayload(nodeId(fromIndex), payload);
    }

    @Override
    public Optional<Long> send(String peerDeviceId, byte[] encodedPayload) {
        return network.send(selfIndex, indexOf(peerDeviceId), encodedPayload);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ordered by node index, not by device id. Identity is a hash of a freshly
     * generated key, so ordering by it would differ between runs and would make
     * the simulator non-reproducible. The routing engine relies on this order
     * being stable when the fan-out cap chooses which peers to forward to.
     */
    @Override
    public Set<String> connectedPeerIds() {
        List<Integer> indices = new ArrayList<>();
        for (String peerId : peers) {
            indices.add(indexOf(peerId));
        }
        indices.sort(Integer::compareTo);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (int index : indices) {
            ordered.add(nodeId(index));
        }
        return Collections.unmodifiableSet(ordered);
    }

    @Override
    public void setInboundHandler(InboundHandler handler) {
        this.inbound = handler;
    }

    @Override
    public void setConnectionListener(ConnectionListener connectionListener) {
        this.listener = connectionListener;
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void stop() {
        started = false;
        peers.clear();
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    private String nodeId(int index) {
        SimulatedNode node = network.nodes().get(index);
        return node == null ? "n" + index : node.deviceId();
    }

    private int indexOf(String deviceId) {
        for (SimulatedNode node : network.nodes().values()) {
            if (node.deviceId().equals(deviceId)) {
                return node.index();
            }
        }
        return -1;
    }
}