package dev.resqnet.backend.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.resqnet.backend.domain.AuditEvent;
import dev.resqnet.backend.domain.ResponderRole;
import dev.resqnet.backend.repository.AuditEventRepository;

/**
 * Writes the audit trail.
 *
 * <p>{@code REQUIRES_NEW} on purpose. An audit entry must survive the failure
 * of the action it describes: if a responder attempts something forbidden and
 * the surrounding transaction rolls back, the fact that they attempted it is
 * exactly what an investigator needs.
 */
@Service
public class AuditService {

    private final AuditEventRepository auditEvents;

    public AuditService(AuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, ResponderRole role, String action, String targetType,
                       String targetId, String detail, String requestId) {
        auditEvents.save(new AuditEvent(actor, role, action, targetType, targetId, detail,
                requestId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String role, String action, String targetType,
                       String targetId, String detail, String requestId) {
        auditEvents.save(new AuditEvent(actor, ResponderRole.valueOf(role), action, targetType,
                targetId, detail, requestId));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recent(int limit) {
        return auditEvents.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit)).getContent();
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> forTarget(String targetType, String targetId) {
        return auditEvents.findTop100ByTargetTypeAndTargetIdOrderByIdAsc(targetType, targetId);
    }

    /** Timestamp helper so callers do not each invent their own clock. */
    public Instant now() {
        return Instant.now();
    }
}
