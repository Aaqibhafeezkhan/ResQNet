package dev.resqnet.android.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * The user-facing half of a wire payload.
 *
 * <p>Location handling gets the most attention because it is the one place where
 * a convenience decision becomes a privacy failure. Everything here runs on the
 * JVM; nothing about it needs a device.
 */
public class EmergencyPayloadTest {

    @Test
    public void encodesAndDecodesAPlainMessage() {
        byte[] encoded = EmergencyPayload.encode("we are trapped upstairs", false, 0, 0);

        EmergencyPayload.Decoded decoded = EmergencyPayload.decode(encoded);

        assertEquals("we are trapped upstairs", decoded.body());
        assertFalse(decoded.hasLocation());
    }

    @Test
    public void neverAttachesLocationUnlessAsked() {
        byte[] encoded = EmergencyPayload.encode("help", false, 51.5074, -0.1278);

        assertFalse(EmergencyPayload.decode(encoded).hasLocation());
        assertFalse("coordinates must not leak into the payload text",
                new String(encoded, StandardCharsets.UTF_8).contains("51.5"));
    }

    @Test
    public void attachesLocationWhenRequested() {
        byte[] encoded = EmergencyPayload.encode("help", true, 51.507412, -0.127765);
        EmergencyPayload.Decoded decoded = EmergencyPayload.decode(encoded);

        assertTrue(decoded.hasLocation());
        assertEquals(51.50741, decoded.latitude(), 0.00001);
        assertEquals(-0.12777, decoded.longitude(), 0.00001);
    }

    @Test
    public void treatsNullIslandAsNoFix() {
        byte[] encoded = EmergencyPayload.encode("help", true, 0, 0);

        assertFalse("a zero fix is an absent fix, not a location off West Africa",
                EmergencyPayload.decode(encoded).hasLocation());
    }

    @Test
    public void rejectsImpossibleCoordinates() {
        assertFalse(EmergencyPayload.inRange(91, 0));
        assertFalse(EmergencyPayload.inRange(0, 181));
        assertTrue(EmergencyPayload.inRange(-33.8688, 151.2093));
    }

    @Test
    public void survivesAnEmptyBody() {
        EmergencyPayload.Decoded decoded =
                EmergencyPayload.decode(EmergencyPayload.encode("", false, 0, 0));

        assertEquals("", decoded.body());
        assertFalse(decoded.hasLocation());
    }

    @Test
    public void survivesAnEmptyPayload() {
        EmergencyPayload.Decoded decoded = EmergencyPayload.decode(new byte[0]);

        assertEquals("", decoded.body());
        assertFalse(decoded.hasLocation());
    }

    @Test
    public void survivesANullPayload() {
        assertEquals("", EmergencyPayload.decode(null).body());
    }

    @Test
    public void truncatesAnOversizedBodyRatherThanFailing() {
        String huge = "x".repeat(EmergencyPayload.MAX_BODY_CHARS + 500);

        String decoded = EmergencyPayload.decode(EmergencyPayload.encode(huge, false, 0, 0)).body();

        assertEquals(EmergencyPayload.MAX_BODY_CHARS, decoded.length());
    }

    @Test
    public void preservesMultiLineMessages() {
        String body = "line one\nline two\nline three";

        assertEquals(body, EmergencyPayload.decode(
                EmergencyPayload.encode(body, false, 0, 0)).body());
    }

    @Test
    public void preservesNonAsciiText() {
        String body = "wir sind eingeschlossen — Hilfe";

        assertEquals(body, EmergencyPayload.decode(
                EmergencyPayload.encode(body, false, 0, 0)).body());
    }

    @Test
    public void ignoresAMalformedLocationLine() {
        byte[] tampered = "RQN1\nLOC not-a-number\nbody".getBytes(StandardCharsets.UTF_8);

        EmergencyPayload.Decoded decoded = EmergencyPayload.decode(tampered);

        assertFalse(decoded.hasLocation());
        assertEquals("body", decoded.body());
    }

    @Test
    public void validatesTypedCoordinatesWithUsefulMessages() {
        assertEquals(null, EmergencyPayload.validateCoordinates("51.5074", "-0.1278"));
        assertTrue(EmergencyPayload.validateCoordinates("abc", "0").contains("numbers"));
        assertTrue(EmergencyPayload.validateCoordinates("100", "0").contains("-90"));
        assertTrue(EmergencyPayload.validateCoordinates("0", "200").contains("-180"));
    }

    @Test
    public void treatsBlankCoordinatesAsAbsentRatherThanInvalid() {
        assertEquals(null, EmergencyPayload.validateCoordinates("", ""));
        assertEquals(null, EmergencyPayload.validateCoordinates(null, null));
    }
}
