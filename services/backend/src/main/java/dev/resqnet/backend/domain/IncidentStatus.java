package dev.resqnet.backend.domain;

/**
 * Lifecycle of an incident as a responder sees it.
 *
 * <p>Note that {@link #RESOLVED} is not reachable by an automated rule. Nothing
 * in this service decides that someone is safe.
 */
public enum IncidentStatus {

    /** Reported, nobody has looked yet. */
    NEW("New"),
    /** A responder has opened it. */
    TRIAGED("Triaged"),
    /** Work is in progress. */
    IN_PROGRESS("In progress"),
    /** Handled. Set by a responder, never by a rule. */
    RESOLVED("Resolved"),
    /** Determined not to be an incident. Set by a responder. */
    DISMISSED("Dismissed");

    private final String label;

    IncidentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True when no further responder action is outstanding. */
    public boolean closed() {
        return this == RESOLVED || this == DISMISSED;
    }

    /** True when a responder may still change it. */
    public boolean mutable() {
        return !closed();
    }
}
