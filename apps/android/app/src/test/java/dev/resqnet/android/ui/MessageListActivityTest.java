
package dev.resqnet.android.ui;

import static org.assertj.core.api.Assertions.assertThat;

import android.view.View;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import dev.resqnet.android.R;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.protocol.Priority;
import dev.resqnet.mesh.routing.StoredMessage;

@RunWith(RobolectricTestRunner.class)
public class MessageListActivityTest {

    @Test
    public void refreshStopsSpinnerWhenListIsEmpty() throws Exception {
        TestMessageListActivity activity = launchActivity();

        SwipeRefreshLayout refresh = activity.findViewById(R.id.refresh);
        refresh.setRefreshing(true);
        invokeRefresh(activity);

        assertThat(refresh.isRefreshing()).isFalse();
        assertThat(activity.findViewById(R.id.emptyState).getVisibility())
                .isEqualTo(View.VISIBLE);
        assertThat(activity.findViewById(R.id.list).getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void refreshStopsSpinnerWhenListHasMessages() throws Exception {
        TestMessageListActivity activity = launchActivity();
        activity.records.add(new StoredMessage(
                "test-message",
                new byte[0],
                DeliveryState.CREATED,
                "test-origin",
                null,
                MessageType.TEXT,
                Priority.SOS,
                System.currentTimeMillis(),
                System.currentTimeMillis() + 60_000L,
                0,
                0L,
                "test",
                -1L));

        SwipeRefreshLayout refresh = activity.findViewById(R.id.refresh);
        refresh.setRefreshing(true);
        invokeRefresh(activity);

        assertThat(refresh.isRefreshing()).isFalse();
        assertThat(activity.findViewById(R.id.emptyState).getVisibility())
                .isEqualTo(View.GONE);
        assertThat(activity.findViewById(R.id.list).getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    private static TestMessageListActivity launchActivity() {
        return Robolectric.buildActivity(TestMessageListActivity.class)
                .setup()
                .get();
    }

    private static void invokeRefresh(MessageListActivity activity)
            throws Exception {
        Method method = MessageListActivity.class.getDeclaredMethod("refresh");
        method.setAccessible(true);
        method.invoke(activity);
    }

    public static class TestMessageListActivity extends MessageListActivity {
        final List<StoredMessage> records = new ArrayList<>();

        @Override
        protected List<StoredMessage> loadRecords() {
            return records;
        }

        @Override
        protected int titleRes() {
            return R.string.inbox_title;
        }

        @Override
        protected int emptyTextRes() {
            return R.string.inbox_empty;
        }

        @Override
        protected void openDetail(StoredMessage record) {
            // No navigation is needed for this test.
        }
    }
}
