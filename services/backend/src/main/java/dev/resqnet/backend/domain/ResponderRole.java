package dev.resqnet.backend.domain;

/**
 * What an authenticated account is allowed to do.
 *
 * <p>Roles exist only server-side and only in the database. No request body,
 * header or token claim from a client can create or elevate one. A gateway
 * device has no role at all: it synchronises, it does not decide.
 */
public enum ResponderRole {

    /** Read-only dashboard access. */
    OBSERVER,

    /** Can triage, assign and resolve incidents. */
    RESPONDER,

    /** Can manage accounts and read the full audit trail. */
    ADMIN;

    /** True when the role may change incident state. */
    public boolean canMutateIncidents() {
        return this == RESPONDER || this == ADMIN;
    }

    /** True when the role may read the audit trail. */
    public boolean canReadAudit() {
        return this == ADMIN;
    }
}
