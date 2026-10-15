package dev.resqnet.mesh.routing;

import java.util.List;

import dev.resqnet.mesh.protocol.MessageEnvelope;

/**
 * Structured, ordered record of everything the routing engine did.
 *
 * <p>The event stream is not a debugging afterthought. It is the contract
 * between three consumers: the unit tests, which assert on routing behaviour
 * through it; the simulator, which replays it to the dashboard; and the Android
 * UI, which renders delivery progress from it. Because it is a sealed hierarchy,
 * a listener that switches over it will not silently miss a new event kind.
 *
 * @param atEpochMillis when the event happened, from the injected clock
 */
public sealed interface MeshEvent {

    long atEpochMillis();

    /** A validated envelope accepted by a relay node. */
    record Accepted(long atEpochMillis, String relayNodeId, MessageEnvelope envelope,
                    int hopCount, int hopLimit, String fromPeerDeviceId) implements MeshEvent {
        @Override
        public String toString() {
            return "Accepted{" + envelope.messageId() + " via " + fromPeerDeviceId
                    + " hop=" + hopCount + "/" + hopLimit + "}";
        }
    }

    /** An envelope handed to one or more directly connected neighbours. */
    record Forwarded(long atEpochMillis, String relayNodeId, String messageId,
                     List<String> peerDeviceIds, int hopCount) implements MeshEvent {
        public Forwarded {
            peerDeviceIds = List.copyOf(peerDeviceIds);
        }

        @Override
        public String toString() {
            return "Forwarded{" + messageId + " to " + peerDeviceIds.size() + " peer(s)}";
        }
    }

    /** An envelope reached the device it was addressed to. */
    record Delivered(long atEpochMillis, String deviceId, MessageEnvelope envelope) implements MeshEvent {
        @Override
        public String toString() {
            return "Delivered{" + envelope.messageId() + " to " + deviceId + "}";
        }
    }

    /** An acknowledgement for one of our messages arrived. */
    record AcknowledgementReceived(long atEpochMillis, String deviceId, String messageId,
                                   String acknowledgedBy) implements MeshEvent {
        @Override
        public String toString() {
            return "Ack{" + messageId + " from " + acknowledgedBy + "}";
        }
    }

    /** This node generated an acknowledgement for a message it received. */
    record AcknowledgementSent(long atEpochMillis, String deviceId, String messageId,
                               String acknowledgedTo) implements MeshEvent {
        @Override
        public String toString() {
            return "AckSent{" + messageId + " to " + acknowledgedTo + "}";
        }
    }

    /** A peer connected or disconnected. */
    record PeerConnectionChanged(long atEpochMillis, String deviceId, boolean connected)
            implements MeshEvent {
        @Override
        public String toString() {
            return (connected ? "PeerUp{" : "PeerDown{") + deviceId + "}";
        }
    }

    /**
     * An envelope was refused.
     *
     * <p>Identities are carried as structured fields rather than interpolated
     * into {@code detail}. Free-text identity strings leak a 69 character key
     * derived id into every log line and make otherwise reproducible reports
     * differ between runs.
     *
     * @param originDeviceId origin as claimed by the envelope, may be null
     * @param fromPeerDeviceId the neighbour that delivered the frame, may be null
     */
    record Rejected(long atEpochMillis, String relayNodeId, String messageId, String originDeviceId,
                    dev.resqnet.mesh.protocol.RejectReason reason, String detail,
                    String fromPeerDeviceId)
            implements MeshEvent {
        @Override
        public String toString() {
            return "Rejected{" + messageId + " " + reason + ": " + detail + "}";
        }
    }

    /** A locally originated message changed delivery state. */
    record DeliveryStateChanged(long atEpochMillis, String deviceId, String messageId,
                                dev.resqnet.mesh.protocol.DeliveryState previous,
                                dev.resqnet.mesh.protocol.DeliveryState current, String detail)
            implements MeshEvent {
        @Override
        public String toString() {
            return "State{" + messageId + " " + previous + "->" + current + ": " + detail + "}";
        }
    }

    /** The advisory triage classifier produced a suggestion for a message. */
    record TriageSuggestion(long atEpochMillis, String deviceId, String messageId,
                            dev.resqnet.mesh.triage.TriageResult result) implements MeshEvent {
        @Override
        public String toString() {
            return "Triage{" + messageId + " -> " + result.category() + "}";
        }
    }

    /** A peer asked us to relay a message but we had nowhere to put it. */
    record BackpressureApplied(long atEpochMillis, String relayNodeId, int pendingMessages,
                               int capacity) implements MeshEvent {
        @Override
        public String toString() {
            return "Backpressure{" + pendingMessages + "/" + capacity + "}";
        }
    }
}