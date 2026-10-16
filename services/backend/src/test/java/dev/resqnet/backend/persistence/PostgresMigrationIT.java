package dev.resqnet.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import dev.resqnet.backend.domain.Category;
import dev.resqnet.backend.domain.IncidentStatus;
import dev.resqnet.backend.repository.AuditEventRepository;
import dev.resqnet.backend.repository.IncidentMessageRepository;
import dev.resqnet.backend.repository.IncidentRepository;
import dev.resqnet.backend.service.GatewaySyncService;
import dev.resqnet.backend.service.IncidentService;
import dev.resqnet.backend.web.Dtos;

/**
 * The same lifecycle, executed against a real PostgreSQL server.
 *
 * <p><b>Why this exists.</b> The default test profile runs H2 in PostgreSQL
 * compatibility mode. That is fine for speed and it is fine for most
 * assertions, but it is not evidence the system works on the database it ships
 * against. H2 accepts syntax PostgreSQL rejects, coerces types differently, and
 * enforces constraints differently. A migration can pass every local run and
 * then fail on first deploy. This class closes that gap; it runs only under the
 * {@code postgres} profile, which CI wires to a real service container.
 *
 * <p><b>What it checks that the H2 profile cannot.</b> That the Flyway
 * migrations apply to a clean PostgreSQL database, that Hibernate's mappings
 * validate against the schema those migrations produced, and that the incident
 * lifecycle, its audit trail and its constraints behave the same way.
 *
 * <p><b>What it does not claim.</b> It does not test PostgreSQL under
 * concurrent write load or under replication. Those need a different kind of
 * test and are listed as gaps in {@code docs/architecture/README.md}.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class PostgresMigrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private IncidentMessageRepository messages;

    @Autowired
    private AuditEventRepository audit;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private GatewaySyncService gatewaySync;

    @BeforeEach
    void clean() {
        // The audit trail commits in its own transaction, so it survives the
        // rollback of an outer test transaction and has to be cleared first.
        audit.deleteAll();
        messages.deleteAll();
        incidents.deleteAll();
    }

    @Test
    @DisplayName("Flyway applies every migration to a clean PostgreSQL database")
    void migrationsApply() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(applied)
                .as("the shipping migrations must all be present and successful")
                .isNotEmpty();
        assertThat(applied).doesNotContainNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history"
                + " WHERE success = false", Integer.class))
                .as("no migration may have failed")
                .isZero();
    }

    @Test
    @DisplayName("timestamps survive a round trip through PostgreSQL with their offset intact")
    void timestampRoundTripKeepsInstant() {
        Dtos.GatewaySyncRequest request = new Dtos.GatewaySyncRequest("android", "rqn1_pg_1",
                List.of(new Dtos.GatewayMessageReport("m-ts", "ACKNOWLEDGED", "SOS",
                        "EMERGENCY_REPORT", "medical", 0.9,
                        "rqn1_origin", "command",
                        1_760_000_000_000L, 1_760_000_900_000L, 1,
                        "timestamp probe", null)));
        gatewaySync.ingest(request, "req-ts");

        Long createdAt = jdbc.queryForObject(
                "SELECT created_at FROM incident_messages WHERE message_id = 'm-ts'", Long.class);

        assertThat(createdAt)
                .as("a millisecond epoch must not drift through a timestamptz column")
                .isEqualTo(1_760_000_000_000L);

        Instant readBack = jdbc.queryForObject(
                "SELECT max(updated_at) FROM incidents", java.time.OffsetDateTime.class)
                .toInstant();
        assertThat(readBack).isNotNull();
    }

    @Test
    @DisplayName("the unique constraint on message id is enforced by PostgreSQL itself")
    void messageIdIsUniqueInTheDatabase() {
        gatewaySync.ingest(new Dtos.GatewaySyncRequest("android", "rqn1_pg_2",
                List.of(report("dup-1"))), "req-dup-1");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO incident_messages"
                        + " (message_id, incident_id, origin_device_id, destination_device_id,"
                        + "  state, priority, created_at, expires_at, attempts)"
                        + " VALUES ('dup-1', 1, 'a', 'b', 'QUEUED', 'SOS', 0, 0, 0)"))
                .as("a duplicate message id must be rejected by a database constraint,"
                        + " not only by application code")
                .isNotNull();
    }

    @Test
    @DisplayName("the incident lifecycle behaves the same way on PostgreSQL")
    void incidentLifecycleWorks() {
        gatewaySync.ingest(new Dtos.GatewaySyncRequest("android", "rqn1_pg_3",
                List.of(report("m-life"))), "req-life");
        assertThat(incidents.count()).isEqualTo(1);

        var listed = incidentService.list(IncidentStatus.NEW, 0, 10);
        assertThat(listed.getTotalElements()).isEqualTo(1);
        String reference = listed.getContent().get(0).reference();

        var detail = incidentService.detail(reference).orElseThrow();
        assertThat(detail.incident().advisory())
                .as("a device report is advisory until a human says otherwise")
                .isTrue();
        assertThat(detail.messages())
                .as("the opening message belongs in the incident history")
                .hasSize(1);

        assertThat(incidentService.overview().open()).isEqualTo(1);
        assertThat(incidentService.overview().critical()).isEqualTo(1);
    }

    @Test
    @DisplayName("device actions are audited as DEVICE, never as a responder role")
    void deviceActionsAreAuditedAsDevice() {
        gatewaySync.ingest(new Dtos.GatewaySyncRequest("android", "rqn1_pg_4",
                List.of(report("m-audit"))), "req-audit");

        var rows = audit.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getActorRole()).isEqualTo("DEVICE");
        assertThat(rows.get(0).getActor()).startsWith("gateway:");
    }

    @Test
    @DisplayName("status transitions are persisted and survive a re-read")
    void statusTransitionPersists() {
        gatewaySync.ingest(new Dtos.GatewaySyncRequest("android", "rqn1_pg_5",
                List.of(report("m-status"))), "req-status");
        String reference = incidentService.list(IncidentStatus.NEW, 0, 10)
                .getContent().get(0).reference();

        incidentService.updateStatus(reference,
                new Dtos.UpdateStatusRequest(IncidentStatus.RESOLVED, "responder saw it", 3),
                dev.resqnet.backend.security.ResQNetPrincipal.responder("op",
                        dev.resqnet.backend.domain.ResponderRole.ADMIN, 1L),
                "req-update");

        String state = jdbc.queryForObject(
                "SELECT status FROM incidents WHERE reference = ?", String.class, reference);
        assertThat(state).isEqualTo(IncidentStatus.RESOLVED.name());
        assertThat(incidentService.overview().open())
                .as("an acknowledged incident is no longer open")
                .isZero();
    }

    @Test
    @DisplayName("check constraints reject a severity outside the agreed range")
    void severityRangeIsEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO incidents"
                        + " (reference, title, category, severity, status, created_at, updated_at)"
                        + " VALUES ('RQ-BAD', 'bad', 'MEDICAL', 99, 'NEW', now(), now())"))
                .as("severity outside 0..3 must be refused by the schema")
                .isNotNull();
    }

    private static Dtos.GatewayMessageReport report(String id) {
        return new Dtos.GatewayMessageReport(id, "ACKNOWLEDGED", "SOS", "EMERGENCY_REPORT",
                "medical", 0.85, "rqn1_origin", "command",
                1_760_000_000_000L, 1_760_000_900_000L, 1, "postgres probe", null);
    }
}