package dev.resqnet.backend.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public SimulationController(SimulationIngestService simulations) {
        this.simulations = simulations;
    }

    /**
     * Accepts a run report.
     *
     * <p>Open to the simulator CLI. It writes only to simulation tables, so an
     * unauthenticated caller cannot create an incident or touch a responder
     * account. Generation must never be able to impersonate field data.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ingest(@RequestBody String body) {
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
