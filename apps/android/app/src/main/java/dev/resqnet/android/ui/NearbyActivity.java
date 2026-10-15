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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dev.resqnet.android.R;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.mesh.crypto.TrustStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Devices currently in radio range, and how far this device trusts each one.
 *
 * <p>Two states matter and are shown separately. <em>In range</em> means a radio
 * link exists, which is not trust. <em>Verified</em> means a human compared
 * eight characters of fingerprint on two screens. Everything in the app that
 * accepts content from a peer depends on the second, never the first.
 *
 * <p>The verification flow deliberately has no convenient path. Compare, then
 * tap. A verification that could be completed without looking at the other
 * screen would not be a verification.
 */
public final class NearbyActivity extends AppCompatActivity {

    private MeshEngine engine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PeerAdapter adapter;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            adapter.submit(snapshot());
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        engine = MeshService.obtainEngine(this);

        ((TextView) findViewById(R.id.listTitle)).setText(R.string.nearby_title);
        RecyclerView list = findViewById(R.id.list);
        adapter = new PeerAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        ((androidx.swiperefreshlayout.widget.SwipeRefreshLayout) findViewById(R.id.refresh))
                .setOnRefreshListener(() -> {
            engine.publishIdentity();
            adapter.submit(snapshot());
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.publishIdentity();
        handler.post(poll);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(poll);
        super.onStop();
    }

    /** One row: a device in range, plus whatever this device knows about it. */
    static final class PeerRow {
        final String deviceId;
        final String label;
        final String shortCode;
        final TrustStore.Trust trust;
        final boolean inRange;

        PeerRow(String deviceId, String label, String shortCode, TrustStore.Trust trust,
                boolean inRange) {
            this.deviceId = deviceId;
            this.label = label;
            this.shortCode = shortCode;
            this.trust = trust;
            this.inRange = inRange;
        }
    }

    private List<PeerRow> snapshot() {
        Map<String, PeerRow> rows = new LinkedHashMap<>();
        for (String deviceId : engine.transport().connectedPeerIds()) {
            TrustStore.Entry entry = engine.database().loadPeer(deviceId);
            rows.put(deviceId, new PeerRow(
                    deviceId,
                    entry == null ? "Unrecognised device" : entry.label(),
                    entry == null ? "????-????" : dev.resqnet.android.data.ResQNetDatabase
                            .shortCode(entry.keyFingerprint()),
                    entry == null ? null : entry.trust(),
                    true));
        }
        engine.trustStore().entries().forEach(entry -> {
            if (!rows.containsKey(entry.deviceId())) {
                rows.put(entry.deviceId(), new PeerRow(entry.deviceId(), entry.label(),
                        dev.resqnet.android.data.ResQNetDatabase.shortCode(entry.keyFingerprint()),
                        entry.trust(), false));
            }
        });
        return new ArrayList<>(rows.values());
    }

    private void promptVerification(PeerRow row) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nearby_verify_title)
                .setMessage(getString(R.string.nearby_verify_body)
                        + "\n\n" + row.shortCode)
                .setPositiveButton(R.string.nearby_verify_confirm, (dialog, which) ->
                        startActivity(PeerVerificationActivity.intentFor(this,
                                row.deviceId, row.shortCode, row.label)))
                .setNegativeButton(R.string.nearby_verify_cancel, null)
                .show();
    }

    private void confirmBlock(PeerRow row) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nearby_block_action)
                .setMessage(row.label)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    engine.blockPeer(row.deviceId);
                    adapter.submit(snapshot());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Renders the peer list. */
    final class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
                final TextView name;
                final TextView trust;
                final TextView code;
                final TextView state;
                final View verify;
                final View block;

            Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.peerName);
            trust = itemView.findViewById(R.id.peerTrust);
            code = itemView.findViewById(R.id.peerCode);
            state = itemView.findViewById(R.id.peerState);
            verify = itemView.findViewById(R.id.peerVerify);
            block = itemView.findViewById(R.id.peerBlock);
        }
    }


        private final List<PeerRow> rows = new ArrayList<>();

        void submit(List<PeerRow> records) {
            rows.clear();
            rows.addAll(records);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_peer, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PeerRow row = rows.get(position);
            holder.name.setText(row.label);
            holder.code.setText(row.shortCode);
            holder.trust.setText(row.trust == TrustStore.Trust.VERIFIED
                    ? R.string.nearby_verified : R.string.nearby_unverified);
            holder.state.setText(row.inRange ? "In range" : "Not currently in range");
            holder.verify.setVisibility(row.trust == TrustStore.Trust.VERIFIED
                    ? View.GONE : View.VISIBLE);
            holder.verify.setOnClickListener(v -> promptVerification(row));
            holder.block.setVisibility(row.trust == TrustStore.Trust.BLOCKED
                    ? View.GONE : View.VISIBLE);
            holder.block.setOnClickListener(v -> confirmBlock(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    /** View holder for a peer row. */
}
