package dev.resqnet.android;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static dev.resqnet.android.TestSupport.eventually;
import static dev.resqnet.android.TestSupport.isResumed;
import static dev.resqnet.android.TestSupport.permissionsGranted;
import static dev.resqnet.android.TestSupport.scrollToEnd;
import static org.junit.Assert.assertTrue;

import androidx.core.widget.NestedScrollView;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import dev.resqnet.android.ui.HomeActivity;
import dev.resqnet.android.ui.WelcomeActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * First run with every permission already granted.
 *
 * <p>Separated from {@link PermissionFlowTest} on purpose: {@link
 * GrantPermissionRule} grants once for the whole instrumentation run and never
 * revokes, so holding one in the same class as a "user denied" test would make
 * that test permanently unreachable while still reporting as if it ran.
 */
@RunWith(AndroidJUnit4.class)
public class GrantedPermissionFlowTest {

    @Rule
    public GrantPermissionRule granted = GrantPermissionRule.grant(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.POST_NOTIFICATIONS);

    @Rule
    public ActivityScenarioRule<WelcomeActivity> welcome =
            new ActivityScenarioRule<>(WelcomeActivity.class);

    /**
     * The granted path must reach home without any prompt appearing.
     *
     * <p>A re-prompt when nothing is missing is the failure mode users notice
     * first, and it is invisible to a test that dismisses dialogs for the app.
     */
    @Test
    public void continueWithEverythingGrantedNeverPromptsAgain() {
        assertTrue("the rule must actually have granted what the app asks for",
                permissionsGranted());

        onView(isAssignableFrom(NestedScrollView.class)).perform(scrollToEnd());
        onView(withId(R.id.welcomeContinue)).perform(click());

        // No dialog is dismissed anywhere in this test: reaching home is only
        // possible if the app found nothing to ask for.
        eventually("home screen with no prompt", 20_000L,
                () -> isResumed(HomeActivity.class));

        onView(withId(R.id.sosButton)).check(matches(isDisplayed()));
        onView(withId(R.id.quickActions)).check(matches(isDisplayed()));
    }

    /** The rule's grants and the app's own check must agree. */
    @Test
    public void appPermissionCheckAgreesWithTheGrantedState() {
        assertTrue(
                "the app's ContextCompat check must report granted after the rule",
                permissionsGranted());
    }
}
