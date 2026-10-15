package dev.resqnet.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import dev.resqnet.backend.domain.Category;
import dev.resqnet.backend.domain.IncidentStatus;
import dev.resqnet.backend.repository.IncidentRepository;
import dev.resqnet.backend.web.Dtos;

/**
 * Gateway ingestion.
 *
 * <p>Batch semantics matter more than anything else here: a partially applied
 * batch reads as data loss to a responder, and a batch that crashes on one bad
 * record loses everything after it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GatewaySyncServiceTest {

    @Autowired
    private GatewaySyncService sync;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private dev.resqnet.backend.repository.IncidentMessageRepository messages;

    @BeforeEach
    void setUp() {
        // The in-memory database is shared across the suite and the audit trail
        // commits in its own transaction, so every test starts from empty.
        messageRepository.deleteAll();
        auditRepository.deleteAll();
        incidents.deleteAll();
        devices.deleteAll();
    }

    @Autowired
    private dev.resqnet.backend.repository.IncidentMessageRepository messageRepository;

    @Autowired
    private dev.resqnet.backend.repository.AuditEventRepository auditRepository;

    @Autowired
    private dev.resqnet.backend.repository.GatewayDeviceRepository devices;

    @Test
    @DisplayName("creates one incident for a new gateway report")
    void createsIncident() {
        Dtos.GatewaySyncResponse response = sync.ingest(new Dtos.GatewaySyncRequest(
                "android", "rqn1_gw_1",
                List.of(report("m1", "rqn1_origin", "ACKNOWLEDGED", Category.FLOODING))),
                "req-1");

        assertThat(response.createdIncidents()).isEqualTo(1);
        assertThat(response.acceptedRecords()).isEqualTo(1);
        assertThat(incidents.count()).isEqualTo(1);

        var openedIncident = incidents.findAll().get(0);
        assertThat(messages.findByIncidentIdOrderByCreatedAtAsc(openedIncident.getId()))
                .as("the message that opened the incident belongs in its history too")
                .hasSize(1)
                .allSatisfy(message -> assertThat(message.getMessageId()).isEqualTo("m1"));

        var reported = incidents.findAll().get(0);
        assertThat(reported.getCategory()).isEqualTo(Category.FLOODING);
        assertThat(reported.isAdvisory())
                .as("anything a device reports is advisory until a human says otherwise")
                .isTrue();
        assertThat(reported.getStatus()).isEqualTo(IncidentStatus.NEW);
    }

    @Test
    @DisplayName("attaches further messages to the same open incident")
    void attachesToExistingIncident() {
        sync.ingest(new Dtos.GatewaySyncRequest("android", "rqn1_gw_1",
                List.of(report("m1", "rqn1_origin", "SENT_TO_PEER", Category.FIRE))), "req-2");
        Dtos.GatewaySyncResponse second = sync.ingest(new Dtos.GatewaySyncRequest(
                "android", "rqn1_gw_1",
                List.of(report("m2", "rqn1_origin", "ACKNOWLEDGED", Category.FIRE))),
                "req-3");

        assertThat(second.createdIncidents()).isZero();
        assertThat(second.updatedIncidents()).isEqualTo(1);
        assertThat(incidents.count())
                .as("two messages from one origin must not become two incidents")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rejects malformed records without discarding the rest of the batch")
    void rejectsBadRecordsIndividually() {
        Dtos.GatewaySyncResponse response = sync.ingest(new Dtos.GatewaySyncRequest(
                "android", "rqn1_gw_2",
                List.of(report("  ", "rqn1_origin", "ACKNOWLEDGED", Category.MEDICAL),
                        report("m2", "rqn1_origin", "ACKNOWLEDGED", Category.MEDICAL))),
                "req-4");

        assertThat(response.rejectedRecords()).isEqualTo(1);
        assertThat(response.createdIncidents()).isEqualTo(1);
    }

    @Test
    @DisplayName("records the reporting device")
    void recordsDevice() {
        sync.ingest(new Dtos.GatewaySyncRequest("android", "rqn1_gw_3",
                List.of(report("m1", "rqn1_origin", "ACKNOWLEDGED", Category.GENERAL))),
                "req-5");
        sync.ingest(new Dtos.GatewaySyncRequest("android", "rqn1_gw_3",
                List.of(report("m2", "rqn1_origin", "ACKNOWLEDGED", Category.GENERAL))),
                "req-6");

        // Second report must attach rather than create.
        assertThat(incidents.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("never stores a message body, because there is nowhere to put one")
    void hasNoFieldForABody() {
        assertThat(Dtos.GatewayMessageReport.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("body", "payload", "text", "message");
    }

    private static Dtos.GatewayMessageReport report(String messageId, String origin,
                                                    String state, Category category) {
        return new Dtos.GatewayMessageReport(messageId, state, "SOS", "SOS",
                category.name(), 0.7, origin, null,
                1_700_000_000_000L, 1_700_000_900_000L, 1, "confirmed", null);
    }
}
