package dev.resqnet.backend.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.resqnet.backend.service.AuditService;

/**
 * The audit trail.
 *
 * <p>Admin only. Read-only by construction: there is no endpoint that writes,
 * edits or deletes an audit row, because an audit trail a responder can modify
 * is not an audit trail.
 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<Dtos.AuditRow> recent(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        return audit.recent(Math.min(500, Math.max(1, limit))).stream()
                .map(e -> new Dtos.AuditRow(e.getOccurredAt(), e.getActor(), e.getActorRole(),
                        e.getAction(), e.getTargetType(), e.getTargetId(), e.getDetail()))
                .toList();
    }
}
