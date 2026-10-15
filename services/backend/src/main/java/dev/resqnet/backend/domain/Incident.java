package dev.resqnet.backend.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One reported emergency, as a responder sees it.
 *
 * <p>The fields that carry the product's most important guarantees:
 *
 * <ul>
 *   <li>{@code advisory} and {@code advisoryConfidence} are always set for
 *       device-reported incidents. The classifier that produced them is a
 *       keyword model and says so.</li>
 *   <li>{@code locationShared} distinguishes "no location was sent" from "a
 *       location was sent". A responder must be able to tell the difference
 *       before deciding to send someone to a coordinate.</li>
 *   <li>{@code status} only ever changes because a responder changed it.</li>
 * </ul>
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-quotable reference, used in radio traffic and audit records. */
    @Column(nullable = false, unique = true, length = 48)
    private String reference;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Category category = Category.UNKNOWN;

    @Column(nullable = false)
    private int severity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus status = IncidentStatus.NEW;

    @Column(nullable = false)
    private boolean advisory = true;

    @Column
    private Double advisoryConfidence;

    @Column(length = 96)
    private String sourceDeviceId;

    @Column(length = 96)
    private String gatewayDeviceId;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(nullable = false)
    private boolean locationShared = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column
    private Instant acknowledgedAt;

    @Column
    private Instant resolvedAt;

    @Column
    private Long assignedTo;

    protected Incident() {
        // for JPA
    }

    public Incident(String reference, String title, Category category, String sourceDeviceId,
                     String gatewayDeviceId, Double advisoryConfidence) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.title = Objects.requireNonNull(title, "title");
        this.category = category == null ? Category.UNKNOWN : category;
        this.sourceDeviceId = sourceDeviceId;
        this.gatewayDeviceId = gatewayDeviceId;
        this.advisoryConfidence = advisoryConfidence;
        this.severity = this.category.defaultSeverity();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category == null ? Category.UNKNOWN : category;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = Math.max(0, Math.min(3, severity));
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = Objects.requireNonNull(status, "status");
        if (status == IncidentStatus.TRIAGED && acknowledgedAt == null) {
            this.acknowledgedAt = Instant.now();
        }
        if (status.closed() && resolvedAt == null) {
            this.resolvedAt = Instant.now();
        }
    }

    public boolean isAdvisory() {
        return advisory;
    }

    /**
     * Clears the advisory flag.
     *
     * <p>Only used when a responder creates an incident by hand, where the
     * classification is theirs rather than a model's. A device-reported incident
     * stays advisory until a responder corrects its category.
     */
    public void clearAdvisory() {
        this.advisory = false;
        this.advisoryConfidence = null;
    }

    public Double getAdvisoryConfidence() {
        return advisoryConfidence;
    }

    public String getSourceDeviceId() {
        return sourceDeviceId;
    }

    public String getGatewayDeviceId() {
        return gatewayDeviceId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public boolean isLocationShared() {
        return locationShared;
    }

    public void setLocation(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            this.latitude = null;
            this.longitude = null;
            this.locationShared = false;
            return;
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationShared = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }
}
