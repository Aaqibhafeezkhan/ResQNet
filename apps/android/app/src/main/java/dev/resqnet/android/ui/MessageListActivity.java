
package dev.resqnet.android.ui;

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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import dev.resqnet.android.R;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.mesh.protocol.DeliveryState;
import dev.resqnet.mesh.protocol.MessageType;
import dev.resqnet.mesh.routing.StoredMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Shared implementation for the inbox and history screens.
 *
 * <p>Both screens show the same underlying records with different filtering, so
 * they share one adapter and one render path. Polling rather than observing a
 * live database keeps the list correct even when the service was never bound,
 * which is the common case after a cold start.
 */
abstract class MessageListActivity extends AppCompatActivity {

    /** Rows refreshed on this cadence. Cheap, and faster than a user notices. */
    private static final long POLL_INTERVAL_MILLIS = 1_000L;

    protected MeshEngine engine;
    private RecyclerView list;
    private View emptyState;
    private TextView emptyText;
    private SwipeRefreshLayout refreshLayout;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MessageAdapter adapter;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, POLL_INTERVAL_MILLIS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        engine = MeshService.obtainEngine(this);

        ((TextView) findViewById(R.id.listTitle)).setText(titleRes());
        list = findViewById(R.id.list);
        emptyState = findViewById(R.id.emptyState);
        emptyText = findViewById(R.id.emptyText);
        emptyText.setText(emptyTextRes());

        adapter = new MessageAdapter(this, record -> openDetail(record));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        refreshLayout = findViewById(R.id.refresh);
        refreshLayout.setOnRefreshListener(this::refresh);
    }

    /** Rows to show, already filtered. */
    protected abstract List<StoredMessage> loadRecords();

    protected abstract int titleRes();

    protected abstract int emptyTextRes();

    protected abstract void openDetail(StoredMessage record);

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(poll);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(poll);
        super.onStop();
    }

    /**
     * Updates the list and always clears the pull-to-refresh indicator.
     *
     * <p>This method is also called by periodic polling, which continues on its
     * existing schedule independently of the refresh indicator.
     */
    private void refresh() {
        try {
            List<StoredMessage> records = loadRecords();
            adapter.submit(records);

            boolean empty = records.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        } finally {
            refreshLayout.setRefreshing(false);
        }
    }

    /** Formatter shared by every list row. */
    static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    /** Formatter used on the audit trail, where the date matters. */
    static final SimpleDateFormat STAMP_FORMAT =
            new SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault());

    /** Renders one stored message row. */
    final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final TextView peer;
            final TextView state;
            final TextView body;
            final TextView meta;
            final TextView location;

            Holder(@NonNull View itemView) {
                super(itemView);
                peer = itemView.findViewById(R.id.messagePeer);
                state = itemView.findViewById(R.id.messageState);
                body = itemView.findViewById(R.id.messageBody);
                meta = itemView.findViewById(R.id.messageMeta);
                location = itemView.findViewById(R.id.messageLocation);
            }
        }

        interface OnClick {
            void onClick(StoredMessage record);
        }

        private final MessageListActivity activity;
        private final OnClick onClick;
        private final List<StoredMessage> items = new ArrayList<>();

        MessageAdapter(MessageListActivity activity, OnClick onClick) {
            this.activity = activity;
            this.onClick = onClick;
        }

        void submit(List<StoredMessage> records) {
            items.clear();
            items.addAll(records);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            StoredMessage record = items.get(position);
            holder.body.setText(engine.readBody(record).body());
            holder.peer.setText(peerLabel(record));
            holder.state.setText(HomeActivity.stateLabel(activity, record.state()));
            styleStateChip(holder.state, record.state());
            holder.meta.setText(metaLabel(record));
            holder.location.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> onClick.onClick(record));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String peerLabel(StoredMessage record) {
            String origin = record.originDeviceId();
            if (origin != null && origin.equals(engine.deviceId())) {
                return "You";
            }
            var entry = engine.trustStore().entry(origin == null ? "" : origin);
            return entry.map(e -> e.label()).orElse("Unknown device");
        }

        private String metaLabel(StoredMessage record) {
            String label = TIME_FORMAT.format(new Date(record.createdAt()))
                    + " · " + record.priority().name();
            if (record.type().protectedContent()) {
                return label + " · E2EE";
            }
            if (record.type() == MessageType.SOS || record.type() == MessageType.TEXT) {
                return label + (record.destinationDeviceId() == null
                        ? " · public" : " · unencrypted");
            }
            return label;
        }

        private void styleStateChip(TextView chip, DeliveryState state) {
            int colour;
            int background;
            if (state == DeliveryState.ACKNOWLEDGED || state == DeliveryState.DELIVERED) {
                colour = R.color.delivered_emerald;
                background = R.drawable.bg_status_chip_delivered;
            } else if (state == DeliveryState.QUEUED || state == DeliveryState.SENT_TO_PEER
                    || state == DeliveryState.CREATED) {
                colour = R.color.warning_amber;
                background = R.drawable.bg_status_chip_pending;
            } else {
                colour = R.color.text_secondary;
                background = R.drawable.bg_status_chip_pending;
            }
            chip.setTextColor(activity.getColor(colour));
            chip.setBackgroundResource(background);
        }
    }

    /** View holder for a message row. */
}
