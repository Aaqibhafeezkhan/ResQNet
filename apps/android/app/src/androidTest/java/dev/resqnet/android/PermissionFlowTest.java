package dev.resqnet.android;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static dev.resqnet.android.TestSupport.denyPermissionDialogs;
import static dev.resqnet.android.TestSupport.eventually;
import static dev.resqnet.android.TestSupport.isResumed;
import static dev.resqnet.android.TestSupport.permissionsGranted;
import static dev.resqnet.android.TestSupport.scrollToEnd;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import androidx.core.widget.NestedScrollView;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.service.MeshService;
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

        // Present as text, not merely as views occupying space. Asserted before
        // scrolling, because scrolling to the buttons takes these off screen.
        onView(withId(R.id.permissionNearby))
                .check(matches(withText(R.string.welcome_permission_nearby)));
        onView(withId(R.id.permissionLocation))
                .check(matches(withText(R.string.welcome_permission_location)));

        // The buttons are the last children of a scrolled layout, so on a screen
        // short enough they start below the fold and would otherwise fail the
        // visibility check on geometry alone.
        onView(isAssignableFrom(NestedScrollView.class)).perform(scrollToEnd());
        onView(withId(R.id.welcomeContinue)).check(matches(isDisplayed()));
        onView(withId(R.id.welcomeNotNow)).check(matches(isDisplayed()));
    }

    /**
     * Denying every runtime permission must not wedge the app.
     *
     * <p>If a previous class already granted them, this is skipped rather than
     * asserted: an assumed precondition is honest, a green check over a dialog
     * that never appeared is not.
     *
     * <p>Also the regression test for the "Continue" half of onboarding:
     * Continue is the action that opts the user in, so it is the one that must
     * persist the enablement flag and start the mesh.
     */
    @Test
    public void denyingEveryPermissionStillLeavesAUsableApp() {
        assumeFalse("permissions already granted; the dialog would never appear",
                permissionsGranted());

        ResQNetDatabase.get(TestSupport.app())
                .putSetting(SettingsKeys.MESH_ENABLED, "false");
        MeshService.stop(TestSupport.app());
        eventually("the mesh to be down before Continue is pressed", 20_000L,
                () -> !MeshService.isRunning());

        onView(isAssignableFrom(NestedScrollView.class)).perform(scrollToEnd());
        onView(withId(R.id.welcomeContinue)).perform(click());
        denyPermissionDialogs();

        // Whatever the decision, the app must end up on a working home screen.
        // Staying on welcome forever, or crashing, are both failures.
        eventually("home screen after the permission decision", 20_000L,
                () -> isResumed(HomeActivity.class));

        onView(withId(R.id.sosButton)).check(matches(isDisplayed()));
        onView(withId(R.id.quickActions)).check(matches(isDisplayed()));

        assertEquals("'Continue' is the opt-in, so it must persist the flag",
                "true",
                ResQNetDatabase.get(TestSupport.app())
                        .getSetting(SettingsKeys.MESH_ENABLED)
                        .orElse(""));
        eventually("'Continue' must also start the mesh", 20_000L,
                MeshService::isRunning);
    }

    /**
     * "Not now" must decline, not enable.
     *
     * <p>Both welcome buttons used to write the same value, which made the
     * choice decorative: a user who pressed "Not now" still got a mesh that ran
     * in the background and was restored again after every reboot, forever,
     * from a decision they never made.
     *
     * <p>Seeded with the flag already on and the service already down so the
     * assertions cannot pass by accident: the only things that can turn the
     * flag off and keep the service down are this path's own behaviour.
     */
    @Test
    public void notNowDeclinesTheMeshAndStillReachesHome() {
        ResQNetDatabase.get(TestSupport.app())
                .putSetting(SettingsKeys.MESH_ENABLED, "true");
        MeshService.stop(TestSupport.app());
        eventually("the mesh to be down before Not now is pressed", 20_000L,
                () -> !MeshService.isRunning());

        onView(isAssignableFrom(NestedScrollView.class)).perform(scrollToEnd());
        onView(withId(R.id.welcomeNotNow)).perform(click());

        eventually("home screen via Not now", 20_000L,
                () -> isResumed(HomeActivity.class));

        assertEquals(
                "'Not now' must record a decline rather than a silent opt-in",
                "false",
                ResQNetDatabase.get(TestSupport.app())
                        .getSetting(SettingsKeys.MESH_ENABLED)
                        .orElse(""));
        eventually("a declined first run must not start background participation",
                20_000L,
                () -> !MeshService.isRunning());
    }
}
