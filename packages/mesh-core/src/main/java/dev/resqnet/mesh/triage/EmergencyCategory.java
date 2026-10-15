package dev.resqnet.mesh.triage;

/**
 * Advisory category assigned to an emergency message.
 *
 * <p>These are communication categories, not medical diagnoses. The classifier
 * exists to help a responder reading a queue of messages decide what to open
 * first; it never decides that anyone should be sent anywhere.
 */
public enum EmergencyCategory {

    /** Someone reports an injury or a medical emergency. */
    MEDICAL("Medical emergency", 3),
    /** Rising water, flooding, or a person caught by water. */
    FLOODING("Flooding", 2),
    /** Fire, smoke, or an active blaze. */
    FIRE("Fire", 3),
    /** A person pinned, trapped, or unable to get out. */
    TRAPPED("Trapped / unable to escape", 3),
    /** A person is missing, including children. */
    MISSING_PERSON("Missing person", 2),
    /** Food, water, or medication needed. */
    SUPPLIES("Food, water or medication", 1),
    /** Real distress that does not match a more specific category. */
    GENERAL("General assistance", 1),
    /** Nothing matched. Never silently coerced into a confident category. */
    UNKNOWN("Unclassified", 0);

    private final String label;
    private final int defaultSeverity;

    EmergencyCategory(String label, int defaultSeverity) {
        this.label = label;
        this.defaultSeverity = defaultSeverity;
    }

    public String label() {
        return label;
    }

    /**
     * Baseline triage severity from 0 to 3, used to seed responder-side
     * ordering before any responder input. Advisory only.
     */
    public int defaultSeverity() {
        return defaultSeverity;
    }
}