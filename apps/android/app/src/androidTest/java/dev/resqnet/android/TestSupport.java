package dev.resqnet.android;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;

import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.mesh.routing.StoredMessage;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared helpers for the instrumentation suite.
 *
 * <p>Everything here deliberately avoids sleeping for a fixed period and instead
 * polls with a deadline. The mesh writes from a background executor, so a fixed
 * sleep is either flaky on a slow emulator or slow on a fast one.
 */
public final class TestSupport {

    private TestSupport() {
    }

    static Context app() {
        return ApplicationProvider.getApplicationContext();
    }

    /**
     * The engine singleton the activities themselves use.
     *
     * <p>Obtaining it here rather than constructing a second one is the point:
     * a test that opened its own engine would read its own queue and pass while
     * the app's queue was empty.
     */
    static MeshEngine engine() {
        return MeshService.obtainEngine(app());
    }

    /** A body unique to this test, so leftovers from another test cannot match it. */
    static String uniqueBody(String tag) {
        return tag + " " + UUID.randomUUID();
    }

    /** Clears every message row so empty-state assertions are meaningful. */
    static void clearMessages() {
        ResQNetDatabase db = ResQNetDatabase.get(app());
        db.getWritableDatabase().delete(ResQNetDatabase.TABLE_MESSAGES, null, null);
    }

    /** True when every permission the welcome screen asks for is granted. */
    static boolean permissionsGranted() {
        String[] required;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            required = new String[] {
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    android.Manifest.permission.NEARBY_WIFI_DEVICES,
            };
        } else {
            required = new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION};
        }
        for (String permission : required) {
            if (ContextCompat.checkSelfPermission(app(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Polls the real store for a message carrying {@code body}. Null on timeout. */
    static StoredMessage awaitBody(String body, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Optional<StoredMessage> found = findBody(body);
            if (found.isPresent()) {
                return found.get();
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** One lookup, used both for polling and for a single immediate assertion. */
    static Optional<StoredMessage> findBody(String body) {
        List<StoredMessage> recent = engine().store().recent(200);
        for (StoredMessage message : recent) {
            if (body.equals(decodeBody(message))) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    /**
     * Decodes the body exactly the way the inbox does.
     *
     * <p>Same two calls, in the same order, as
     * {@code MessageListActivity.decodeBody}: an assertion written against raw
     * bytes would pass while the screen was showing the fallback string instead.
     */
    static String decodeBody(StoredMessage message) {
        try {
            var envelope = dev.resqnet.mesh.protocol.EnvelopeCodec.decode(message.envelope());
            return dev.resqnet.android.model.EmergencyPayload.decode(envelope.payload()).body();
        } catch (Exception e) {
            return "(this message could not be decoded)";
        }
    }

    /** Asserts a boolean condition eventually becomes true, without a bare sleep. */
    static void eventually(String description, long timeoutMillis, Condition condition) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.isSatisfied()) {
                    return;
                }
            } catch (RuntimeException e) {
                last = e;
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for: " + description);
            }
        }
        if (last != null) {
            fail(String.format(Locale.US, "timed out waiting for %s; last error: %s",
                    description, last.getMessage()));
        }
        fail("timed out waiting for: " + description);
    }

    @FunctionalInterface
    interface Condition {
        boolean isSatisfied();
    }

    /** Fails the test loudly when a precondition is not met, rather than skipping. */
    static void require(boolean condition, String message) {
        assertTrue(message, condition);
    }

    /**
     * The currently resumed activity, or null.
     *
     * <p>Navigation is asserted against the resumed activity rather than against
     * a view, because a view assertion cannot distinguish "the inbox opened" from
     * "a stale view with the same id was still on screen".
     */
    static android.app.Activity currentActivity() {
        android.app.Activity[] resumed = new android.app.Activity[1];
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity
                    : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                resumed[0] = activity;
            }
        });
        return resumed[0];
    }

    /** True when the given activity class is the one on screen right now. */
    static boolean isResumed(Class<?> type) {
        android.app.Activity activity = currentActivity();
        return activity != null && type.isInstance(activity);
    }

    /**
     * Dismisses the system runtime-permission dialogs, denying each one.
     *
     * <p>Only the permission controller is matched, by package, so this cannot
     * accidentally tap something of ours. Several dialogs can appear in sequence
     * because the welcome screen asks for a group of them.
     */
    static void denyPermissionDialogs() {
        androidx.test.uiautomator.UiDevice device = androidx.test.uiautomator.UiDevice
                .getInstance(androidx.test.platform.app.InstrumentationRegistry
                        .getInstrumentation());
        String[] controllers = {
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
        };
        String[] denyIds = {"permission_deny_button", "permission_deny_all_button"};
        String[] denyLabels = {
                "Deny",
                "Don't allow",
                "Don\u2019t allow",
                "Don't allow this time",
                "Deny & don't ask again",
        };
        for (int attempt = 0; attempt < 12; attempt++) {
            androidx.test.uiautomator.UiObject2 button = null;
            for (String controller : controllers) {
                for (String id : denyIds) {
                    button = device.findObject(androidx.test.uiautomator.By
                            .res(controller, id));
                    if (button != null) {
                        break;
                    }
                }
                if (button != null) {
                    break;
                }
                for (String label : denyLabels) {
                    button = device.findObject(androidx.test.uiautomator.By
                            .pkg(controller).text(label));
                    if (button != null) {
                        break;
                    }
                }
                if (button != null) {
                    break;
                }
            }
            if (button == null) {
                return;
            }
            button.click();
            try {
                Thread.sleep(400L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
