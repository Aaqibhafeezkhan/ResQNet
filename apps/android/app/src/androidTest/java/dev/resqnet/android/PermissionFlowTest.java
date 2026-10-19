package dev.resqnet.android;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static dev.resqnet.android.TestSupport.denyPermissionDialogs;
import static dev.resqnet.android.TestSupport.eventually;
import static dev.resqnet.android.TestSupport.isResumed;
import static dev.resqnet.android.TestSupport.permissionsGranted;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.service.SettingsKeys;
import dev.resqnet.android.ui.HomeActivity;
import dev.resqnet.android.ui.WelcomeActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * First run, with nothing granted yet.
 *
 * <p>This class deliberately declares no {@code GrantPermissionRule}. That rule
 * grants for the remainder of the instrumentation run and never takes the
 * permission back, so the moment a class holds one, every "user denied" test in
 * it becomes unreachable — it would then pass vacuously, or skip, while looking
 * like coverage. The granted path lives in {@link GrantedPermissionFlowTest}.
 */
@RunWith(AndroidJUnit4.class)
public class PermissionFlowTest {

    @Rule
    public ActivityScenarioRule<WelcomeActivity> welcome =
            new ActivityScenarioRule<>(WelcomeActivity.class);

    /** The screen's job is to explain the permissions before asking for them. */
    @Test
    public void firstRunExplainsThePermissionsItAsksFor() {
        onView(withId(R.id.welcomeHeadline)).check(matches(isDisplayed()));
        onView(withId(R.id.permissionNearby)).check(matches(isDisplayed()));
        onView(withId(R.id.permissionLocation)).check(matches(isDisplayed()));
        onView(withId(R.id.welcomeContinue)).check(matches(isDisplayed()));
        onView(withId(R.id.welcomeNotNow)).check(matches(isDisplayed()));

        // Present as text, not merely as views occupying space.
        onView(withId(R.id.permissionNearby))
                .check(matches(withText(R.string.welcome_permission_nearby)));
        onView(withId(R.id.permissionLocation))
                .check(matches(withText(R.string.welcome_permission_location)));
    }

    /**
     * Denying every runtime permission must not wedge the app.
     *
     * <p>If a previous class already granted them, this is skipped rather than
     * asserted: an assumed precondition is honest, a green check over a dialog
     * that never appeared is not.
     */
    @Test
    public void denyingEveryPermissionStillLeavesAUsableApp() {
        assumeFalse("permissions already granted; the dialog would never appear",
                permissionsGranted());

        onView(withId(R.id.welcomeContinue)).perform(click());
        denyPermissionDialogs();

        // Whatever the decision, the app must end up on a working home screen.
        // Staying on welcome forever, or crashing, are both failures.
        eventually("home screen after the permission decision", 20_000L,
                () -> isResumed(HomeActivity.class));

        onView(withId(R.id.sosButton)).check(matches(isDisplayed()));
        onView(withId(R.id.quickActions)).check(matches(isDisplayed()));
    }

    /** "Not now" must reach the app and must still enable the mesh flag. */
    @Test
    public void notNowReachesHomeAndEnablesTheMesh() {
        onView(withId(R.id.welcomeNotNow)).perform(click());

        eventually("home screen via Not now", 20_000L,
                () -> isResumed(HomeActivity.class));

        assertEquals(
                "both welcome paths must write the mesh enablement flag",
                "true",
                ResQNetDatabase.get(TestSupport.app())
                        .getSetting(SettingsKeys.MESH_ENABLED)
                        .orElse(""));
    }
}
