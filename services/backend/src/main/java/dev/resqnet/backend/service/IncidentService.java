package dev.resqnet.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.resqnet.backend.domain.Category;
import dev.resqnet.backend.domain.Incident;
import dev.resqnet.backend.domain.IncidentMessage;
import dev.resqnet.backend.domain.IncidentStatus;
import dev.resqnet.backend.repository.IncidentMessageRepository;
import dev.resqnet.backend.repository.IncidentRepository;
import dev.resqnet.backend.security.ResQNetPrincipal;
import dev.resqnet.backend.web.Dtos;

/**
 * Incident lifecycle.
 *
 * <p>Two invariants are enforced here rather than left to callers:
 *
 * <ul>
 *   <li>Only a responder principal can change an incident. A gateway
 *       synchronisation token cannot, no matter what the request body says.</li>
 *   <li>Closing an incident always records who closed it and when. Nothing in
 *       the system resolves an incident automatically, so an unresolved status
 *       is always an honest one.</li>
 * </ul>
 */
@Service
public class IncidentService {

    /** How many open incidents the command overview counts as critical. */
    public static final int CRITICAL_SEVERITY = 3;

    private final IncidentRepository incidents;
    private final IncidentMessageRepository messages;
    private final AuditService audit;

    public IncidentService(IncidentRepository incidents, IncidentMessageRepository messages,
                           AuditService audit) {
        this.incidents = incidents;
        this.messages = messages;
        this.audit = audit;
    }

    /** Creates an incident from a device report. */
    @Transactional
    public Incident createFromReport(String title, Category category, Double confidence,
                                     String originDeviceId, String gatewayDeviceId,
                                     Double latitude, Double longitude, String requestId,
                                     boolean simulated) {
        Incident incident = new Incident(newReference(), title, category, originDeviceId,
                gatewayDeviceId, confidence);
        incident.setLocation(latitude, longitude);
        Incident saved = incidents.save(incident);
        // Recorded as a device, never as a responder role. A gateway
        // synchronises; it does not triage, and the audit trail must not imply
        // that someone made this call.
        audit.recordDevice(
                gatewayDeviceId == null ? "gateway" : "gateway:" + gatewayDeviceId,
                simulated ? "INCIDENT_CREATED_FROM_SIMULATION" : "INCIDENT_CREATED_FROM_GATEWAY",
                "incident", saved.getReference(),
                "category=" + saved.getCategory() + " severity=" + saved.getSeverity(),
                requestId);
        return saved;
    }

    /** Creates an incident manually by a responder. */
    @Transactional
    public Incident createManually(Dtos.CreateIncidentRequest request, ResQNetPrincipal actor,
                                   String requestId) {
        requireResponder(actor, "create an incident");
        Incident incident = new Incident(newReference(), request.title(), request.category(),
                null, null, null);
        incident.clearAdvisory();
        if (request.severity() != null) {
            incident.setSeverity(request.severity());
        }
        incident.setLocation(request.latitude(), request.longitude());
        Incident saved = incidents.save(incident);
        audit.record(actor.name(), actor.role(), "INCIDENT_CREATED", "incident",
                saved.getReference(), request.title(), requestId);
        return saved;
    }

    /** Lists incidents, newest and most severe first. */
    @Transactional(readOnly = true)
    public Page<Dtos.IncidentSummary> list(IncidentStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("severity"), Sort.Order.desc("createdAt")));
        Page<Incident> source = status == null
                ? incidents.findAllByOrderBySeverityDescCreatedAtDesc(pageRequest)
                : incidents.findByStatusInOrderBySeverityDescCreatedAtDesc(
                        List.of(status), pageRequest);
        return source.map(this::toSummary);
    }

    /** Full detail including delivery metadata and the audit trail. */
    @Transactional(readOnly = true)
    public Optional<Dtos.IncidentDetail> detail(String reference) {
        return incidents.findByReference(reference).map(incident -> new Dtos.IncidentDetail(
                toSummary(incident),
                messages.findByIncidentIdOrderByCreatedAtAsc(incident.getId()).stream()
                        .map(IncidentService::toDelivery).toList(),
                audit.forTarget("incident", reference).stream()
                        .map(e -> new Dtos.AuditRow(e.getOccurredAt(), e.getActor(),
                                e.getActorRole(), e.getAction(), e.getTargetType(),
                                e.getTargetId(), e.getDetail()))
                        .toList()));
    }

    /** Changes status, with the permission check enforced here as well as at the edge. */
    @Transactional
    public Incident updateStatus(String reference, Dtos.UpdateStatusRequest request,
                                 ResQNetPrincipal actor, String requestId) {
        requireResponder(actor, "change an incident status");
        Incident incident = incidents.findByReference(reference)
                .orElseThrow(() -> new IncidentNotFoundException(reference));
        if (incident.getStatus().closed()) {
            throw new IllegalStateException("Incident " + reference + " is already "
                    + incident.getStatus().label().toLowerCase(Locale.ROOT));
        }
        IncidentStatus previous = incident.getStatus();
        incident.setStatus(request.status());
        if (request.severity() != null) {
            incident.setSeverity(request.severity());
        }
        Incident saved = incidents.save(incident);
        audit.record(actor.name(), actor.role(), "INCIDENT_STATUS_CHANGED", "incident",
                reference, previous + " -> " + saved.getStatus()
                        + (request.note() == null ? "" : " (" + request.note() + ")"),
                requestId);
        return saved;
    }

    /** Assigns an incident to a responder. */
    @Transactional
    public Incident assign(String reference, Long accountId, ResQNetPrincipal actor,
                           String requestId) {
        requireResponder(actor, "assign an incident");
        Incident incident = incidents.findByReference(reference)
                .orElseThrow(() -> new IncidentNotFoundException(reference));
        incident.setAssignedTo(accountId);
        Incident saved = incidents.save(incident);
        audit.record(actor.name(), actor.role(), "INCIDENT_ASSIGNED", "incident", reference,
                "assigned to account " + accountId, requestId);
        return saved;
    }

    /** Attaches delivery metadata reported by a gateway. */
    @Transactional
    public void attachMessage(Long incidentId, Dtos.GatewayMessageReport report,
                              boolean simulated) {
        boolean alreadyKnown = messages.findByMessageId(report.messageId()).stream()
                .anyMatch(m -> m.getIncidentId().equals(incidentId));
        if (alreadyKnown) {
            return;
        }
        messages.save(new IncidentMessage(incidentId, report.messageId(),
                report.origin() == null ? "unknown" : report.origin(), report.destination(),
                report.state() == null ? "UNKNOWN" : report.state(),
                report.priority() == null ? "NORMAL" : report.priority(),
                report.createdAt(), report.expiresAt(),
                report.attempts() == null ? 0 : report.attempts(),
                report.detail(), simulated));
    }

    /** Counts used by the command overview header. */
    @Transactional(readOnly = true)
    public Overview overview() {
        long open = incidents.countOpen();
        long critical = incidents.findAll().stream()
                .filter(i -> !i.getStatus().closed() && i.getSeverity() >= CRITICAL_SEVERITY)
                .count();
        return new Overview(open, critical, incidents.count(),
                incidents.countByStatus(IncidentStatus.NEW),
                incidents.countByStatus(IncidentStatus.RESOLVED));
    }

    /** Header counters for the command overview. */
    public record Overview(long open, long critical, long total, long newIncidents,
                           long resolved) {
    }

    /**
     * Search by reference or title.
     *
     * <p>Filtered in memory over a bounded page rather than with a LIKE query.
     * An emergency queue is thousands of rows, not millions, and an unindexed
     * wildcard search over a large table is a cheap way for a responder's
     * search box to become a denial of service against the API.
     */
    @Transactional(readOnly = true)
    public Page<Dtos.IncidentSummary> search(String query, int page, int size) {
        int safeSize = Math.min(100, Math.max(1, size));
        int safePage = Math.max(0, page);
        List<Dtos.IncidentSummary> matched = incidents.findAll(PageRequest.of(
                        safePage, safeSize * 4,
                        Sort.by(Sort.Order.desc("severity"), Sort.Order.desc("createdAt"))))
                .stream()
                .filter(i -> matches(i, query))
                .map(this::toSummary)
                .collect(java.util.stream.Collectors.toList());
        int from = Math.min(matched.size(), safePage * safeSize);
        int to = Math.min(matched.size(), from + safeSize);
        return new org.springframework.data.domain.PageImpl<>(
                matched.subList(from, to),
                PageRequest.of(safePage, safeSize),
                matched.size());
    }

    private static boolean matches(Incident incident, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return incident.getReference().toLowerCase(Locale.ROOT).contains(needle)
                || incident.getTitle().toLowerCase(Locale.ROOT).contains(needle);
    }

    private void requireResponder(ResQNetPrincipal actor, String action) {
        if (actor == null || !actor.canMutateIncidents()) {
            throw new ForbiddenActionException(
                    "Only a responder may " + action + "; this principal may not");
        }
    }

    /** Builds the human-quotable reference, e.g. {@code RQ-7F3A2C}. */
    static String newReference() {
        return "RQ-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    public Dtos.IncidentSummary toSummary(Incident incident) {
        return new Dtos.IncidentSummary(
                incident.getId(),
                incident.getReference(),
                incident.getTitle(),
                incident.getCategory(),
                incident.getCategory().label(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getStatus().label(),
                incident.isAdvisory(),
                incident.getAdvisoryConfidence(),
                incident.isLocationShared(),
                incident.getLatitude(),
                incident.getLongitude(),
                incident.getSourceDeviceId(),
                incident.getGatewayDeviceId(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getAcknowledgedAt(),
                incident.getResolvedAt(),
                incident.getAssignedTo(),
                (int) messages.countByIncidentId(incident.getId()));
    }

    static Dtos.MessageDelivery toDelivery(IncidentMessage message) {
        return new Dtos.MessageDelivery(
                message.getMessageId(),
                message.getOriginDeviceId(),
                message.getDestinationDeviceId(),
                message.getState(),
                message.getPriority(),
                message.getCreatedAt(),
                message.getExpiresAt(),
                message.getAttempts(),
                message.getDetail(),
                message.isSimulated());
    }

    /** Raised when a referenced incident does not exist. */
    public static class IncidentNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public IncidentNotFoundException(String reference) {
            super("No incident with reference " + reference);
        }
    }

    /** Raised when a principal attempts an action outside its role. */
    public static class ForbiddenActionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ForbiddenActionException(String message) {
            super(message);
        }
    }
}
