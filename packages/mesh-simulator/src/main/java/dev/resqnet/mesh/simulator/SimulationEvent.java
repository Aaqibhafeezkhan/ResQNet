package dev.resqnet.mesh.simulator;

import java.util.List;

import dev.resqnet.mesh.protocol.RejectReason;
import dev.resqnet.mesh.routing.MeshEvent;
import dev.resqnet.mesh.simulator.json.JsonObject;

/**
 * A routing event flattened into a shape the gateway and dashboard can consume.
 *
 * <p>The simulation is the only source of data on a responder dashboard when no
 * real gateway has ever connected, so every event carries its provenance. The
 * dashboard labels simulator-sourced nodes and events explicitly; it must never
 * present them as field telemetry.
 *
 * @param atEpochMillis simulated wall-clock time
 * @param round simulation round
 * @param nodeIndex index of the node the event happened on
 * @param nodeShortId stable short label for the dashboard topology view
 * @param kind event discriminator, e.g. {@code accepted}, {@code forwarded}
 * @param messageId protocol message id, {@code null} for topology events
 * @param peerIndexes peers involved, empty when not applicable
 * @param hopCount hops travelled at the time of the event, -1 when unknown
 * @param reason rejection reason, {@code null} when the event was accepted
 * @param detail human-readable note
 * @param simulated always true; consumed by the dashboard to label provenance
 */
public record SimulationEvent(
        long atEpochMillis,
        int round,
        int nodeIndex,
        String nodeShortId,
        String kind,
        String messageId,
        List<Integer> peerIndexes,
        int hopCount,
        RejectReason reason,
        String detail,
        boolean simulated
) {

    public SimulationEvent {
        peerIndexes = peerIndexes == null ? List.of() : List.copyOf(peerIndexes);
    }

    /** Maps a device identity onto a readable node label. */
    @FunctionalInterface
    public interface NodeResolver {
        String labelOf(String deviceId);
    }

    /**
     * Flattens a {@link MeshEvent} into its transport-friendly form.
     *
     * <p>Device identities are resolved to node labels rather than embedded raw.
     * A 69 character identity in every event line is unreadable on a dashboard,
     * and because key material is regenerated on every run it would also make an
     * otherwise reproducible report differ between runs.
     */
    static SimulationEvent from(MeshEvent event, int round, int nodeIndex,
                                NodeResolver resolver) {
        String nodeShortId = "n" + nodeIndex;
        if (event instanceof MeshEvent.Accepted accepted) {
            return new SimulationEvent(accepted.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "accepted", accepted.envelope().messageId(), List.of(),
                    accepted.hopCount(), null, "from " + resolver.labelOf(accepted.fromPeerDeviceId()), true);
        }
        if (event instanceof MeshEvent.Forwarded forwarded) {
            return new SimulationEvent(forwarded.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "forwarded", forwarded.messageId(), List.of(), forwarded.hopCount(), null,
                    "relayed to " + forwarded.peerDeviceIds().size() + " peer(s)", true);
        }
        if (event instanceof MeshEvent.Delivered delivered) {
            return new SimulationEvent(delivered.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "delivered", delivered.envelope().messageId(), List.of(),
                    delivered.envelope().hopCount(), null, "arrived at destination", true);
        }
        if (event instanceof MeshEvent.AcknowledgementReceived ack) {
            return new SimulationEvent(ack.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "acknowledged", ack.messageId(), List.of(), -1, null,
                    "confirmed by " + resolver.labelOf(ack.acknowledgedBy()), true);
        }
        if (event instanceof MeshEvent.AcknowledgementSent ack) {
            return new SimulationEvent(ack.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "acknowledgement-sent", ack.messageId(), List.of(), -1, null,
                    "confirming to " + resolver.labelOf(ack.acknowledgedTo()), true);
        }
        if (event instanceof MeshEvent.PeerConnectionChanged peer) {
            return new SimulationEvent(peer.atEpochMillis(), round, nodeIndex, nodeShortId,
                    peer.connected() ? "peer-connected" : "peer-disconnected", null, List.of(),
                    -1, null, "peer " + resolver.labelOf(peer.deviceId()), true);
        }
        if (event instanceof MeshEvent.Rejected rejected) {
            return new SimulationEvent(rejected.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "rejected", rejected.messageId(),
                    List.of(), -1, rejected.reason(),
                    rejected.detail() + " (via " + resolver.labelOf(rejected.fromPeerDeviceId())
                            + ", origin " + resolver.labelOf(rejected.originDeviceId()) + ")",
                    true);
        }
        if (event instanceof MeshEvent.DeliveryStateChanged state) {
            return new SimulationEvent(state.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "state", state.messageId(), List.of(), -1, null,
                    state.previous() + " -> " + state.current() + ": " + state.detail(), true);
        }
        if (event instanceof MeshEvent.TriageSuggestion triage) {
            return new SimulationEvent(triage.atEpochMillis(), round, nodeIndex, nodeShortId,
                    "triage", triage.messageId(), List.of(), -1, null,
                    triage.result().summary(), true);
        }
        if (event instanceof MeshEvent.BackpressureApplied backpressure) {
            return new SimulationEvent(backpressure.atEpochMillis(), round, nodeIndex,
                    nodeShortId, "backpressure", null, List.of(), -1, null,
                    backpressure.pendingMessages() + "/" + backpressure.capacity() + " queued",
                    true);
        }
        return new SimulationEvent(0L, round, nodeIndex, nodeShortId, "other", null, List.of(),
                -1, null, event.getClass().getSimpleName(), true);
    }

    /** JSON projection used by the gateway ingestion endpoint. */
    public JsonObject toJson() {
        return JsonObject.object()
                .put("atEpochMillis", atEpochMillis)
                .put("round", round)
                .put("node", nodeShortId)
                .put("kind", kind)
                .put("messageId", messageId)
                .putObjects("peers", peerIndexes.stream().map(Object::toString).toList())
                .put("hopCount", hopCount)
                .put("reason", reason == null ? null : reason.name())
                .put("detail", detail)
                .put("simulated", simulated);
    }

}