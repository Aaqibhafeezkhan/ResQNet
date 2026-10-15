package dev.resqnet.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A handset that reported something at some point.
 *
 * <p>A device that has not reported for a while is still listed, with its last
 * contact time. A dashboard that silently dropped stale nodes would make a
 * partitioned mesh look empty, which is the opposite of what a responder needs.
 */
@Entity
@Table(name = "gateway_devices")
public class GatewayDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 96)
    private String deviceId;

    @Column(length = 128)
    private String label;

    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column
    private Instant lastReportedAt;

    @Column(nullable = false)
    private long messagesReported = 0;

    @Column(nullable = false)
    private boolean active = true;

    protected GatewayDevice() {
        // for JPA
    }

    public GatewayDevice(String deviceId, String label) {
        this.deviceId = deviceId;
        this.label = label;
    }

    public void recordReport(Instant at) {
        this.lastReportedAt = at;
        this.lastSeenAt = at;
        this.messagesReported++;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getLabel() {
        return label;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getLastReportedAt() {
        return lastReportedAt;
    }

    public long getMessagesReported() {
        return messagesReported;
    }

    public boolean isActive() {
        return active;
    }
}
