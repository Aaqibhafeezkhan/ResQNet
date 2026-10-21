package dev.resqnet.android.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.materialswitch.MaterialSwitch;

import dev.resqnet.android.R;
import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.model.EmergencyPayload;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.android.service.SettingsKeys;
import dev.resqnet.mesh.crypto.TrustStore;
import dev.resqnet.mesh.triage.EmergencyCategory;
import dev.resqnet.mesh.triage.KeywordTriageClassifier;
import dev.resqnet.mesh.triage.TriageResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes an emergency message.
 *
 * <p>The advisory classifier runs on every keystroke and reports its own
 * uncertainty. When it is unsure it says so; when it is confident it still
 * labels the result advisory. It never blocks the send button and never
 * changes what is actually transmitted. Someone who knows their own situation
 * is a better source of truth than a keyword model.
 *
 * <p>A confirmation step precedes transmission because a message cannot be
 * recalled. The dialog says plainly whether the next send is a public broadcast
 * or an end-to-end encrypted message to one verified device.
 */
public final class SosComposerActivity extends AppCompatActivity {

    private final KeywordTriageClassifier classifier = new KeywordTriageClassifier();

    private MeshEngine engine;
    private EditText body;
    private TextView characterCount;
    private TextView triageLabel;
    private RadioButton directScope;
    private TextView directTarget;
    private MaterialSwitch attachLocation;
    private String selectedTargetDeviceId;
    private String selectedTargetLabel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_composer);
        engine = MeshService.obtainEngine(this);

        body = findViewById(R.id.messageBody);
        characterCount = findViewById(R.id.characterCount);
        triageLabel = findViewById(R.id.triageLabel);
        directScope = findViewById(R.id.scopeDirect);
        directTarget = findViewById(R.id.directTarget);
        attachLocation = findViewById(R.id.attachLocation);

        boolean locationDefault = ResQNetDatabase.get(this)
                .getSetting(SettingsKeys.LOCATION_ENABLED).map(Boolean::parseBoolean)
                .orElse(Boolean.FALSE);
        attachLocation.setChecked(locationDefault);

        body.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onBodyChanged(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        directScope.setOnClickListener(v -> {
            if (directScope.isChecked() && selectedTargetDeviceId == null) {
                chooseTarget();
            }
            updateTargetVisibility();
        });
        directTarget.setOnClickListener(v -> chooseTarget());

        findViewById(R.id.sendButton).setOnClickListener(v -> confirmThenSend());
        onBodyChanged("");
    }

    private void onBodyChanged(String text) {
        characterCount.setText(getString(R.string.sos_character_count,
                text.length(), EmergencyPayload.MAX_BODY_CHARS));
        TriageResult result = classifier.classify(text);
        if (result.category() == EmergencyCategory.UNKNOWN) {
            triageLabel.setVisibility(View.GONE);
            return;
        }
        triageLabel.setVisibility(View.VISIBLE);
        triageLabel.setText(getString(R.string.sos_triage_label) + ": " + result.summary());
    }

    private void updateTargetVisibility() {
        boolean direct = directScope.isChecked();
        directTarget.setVisibility(direct ? View.VISIBLE : View.GONE);
        if (!direct) {
            return;
        }
        if (selectedTargetDeviceId == null) {
            directTarget.setText(R.string.sos_choose_target);
        } else {
            directTarget.setText(getString(R.string.sos_encrypted_to, selectedTargetLabel));
        }
    }

    /** Offers only peers whose identity the user verified and whose HPKE key is known. */
    private void chooseTarget() {
        List<TrustStore.Entry> peers = encryptablePeers();
        if (peers.isEmpty()) {
            selectedTargetDeviceId = null;
            selectedTargetLabel = null;
            updateTargetVisibility();
            Snackbar.make(findViewById(R.id.sendButton),
                    R.string.sos_no_verified_peers, Snackbar.LENGTH_LONG).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (TrustStore.Entry peer : peers) {
            labels.add(peer.label() + "  ·  " + peer.keyFingerprint().substring(0, 4)
                    + "-" + peer.keyFingerprint().substring(4, 8));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.sos_choose_target_title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    TrustStore.Entry peer = peers.get(which);
                    selectedTargetDeviceId = peer.deviceId();
                    selectedTargetLabel = peer.label();
                    updateTargetVisibility();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private List<TrustStore.Entry> encryptablePeers() {
        List<TrustStore.Entry> peers = new ArrayList<>();
        for (TrustStore.Entry entry : engine.trustStore().entries()) {
            if (entry.trust() == TrustStore.Trust.VERIFIED
                    && engine.trustStore().canEncryptTo(entry.deviceId())) {
                peers.add(entry);
            }
        }
        return peers;
    }

    private void confirmThenSend() {
        String text = body.getText().toString().strip();
        boolean broadcast = !directScope.isChecked();
        if (!broadcast && selectedTargetDeviceId == null) {
            chooseTarget();
            return;
        }

        String message = broadcast
                ? getString(R.string.sos_confirm_body)
                : getString(R.string.sos_confirm_direct_body, selectedTargetLabel);
        new AlertDialog.Builder(this)
                .setTitle(R.string.sos_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.sos_confirm_yes, (dialog, which) -> send(text))
                .setNegativeButton(R.string.sos_confirm_no, null)
                .show();
    }

    private void send(String text) {
        boolean withLocation = attachLocation.isChecked()
                && hasLocationPermission();
        double latitude = 0;
        double longitude = 0;
        if (withLocation) {
            double[] fix = LastKnownFixProvider.get(this);
            latitude = fix[0];
            longitude = fix[1];
            // A fix we could not obtain is simply not attached. Sending a
            // plausible-looking zero coordinate would be worse than sending none.
            withLocation = EmergencyPayload.inRange(latitude, longitude);
        }

        String destination = directScope.isChecked() ? selectedTargetDeviceId : null;
        if (destination != null && !engine.trustStore().canEncryptTo(destination)) {
            Snackbar.make(findViewById(R.id.sendButton),
                    R.string.sos_encryption_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }
        try {
            engine.sendSos(text, destination, withLocation, latitude, longitude);
            boolean peers = !engine.transport().connectedPeerIds().isEmpty();
            int sent = destination != null ? R.string.sos_sent_encrypted
                    : (peers ? R.string.sos_sent : R.string.sos_sent_no_peers);
            Snackbar.make(findViewById(R.id.sendButton), sent, Snackbar.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Snackbar.make(findViewById(R.id.sendButton), String.valueOf(e.getMessage()),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
