package dev.resqnet.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A human who can see or act on incidents.
 *
 * <p>Accounts are created by an administrator. There is no self-service
 * registration, and there is deliberately no endpoint a client can call to
 * change a role. Responder privileges are a human decision, made by another
 * human, recorded in the audit trail.
 */
@Entity
@Table(name = "responder_accounts")
public class ResponderAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String displayName;

    /** BCrypt hash. The plaintext password is never stored, logged or returned. */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResponderRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant lastLoginAt;

    protected ResponderAccount() {
        // for JPA
    }

    public ResponderAccount(String username, String displayName, String passwordHash,
                            ResponderRole role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public ResponderRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
