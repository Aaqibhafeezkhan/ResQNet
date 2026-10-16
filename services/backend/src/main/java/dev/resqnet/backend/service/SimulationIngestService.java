package dev.resqnet.backend.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.resqnet.backend.web.Dtos;

/**
 * Stores simulation runs so the dashboard can display them.
 *
 * <p>Every stored record carries {@code simulated = true} and the run id that
 * produced it. There is no path by which generated data is presented as field
 * telemetry: the dashboard labels it, the API labels it, and the database has a
 * column that says so.
 */
@Service
public class SimulationIngestService {

    private static final Logger log = LoggerFactory.getLogger(SimulationIngestService.class);

    /** Events retained per run. Beyond this the dashboard shows the tail. */
    private static final int MAX_EVENTS = 4_000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private final IncidentEventPublisher events;

    public SimulationIngestService(JdbcTemplate jdbc, ObjectMapper mapper,
                                   IncidentEventPublisher events) {
        this.events = events;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Stores a run report.
     *
     * <p>Accepts the document mesh-simulator emits verbatim rather than a
     * reshaped DTO, so the CLI and the backend cannot drift apart.
     */
    @Transactional
    public Map<String, Object> ingest(String body) {
        String runId = "sim-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode simulation = root.path("simulation");
            JsonNode outcome = root.path("outcome");
            JsonNode latency = root.path("latencyMillis");
            JsonNode radio = root.path("radio");

            jdbc.update("""
                            INSERT INTO simulation_runs (run_id, topology, node_count, seed,
                                originated, acknowledged, delivery_rate, median_latency_ms,
                                p95_latency_ms, amplification, partitions, report)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    runId,
                    simulation.path("topology").asText("unknown"),
                    simulation.path("nodeCount").asInt(0),
                    simulation.path("seed").asLong(0L),
                    outcome.path("originated").asLong(0L),
                    outcome.path("acknowledged").asLong(0L),
                    root.path("deliveryRate").asDouble(0),
                    latency.path("median").asLong(-1L),
                    latency.path("p95").asLong(-1L),
                    root.path("amplificationFactor").asDouble(0),
                    radio.path("partitions").asInt(0),
                    truncate(body));

            int stored = 0;
            for (JsonNode event : root.path("events")) {
                if (stored >= MAX_EVENTS) {
                    break;
                }
                jdbc.update("""
                                INSERT INTO simulation_events (run_id, at_epoch_millis,
                                    round_number, node_label, kind, message_id, hop_count,
                                    reason, detail)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        runId,
                        event.path("atEpochMillis").asLong(0L),
                        event.path("round").asInt(0),
                        event.path("node").asText("?"),
                        event.path("kind").asText("other"),
                        nullable(event.path("messageId")),
                        event.path("hopCount").asInt(-1),
                        nullable(event.path("reason")),
                        truncate(event.path("detail").asText("")));
                stored++;
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("runId", runId);
            response.put("acceptedEvents", stored);
            response.put("simulated", true);

            // Announced on the live stream so a dashboard that is showing the
            // topology panel updates without waiting for its next poll. The
            // detail is explicit that this is generated data: a run appearing
            // in a responder console must never be mistakable for a handset
            // reporting from a street.
            events.stage(new IncidentEventPublisher.IncidentEvent(
                    IncidentEventPublisher.SIMULATION_INGESTED, runId,
                    java.time.Instant.now().toString(),
                    "SIMULATED run ingested: " + stored + " events",
                    null));
            return response;
        } catch (Exception e) {
            log.warn("Rejected a simulation report: {}", e.getMessage());
            throw new IllegalArgumentException("Malformed simulation report", e);
        }
    }

    /** Recent runs, newest first. */
    @Transactional(readOnly = true)
    public List<Dtos.SimulationRunSummary> recentRuns(int limit) {
        return jdbc.query("""
                SELECT run_id, topology, node_count, seed, started_at, originated,
                       acknowledged, delivery_rate, median_latency_ms, p95_latency_ms,
                       amplification, partitions
                FROM simulation_runs ORDER BY started_at DESC LIMIT ?
                """, (rs, row) -> new Dtos.SimulationRunSummary(
                rs.getString("run_id"), rs.getString("topology"), rs.getInt("node_count"),
                rs.getLong("seed"), rs.getTimestamp("started_at").toInstant(),
                rs.getLong("originated"), rs.getLong("acknowledged"),
                rs.getDouble("delivery_rate"),
                nullableLong(rs, "median_latency_ms"), nullableLong(rs, "p95_latency_ms"),
                nullableDouble(rs, "amplification"), rs.getInt("partitions"), true), limit);
    }

    /** Events for a run, oldest first, capped. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(String runId, int limit) {
        return jdbc.query("""
                SELECT at_epoch_millis, round_number, node_label, kind, message_id,
                       hop_count, reason, detail
                FROM simulation_events WHERE run_id = ? ORDER BY id ASC LIMIT ?
                """, (rs, row) -> {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("atEpochMillis", rs.getLong("at_epoch_millis"));
            event.put("round", rs.getInt("round_number"));
            event.put("node", rs.getString("node_label"));
            event.put("kind", rs.getString("kind"));
            event.put("messageId", rs.getString("message_id"));
            event.put("hopCount", rs.getInt("hop_count"));
            event.put("reason", rs.getString("reason"));
            event.put("detail", rs.getString("detail"));
            event.put("simulated", true);
            return event;
        }, runId, limit);
    }

    private static String nullable(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    /** Timestamps are stored by the database, not by the caller. */
    public Instant receivedAt() {
        return Instant.now();
    }

    /** Truncates a value that would otherwise exceed its column. */
    static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 509) + "...";
    }
}
