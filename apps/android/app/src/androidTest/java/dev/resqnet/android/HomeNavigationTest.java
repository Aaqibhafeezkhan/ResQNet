package dev.resqnet.android;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static dev.resqnet.android.TestSupport.eventually;
import static dev.resqnet.android.TestSupport.isResumed;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import dev.resqnet.android.ui.DiagnosticsActivity;
import dev.resqnet.android.ui.HomeActivity;
import dev.resqnet.android.ui.InboxActivity;
import dev.resqnet.android.ui.SettingsActivity;
import dev.resqnet.android.ui.SosComposerActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Home screen: what it shows, and whether every entry on it actually goes
 * somewhere.
 *
 * <p>Navigation is asserted against the resumed activity rather than against a
 * view, because a view assertion cannot tell "the inbox opened" from "a stale
 * view with that id was still on screen".
 */
@RunWith(AndroidJUnit4.class)
public class HomeNavigationTest {

    @Rule
    public GrantPermissionRule granted = GrantPermissionRule.grant(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.POST_NOTIFICATIONS);

    @Rule
    public ActivityScenarioRule<HomeActivity> home =
            new ActivityScenarioRule<>(HomeActivity.class);

    /** The three things a responder needs at a glance. */
    @Test
    public void homeShowsStatusSosAndTheActionList() {
        onView(withId(R.id.meshStateChip)).check(matches(isDisplayed()));
        onView(withId(R.id.meshStatusDetail)).check(matches(isDisplayed()));
        onView(withId(R.id.sosButton)).check(matches(isDisplayed()));
        onView(withId(R.id.quickActions)).check(matches(isDisplayed()));
        onView(withId(R.id.sosButton)).check(matches(withText(R.string.home_sos)));
    }

    /** Every row in the quick-action list must lead to a real screen. */
    @Test
    public void quickActionOpensInbox() {
        openQuickAction(R.string.home_inbox);
        eventually("inbox screen", 15_000L, () -> isResumed(InboxActivity.class));
        onView(withId(R.id.listTitle)).check(matches(withText(R.string.inbox_title)));
    }

    @Test
    public void quickActionOpensSettings() {
        openQuickAction(R.string.home_settings);
        eventually("settings screen", 15_000L, () -> isResumed(SettingsActivity.class));
        onView(withId(R.id.identityCode)).check(matches(isDisplayed()));
        onView(withId(R.id.identityFingerprint)).check(matches(isDisplayed()));
    }

    @Test
    public void quickActionOpensDiagnostics() {
        openQuickAction(R.string.home_diagnostics);
        eventually("diagnostics screen", 15_000L,
                () -> isResumed(DiagnosticsActivity.class));
        onView(withId(R.id.diagnosticsRadio)).check(matches(isDisplayed()));
    }

    /** The primary action on the screen. */
    @Test
    public void sosButtonOpensTheComposer() {
        onView(withId(R.id.sosButton)).perform(click());
        eventually("SOS composer", 15_000L, () -> isResumed(SosComposerActivity.class));
        onView(withId(R.id.messageBody)).check(matches(isDisplayed()));
        onView(withId(R.id.sendButton)).check(matches(isDisplayed()));
    }

    /** The status chip must be one of the three states the app actually defines. */
    @Test
    public void statusChipShowsAKnownState() {
        onView(withId(R.id.meshStateChip)).check((view, noViewFoundException) -> {
            if (noViewFoundException != null) {
                throw noViewFoundException;
            }
            android.content.res.Resources res = view.getResources();
            String[] known = {
                    res.getString(R.string.home_mesh_on),
                    res.getString(R.string.home_mesh_off),
                    res.getString(R.string.home_mesh_degraded),
            };
            String actual = String.valueOf(((android.widget.TextView) view).getText());
            boolean recognised = false;
            for (String candidate : known) {
                if (candidate.equals(actual)) {
                    recognised = true;
                    break;
                }
            }
            org.junit.Assert.assertTrue(
                    "mesh chip showed an unrecognised state: [" + actual + "]", recognised);
        });
    }

    private void openQuickAction(int labelRes) {
        onView(withId(R.id.quickActions))
                .perform(actionOnItem(hasDescendant(withText(labelRes)), click()));
    }
}
