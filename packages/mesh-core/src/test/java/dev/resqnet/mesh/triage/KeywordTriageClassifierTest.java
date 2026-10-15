package dev.resqnet.mesh.triage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Advisory triage.
 *
 * <p>Includes deliberate negative tests: a classifier that is confidently wrong
 * about an emergency is worse than one that says "I do not know". These tests
 * assert the abstain behaviour, not just the happy path.
 */
@DisplayName("KeywordTriageClassifier")
class KeywordTriageClassifierTest {

    private final KeywordTriageClassifier classifier = new KeywordTriageClassifier();

    @Test
    @DisplayName("recognises a medical emergency")
    void recognisesMedical() {
        TriageResult result = classifier.classify(
                "my father collapsed and is not breathing, there is blood everywhere");

        assertThat(result.category()).isEqualTo(EmergencyCategory.MEDICAL);
        assertThat(result.confidence()).isGreaterThan(0.0);
        assertThat(result.signals()).isNotEmpty();
        assertThat(result.advisory()).isTrue();
    }

    @Test
    @DisplayName("recognises flooding")
    void recognisesFlooding() {
        TriageResult result = classifier.classify(
                "the river burst and the water is rising fast, our street is flooded");

        assertThat(result.category()).isEqualTo(EmergencyCategory.FLOODING);
    }

    @Test
    @DisplayName("recognises fire")
    void recognisesFire() {
        TriageResult result = classifier.classify(
                "there is heavy smoke and fire spreading across the building");

        assertThat(result.category()).isEqualTo(EmergencyCategory.FIRE);
    }

    @Test
    @DisplayName("recognises being trapped")
    void recognisesTrapped() {
        TriageResult result = classifier.classify(
                "we are trapped under rubble after the building collapsed");

        assertThat(result.category()).isEqualTo(EmergencyCategory.TRAPPED);
    }

    @Test
    @DisplayName("recognises a missing person")
    void recognisesMissingPerson() {
        TriageResult result = classifier.classify(
                "my son has not returned since the storm, we cannot find him anywhere");

        assertThat(result.category()).isEqualTo(EmergencyCategory.MISSING_PERSON);
    }

    @Test
    @DisplayName("recognises a supply request")
    void recognisesSupplies() {
        TriageResult result = classifier.classify(
                "we are running out of food and water and need medication urgently");

        assertThat(result.category()).isEqualTo(EmergencyCategory.SUPPLIES);
    }

    @Test
    @DisplayName("prefers a specific category over the generic distress bucket")
    void prefersSpecificCategory() {
        TriageResult result = classifier.classify(
                "help, there is a fire in the stairwell and we cannot get out");

        assertThat(result.category())
                .isNotEqualTo(EmergencyCategory.GENERAL);
        assertThat(result.category()).isIn(EmergencyCategory.FIRE, EmergencyCategory.TRAPPED);
    }

    @Test
    @DisplayName("abstains when the message says nothing recognisable")
    void abstainsOnUnknownText() {
        TriageResult result = classifier.classify("hello, is anyone there");

        assertThat(result.category()).isEqualTo(EmergencyCategory.UNKNOWN);
        assertThat(result.confidence()).isLessThan(TriageResult.LOW_CONFIDENCE_THRESHOLD);
        assertThat(result.summary()).contains("human review");
    }

    @Test
    @DisplayName("abstains on empty input rather than guessing")
    void abstainsOnEmptyInput() {
        assertThat(classifier.classify("").category()).isEqualTo(EmergencyCategory.UNKNOWN);
        assertThat(classifier.classify("   ").category()).isEqualTo(EmergencyCategory.UNKNOWN);
        assertThat(classifier.classify(null).category()).isEqualTo(EmergencyCategory.UNKNOWN);
        assertThat(classifier.classify("!!! ??? ***").category()).isEqualTo(EmergencyCategory.UNKNOWN);
    }

    @Test
    @DisplayName("damps specific categories when the message is only one word")
    void dampsShortMessages() {
        TriageResult result = classifier.classify("fire");

        assertThat(result.lowConfidence())
                .as("one word is not enough evidence for a specific emergency category")
                .isTrue();
    }

    @Test
    @DisplayName("reports lower confidence for a narrower margin")
    void reportsUncertainWhenCategoriesTie() {
        TriageResult specific = classifier.classify(
                "fire smoke burning blaze evacuation is happening right now");
        TriageResult ambiguous = classifier.classify(
                "fire smoke burning blaze evacuation");

        assertThat(ambiguous.confidence())
                .as("an ambiguous message must not look as certain as an unambiguous one")
                .isLessThanOrEqualTo(specific.confidence());
    }

    @Test
    @DisplayName("supports a manual override by a human")
    void supportsManualOverride() {
        TriageResult automatic = classifier.classify("we need food and water");
        TriageResult corrected = automatic.withManualOverride(EmergencyCategory.MEDICAL);

        assertThat(corrected.manualOverrideApplied()).isTrue();
        assertThat(corrected.category()).isEqualTo(EmergencyCategory.MEDICAL);
        assertThat(corrected.confidence()).isEqualTo(1.0);
        assertThat(automatic.manualOverrideApplied())
                .as("the override must not mutate the original result")
                .isFalse();
    }

    @Test
    @DisplayName("is deterministic for identical input")
    void isDeterministic() {
        String text = "trapped under debris and bleeding badly";

        TriageResult first = classifier.classify(text);
        TriageResult second = classifier.classify(text);

        assertThat(first.category()).isEqualTo(second.category());
        assertThat(first.confidence()).isEqualTo(second.confidence());
        assertThat(first.signals()).isEqualTo(second.signals());
    }

    @Test
    @DisplayName("runs entirely offline")
    void runsOffline() {
        assertThat(KeywordTriageClassifier.isFullyOffline()).isTrue();
        assertThat(TriageClassifier.disabled().classify("fire").category())
                .isEqualTo(EmergencyCategory.UNKNOWN);
    }

    @Test
    @DisplayName("normalises punctuation and case before matching")
    void normalisesInput() {
        TriageResult messy = classifier.classify("FIRE!!! Smoke, everywhere... FIRE");
        TriageResult clean = classifier.classify("fire smoke everywhere fire");

        assertThat(messy.category()).isEqualTo(clean.category());
    }

    @Test
    @DisplayName("exposes the severity ordering used for responder triage")
    void exposesSeverity() {
        assertThat(EmergencyCategory.FIRE.defaultSeverity())
                .isGreaterThan(EmergencyCategory.SUPPLIES.defaultSeverity());
        assertThat(EmergencyCategory.UNKNOWN.defaultSeverity()).isZero();
        assertThat(new TriageResult(EmergencyCategory.TRAPPED, 0.9, EmergencyCategory.UNKNOWN, 0.0,
                java.util.List.of("x"), true, false).severity()).isEqualTo(3);
    }
}