package dev.resqnet.backend.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.resqnet.backend.domain.IncidentStatus;
import dev.resqnet.backend.security.RequestIdFilter;
import dev.resqnet.backend.security.ResQNetPrincipal;
import dev.resqnet.backend.service.IncidentService;

/**
 * Incident management for responders.
 *
 * <p>Read is open to any authenticated principal including a gateway; write is
 * restricted at the method level. The service layer re-checks, because a
 * controller annotation is a guardrail and a service check is the rule.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidents;

    public IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    public Page<Dtos.IncidentSummary> list(
            @RequestParam(name = "status", required = false) IncidentStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return incidents.list(status, Math.max(0, page), Math.min(100, Math.max(1, size)));
    }

    @GetMapping("/search")
    public Page<Dtos.IncidentSummary> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return incidents.search(q, Math.max(0, page), Math.min(100, Math.max(1, size)));
    }

    @GetMapping("/overview")
    public IncidentService.Overview overview() {
        return incidents.overview();
    }

    @GetMapping("/{reference}")
    public ResponseEntity<?> detail(@PathVariable("reference") String reference) {
        return incidents.detail(reference)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new dev.resqnet.backend.service.IncidentService
                        .IncidentNotFoundException(reference));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RESPONDER', 'ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody Dtos.CreateIncidentRequest request,
                                    Authentication authentication,
                                    HttpServletRequest http) {
        var principal = (ResQNetPrincipal) authentication.getPrincipal();
        var incident = incidents.createManually(request, principal,
                (String) http.getAttribute(RequestIdFilter.HEADER));
        return ResponseEntity.ok(incidents.toSummary(incident));
    }

    @PatchMapping("/{reference}/status")
    @PreAuthorize("hasAnyRole('RESPONDER', 'ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable("reference") String reference,
                                          @Valid @RequestBody Dtos.UpdateStatusRequest request,
                                          Authentication authentication,
                                          HttpServletRequest http) {
        var principal = (ResQNetPrincipal) authentication.getPrincipal();
        var incident = incidents.updateStatus(reference, request, principal,
                (String) http.getAttribute(RequestIdFilter.HEADER));
        return ResponseEntity.ok(incidents.toSummary(incident));
    }

    @PostMapping("/{reference}/assign")
    @PreAuthorize("hasAnyRole('RESPONDER', 'ADMIN')")
    public ResponseEntity<?> assign(@PathVariable("reference") String reference,
                                    @Valid @RequestBody Dtos.AssignRequest request,
                                    Authentication authentication,
                                    HttpServletRequest http) {
        var principal = (ResQNetPrincipal) authentication.getPrincipal();
        var incident = incidents.assign(reference, request.accountId(), principal,
                (String) http.getAttribute(RequestIdFilter.HEADER));
        return ResponseEntity.ok(incidents.toSummary(incident));
    }
}
