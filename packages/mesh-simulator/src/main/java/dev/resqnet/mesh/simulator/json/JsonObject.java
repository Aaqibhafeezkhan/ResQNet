package dev.resqnet.mesh.simulator.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fluent JSON object builder that preserves insertion order. */
public final class JsonObject {

    private final Map<String, Object> members = new LinkedHashMap<>();

    /** Creates an empty JSON object. */
    public static JsonObject object() {
        return new JsonObject();
    }

    /** Creates a JSON object from an existing map. */
    public static JsonObject object(Map<String, ?> values) {
        JsonObject object = new JsonObject();
        object.putAll(values);
        return object;
    }

    public JsonObject put(String key, Object value) {
        members.put(key, value);
        return this;
    }

    public JsonObject put(String key, long value) {
        members.put(key, value);
        return this;
    }

    public JsonObject put(String key, int value) {
        members.put(key, value);
        return this;
    }

    public JsonObject put(String key, double value) {
        members.put(key, value);
        return this;
    }

    public JsonObject put(String key, boolean value) {
        members.put(key, value);
        return this;
    }

    public JsonObject putIfPresent(String key, Object value) {
        if (value != null) {
            members.put(key, value);
        }
        return this;
    }

    public JsonObject putStrings(String key, Collection<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        members.put(key, array);
        return this;
    }

    public JsonObject putObjects(String key, Collection<? extends Object> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        members.put(key, array);
        return this;
    }

    public JsonObject putAll(Map<String, ?> values) {
        members.putAll(values);
        return this;
    }

    /** Builds a JSON object of string keys to numeric counts. */
    public static JsonObject ofCounts(Map<Integer, Long> counts) {
        JsonObject object = new JsonObject();
        counts.forEach((key, value) -> object.put(String.valueOf(key), value));
        return object;
    }

    /** Live view in insertion order; never a HashMap, so output is stable. */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(members);
    }

    @Override
    public String toString() {
        return Json.write(members);
    }

    public String toPrettyString() {
        return Json.writePretty(members);
    }
}
