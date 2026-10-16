package dev.resqnet.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dev.resqnet.android.R;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.mesh.protocol.DeliveryState;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen someone opens when something is wrong.
 *
 * <p>Everything above the fold is one 96dp button. Under stress, with wet hands
 * and no time, the interface has to be usable without reading it. The quick
 * actions below carry the detail.
 *
 * <p>The mesh status is shown honestly: stopped, running, or degraded. It is
 * never shown as "connected" on the strength of a successful radio handover.
 */
public final class HomeActivity extends AppCompatActivity {

    private MeshEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView meshChip;
    private TextView meshDetail;
    private TextView unreadLabel;
    private boolean serviceBound;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        engine = MeshService.obtainEngine(this);

        meshChip = findViewById(R.id.meshStateChip);
        meshDetail = findViewById(R.id.meshStatusDetail);
        unreadLabel = findViewById(R.id.unreadLabel);

        findViewById(R.id.sosButton).setOnClickListener(v -> startActivity(
                new Intent(this, SosComposerActivity.class)));

        RecyclerView actions = findViewById(R.id.quickActions);
        actions.setLayoutManager(new LinearLayoutManager(this));
        actions.setAdapter(new QuickActionAdapter(buildActions()));
    }

    private List<QuickAction> buildActions() {
        List<QuickAction> actions = new ArrayList<>();
        actions.add(new QuickAction(getString(R.string.home_nearby),
                getString(R.string.home_nearby),
                () -> startActivity(new Intent(this, NearbyActivity.class))));
        actions.add(new QuickAction(getString(R.string.home_diagnostics),
                getString(R.string.home_diagnostics),
                () -> startActivity(new Intent(this, DiagnosticsActivity.class))));
        actions.add(new QuickAction(getString(R.string.home_inbox), getString(R.string.home_inbox),
                () -> startActivity(new Intent(this, InboxActivity.class))));
        actions.add(new QuickAction(getString(R.string.home_history), getString(R.string.home_history),
                () -> startActivity(new Intent(this, HistoryActivity.class))));
        actions.add(new QuickAction(getString(R.string.home_settings), getString(R.string.home_settings),
                () -> startActivity(new Intent(this, SettingsActivity.class))));
        return actions;
    }

    @Override
    protected void onStart() {
        super.onStart();
        MeshService.start(this);
        serviceBound = true;
        handler.post(poll);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(poll);
        super.onStop();
    }

    private void render() {
        boolean radioUp = engine.transport().isStarted();
        int peers = engine.transport().connectedPeerIds().size();
        int queued = engine.store().pendingCount();

        if (!radioUp) {
            meshChip.setText(R.string.home_mesh_degraded);
            meshChip.setTextColor(getColor(R.color.warning_amber));
            meshChip.setBackgroundResource(R.drawable.bg_status_chip_pending);
        } else {
            meshChip.setText(R.string.home_mesh_on);
            meshChip.setTextColor(getColor(R.color.network_cyan));
            meshChip.setBackgroundResource(R.drawable.bg_status_chip);
        }
        meshDetail.setText(getString(R.string.home_status_peers, peers, queued));

        int unread = engine.database().unreadCount();
        unreadLabel.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        if (unread > 0) {
            unreadLabel.setText(getString(R.string.home_unread, unread));
        }
    }

    /** One row on the home screen. */
    static final class QuickAction {
        final String title;
        final String badge;
        final Runnable onClick;

        QuickAction(String title, String badge, Runnable onClick) {
            this.title = title;
            this.badge = badge;
            this.onClick = onClick;
        }
    }

    /** Simple list adapter for the quick actions. */
    static final class QuickActionAdapter extends RecyclerView.Adapter<QuickActionHolder> {
        private final List<QuickAction> items;

        QuickActionAdapter(List<QuickAction> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public QuickActionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_quick_action, parent, false);
            return new QuickActionHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull QuickActionHolder holder, int position) {
            QuickAction action = items.get(position);
            holder.title.setText(action.title);
            holder.badge.setVisibility(
                    action.badge.equals(action.title) ? View.GONE : View.VISIBLE);
            holder.badge.setText(action.badge);
            holder.itemView.setOnClickListener(v -> action.onClick.run());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** View holder for a quick action. */
    static final class QuickActionHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView badge;

        QuickActionHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.actionTitle);
            badge = itemView.findViewById(R.id.actionBadge);
        }
    }

    /** Human-readable label for a delivery state, shared with other screens. */
    public static String stateLabel(android.content.Context context, DeliveryState state) {
        switch (state) {
            case CREATED:
                return context.getString(R.string.state_created);
            case QUEUED:
                return context.getString(R.string.state_queued);
            case SENT_TO_PEER:
                return context.getString(R.string.state_sent_to_peer);
            case RELAYED:
                return context.getString(R.string.state_relayed);
            case DELIVERED:
                return context.getString(R.string.state_delivered);
            case ACKNOWLEDGED:
                return context.getString(R.string.state_acknowledged);
            case EXPIRED:
                return context.getString(R.string.state_expired);
            case FAILED:
            default:
                return context.getString(R.string.state_failed);
        }
    }
}
