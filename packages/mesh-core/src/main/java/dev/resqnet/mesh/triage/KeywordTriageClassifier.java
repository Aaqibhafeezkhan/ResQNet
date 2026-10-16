package dev.resqnet.mesh.triage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic, explainable, fully offline emergency text classifier.
 *
 * <p>The design goal is not maximum accuracy, it is being <em>correctly
 * uncertain</em>. A keyword model can be audited line by line, ships in 0
 * dependencies, runs in microseconds on a low-end phone with no battery budget,
 * and can tell the responder exactly which words drove its answer. A neural
 * model would be marginally more accurate and completely un-auditable, which is
 * the wrong trade for software that touches emergency response.
 *
 * <p>Scoring combines four independent signals:
 *
 * <ol>
 *   <li><b>Weighted keywords and phrases</b>, including negation handling.</li>
 *   <li><b>Distress markers</b> such as "trapped" or "cannot breathe".</li>
 *   <li><b>Urgency markers</b> such as "urgent" or all-caps shouting.</li>
 *   <li>A <b>length prior</b>: one word is not enough evidence for a specific
 *       category, so short messages are pulled toward GENERAL.</li>
 * </ol>
 *
 * <p>No output is ever presented as certain. Confidence is derived from the
 * margin between the best and second best category rather than from the raw
 * best score, so an ambiguous message reports low confidence instead of a
 * confident wrong answer.
 *
 * <p>Thread safe and allocation-light enough to run on every composed message.
 */
public final class KeywordTriageClassifier implements TriageClassifier {

    /** Phrases that lift a category, with their weight. */
    private static final Map<EmergencyCategory, Map<String, Double>> KEYWORDS = buildKeywords();

    /** Hard distress signals that always imply at least GENERAL. */
    private static final List<String> DISTRESS_MARKERS = List.of(
            "help", "trapped", "stuck", "emergency", "urgent", "sos", "danger",
            "hurt", "bleeding", "unconscious", "cannot move", "can't move");

    /** Words that suppress a category when they appear before it. */
    private static final List<String> NEGATIONS = List.of(
            "no ", "not ", "without ", "denies ", "nothing ", "nobody ");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}\\p{N}\\s']");

    @Override
    public TriageResult classify(String text) {
        if (text == null || text.isBlank()) {
            return TriageResult.unknown("empty message body");
        }
        String normalised = normalise(text);
        if (normalised.isBlank()) {
            return TriageResult.unknown("message contained no words");
        }

        Map<EmergencyCategory, Double> scores = new LinkedHashMap<>();
        List<String> signals = new ArrayList<>();

        for (Map.Entry<EmergencyCategory, Map<String, Double>> entry : KEYWORDS.entrySet()) {
            double score = 0.0;
            for (Map.Entry<String, Double> term : entry.getValue().entrySet()) {
                int hits = countOccurrences(normalised, term.getKey());
                if (hits <= 0) {
                    continue;
                }
                double weight = term.getValue();
                if (isNegated(normalised, term.getKey())) {
                    weight = -weight * 0.75;
                    signals.add("negated: '" + term.getKey() + "'");
                } else {
                    signals.add("'" + term.getKey() + "' x" + hits);
                }
                score += weight * Math.min(hits, 3);
            }
            scores.put(entry.getKey(), score);
        }

        double distressHits = 0.0;
        for (String marker : DISTRESS_MARKERS) {
            if (normalised.contains(marker)) {
                distressHits += 0.5;
                signals.add("distress: '" + marker + "'");
            }
        }
        scores.merge(EmergencyCategory.GENERAL, distressHits, Double::sum);

        double urgency = urgencyBoost(normalised);
        if (urgency > 0) {
            signals.add("urgency markers +" + format(urgency));
        }

        // A single short word cannot justify a specific category. Without this
        // prior the model would assign "fire" to the single word "fire" with
        // full confidence and "office" would look like an incident.
        int wordCount = normalised.isEmpty() ? 0 : normalised.split(" ").length;
        if (wordCount < 3) {
            for (EmergencyCategory category : scores.keySet()) {
                if (category != EmergencyCategory.GENERAL
                        && category != EmergencyCategory.UNKNOWN) {
                    scores.computeIfPresent(category, (k, v) -> v * 0.6);
                    signals.add("short message: damped specific categories");
                }
            }
        }

        List<Map.Entry<EmergencyCategory, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.removeIf(e -> e.getValue() <= 0.0);
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        if (ranked.isEmpty()) {
            // No evidence at all, not even distress. Claiming GENERAL here would
            // quietly inflate the emergency queue with every "hello, anyone
            // there?" and train responders to ignore the classifier.
            return TriageResult.unknown("no recognisable emergency signal");
        }

        Map.Entry<EmergencyCategory, Double> best = ranked.get(0);
        Map.Entry<EmergencyCategory, Double> second = ranked.size() > 1 ? ranked.get(1) : null;

        double bestScore = best.getValue();
        double secondScore = second == null ? 0.0 : second.getValue();
        double margin = (bestScore - secondScore) / bestScore;
        double saturation = bestScore / (bestScore + 3.0);

        // Confidence needs both evidence strength and separation from the
        // runner-up. High saturation with a near-tie is exactly the ambiguous
        // case we must not present as confident.
        double confidence = clamp(saturation * (0.55 + 0.45 * margin));
        confidence = clamp(confidence + urgency * 0.15);

        EmergencyCategory runnerUpCategory = second == null
                ? EmergencyCategory.UNKNOWN : second.getKey();
        double runnerUpConfidence = second == null ? 0.0 : clamp(secondScore / (bestScore + 3.0));

        if (best.getKey() == EmergencyCategory.GENERAL && second != null) {
            // Prefer a specific category over GENERAL when one matched at all,
            // because GENERAL is what you get when nothing matched.
            best = second;
            confidence = clamp(saturation * 0.7);
            runnerUpCategory = EmergencyCategory.GENERAL;
            runnerUpConfidence = clamp(distressHits / (distressHits + 3.0));
        }

        if (best.getKey() == EmergencyCategory.UNKNOWN) {
            return TriageResult.unknown("no recognisable emergency signal");
        }

        return new TriageResult(best.getKey(), confidence, runnerUpCategory,
                runnerUpConfidence, signals, true, false);
    }

    private static String normalise(String text) {
        String cleaned = NON_LETTERS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static boolean isNegated(String normalised, String term) {
        int index = normalised.indexOf(term);
        while (index > 0) {
            int windowStart = Math.max(0, index - 12);
            String window = normalised.substring(windowStart, index);
            for (String negation : NEGATIONS) {
                if (window.contains(negation.trim() + " ")
                        || window.endsWith(negation.trim())) {
                    return true;
                }
            }
            index = normalised.indexOf(term, index + term.length());
            if (index < 0) {
                return false;
            }
        }
        return false;
    }

    private static double urgencyBoost(String normalised) {
        double boost = 0.0;
        if (normalised.contains("urgent") || normalised.contains("asap")
                || normalised.contains("immediately") || normalised.contains("right now")) {
            boost += 0.4;
        }
        if (normalised.contains("please") || normalised.contains("quick")) {
            boost += 0.1;
        }
        return Math.min(boost, 0.6);
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static Map<EmergencyCategory, Map<String, Double>> buildKeywords() {
        Map<EmergencyCategory, Map<String, Double>> map = new LinkedHashMap<>();

        map.put(EmergencyCategory.MEDICAL, terms(
                "bleeding", 2.0, "blood", 1.6, "injured", 1.8, "injury", 1.8,
                "unconscious", 2.4, "not breathing", 2.6, "cannot breathe", 2.6,
                "chest pain", 2.2, "seizure", 2.2, "overdose", 2.2, "burn", 1.4,
                "broken", 1.8, "fracture", 1.8, "ambulance", 1.6, "unresponsive", 2.4,
                "collapsed", 2.0, "poison", 1.6, "allergic", 1.6, "diabetic", 1.4));

        map.put(EmergencyCategory.FLOODING, terms(
                "flood", 2.4, "flooding", 2.4, "flooded", 2.4, "water rising", 2.2,
                "rising water", 2.2, "drowning", 2.6, "submerged", 2.0, "torrential", 1.6,
                "river burst", 2.0, "dam broke", 2.0, "water level", 1.4, "swept away", 2.0,
                "storm surge", 2.0, "high water", 1.4));

        map.put(EmergencyCategory.FIRE, terms(
                "fire", 2.4, "smoke", 2.0, "burning", 2.0, "blaze", 2.4, "arson", 2.2,
                "explosion", 2.2, "gas leak", 2.2, "wildfire", 2.4, "evacuate fire", 1.8));

        map.put(EmergencyCategory.TRAPPED, terms(
                "trapped", 2.6, "pinned", 2.2, "cannot get out", 2.2, "stuck", 1.8,
                "buried", 2.2, "collapsed building", 2.4, "under rubble", 2.4,
                "locked in", 1.8, "roof caved", 2.0, "crushing", 1.8, "wedged", 1.6));

        map.put(EmergencyCategory.MISSING_PERSON, terms(
                "missing", 2.0, "missing person", 2.4, "lost child", 2.4, "child missing", 2.4,
                "cannot find", 1.8, "has not returned", 2.0, "vanished", 1.8,
                "disappeared", 1.8, "searching for", 1.8));

        map.put(EmergencyCategory.SUPPLIES, terms(
                "food", 1.4, "water", 1.2, "medication", 2.0, "medicine", 1.8,
                "insulin", 2.2, "baby formula", 2.0, "no food", 1.8, "out of water", 1.8,
                "need supplies", 1.6, "running out", 1.4, "dias", 2.0));

        return ordered(map);
    }

    private static Map<String, Double> terms(Object... pairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Double) pairs[i + 1]);
        }
        return ordered(map);
    }

    /**
     * Freezes a map while preserving insertion order.
     *
     * <p>{@code Map.copyOf} would throw that order away and iterate in a
     * per-JVM randomised order. That matters here for three reasons, all of
     * them about the result being reproducible rather than merely correct:
     * the {@code signals} list is the classifier's explanation and should read
     * the same way twice; {@code score +=} is a floating-point sum whose order
     * changes the last bits of the result; and {@code ranked.sort} is stable,
     * so a tie would be broken differently and could select a different
     * category on an identical input.
     */
    private static <K, V> Map<K, V> ordered(Map<K, V> source) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /** Exposes the weight table for documentation and tests. */
    public static Map<String, Double> weightsFor(EmergencyCategory category) {
        Map<String, Double> weights = KEYWORDS.get(category);
        return weights == null ? Map.of() : weights;
    }

    /** Reports whether this classifier can run without any platform dependency. */
    public static boolean isFullyOffline() {
        return true;
    }

    /** Sanity helper used by tests that want the normalised form. */
    public static String normaliseForTest(String text) {
        return normalise(text);
    }
}