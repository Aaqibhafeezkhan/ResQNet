package dev.resqnet.android.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import dev.resqnet.android.R;
import dev.resqnet.mesh.routing.StoredMessage;

import java.util.List;

/**
 * Every message this device has sent or received.
 *
 * <p>Shows both directions on one timeline. Separating "sent" from "received"
 * would make the conversation harder to follow, and the state chip already says
 * which is which without ambiguity.
 */
public final class InboxActivity extends MessageListActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findViewById(R.id.emptyAction).setVisibility(android.view.View.VISIBLE);
        findViewById(R.id.emptyAction).setOnClickListener(
                v -> startActivity(new Intent(this, SosComposerActivity.class)));
    }

    @Override
    protected List<StoredMessage> loadRecords() {
        return engine.store().recent(200);
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
        startActivity(MessageDetailActivity.intentFor(this, record.messageId()));
    }
}
