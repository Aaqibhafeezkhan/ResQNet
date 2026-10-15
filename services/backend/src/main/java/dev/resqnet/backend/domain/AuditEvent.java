package dev.resqnet.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An append-only record of somebody doing something.
 *
 * <p>There is no update path and no delete path anywhere in the application.
 * That is the entire value of an audit trail: if it can be rewritten it is not
 * one.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt = Instant.now();

    /** Who acted. Never a client-supplied value. */
    @Column(nullable = false, length = 96)
    private String actor;

    @Column(nullable = false, length = 32)
    private String actorRole;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 48)
    private String targetType;

    @Column(length = 64)
    private String targetId;

    @Column(length = 512)
    private String detail;

    /** Correlates every event produced by one request. */
    @Column(length = 64)
    private String requestId;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(String actor, ResponderRole role, String action, String targetType,
                      String targetId, String detail, String requestId) {
        this.actor = actor;
        this.actorRole = role.name();
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.requestId = requestId;
        this.occurredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getRequestId() {
        return requestId;
    }
}
