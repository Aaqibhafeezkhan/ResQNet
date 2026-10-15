package dev.resqnet.android.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Display helpers.
 *
 * <p>The fingerprint grouping matters more than it looks: the settings screen is
 * where a user reads out their key to verify a peer in person, and an unbroken
 * 64 character hex string is genuinely hard to read aloud without a mistake.
 */
public class SettingsDisplayTest {

    @Test
    public void groupsFingerprintIntoReadableBlocks() {
        String fingerprint = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        String grouped = dev.resqnet.android.ui.SettingsActivity.chunks(fingerprint);

        String[] lines = grouped.split("\n");
        assertEquals(8, lines.length);
        for (String line : lines) {
            assertEquals(8, line.length());
        }
        assertEquals("01234567", lines[0]);
        assertTrue(grouped.replace("\n", "").equals(fingerprint));
    }

    @Test
    public void handlesARaggedFingerprint() {
        String grouped = dev.resqnet.android.ui.SettingsActivity.chunks("abc");

        assertEquals("abc", grouped);
    }

    @Test
    public void exposesStableSettingKeys() {
        assertEquals("mesh.enabled", SettingsKeys.MESH_ENABLED);
        assertEquals("location.enabled", SettingsKeys.LOCATION_ENABLED);
        assertEquals("triage.enabled", SettingsKeys.TRIAGE_ENABLED);
    }
}
