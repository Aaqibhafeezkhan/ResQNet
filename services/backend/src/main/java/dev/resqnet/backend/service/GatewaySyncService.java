package dev.resqnet.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.resqnet.backend.domain.Category;
import dev.resqnet.backend.domain.GatewayDevice;
import dev.resqnet.backend.domain.Incident;
import dev.resqnet.backend.repository.GatewayDeviceRepository;
import dev.resqnet.backend.repository.IncidentRepository;
import dev.resqnet.backend.web.Dtos;

/**
 * Ingests delivery summaries from a handset that happens to have connectivity.
 *
 * <p>Strictly a reporting path. Nothing here can dispatch anybody, close an
 * incident, or change a responder's actions. The only authority a gateway token
 * carries is the ability to create a new incident, and even that is marked
 * advisory so a human has to confirm it.
 */
@Service
public class GatewaySyncService {

    private final IncidentRepository incidents;
    private final GatewayDeviceRepository devices;
    private final IncidentService incidentService;
    private final AuditService audit;

    public GatewaySyncService(IncidentRepository incidents, GatewayDeviceRepository devices,
                              IncidentService incidentService, AuditService audit) {
        this.incidents = incidents;
        this.devices = devices;
        this.incidentService = incidentService;
        this.audit = audit;
    }

    /**
     * Ingests a batch.
     *
     * <p>One transaction per batch. A partial batch would leave the dashboard
     * showing half of what a device actually reported, which reads as data loss
     * when it is a rejected record.
     */
    @Transactional
    public Dtos.GatewaySyncResponse ingest(Dtos.GatewaySyncRequest request, String requestId) {
        List<Dtos.GatewayMessageReport> records = request.records() == null
                ? List.of() : request.records();
        int created = 0;
        int updated = 0;
        int rejected = 0;

        String deviceId = request.deviceId() == null || request.deviceId().isBlank()
                ? "unknown-gateway" : request.deviceId();
        GatewayDevice device = devices.findByDeviceId(deviceId)
                .orElseGet(() -> devices.save(new GatewayDevice(deviceId, request.source())));
        device.recordReport(Instant.now());
        devices.save(device);

        for (Dtos.GatewayMessageReport report : records) {
            if (report.messageId() == null || report.messageId().isBlank()) {
                rejected++;
                continue;
            }
            try {
                Incident incident = resolveIncident(report);
                if (incident == null) {
                    Incident created1 = incidentService.createFromReport(
                            titleFor(report),
                            Category.from(report.category()),
                            report.advisoryConfidence(),
                            report.origin(),
                            deviceId,
                            null,
                            null,
                            requestId,
                            false);
                    // The message that opened the incident is also part of its
                    // delivery history. Attaching only later messages would make
                    // the first message the one a responder cannot see the state
                    // of, which is the message they most need to see.
                    incidentService.attachMessage(created1.getId(), report, false);
                    created++;
                } else {
                    incidentService.attachMessage(incident.getId(), report, false);
                    updated++;
                }
            } catch (RuntimeException e) {
                // One malformed record must not discard the rest of the batch.
                rejected++;
            }
        }

        return new Dtos.GatewaySyncResponse(UUID_NAME, records.size(), created, updated,
                rejected, rejected == 0 ? null : rejected + " record(s) rejected");
    }

    /** Correlation id returned to the device; also used as the run id. */
    private static final String UUID_NAME = "gateway-sync";

    private Incident resolveIncident(Dtos.GatewayMessageReport report) {
        if (report.incidentReference() != null && !report.incidentReference().isBlank()) {
            Optional<Incident> existing =
                    incidents.findByReference(report.incidentReference());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        // Fall back to the origin device's most recent open incident, so a
        // handset that reports without a reference still lands in the right
        // place rather than creating one incident per message.
        if (report.origin() != null) {
            return incidents.findAll().stream()
                    .filter(i -> report.origin().equals(i.getSourceDeviceId()))
                    .filter(i -> !i.getStatus().closed())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /** Builds an incident title from a delivery summary. */
    static String titleFor(Dtos.GatewayMessageReport report) {
        Category category = Category.from(report.category());
        String state = report.state() == null ? "UNKNOWN" : report.state();
        return category.label() + " reported (" + state.toLowerCase(java.util.Locale.ROOT) + ")";
    }
}
