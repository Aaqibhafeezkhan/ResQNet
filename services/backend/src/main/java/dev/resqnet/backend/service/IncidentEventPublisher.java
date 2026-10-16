package dev.resqnet.backend.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.resqnet.backend.web.Dtos;
import dev.resqnet.backend.web.EventStreamHandler;

/**
 * Publishes incident changes to the live dashboard stream.
 *
 * <h2>Why every event is deferred to after commit</h2>
 *
 * <p>Emitting inside the transaction would tell a dashboard about an incident
 * that then failed to commit. The operator sees a new incident appear, opens it,
 * and finds nothing: a worse failure than being briefly late, because it
 * teaches people to distrust the screen.
 *
 * <p>Every listener below is bound to {@link TransactionPhase#AFTER_COMMIT}. If
 * the transaction rolls back, nothing is published, which is exactly right.
 *
 * <h2>Why the stream is an optimisation rather than the source of truth</h2>
 *
 * <p>The dashboard still polls. A responder on a hospital network that blocks
 * WebSocket upgrades, or a browser tab that was asleep, must not miss an
 * incident. Events make the screen feel live; they do not make it correct.
 * Every event therefore carries the full incident summary, so the client can
 * apply it without a follow-up request, and the client refreshes on reconnect
 * regardless of what it missed.
 */
@Service
public class IncidentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventPublisher.class);

    private final ApplicationEventPublisher application;
    private final EventStreamHandler stream;
    private final dev.resqnet.backend.web.EventJson encoder;

    public IncidentEventPublisher(ApplicationEventPublisher application,
                                  EventStreamHandler stream,
                                  dev.resqnet.backend.web.EventJson encoder) {
        this.application = application;
        this.stream = stream;
        this.encoder = encoder;
    }

    /** Event types on the wire. Constants so a typo is a compile error. */
    public static final String INCIDENT_CREATED = "incident.created";
    public static final String INCIDENT_UPDATED = "incident.updated";
    public static final String INCIDENT_ASSIGNED = "incident.assigned";
    public static final String RESOLVED = "incident.resolved";
    public static final String GATEWAY_SYNC = "gateway.sync";
    public static final String SIMULATION_INGESTED = "simulation.ingested";

    /**
     * A change worth telling a dashboard about.
     *
     * <p>{@code incident} is null for batch-level events such as a gateway sync,
     * where the detail line already carries everything there is to say.
     */
    public record IncidentEvent(String type, String reference, String at, String detail,
                                Dtos.IncidentSummary incident) {

        Map<String, Object> toMap() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", type);
            body.put("reference", reference);
            body.put("at", at);
            body.put("detail", detail);
            body.put("incident", incident);
            return body;
        }
    }

    /**
     * Stages an event for publication once the current transaction commits.
     *
     * <p>Called from inside the transaction. Nothing reaches a client until
     * {@link #send} runs, which is after commit.
     */
    public void stage(IncidentEvent event) {
        application.publishEvent(event);
    }

    /** Convenience for the common case of an incident state change. */
    public void stageIncidentChange(String type, Dtos.IncidentSummary incident, String detail) {
        stage(new IncidentEvent(type, incident.reference(), Instant.now().toString(),
                detail, incident));
    }

    /**
     * Pushes a committed change to every connected dashboard.
     *
     * <p>Never throws. The transaction has already committed by the time this
     * runs, so a broken dashboard connection has no business turning a
     * successful write into a failed request.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(IncidentEvent event) {
        try {
            stream.broadcast(encoder.encode(event.toMap()));
        } catch (RuntimeException e) {
            log.warn("Could not publish {} for {}: {}", event.type(), event.reference(),
                    e.toString());
        }
    }

    /**
     * Events raised with no transaction in progress publish immediately.
     *
     * <p>{@link TransactionPhase#AFTER_COMMIT} does not fire when there is no
     * transaction, which would silently drop anything published outside one.
     */
    @EventListener
    public void sendImmediately(IncidentEvent event) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            send(event);
        }
    }
}