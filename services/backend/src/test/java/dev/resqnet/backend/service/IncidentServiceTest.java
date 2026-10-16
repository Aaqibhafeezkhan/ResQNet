package dev.resqnet.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import dev.resqnet.backend.domain.Category;
import dev.resqnet.backend.domain.Incident;
import dev.resqnet.backend.domain.IncidentStatus;
import dev.resqnet.backend.domain.ResponderRole;
import dev.resqnet.backend.repository.IncidentRepository;
import dev.resqnet.backend.repository.ResponderAccountRepository;
import dev.resqnet.backend.security.ResQNetPrincipal;
import dev.resqnet.backend.web.Dtos;

/**
 * Incident lifecycle, against the real schema and the real migrations.
 *
 * <p>Runs on H2 in PostgreSQL compatibility mode so the Flyway migrations that
 * ship are the ones under test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidentServiceTest {

    @Autowired
    private IncidentService incidents;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private ResponderAccountRepository accounts;

    @Autowired
    private AuditService audit;

    private ResQNetPrincipal responder;
    private ResQNetPrincipal observer;
    private ResQNetPrincipal gateway;

    @Autowired
    private dev.resqnet.backend.repository.IncidentMessageRepository messageRepository;

    @Autowired
    private dev.resqnet.backend.repository.AuditEventRepository auditRepository;

    @BeforeEach
    void setUp() {
        // The in-memory database outlives a single test, and the audit trail is
        // written in its own transaction so it survives rollback. Clearing first
        // keeps every assertion about counts honest.
        messageRepository.deleteAll();
        auditRepository.deleteAll();
        incidentRepository.deleteAll();

        responder = ResQNetPrincipal.responder("responder", ResponderRole.RESPONDER, 1L);
        observer = ResQNetPrincipal.responder("observer", ResponderRole.OBSERVER, 2L);
        gateway = ResQNetPrincipal.gateway("rqn1_gateway");
    }

    @Test
    @DisplayName("creates an advisory incident from a device report")
    void createsFromDeviceReport() {
        Incident incident = incidents.createFromReport("Fire reported (new)",
                Category.FIRE, 0.61, "rqn1_origin", "rqn1_gateway", 51.5, -0.12,
                "req-1", false);

        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getReference()).startsWith("RQ-");
        assertThat(incident.isAdvisory())
                .as("a classifier-produced category must always be marked advisory")
                .isTrue();
        assertThat(incident.getAdvisoryConfidence()).isEqualTo(0.61);
        assertThat(incident.getSeverity())
                .as("fire defaults to the highest severity")
                .isEqualTo(3);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);
        assertThat(incident.isLocationShared()).isTrue();
    }

    @Test
    @DisplayName("marks an incident as having no location when none was shared")
    void recordsAbsentLocation() {
        Incident incident = incidents.createFromReport("Help", Category.GENERAL, 0.3,
                "rqn1_origin", "rqn1_gateway", null, null, "req-2", false);

        assertThat(incident.isLocationShared()).isFalse();
        assertThat(incident.getLatitude()).isNull();
        assertThat(incident.getLongitude()).isNull();
    }

    @Test
    @DisplayName("an observer may read but not change an incident")
    void observerCannotMutate() {
        Incident incident = incidents.createFromReport("Help", Category.GENERAL, 0.3,
                "rqn1_origin", "rqn1_gateway", null, null, "req-3", false);

        assertThatThrownBy(() -> incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.TRIAGED, null, null), observer,
                "req-4"))
                .isInstanceOf(IncidentService.ForbiddenActionException.class)
                .hasMessageContaining("Only a responder");
    }

    @Test
    @DisplayName("a gateway token can never change an incident")
    void gatewayCannotMutate() {
        Incident incident = incidents.createFromReport("Help", Category.GENERAL, 0.3,
                "rqn1_origin", "rqn1_gateway", null, null, "req-5", false);

        assertThatThrownBy(() -> incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.RESOLVED, null, null), gateway,
                "req-6"))
                .isInstanceOf(IncidentService.ForbiddenActionException.class);

        assertThatThrownBy(() -> incidents.assign(incident.getReference(), 1L, gateway,
                "req-7"))
                .isInstanceOf(IncidentService.ForbiddenActionException.class);
    }

    @Test
    @DisplayName("walks the full status lifecycle and stamps the timestamps")
    void walksLifecycle() {
        Incident incident = incidents.createFromReport("Trapped", Category.TRAPPED, 0.8,
                "rqn1_origin", "rqn1_gateway", null, null, "req-8", false);

        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.TRIAGED, "responder opened", null),
                responder, "req-9");
        Incident triaged = incidentRepository.findByReference(incident.getReference())
                .orElseThrow();
        assertThat(triaged.getStatus()).isEqualTo(IncidentStatus.TRIAGED);
        assertThat(triaged.getAcknowledgedAt()).isNotNull();

        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.RESOLVED, "all safe", null),
                responder, "req-10");
        Incident resolved = incidentRepository.findByReference(incident.getReference())
                .orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("records acknowledgement however a responder reaches a live state")
    void acknowledgementIsNotLostBySkippingTriaged() {
        // A responder under pressure goes straight to IN_PROGRESS and never
        // touches TRIAGED. The timestamp of when somebody first looked at an
        // incident is evidence after the fact, so losing it because of the
        // order two buttons were pressed in is not acceptable.
        Incident incident = incidents.createFromReport("Trapped", Category.TRAPPED, 0.8,
                "rqn1_origin2", "rqn1_gateway", null, null, "req-ack", false);

        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.IN_PROGRESS, "responding", null),
                responder, "req-ack-1");

        Incident working = incidentRepository.findByReference(incident.getReference())
                .orElseThrow();
        assertThat(working.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(working.getAcknowledgedAt())
                .as("IN_PROGRESS means a responder has picked this up")
                .isNotNull();

        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.RESOLVED, "done", null),
                responder, "req-ack-2");
        Incident resolved = incidentRepository.findByReference(incident.getReference())
                .orElseThrow();
        assertThat(resolved.getAcknowledgedAt())
                .as("and the original timestamp must survive the closure")
                .isNotNull();
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("refuses to change an incident that is already closed")
    void refusesClosedIncident() {
        Incident incident = incidents.createFromReport("Done", Category.GENERAL, 0.2,
                "rqn1_origin", "rqn1_gateway", null, null, "req-11", false);
        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.RESOLVED, null, null),
                responder, "req-12");

        assertThatThrownBy(() -> incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.IN_PROGRESS, null, null),
                responder, "req-13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already resolved");
    }

    @Test
    @DisplayName("reports an unknown reference rather than inventing one")
    void reportsUnknownReference() {
        assertThatThrownBy(() -> incidents.updateStatus("RQ-NOPE",
                new Dtos.UpdateStatusRequest(IncidentStatus.TRIAGED, null, null),
                responder, "req-14"))
                .isInstanceOf(IncidentService.IncidentNotFoundException.class);
    }

    @Test
    @DisplayName("clamps severity to the documented range")
    void clampsSeverity() {
        Incident incident = incidents.createFromReport("Help", Category.GENERAL, 0.1,
                "rqn1_origin", "rqn1_gateway", null, null, "req-15", false);

        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.TRIAGED, null, 9), responder,
                "req-16");

        assertThat(incidentRepository.findByReference(incident.getReference())
                .orElseThrow().getSeverity()).isEqualTo(3);
    }

    @Test
    @DisplayName("assigns an incident to a responder account")
    void assignsIncident() {
        Incident incident = incidents.createFromReport("Help", Category.MEDICAL, 0.7,
                "rqn1_origin", "rqn1_gateway", null, null, "req-17", false);
        var account = accounts.save(new dev.resqnet.backend.domain.ResponderAccount(
                "assignee-" + System.nanoTime(), "Assignee", "{noop}", ResponderRole.RESPONDER));

        incidents.assign(incident.getReference(), account.getId(), responder, "req-18");

        assertThat(incidentRepository.findByReference(incident.getReference())
                .orElseThrow().getAssignedTo()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("counts open and critical incidents for the overview")
    void countsOverview() {
        incidents.createFromReport("Fire", Category.FIRE, 0.9, "rqn1_a", "rqn1_gw",
                null, null, "req-20", false);
        incidents.createFromReport("Supplies", Category.SUPPLIES, 0.4, "rqn1_b", "rqn1_gw",
                null, null, "req-21", false);

        IncidentService.Overview overview = incidents.overview();

        assertThat(overview.open()).isEqualTo(2);
        assertThat(overview.critical())
                .as("only severity 3 counts as critical")
                .isEqualTo(1);
        assertThat(overview.newIncidents()).isEqualTo(2);
    }

    @Test
    @DisplayName("records an audit entry for every status change")
    void auditsStatusChanges() {
        Incident incident = incidents.createFromReport("Help", Category.GENERAL, 0.2,
                "rqn1_origin", "rqn1_gateway", null, null, "req-30", false);

        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.TRIAGED, "opened", null),
                responder, "req-31");
        incidents.updateStatus(incident.getReference(),
                new Dtos.UpdateStatusRequest(IncidentStatus.RESOLVED, "closed", null),
                responder, "req-32");

        var trail = audit.forTarget("incident", incident.getReference());

        assertThat(trail).hasSize(3);
        assertThat(trail.get(0).getAction()).isEqualTo("INCIDENT_CREATED_FROM_GATEWAY");
        assertThat(trail.get(1).getAction()).isEqualTo("INCIDENT_STATUS_CHANGED");
        assertThat(trail.get(1).getActor())
                .as("the audit actor is the authenticated principal, never the client")
                .isEqualTo("responder");
        assertThat(trail.get(1).getDetail()).contains("NEW -> TRIAGED");
        assertThat(trail.get(2).getDetail()).contains("closed");
    }

    @Test
    @DisplayName("a manually created incident is not marked advisory")
    void manualCreationIsNotAdvisory() {
        Incident incident = incidents.createManually(
                new Dtos.CreateIncidentRequest("Bridge down", Category.FLOODING, 2,
                        51.5, -0.12), responder, "req-40");

        assertThat(incident.isAdvisory()).isFalse();
        assertThat(incident.getAdvisoryConfidence()).isNull();
        assertThat(incident.getSeverity()).isEqualTo(2);
    }

    @Test
    @DisplayName("finds incidents by reference or title")
    void searchesIncidents() {
        Incident incident = incidents.createFromReport("Bridge down", Category.FLOODING, 0.5,
                "rqn1_origin", "rqn1_gateway", null, null, "req-50", false);

        assertThat(incidents.search(incident.getReference(), 0, 10).getContent())
                .isNotEmpty();
        assertThat(incidents.search("bridge", 0, 10).getContent()).isNotEmpty();
        assertThat(incidents.search("no-such-incident", 0, 10).getContent()).isEmpty();
    }
}
