package dev.resqnet.backend.domain;

/**
 * Incident category as reported by a device.
 *
 * <p>Deliberately mirrors the handset's advisory categories so a report does
 * not need translation. Every incident created from a device report is marked
 * advisory: the classification came from a keyword model, and a responder
 * confirms or corrects it.
 */
public enum Category {

    MEDICAL("Medical emergency"),
    FLOODING("Flooding"),
    FIRE("Fire"),
    TRAPPED("Trapped / unable to escape"),
    MISSING_PERSON("Missing person"),
    SUPPLIES("Food, water or medication"),
    GENERAL("General assistance"),
    UNKNOWN("Unclassified");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Resolves a wire value, falling back to UNKNOWN rather than failing. */
    public static Category from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (Category category : values()) {
            if (category.name().equalsIgnoreCase(value.trim())) {
                return category;
            }
        }
        return UNKNOWN;
    }

    /** Baseline severity from 0 to 3, used for ordering before human triage. */
    public int defaultSeverity() {
        return switch (this) {
            case MEDICAL, FIRE, TRAPPED -> 3;
            case FLOODING, MISSING_PERSON -> 2;
            case SUPPLIES, GENERAL -> 1;
            case UNKNOWN -> 0;
        };
    }
}
