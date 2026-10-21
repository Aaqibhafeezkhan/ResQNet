package dev.resqnet.android;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static dev.resqnet.android.TestSupport.eventually;
import static dev.resqnet.android.TestSupport.isResumed;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.data.SqliteMessageStore;
import dev.resqnet.android.ui.HomeActivity;
import dev.resqnet.android.ui.InboxActivity;
import dev.resqnet.android.ui.MessageDetailActivity;
import dev.resqnet.android.ui.SosComposerActivity;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.StoredMessage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/**
 * How the app behaves when something is missing, empty or broken.
 *
 * <p>Each case is a state a real handset reaches: a first run with an empty
 * queue, a row whose bytes cannot be decoded after a partial write, a detail
 * screen opened for an id that has since been evicted. None of them may crash,
 * and none of them may render a failure as though it were a success.
 */
@RunWith(AndroidJUnit4.class)
public class ErrorHandlingTest {

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
    public void startWithAnEmptyQueue() {
        TestSupport.clearMessages();
    }

    /** An empty queue is a normal state, and must look like one. */
    @Test
    public void emptyInboxExplainsItselfRatherThanShowingAnError() {
        ActivityScenario.launch(InboxActivity.class);

        eventually("empty state", 15_000L, () -> {
            try {
                onView(withId(R.id.emptyState)).check(matches(isDisplayed()));
                onView(withId(R.id.emptyText))
                        .check(matches(withText(R.string.inbox_empty)));
                onView(withId(R.id.emptyAction)).check(matches(isDisplayed()));
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });
    }

    /** The empty state's button must be a real shortcut, not decoration. */
    @Test
    public void emptyStateActionOpensTheComposer() {
        ActivityScenario.launch(InboxActivity.class);

        onView(withId(R.id.emptyAction)).perform(click());
        eventually("composer from the empty inbox action", 15_000L,
                () -> isResumed(SosComposerActivity.class));
        onView(withId(R.id.messageBody)).check(matches(isDisplayed()));
    }

    /**
     * A row whose envelope bytes do not decode must render the fallback string.
     *
     * <p>This is what a torn write or a future format change would produce. A
     * crash here would lose the whole list behind one bad row.
     */
    @Test
    public void anUndecodableRowRendersTheFallbackInsteadOfCrashing() {
        String corruptId = "corrupt-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        ResQNetDatabase db = ResQNetDatabase.get(TestSupport.app());
        SqliteMessageStore writer = new SqliteMessageStore(db, 512, null);
        try {
            writer.upsert(new StoredMessage(
                    corruptId,
                    new byte[] {0x00, 0x01, 0x02},
                    DeliveryState.QUEUED,
                    "rqn1_" + "a".repeat(64),
                    null,
                    MessageType.SOS,
                    Priority.SOS,
                    now,
                    now + 60_000L,
                    0,
                    now,
                    "written by a test as deliberately unreadable bytes",
                    -1L));
        } finally {
            writer.close();
        }

        ActivityScenario.launch(InboxActivity.class);

        // The list must render and the unreadable row must show the documented
        // fallback rather than taking the whole screen down with it.
        eventually("the undecodable row renders its fallback", 20_000L, () -> {
            try {
                onView(withText("(this message could not be decoded)"))
                        .check(matches(isDisplayed()));
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });
        onView(withId(R.id.listTitle)).check(matches(withText(R.string.inbox_title)));
    }

    /**
     * No validation exists for an empty body, so at minimum it must not crash.
     *
     * <p>If someone later adds validation, this test still passes: it asserts
     * survival, not that empty input ought to be accepted.
     */
    @Test
    public void anEmptyBodyDoesNotCrashTheComposer() {
        // Started from an Intent rather than a second ActivityScenario, which
        // would finish the rule's Home and leave the back stack empty once the
        // composer closes.
        android.content.Intent intent =
                new android.content.Intent(TestSupport.app(), SosComposerActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        TestSupport.app().startActivity(intent);
        eventually("the composer to attach", 10_000L, () -> {
            try {
                onView(withId(R.id.messageBody)).check(matches(isDisplayed()));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });

        onView(withId(R.id.messageBody)).perform(replaceText(""), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());
        onView(withText(R.string.sos_confirm_yes)).perform(click());

        // The composer finishes on success; whatever comes next, the process is
        // still alive and there is a resumed activity to find.
        eventually("a live activity after sending an empty body", 15_000L,
                () -> TestSupport.currentActivity() != null);
    }

    /**
     * A detail screen opened for an id that no longer exists must close itself
     * rather than render an empty page or crash.
     */
    @Test
    public void detailForAnUnknownMessageDoesNotLeaveTheAppStuck() {
        android.content.Intent intent =
                new android.content.Intent(TestSupport.app(), MessageDetailActivity.class);
        intent.putExtra("messageId", "missing-" + UUID.randomUUID());
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        TestSupport.app().startActivity(intent);

        // Either it never became the foreground screen, or it finished again.
        // Both are correct; a wedged blank detail screen is not.
        eventually("a working activity after opening an unknown message", 20_000L,
                () -> isResumed(HomeActivity.class)
                        || isResumed(InboxActivity.class));
    }

    /** Detail for a real message must render the body and the audit trail. */
    @Test
    public void detailForARealMessageRendersBodyAndHistory() throws Exception {
        String body = TestSupport.uniqueBody("detail");
        long now = System.currentTimeMillis();

        ResQNetDatabase db = ResQNetDatabase.get(TestSupport.app());
        SqliteMessageStore writer = new SqliteMessageStore(db, 512, null);
        String messageId = "detail-" + UUID.randomUUID();
        try {
            dev.resqnet.mesh.protocol.MessageEnvelope envelope =
                    dev.resqnet.mesh.protocol.MessageEnvelope.builder()
                            .messageId(messageId)
                            .origin(TestSupport.engine().deviceId())
                            .originKeyFingerprint(TestSupport.engine().identity().keyFingerprint())
                            .type(MessageType.SOS)
                            .priority(Priority.SOS)
                            .createdAt(now)
                            .expiresAt(now + 60_000L)
                            .hopLimit(8)
                            .payload(dev.resqnet.android.model.EmergencyPayload
                                    .encode(body, false, 0, 0))
                            .build();
            writer.upsert(StoredMessage.of(envelope,
                    dev.resqnet.mesh.protocol.EnvelopeCodec.encode(envelope), now));
        } finally {
            writer.close();
        }
        // The seeding store carries no auditor, so write the audit row the way
        // the engine would. Without it the history list is empty, has zero
        // height, and can never be shown.
        db.appendDelivery(messageId, DeliveryState.QUEUED, "seeded by the test",
                now, TestSupport.engine().deviceId());
        assertNotNull("the seed must be readable", TestSupport.findBody(body).orElse(null));

        android.content.Intent intent =
                new android.content.Intent(TestSupport.app(), MessageDetailActivity.class);
        intent.putExtra("messageId", messageId);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        TestSupport.app().startActivity(intent);

        eventually("detail screen with the composed body", 20_000L, () -> {
            try {
                onView(withId(R.id.detailBody)).check(matches(withText(body)));
                onView(withId(R.id.detailHistory)).check(matches(isDisplayed()));
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });
        assertTrue("the detail screen must be showing this device's own message",
                isResumed(MessageDetailActivity.class));
    }
}
