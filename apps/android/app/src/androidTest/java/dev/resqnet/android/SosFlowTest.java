package dev.resqnet.android;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static dev.resqnet.android.TestSupport.awaitBody;
import static dev.resqnet.android.TestSupport.eventually;
import static dev.resqnet.android.TestSupport.isResumed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import dev.resqnet.android.ui.HomeActivity;
import dev.resqnet.android.ui.InboxActivity;
import dev.resqnet.android.ui.SosComposerActivity;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.routing.StoredMessage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Compose an SOS, see it in the inbox, and prove it outlives the UI.
 *
 * <p>Every assertion here runs against the real SQLite-backed store the app
 * itself reads, decoded the way the inbox decodes it. Nothing is seeded behind
 * the app's back: the message is produced by pressing the buttons a user presses.
 */
@RunWith(AndroidJUnit4.class)
public class SosFlowTest {

    /** Must match {@code MeshEngine.IDLE_SWEEP_INTERVAL_MILLIS}. */
    private static final long IDLE_SWEEP_MILLIS = 5_000L;

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

    @Before
    public void startFromACleanQueue() {
        TestSupport.clearMessages();
    }

    /** Composing must persist the message before any radio attempt could happen. */
    @Test
    public void composingAnSosPersistsItToTheQueue() throws Exception {
        String body = TestSupport.uniqueBody("compose");

        compose(body);

        StoredMessage stored = awaitBody(body, 20_000L);
        assertNotNull("a composed SOS must be in the store", stored);
        assertEquals(
                "with no peer in range the message must be queued, never failed",
                DeliveryState.QUEUED,
                stored.state());
        assertTrue("the body must round-trip through encode and decode",
                TestSupport.decodeBody(stored).contains(body));

        // Long enough for the idle sweep to run at least once. Alone, with no
        // peer ever seen, the queue must still hold the message as pending.
        Thread.sleep(IDLE_SWEEP_MILLIS + 1_500L);
        StoredMessage afterSweep = TestSupport.findBody(body).orElse(null);
        assertNotNull("the message must survive a retry sweep", afterSweep);
        assertEquals("with no peer in range it must still be queued, never failed",
                DeliveryState.QUEUED, afterSweep.state());
    }

    /** The inbox must show the composed message, with a human state label. */
    @Test
    public void inboxShowsTheComposedMessageWithItsStateLabel() {
        String body = TestSupport.uniqueBody("inbox");

        compose(body);
        assertNotNull("the SOS must exist before the inbox can show it",
                awaitBody(body, 20_000L));

        ActivityScenario.launch(InboxActivity.class);

        eventually("inbox row for the composed message", 20_000L,
                () -> rowPresent(body));

        onView(withId(R.id.listTitle)).check(matches(withText(R.string.inbox_title)));
        onView(allOfBody(body)).check(matches(isDisplayed()));
        // The state chip carries the wording that decides whether a responder
        // holds a position, so it must read as still-pending, not as delivered.
        onView(withText(R.string.state_queued)).check(matches(isDisplayed()));
    }

    /** Persistence: a fresh read of the database must find it, not a cached copy. */
    @Test
    public void aComposedMessageIsDurableAcrossStoreInstances() {
        String body = TestSupport.uniqueBody("durable");

        compose(body);
        assertNotNull(awaitBody(body, 20_000L));

        // A second store over the same database proves the row hit SQLite rather
        // than living in the first store object's memory.
        dev.resqnet.android.data.ResQNetDatabase db =
                dev.resqnet.android.data.ResQNetDatabase.get(TestSupport.app());
        dev.resqnet.android.data.SqliteMessageStore reopened =
                new dev.resqnet.android.data.SqliteMessageStore(db, 512, null);
        try {
            String found = null;
            for (StoredMessage message : reopened.recent(200)) {
                if (body.equals(TestSupport.decodeBody(message))) {
                    found = message.messageId();
                    assertEquals("state must be durable too",
                            DeliveryState.QUEUED, message.state());
                    break;
                }
            }
            assertNotNull("a second store over the same file must see the row", found);
        } finally {
            reopened.close();
        }
    }

    /**
     * Restart recovery: the queue and the identity must survive the UI being
     * torn down and rebuilt, which is the mechanism START_STICKY relies on.
     */
    @Test
    public void queueAndIdentitySurviveRecreatingTheUI() {
        String body = TestSupport.uniqueBody("recreate");

        compose(body);
        StoredMessage before = awaitBody(body, 20_000L);
        assertNotNull(before);
        String identityBefore = TestSupport.engine().deviceId();
        String codeBefore = TestSupport.engine().shortCode();

        ActivityScenario.launch(InboxActivity.class).recreate();
        ActivityScenario.launch(dev.resqnet.android.ui.SettingsActivity.class).recreate();

        StoredMessage after = TestSupport.findBody(body).orElse(null);
        assertNotNull("the queue must survive the activity being destroyed", after);
        assertEquals("message id must not change across a restart",
                before.messageId(), after.messageId());
        assertEquals("device id must be stable across a restart",
                identityBefore, TestSupport.engine().deviceId());
        assertEquals("verification code must be stable across a restart",
                codeBefore, TestSupport.engine().shortCode());
    }

    /** A directed send with no verified peer must fail closed, never go public. */
    @Test
    public void directSendWithoutAVerifiedPeerSendsNothing() {
        String body = TestSupport.uniqueBody("no-verified-peer");

        ActivityScenario<SosComposerActivity> scenario =
                ActivityScenario.launch(SosComposerActivity.class);
        // The radio is partly clipped on smaller emulator widths; invoking it on
        // the activity still exercises the same listener the user's tap reaches.
        scenario.onActivity(activity ->
                activity.findViewById(R.id.scopeDirect).performClick());
        onView(withId(R.id.messageBody))
                .perform(replaceText(body), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        // There is deliberately no dialog to confirm: the composer refuses to
        // invent a destination and must not fall back to the public broadcast.
        eventually("the refusal snackbar", 5_000L, () -> {
            try {
                onView(withText(R.string.sos_no_verified_peers)).check(matches(isDisplayed()));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });
        assertEquals("no message may be queued when encryption is unavailable",
                0, TestSupport.engine().store().recent(200).stream()
                        .filter(message -> body.equals(TestSupport.decodeBody(message)))
                        .count());
    }

    /** Home must still reach the inbox after a message exists. */
    @Test
    public void homeStillNavigatesToAnInboxThatContainsTheMessage() {
        String body = TestSupport.uniqueBody("navigable");
        compose(body);
        assertNotNull(awaitBody(body, 20_000L));

        ActivityScenario.launch(HomeActivity.class);
        onView(withId(R.id.quickActions))
                .perform(actionOnItem(
                        hasDescendant(withText(R.string.home_inbox)), click()));
        eventually("inbox from home", 15_000L, () -> isResumed(InboxActivity.class));
        onView(allOfBody(body)).check(matches(isDisplayed()));
    }

    // ---------------------------------------------------------------- helpers

    /** Types the body, confirms the dialog, and lets the activity finish. */
    private void compose(String body) {
        ActivityScenario.launch(SosComposerActivity.class);
        onView(withId(R.id.messageBody))
                .perform(replaceText(body), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());
        onView(withText(R.string.sos_confirm_yes)).perform(click());
    }

    /** Matches the single inbox row carrying this body. */
    private static org.hamcrest.Matcher<android.view.View> allOfBody(String body) {
        return org.hamcrest.Matchers.allOf(
                withId(R.id.messageBody), withText(body));
    }

    private static boolean rowPresent(String body) {
        try {
            onView(allOfBody(body)).check(matches(isDisplayed()));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
