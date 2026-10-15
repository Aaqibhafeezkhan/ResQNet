package dev.resqnet.backend.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.resqnet.backend.security.AuthTokenService;
import dev.resqnet.backend.security.ResQNetPrincipal;
import dev.resqnet.backend.service.SimulationIngestService;

/**
 * Ingests and serves deterministic simulation runs.
 *
 * <p>This is what lets the responder dashboard be populated without a handset
 * ever having connected, which matters for demos and for reviewing behaviour
 * after an incident. Every response from here is labelled simulated.
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationIngestService simulations;
    private final AuthTokenService authTokens;

    public SimulationController(SimulationIngestService simulations, AuthTokenService authTokens) {
        this.simulations = simulations;
        this.authTokens = authTokens;
    }

    /**
     * Accepts a run report.
     *
     * <p>Authenticated as a device rather than as a responder. The simulator is
     * a generator, not a handset, and giving it a human session would imply an
     * accountability model that does not exist: a responder's actions are
     * attributable, a simulator's are not and should not pretend to be.
     *
     * <p>A responder JWT is also accepted so an operator can post a captured
     * run without leaving the dashboard session.
     *
     * <p>Neither path can reach an incident or a responder account. Ingest
     * writes only to simulation tables, which is what keeps generated traffic
     * from ever being mistaken for field data.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ingest(@RequestBody String body,
                                    @RequestHeader(value = "Authorization", required = false)
                                    String authorization) {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        boolean responderSession = existing != null && existing.isAuthenticated()
                && existing.getPrincipal() instanceof ResQNetPrincipal principal
                && principal.canReadAudit();
        if (!responderSession) {
            String token = authorization != null && authorization.startsWith("Bearer ")
                    ? authorization.substring(7).trim() : "";
            // Any non-empty device id: the simulator reports which run and which
            // topology produced the data, and that is the whole audit value here.
            if (authTokens.authenticateGateway(token, "simulator").isEmpty()) {
                return ResponseEntity.status(401)
                        .body(java.util.Map.of("error", "simulation ingestion authentication failed"));
            }
        }
        return ResponseEntity.ok(simulations.ingest(body));
    }

    @GetMapping
    public List<Dtos.SimulationRunSummary> recent(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return simulations.recentRuns(Math.min(100, Math.max(1, limit)));
    }

    @GetMapping("/{runId}/events")
    public List<Map<String, Object>> events(@PathVariable("runId") String runId,
                                            @RequestParam(name = "limit", defaultValue = "500") int limit) {
        return simulations.events(runId, Math.min(4000, Math.max(1, limit)));
    }
}
