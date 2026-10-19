package dev.resqnet.android;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import dev.resqnet.android.ui.SettingsActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Device identity, as the user actually reads it off the settings screen.
 *
 * <p>This is the human half of the trust model: two people compare eight hex
 * characters aloud. If the screen shows something other than the fingerprint
 * that is signed into every envelope, that comparison proves nothing.
 */
@RunWith(AndroidJUnit4.class)
public class IdentityTest {

    /** {@code xxxx-xxxx}: eight hex characters, one hyphen, no spaces. */
    private static final Pattern SHORT_CODE = Pattern.compile("^[0-9a-f]{4}-[0-9a-f]{4}$");

    /** The bare 64-character lowercase SHA-256 fingerprint. */
    private static final Pattern FINGERPRINT =
            Pattern.compile("^[0-9a-f]{64}$");

    /** {@code rqn1_} followed by that same fingerprint: 69 characters. */
    private static final Pattern DEVICE_ID = Pattern.compile("^rqn1_[0-9a-f]{64}$");

    @Rule
    public GrantPermissionRule granted = GrantPermissionRule.grant(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.POST_NOTIFICATIONS);

    /** The engine's device id must be exactly the documented format. */
    @Test
    public void deviceIdIsDerivedFromTheKeyInDocumentedForm() {
        String deviceId = TestSupport.engine().deviceId();

        assertNotNull("an identity must be created on first use", deviceId);
        assertTrue("device id must be rqn1_ + 64 lowercase hex, was: " + deviceId,
                DEVICE_ID.matcher(deviceId).matches());
        assertEquals("rqn1_ plus 64 hex characters", 69, deviceId.length());
        assertEquals("the short form is a prefix of the full id",
                deviceId.substring(0, 13), dev.resqnet.mesh.crypto.DeviceIdentity
                .shortDeviceId(deviceId));
    }

    /** The verification code must be readable aloud: eight hex, one hyphen. */
    @Test
    public void settingsShowsAWellFormedVerificationCode() {
        String shown = settingsText(R.id.identityCode);

        assertTrue("verification code was [" + shown + "], expected xxxx-xxxx",
                SHORT_CODE.matcher(shown).matches());
        assertEquals("the screen must show the engine's own code",
                TestSupport.engine().shortCode(), shown);
    }

    /** The displayed fingerprint must be the real one, merely wrapped for reading. */
    @Test
    public void settingsShowsTheRealFingerprintWrappedIntoBlocks() {
        String shown = settingsText(R.id.identityFingerprint).replace("\n", "");

        assertTrue("fingerprint was [" + shown + "]",
                FINGERPRINT.matcher(shown).matches());
        assertEquals("the fingerprint on screen must be the key's, not a placeholder",
                TestSupport.engine().identity().keyFingerprint(), shown);
    }

    /**
     * The code a human reads out must be the leading characters of the
     * fingerprint they are trying to confirm, or the comparison is a fiction.
     */
    @Test
    public void verificationCodeIsTheHeadOfTheFingerprint() {
        String fingerprint = TestSupport.engine().identity().keyFingerprint();
        String code = TestSupport.engine().shortCode().replace("-", "");

        assertTrue(FINGERPRINT.matcher(fingerprint).matches());
        assertEquals("the code must be the first 8 hex characters of the fingerprint",
                fingerprint.substring(0, 8), code);
        assertFalse("the code must not be the whole fingerprint",
                fingerprint.equals(TestSupport.engine().shortCode()));
    }

    /** Destroying and reopening the settings screen must not mint a new identity. */
    @Test
    public void identityIsStableWhenTheSettingsScreenIsReopened() {
        String firstId = TestSupport.engine().deviceId();
        String firstCode = settingsText(R.id.identityCode);

        ActivityScenario.launch(SettingsActivity.class).recreate();

        String secondCode = settingsText(R.id.identityCode);
        assertEquals("a screen rotation or process recreation must not re-key the device",
                firstId, TestSupport.engine().deviceId());
        assertEquals("the verification code must be stable", firstCode, secondCode);
    }

    /** Backing storage is reported honestly, whichever way the keystore answers. */
    @Test
    public void settingsReportsHowTheKeyIsBacked() {
        String backing = settingsText(R.id.identityBacking);
        int software = dev.resqnet.android.R.string.settings_hardware_software;
        int hardware = dev.resqnet.android.R.string.settings_hardware_backed;

        boolean expected = TestSupport.engine().identity().hardwareBacked();
        String wanted = TestSupport.app().getString(expected ? hardware : software);
        assertEquals("the backing label must match the keystore's own answer",
                wanted, backing);
    }

    /** The settings screen must actually be showing an identity section. */
    @Test
    public void settingsRendersTheIdentitySection() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.identityCode)).check(matches(isDisplayed()));
            onView(withId(R.id.identityFingerprint)).check(matches(isDisplayed()));
            onView(withId(R.id.identityBacking)).check(matches(isDisplayed()));
            onView(withId(R.id.identityCode))
                    .check(matches(withText(TestSupport.engine().shortCode())));
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Reads a view's text through the scenario, on the main thread. */
    private static String settingsText(int viewId) {
        AtomicReference<String> text = new AtomicReference<>("");
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity ->
                    text.set(String.valueOf(((android.widget.TextView)
                            activity.findViewById(viewId)).getText())));
        }
        return text.get();
    }
}
