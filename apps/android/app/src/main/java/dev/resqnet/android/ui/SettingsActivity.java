package dev.resqnet.android.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import dev.resqnet.android.R;
import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.identity.KeystoreIdentity;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.android.service.SettingsKeys;
import dev.resqnet.mesh.crypto.DeviceIdentity;

import java.security.GeneralSecurityException;

/**
 * Identity, privacy defaults, and an honest description of what this app cannot
 * promise.
 *
 * <p>The limitations block is not legal filler. Someone deciding whether to
 * depend on this software in a flood needs to know that Android can stop any
 * app, including this one, before they find out at the worst possible moment.
 */
public final class SettingsActivity extends AppCompatActivity {

    private MeshEngine engine;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        engine = MeshService.obtainEngine(this);
        ResQNetDatabase database = ResQNetDatabase.get(this);

        ((TextView) findViewById(R.id.identityCode)).setText(engine.shortCode());
        ((TextView) findViewById(R.id.identityFingerprint))
                .setText(chunks(engine.identity().keyFingerprint()));
        ((TextView) findViewById(R.id.identityBacking)).setText(
                engine.identity().hardwareBacked()
                        ? R.string.settings_hardware_backed
                        : R.string.settings_hardware_software);

        MaterialSwitch locationSwitch = findViewById(R.id.locationSwitch);
        locationSwitch.setChecked(database.getSetting(SettingsKeys.LOCATION_ENABLED)
                .map(Boolean::parseBoolean).orElse(Boolean.FALSE));
        locationSwitch.setOnCheckedChangeListener((button, checked) ->
                database.putSetting(SettingsKeys.LOCATION_ENABLED, String.valueOf(checked)));

        MaterialSwitch triageSwitch = findViewById(R.id.triageSwitch);
        triageSwitch.setChecked(database.getSetting(SettingsKeys.TRIAGE_ENABLED)
                .map(Boolean::parseBoolean).orElse(Boolean.TRUE));
        triageSwitch.setOnCheckedChangeListener((button, checked) ->
                database.putSetting(SettingsKeys.TRIAGE_ENABLED, String.valueOf(checked)));

        findViewById(R.id.resetIdentity).setOnClickListener(v -> confirmReset());
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_reset_confirm_title)
                .setMessage(R.string.settings_reset_confirm_body)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> resetIdentity())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void resetIdentity() {
        try {
            KeystoreIdentity.destroy();
        } catch (GeneralSecurityException | java.io.IOException e) {
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        // The running engine still holds the old key. The process is asked to
        // restart so that nothing signed after this point claims an identity the
        // device no longer has.
        Toast.makeText(this, R.string.settings_reset_confirm_body, Toast.LENGTH_LONG).show();
        finishAffinity();
    }

    /** Groups a fingerprint into readable blocks. */
    public static String chunks(String fingerprint) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fingerprint.length(); i += 8) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(fingerprint, i, Math.min(i + 8, fingerprint.length()));
        }
        return out.toString();
    }

    /** Short form used in logs. */
    static String shortId(String deviceId) {
        return DeviceIdentity.shortDeviceId(deviceId);
    }
}
