package dev.resqnet.android.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;

import dev.resqnet.android.R;
import dev.resqnet.mesh.routing.StoredMessage;

import java.util.List;

/**
 * Delivery transitions across every message.
 *
 * <p>Flattened rather than grouped by message, because the question this screen
 * answers is "what has been happening to my messages", not "what did I write".
 */
public final class HistoryActivity extends MessageListActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findViewById(R.id.emptyAction).setVisibility(android.view.View.GONE);
    }

    @Override
    protected List<StoredMessage> loadRecords() {
        return engine.store().recent(200);
    }

    @Override
    protected int titleRes() {
        return R.string.history_title;
    }

    @Override
    protected int emptyTextRes() {
        return R.string.history_empty;
    }

    @Override
    protected void openDetail(StoredMessage record) {
        startActivity(MessageDetailActivity.intentFor(this, record.messageId()));
    }
}
