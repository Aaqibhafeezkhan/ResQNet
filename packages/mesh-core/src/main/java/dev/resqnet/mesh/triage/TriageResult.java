package dev.resqnet.mesh.triage;

import java.util.List;

/**
 * Output of the advisory classifier.
 *
 * <p>Deliberately reports uncertainty rather than hiding it. A responder
 * triaging a flooded street with three radio channels in use should be able to
 * see that the classifier was unsure, and the sender should be able to correct
 * it. The fields here exist to make that possible:
 *
 * @param category best match, possibly {@link EmergencyCategory#UNKNOWN}
 * @param confidence 0.0 to 1.0 calibrated against the classifier's own signals
 * @param runnerUp second best match, {@link EmergencyCategory#UNKNOWN} if none
 * @param runnerUpConfidence confidence in the runner-up
 * @param signals human-readable evidence, e.g. which keywords matched
 * @param advisory always true; nothing here is a diagnosis or an instruction
 * @param manualOverrideApplied true when a human has already corrected this
 */
public record TriageResult(
        EmergencyCategory category,
        double confidence,
        EmergencyCategory runnerUp,
        double runnerUpConfidence,
        List<String> signals,
        boolean advisory,
        boolean manualOverrideApplied
) {

    /** Low confidence means "a human should look at this before trusting it". */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.45;

    public TriageResult {
        signals = List.copyOf(signals);
        confidence = clamp(confidence);
        runnerUpConfidence = clamp(runnerUpConfidence);
    }

    /** True when the classifier is not confident enough to reorder a queue by itself. */
    public boolean lowConfidence() {
        return confidence < LOW_CONFIDENCE_THRESHOLD;
    }

    /** Advisory severity, or -1 when nothing matched. */
    public int severity() {
        return category.defaultSeverity();
    }

    /** Returns a copy marked as corrected by a responder or by the sender. */
    public TriageResult withManualOverride(EmergencyCategory corrected) {
        return new TriageResult(corrected, 1.0, category, confidence,
                List.copyOf(signals), advisory, true);
    }

    /** Human-readable one-line summary for the responder dashboard. */
    public String summary() {
        if (category == EmergencyCategory.UNKNOWN) {
            return "Unclassified - needs human review";
        }
        String pct = Math.round(confidence * 100) + "%";
        if (lowConfidence() || manualOverrideApplied) {
            return category.label() + " (advisory, " + pct + ")";
        }
        return category.label() + " (" + pct + ")";
    }

    private static double clamp(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Result used when classification is unavailable or deliberately skipped. */
    public static TriageResult unknown(String reason) {
        return new TriageResult(EmergencyCategory.UNKNOWN, 0.0, EmergencyCategory.UNKNOWN, 0.0,
                List.of(reason), true, false);
    }
}