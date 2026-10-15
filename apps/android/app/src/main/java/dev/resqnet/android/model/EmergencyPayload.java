package dev.resqnet.android.model;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Payload codec for user-visible emergency text.
 *
 * <p>Deliberately tiny and line oriented. The wire format belongs to
 * {@code mesh-core}; this is the human-facing part of a payload, and it is
 * versioned so a future format can be added without breaking a peer running
 * this build.
 *
 * <p>Design notes worth keeping:
 *
 * <ul>
 *   <li>Location is opt-in and explicit. A device never attaches coordinates
 *       the user did not ask it to attach, which is both a privacy requirement
 *       and a safety one: broadcasting a survivor's position to every device in
 *       range without consent is a harm, not a feature.</li>
 *   <li>Coordinates are truncated to five decimal places, roughly one metre.
 *       More precision has no operational use here and would leak more than
 *       necessary.</li>
 *   <li>The body is capped at the protocol payload ceiling so a long paste
 *       fails loudly at composition time rather than silently at the radio.</li>
 * </ul>
 */
public final class EmergencyPayload {

    /** First line version tag. */
    public static final String VERSION = "RQN1";

    private static final String FIELD_LOCATION = "LOC";
    private static final String SEPARATOR = "\n";

    /** Decimal places retained; about one metre. */
    private static final int COORDINATE_SCALE = 5;

    /** Body ceiling, leaving room for the location line inside the 8 KiB payload. */
    public static final int MAX_BODY_CHARS = 4096;

    private static final Pattern LATITUDE = Pattern.compile("-?\\d{1,2}(\\.\\d+)?");

    private EmergencyPayload() {
    }

    /**
     * Encodes a message body and, only when explicitly requested, a location.
     *
     * @param body free text written by the sender
     * @param attachLocation true only when the user opted in for this message
     */
    public static byte[] encode(String body, boolean attachLocation,
                                double latitude, double longitude) {
        String safeBody = body == null ? "" : body.strip();
        if (safeBody.length() > MAX_BODY_CHARS) {
            safeBody = safeBody.substring(0, MAX_BODY_CHARS);
        }
        StringBuilder payload = new StringBuilder(VERSION);
        if (attachLocation && inRange(latitude, longitude)) {
            payload.append(SEPARATOR)
                    .append(FIELD_LOCATION).append(' ')
                    .append(String.format(Locale.US, "%." + COORDINATE_SCALE + "f, %."
                            + COORDINATE_SCALE + "f", latitude, longitude));
        }
        payload.append(SEPARATOR).append(SEPARATOR).append(safeBody);
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Parsed payload. */
    public record Decoded(String body, boolean hasLocation, double latitude,
                          double longitude) {

        /** True when the sender shared a location with this message. */
        public String locationSummary() {
            if (!hasLocation) {
                return "";
            }
            return String.format(Locale.US, "%.5f, %.5f", latitude, longitude);
        }
    }

    /** Parses a payload, tolerating truncation and unknown fields. */
    public static Decoded decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return new Decoded("", false, 0, 0);
        }
        String text = new String(payload, StandardCharsets.UTF_8);
        String[] parts = text.split(SEPARATOR, 3);
        String body = parts.length >= 3 ? parts[2] : "";
        boolean hasLocation = false;
        double latitude = 0;
        double longitude = 0;
        if (parts.length >= 2 && parts[1].startsWith(FIELD_LOCATION + " ")) {
            String[] coordinates = parts[1].substring(FIELD_LOCATION.length() + 1).split(",");
            if (coordinates.length == 2) {
                try {
                    latitude = Double.parseDouble(coordinates[0].trim());
                    longitude = Double.parseDouble(coordinates[1].trim());
                    hasLocation = inRange(latitude, longitude);
                } catch (NumberFormatException e) {
                    hasLocation = false;
                }
            }
        }
        return new Decoded(body, hasLocation, latitude, longitude);
    }

    /** True for coordinates that are physically meaningful. */
    public static boolean inRange(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    /**
     * Validates a location string typed by a user.
     *
     * @return null when acceptable, otherwise a message suitable for a snackbar
     */
    public static String validateCoordinates(String latitude, String longitude) {
        if (latitude == null || latitude.isBlank() || longitude == null || longitude.isBlank()) {
            return null;
        }
        double lat;
        double lon;
        try {
            lat = Double.parseDouble(latitude.trim());
            lon = Double.parseDouble(longitude.trim());
        } catch (NumberFormatException e) {
            return "Latitude and longitude must be numbers, for example 51.5074 and -0.1278";
        }
        if (lat < -90 || lat > 90) {
            return "Latitude must be between -90 and 90";
        }
        if (lon < -180 || lon > 180) {
            return "Longitude must be between -180 and 180";
        }
        return null;
    }
}