package dev.resqnet.android.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.model.EmergencyPayload;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.mesh.protocol.EnvelopeCodec;
import dev.resqnet.mesh.routing.StoredMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One message in full, with its complete delivery audit trail.
 *
 * <p>The trail is the point. "Sent" and "confirmed received" are different
 * claims with different evidence, and showing them adjacently with timestamps is
 * how a user can tell whether to keep waiting or try something else.
 */
public final class MessageDetailActivity extends AppCompatActivity {

    private static final String EXTRA_MESSAGE_ID = "messageId";

    static Intent intentFor(Context context, String messageId) {
        Intent intent = new Intent(context, MessageDetailActivity.class);
        intent.putExtra(EXTRA_MESSAGE_ID, messageId);
        return intent;
    }

    private MeshEngine engine;
    private String messageId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_detail);
        engine = MeshService.obtainEngine(this);
        messageId = getIntent().getStringExtra(EXTRA_MESSAGE_ID);

        findViewById(R.id.detailRetry).setOnClickListener(v -> {
            engine.nudge();
            render();
        });
        render();
    }

    private void render() {
        Optional<StoredMessage> record = engine.store().find(messageId);
        if (record.isEmpty()) {
            finish();
            return;
        }
        StoredMessage stored = record.get();

        TextView state = findViewById(R.id.detailState);
        state.setText(getString(R.string.detail_state,
                HomeActivity.stateLabel(this, stored.state())));

        EmergencyPayload.Decoded decoded = decode(stored);
        ((TextView) findViewById(R.id.detailBody)).setText(decoded.body());

        TextView route = findViewById(R.id.detailRoute);
        String origin = stored.originDeviceId() == null ? "?" : stored.originDeviceId();
        route.setText(origin + "\n" + stored.lastDetail());

        TextView location = findViewById(R.id.detailLocation);
        if (decoded.hasLocation()) {
            location.setVisibility(View.VISIBLE);
            location.setText(getString(R.string.detail_location, decoded.locationSummary()));
        } else {
            location.setVisibility(View.VISIBLE);
            location.setText(R.string.detail_no_location);
        }

        TextView triage = findViewById(R.id.detailTriage);
        Optional<ResQNetDatabase.TriageRecord> hint = engine.database().loadTriage(messageId);
        triage.setVisibility(hint.isPresent() ? View.VISIBLE : View.GONE);
        hint.ifPresent(record1 -> triage.setText(String.format(java.util.Locale.getDefault(),
                "Advisory triage: %s (%.0f%% confidence, suggestion only)",
                record1.category(), record1.confidence() * 100)));

        engine.database().markRead(messageId, true);

        RecyclerView history = findViewById(R.id.detailHistory);
        history.setLayoutManager(new LinearLayoutManager(this));
        history.setAdapter(new AuditAdapter(engine.database().deliveryHistory(messageId)));
    }

    private EmergencyPayload.Decoded decode(StoredMessage stored) {
        try {
            return EmergencyPayload.decode(
                    EnvelopeCodec.decode(stored.envelope()).payload());
        } catch (Exception e) {
            return new EmergencyPayload.Decoded("(could not decode)", false, 0, 0);
        }
    }

    /** Renders the append-only transition log. */
    static final class AuditAdapter extends RecyclerView.Adapter<AuditAdapter.Holder> {

        private final List<ResQNetDatabase.DeliveryAuditEntry> entries;

        AuditAdapter(List<ResQNetDatabase.DeliveryAuditEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_audit, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ResQNetDatabase.DeliveryAuditEntry entry = entries.get(position);
            holder.state.setText(entry.state().name());
            holder.time.setText(MessageListActivity.STAMP_FORMAT
                    .format(new java.util.Date(entry.atMillis())));
            holder.detail.setText(entry.detail());
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView state;
            final TextView time;
            final TextView detail;

            Holder(@NonNull View itemView) {
                super(itemView);
                state = itemView.findViewById(R.id.auditState);
                time = itemView.findViewById(R.id.auditTime);
                detail = itemView.findViewById(R.id.auditDetail);
            }
        }
    }
}
