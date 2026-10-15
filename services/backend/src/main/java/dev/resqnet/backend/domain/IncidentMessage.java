package dev.resqnet.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Delivery metadata for one message attached to an incident.
 *
 * <p>Metadata only: no body, no payload bytes. The backend records that a
 * message exists, where it came from and whether it arrived. The text itself
 * stays on the devices that were already there.
 */
@Entity
@Table(name = "incident_messages")
public class IncidentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false, length = 160)
    private String messageId;

    @Column(nullable = false, length = 96)
    private String originDeviceId;

    @Column(length = 96)
    private String destinationDeviceId;

    @Column(nullable = false, length = 24)
    private String state;

    @Column(nullable = false, length = 16)
    private String priority;

    @Column(nullable = false)
    private long createdAt;

    @Column(nullable = false)
    private long expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 255)
    private String detail;

    @Column(nullable = false)
    private boolean simulated;

    protected IncidentMessage() {
        // for JPA
    }

    public IncidentMessage(Long incidentId, String messageId, String originDeviceId,
                           String destinationDeviceId, String state, String priority,
                           long createdAt, long expiresAt, int attempts, String detail,
                           boolean simulated) {
        this.incidentId = incidentId;
        this.messageId = messageId;
        this.originDeviceId = originDeviceId;
        this.destinationDeviceId = destinationDeviceId;
        this.state = state;
        this.priority = priority;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attempts = attempts;
        this.detail = detail;
        this.simulated = simulated;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getOriginDeviceId() {
        return originDeviceId;
    }

    public String getDestinationDeviceId() {
        return destinationDeviceId;
    }

    public String getState() {
        return state;
    }

    public String getPriority() {
        return priority;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant reportedAt() {
        return Instant.ofEpochMilli(createdAt);
    }
}
