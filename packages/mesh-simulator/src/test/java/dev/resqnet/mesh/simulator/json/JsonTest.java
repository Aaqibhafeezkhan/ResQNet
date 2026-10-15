package dev.resqnet.mesh.simulator.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Json")
class JsonTest {

    @Test
    @DisplayName("writes objects and arrays with stable member order")
    void writesStableOutput() {
        JsonObject object = JsonObject.object()
                .put("zebra", 1)
                .put("alpha", "two")
                .put("nested", JsonObject.object().put("flag", true));

        assertThat(object.toString())
                .isEqualTo("{\"zebra\":1,\"alpha\":\"two\",\"nested\":{\"flag\":true}}");
    }

    @Test
    @DisplayName("writes nulls and absent values honestly")
    void writesNulls() {
        assertThat(JsonObject.object().put("missing", (Object) null).toString())
                .isEqualTo("{\"missing\":null}");
        assertThat(JsonObject.object().putIfPresent("missing", null).toString())
                .isEqualTo("{}");
    }

    @Test
    @DisplayName("escapes control characters and quotes")
    void escapesStrings() {
        String json = Json.write("line\nbreak\t\"quoted\"\\slash");

        assertThat(json).isEqualTo("\"line\\nbreak\\t\\\"quoted\\\"\\\\slash\"");
        assertThat(Json.parse(json)).isEqualTo("line\nbreak\t\"quoted\"\\slash");
    }

    @Test
    @DisplayName("writes whole doubles without a trailing .0")
    void writesWholeDoubles() {
        assertThat(Json.write(2.0)).isEqualTo("2");
        assertThat(Json.write(2.5)).isEqualTo("2.5");
    }

    @Test
    @DisplayName("writes NaN and infinity as null rather than invalid JSON")
    void writesNonFiniteAsNull() {
        assertThat(Json.write(Double.NaN)).isEqualTo("null");
        assertThat(Json.write(Double.POSITIVE_INFINITY)).isEqualTo("null");
    }

    @Test
    @DisplayName("serialises nested builder objects rather than their toString")
    void serialisesNestedBuilders() {
        JsonObject root = JsonObject.object()
                .put("nodes", Json.array()
                        .add(JsonObject.object().put("id", 1))
                        .add(JsonObject.object().put("id", 2)));

        assertThat(root.toString())
                .isEqualTo("{\"nodes\":[{\"id\":1},{\"id\":2}]}");
    }

    @Test
    @DisplayName("round-trips a realistic report document")
    void roundTripsDocument() {
        JsonObject original = JsonObject.object()
                .put("topology", "RING")
                .put("nodes", 8)
                .put("deliveryRate", 0.875)
                .put("latency", JsonObject.object().put("p95", 4125L))
                .put("events", Json.array()
                        .add(JsonObject.object().put("kind", "forwarded").put("hopCount", 3)))
                .put("partitions", 1);

        Map<String, Object> parsed = Json.parseObject(original.toString());

        assertThat(parsed.get("topology")).isEqualTo("RING");
        assertThat(parsed.get("nodes")).isEqualTo(8.0);
        assertThat(parsed.get("deliveryRate")).isEqualTo(0.875);
        assertThat(((Map<?, ?>) parsed.get("latency")).get("p95")).isEqualTo(4125.0);
        assertThat((List<?>) parsed.get("events")).hasSize(1);
    }

    @Test
    @DisplayName("parses empty containers")
    void parsesEmptyContainers() {
        assertThat(Json.parse("{}")).isEqualTo(Map.of());
        assertThat(Json.parse("[]")).isEqualTo(List.of());
        assertThat(Json.parse("  {\n  \"a\" : 1 \n}  ")).isEqualTo(Map.of("a", 1.0));
    }

    @Test
    @DisplayName("parses escapes including unicode")
    void parsesEscapes() {
        assertThat(Json.parse("\"a\\u00e9b\"")).isEqualTo("aéb");
        assertThat(Json.parse("\"\\/\"")).isEqualTo("/");
    }

    @Test
    @DisplayName("rejects malformed documents instead of guessing")
    void rejectsMalformed() {
        assertThatThrownBy(() -> Json.parse("{"))
                .isInstanceOf(Json.JsonException.class);
        assertThatThrownBy(() -> Json.parse("{\"a\":}"))
                .isInstanceOf(Json.JsonException.class);
        assertThatThrownBy(() -> Json.parse("{\"a\":1}extra"))
                .isInstanceOf(Json.JsonException.class);
        assertThatThrownBy(() -> Json.parse("[1,]"))
                .isInstanceOf(Json.JsonException.class);
        assertThatThrownBy(() -> Json.parseObject("[1]"))
                .isInstanceOf(Json.JsonException.class);
    }

    @Test
    @DisplayName("rejects a declared length that is not present in the document")
    void rejectsTruncatedInput() {
        String valid = Json.write(Map.of("key", "value"));

        for (int cut = 1; cut < valid.length(); cut++) {
            String truncated = valid.substring(0, valid.length() - cut);
            assertThatThrownBy(() -> Json.parse(truncated))
                    .as("truncated by %d characters", cut)
                    .isInstanceOf(Json.JsonException.class);
        }
    }

    @Test
    @DisplayName("builds count maps for histogram output")
    void buildsCountMaps() {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        counts.put(1, 4L);
        counts.put(3, 2L);

        assertThat(JsonObject.ofCounts(counts).toString())
                .isEqualTo("{\"1\":4,\"3\":2}");
    }
}