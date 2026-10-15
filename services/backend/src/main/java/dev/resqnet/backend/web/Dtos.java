package dev.resqnet.backend.web;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import dev.resqnet.backend.domain.Category;
import dev.resqnet.backend.domain.IncidentStatus;

/**
 * Request and response bodies.
 *
 * <p>Records rather than classes: there is no inheritance hierarchy to express
 * here, and immutability is the behaviour we want on the way in.
 */
public final class Dtos {

    private Dtos() {
    }

    // ------------------------------------------------------------------ auth

    /** Sign-in request. */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    /** Sign-in response. */
    public record LoginResponse(String token, String username, String role, long expiresInSeconds) {
    }

    // -------------------------------------------------------------- incidents

    /** Everything the dashboard needs to render an incident row. */
    public record IncidentSummary(
            Long id,
            String reference,
            String title,
            Category category,
            String categoryLabel,
            int severity,
            IncidentStatus status,
            String statusLabel,
            boolean advisory,
            Double advisoryConfidence,
            boolean locationShared,
            Double latitude,
            Double longitude,
            String sourceDeviceId,
            String gatewayDeviceId,
            Instant createdAt,
            Instant updatedAt,
            Instant acknowledgedAt,
            Instant resolvedAt,
            Long assignedTo,
            int messageCount) {
    }

    /** Full incident detail including delivery metadata. */
    public record IncidentDetail(
            IncidentSummary incident,
            List<MessageDelivery> messages,
            List<AuditRow> audit) {
    }

    /** Delivery metadata for one message. Never the body. */
    public record MessageDelivery(
            String messageId,
            String originDeviceId,
            String destinationDeviceId,
            String state,
            String priority,
            long createdAt,
            long expiresAt,
            int attempts,
            String detail,
            boolean simulated) {
    }

    /** One audit row. */
    public record AuditRow(Instant occurredAt, String actor, String actorRole, String action,
                           String targetType, String targetId, String detail) {
    }

    /**
     * Status change request.
     *
     * <p>{@code actor} is intentionally absent. Who did something is the
     * authenticated principal, never something a client can state.
     */
    public record UpdateStatusRequest(@NotNull IncidentStatus status,
                                      @Size(max = 160) String note,
                                      @Min(0) @Max(3) Integer severity) {
    }

    /** Assignment request. */
    public record AssignRequest(@NotBlank Long accountId) {
    }

    /** Manual creation by a responder. */
    public record CreateIncidentRequest(@NotBlank @Size(max = 160) String title,
                                        @NotNull Category category,
                                        @Min(0) @Max(3) Integer severity,
                                        Double latitude,
                                        Double longitude) {
    }

    // ---------------------------------------------------------------- gateway

    /**
     * One delivery summary reported by a handset.
     *
     * <p>There is no body field. The device reports metadata only, and this
     * record reflects that decision rather than merely omitting a field.
     */
    public record GatewayMessageReport(
            @NotBlank @Size(max = 160) String messageId,
            @Size(max = 24) String state,
            @Size(max = 16) String priority,
            @Size(max = 32) String type,
            @Size(max = 32) String category,
            Double advisoryConfidence,
            @Size(max = 96) String origin,
            @Size(max = 96) String destination,
            long createdAt,
            long expiresAt,
            @Min(0) @Max(10_000) Integer attempts,
            @Size(max = 255) String detail,
            @Size(max = 160) String incidentReference) {
    }

    /** A batch from one gateway. */
    public record GatewaySyncRequest(@NotBlank @Size(max = 96) String source,
                                     @Size(max = 96) String deviceId,
                                     @Size(max = 500) List<GatewayMessageReport> records) {
    }

    /** Sync outcome. */
    public record GatewaySyncResponse(String runId, int acceptedRecords, int createdIncidents,
                                      int updatedIncidents, int rejectedRecords,
                                      String rejection) {
    }

    // ------------------------------------------------------------- simulator

    /** A simulation run summary, as produced by mesh-simulator. */
    public record SimulationRunReport(
            SimulationMeta simulation,
            Outcome outcome,
            double deliveryRate,
            double amplificationFactor,
            Latency latencyMillis,
            Radio radio,
            List<SimulationNode> nodes) {
    }

    /** Scenario description echoed from the run. */
    public record SimulationMeta(String topology, int nodeCount, int linkCount, long seed,
                                 int messageCount, int rounds, int hopLimit) {
    }

    /** Counters measured during a run. */
    public record Outcome(long originated, long acknowledged, long expired, long failed,
                          long stillQueued, long duplicatesSuppressed, long forwards) {
    }

    /** Latency distribution in milliseconds. */
    public record Latency(long median, long p95, long p99, long max) {
    }

    /** Radio-plane statistics. */
    public record Radio(long framesSent, long framesDelivered, long framesDropped,
                        long linkChanges, int partitions) {
    }

    /** Per-node counters. */
    public record SimulationNode(int index, String label, long originated, long forwarded,
                                 long duplicatesSuppressed, long stillQueued) {
    }

    /** Stored simulation run. */
    /**
     * One stored simulation run.
     *
     * <p>{@code simulated} is a constant {@code true}. It is carried
     * explicitly rather than implied by the containing endpoint so that a
     * dashboard merging generated runs with field incidents cannot lose the
     * distinction further downstream.
     */
    public record SimulationRunSummary(String runId, String topology, int nodeCount, long seed,
                                       Instant startedAt, long originated, long acknowledged,
                                       double deliveryRate, Long medianLatencyMillis,
                                       Long p95LatencyMillis, Double amplification,
                                       int partitions, boolean simulated) {

        public SimulationRunSummary {
            simulated = true;
        }
    }
}
