package dev.resqnet.mesh.simulator.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Fluent JSON array builder. */
public final class JsonArray {

    private final List<Object> values = new ArrayList<>();

    public JsonArray add(Object value) {
        values.add(value);
        return this;
    }

    public JsonArray addStrings(Collection<String> items) {
        items.forEach(this::add);
        return this;
    }

    public List<Object> asList() {
        return List.copyOf(values);
    }

    public int size() {
        return values.size();
    }

    @Override
    public String toString() {
        return Json.write(values);
    }
}