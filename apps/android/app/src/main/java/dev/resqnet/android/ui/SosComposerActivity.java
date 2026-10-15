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
import dev.resqnet.mesh.triage.EmergencyCategory;
import dev.resqnet.mesh.triage.KeywordTriageClassifier;
import dev.resqnet.mesh.triage.TriageResult;

/**
 * Composes an emergency message.
 *
 * <p>The advisory classifier runs on every keystroke and reports its own
 * uncertainty. When it is unsure it says so; when it is confident it still
 * labels the result advisory. It never blocks the send button and never
 * changes what is actually transmitted. Someone who knows their own situation
 * is a better source of truth than a keyword model.
 *
 * <p>A confirmation step precedes transmission, because an SOS is broadcast to
 * everyone in range and cannot be recalled. One tap to dismiss, not two.
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

        directScope.setOnClickListener(v -> updateTargetVisibility());

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
        directTarget.setVisibility(directScope.isChecked() ? View.VISIBLE : View.GONE);
        if (directScope.isChecked()) {
            int peers = engine.transport().connectedPeerIds().size();
            directTarget.setText(peers == 0
                    ? getString(R.string.nearby_empty)
                    : peers + " device(s) in range");
        }
    }

    private void confirmThenSend() {
        String text = body.getText().toString().strip();
        boolean broadcast = !directScope.isChecked();

        new AlertDialog.Builder(this)
                .setTitle(R.string.sos_confirm_title)
                .setMessage(broadcast ? getString(R.string.sos_confirm_body) : text)
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

        String destination = directScope.isChecked() ? firstPeerDeviceId() : null;
        try {
            engine.sendSos(text, destination, withLocation, latitude, longitude);
            boolean peers = !engine.transport().connectedPeerIds().isEmpty();
            Snackbar.make(findViewById(R.id.sendButton),
                    peers ? R.string.sos_sent : R.string.sos_sent_no_peers,
                    Snackbar.LENGTH_LONG).show();
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

    /**
     * Picks a target for a directed message.
     *
     * <p>Directed sends go to one verified peer. Sending to an arbitrary unknown
     * device is not offered: until a human has compared codes, this device does
     * not know who it would be telling.
     */
    private String firstPeerDeviceId() {
        return engine.transport().connectedPeerIds().stream().findFirst().orElse(null);
    }
}
