package dev.resqnet.mesh.simulator.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer and reader.
 *
 * <p>mesh-core and mesh-simulator deliberately carry no third-party
 * dependencies, so the handful of JSON operations needed to export simulation
 * results and to talk to the gateway live here. It is a few hundred lines rather
 * than a dependency, and its behaviour is completely inspectable.
 *
 * <p>Output is deterministic: object member order follows insertion order, so
 * two runs with the same seed produce byte-identical files. That property is
 * what makes a regression in routing visible in a diff.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ write

    /** Starts a fluent JSON object. */
    public static JsonObject object() {
        return new JsonObject();
    }

    /** Starts a fluent JSON array. */
    public static JsonArray array() {
        return new JsonArray();
    }

    /** Convenience: renders any supported value as a JSON string. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out, 0, false);
        return out.toString();
    }

    /** Convenience: renders any supported value as indented JSON. */
    public static String writePretty(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out, 0, true);
        return out.toString();
    }

    private static void writeValue(Object value, StringBuilder out, int indent, boolean pretty) {
        switch (value) {
            case null -> out.append("null");
            case dev.resqnet.mesh.simulator.json.JsonObject o -> writeObject(o.asMap(), out,
                    indent, pretty);
            case dev.resqnet.mesh.simulator.json.JsonArray a -> writeArray(a.asList(), out,
                    indent, pretty);
            case String s -> writeString(s, out);
            case Boolean b -> out.append(b.booleanValue());
            case Integer i -> out.append(i.intValue());
            case Long l -> out.append(l.longValue());
            case Double d -> writeDouble(d, out);
            case Float f -> writeDouble(f.doubleValue(), out);
            case Map<?, ?> map -> writeObject(map, out, indent, pretty);
            case Iterable<?> iterable -> writeArray(iterable, out, indent, pretty);
            case Object[] array -> writeArray(List.of(array), out, indent, pretty);
            default -> writeString(value.toString(), out);
        }
    }

    private static void writeDouble(double value, StringBuilder out) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            out.append("null");
        } else if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            out.append((long) value);
        } else {
            out.append(value);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out, int indent, boolean pretty) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, indent + 1, pretty);
            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            if (pretty) {
                out.append(' ');
            }
            writeValue(entry.getValue(), out, indent + 1, pretty);
        }
        newline(out, indent, pretty);
        out.append('}');
    }

    private static void writeArray(Iterable<?> iterable, StringBuilder out, int indent,
                                   boolean pretty) {
        var iterator = iterable.iterator();
        if (!iterator.hasNext()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        while (iterator.hasNext()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, indent + 1, pretty);
            writeValue(iterator.next(), out, indent + 1, pretty);
        }
        newline(out, indent, pretty);
        out.append(']');
    }

    private static void newline(StringBuilder out, int indent, boolean pretty) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        out.append("  ".repeat(Math.max(0, indent)));
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ------------------------------------------------------------------- read

    /**
     * Parses JSON into {@code Map}, {@code List}, {@code String}, {@code Double},
     * {@code Boolean} and {@code null}.
     *
     * @throws JsonException on malformed input
     */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Trailing content at offset " + parser.position());
        }
        return value;
    }

    /** Parses a JSON object, failing if the document is anything else. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("Expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    /** Thrown for malformed JSON. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        int position() {
            return index;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw new JsonException("Invalid literal at offset " + index);
            }
            index += literal.length();
            return value;
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                map.put(key, readValue());
                skipWhitespace();
                requireMore('}');
                char c = text.charAt(index);
                index++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new JsonException("Expected , or } at offset " + (index - 1));
                }
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            index++;
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                requireMore(']');
                char c = text.charAt(index);
                index++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new JsonException("Expected , or ] at offset " + (index - 1));
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(
                                text.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new JsonException("Invalid escape \\" + escape);
                }
            }
        }

        private Double readNumber() {
            int start = index;
            while (index < text.length() && "+-0123456789.eE".indexOf(text.charAt(index)) >= 0) {
                index++;
            }
            if (start == index) {
                throw new JsonException("Unexpected character '" + text.charAt(index)
                        + "' at offset " + index);
            }
            try {
                return Double.valueOf(text.substring(start, index));
            } catch (NumberFormatException e) {
                throw new JsonException("Invalid number at offset " + start);
            }
        }

        /** Fails cleanly on truncated input instead of an index-out-of-bounds. */
        private void requireMore(char expected) {
            if (atEnd()) {
                throw new JsonException("Unexpected end of input, expected '" + expected + "'");
            }
        }

        private char peek() {
            return atEnd() ? '\0' : text.charAt(index);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (atEnd() || text.charAt(index) != expected) {
                throw new JsonException("Expected '" + expected + "' at offset " + index);
            }
            index++;
        }
    }
}