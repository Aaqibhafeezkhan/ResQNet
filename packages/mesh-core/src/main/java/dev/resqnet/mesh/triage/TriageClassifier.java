package dev.resqnet.mesh.triage;

/**
 * Advisory emergency classifier.
 *
 * <p>Runs entirely on the device, with no network call and no model download. An
 * implementation must never block message transmission: in mesh-core the
 * classifier is invoked after a message has already been handed to the routing
 * engine, and any exception it throws is swallowed.
 */
@FunctionalInterface
public interface TriageClassifier {

    /**
     * Classifies free text.
     *
     * @param text the human-written message body; may be blank
     * @return never null; return {@link TriageResult#unknown} when unable
     */
    TriageResult classify(String text);

    /** Classifier that never suggests anything. Used when the feature is off. */
    static TriageClassifier disabled() {
        return text -> TriageResult.unknown("triage disabled");
    }
}