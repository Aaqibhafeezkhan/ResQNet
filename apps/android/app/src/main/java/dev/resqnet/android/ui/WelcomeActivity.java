package dev.resqnet.android.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import dev.resqnet.android.R;
import dev.resqnet.android.data.ResQNetDatabase;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.android.service.SettingsKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * First run.
 *
 * <p>Does three things and no more: explains what the app does, asks for the two
 * permissions that actually matter, and starts the mesh.
 *
 * <p>No device key is generated here. Creating the identity before the user has
 * read what happens to it would be a small lie, and it would make the first-run
 * explanation something the app had already decided on the user's behalf.
 */
public final class WelcomeActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                results -> onPermissionsResolved());

        findViewById(R.id.welcomeContinue).setOnClickListener(v -> requestPermissions());
        findViewById(R.id.welcomeNotNow).setOnClickListener(v -> openHome());
    }

    private void requestPermissions() {
        List<String> missing = missingPermissions();
        if (missing.isEmpty()) {
            onPermissionsResolved();
            return;
        }
        permissionLauncher.launch(missing.toArray(new String[0]));
    }

    /** Permissions genuinely required for the emergency network to function. */
    private List<String> missingPermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 split Bluetooth into scan and connect. Nearby
            // Connections needs both.
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
            // Advertising is a separate runtime permission on API 31+ and
            // P2P_CLUSTER cannot work without it. Declaring it in the manifest
            // is only half of the job: startAdvertising throws
            // SecurityException until the user has actually granted it.
            addIfMissing(missing, Manifest.permission.BLUETOOTH_ADVERTISE);
            addIfMissing(missing, Manifest.permission.NEARBY_WIFI_DEVICES);
        } else {
            addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        // Location is optional. It is requested separately, and only so the
        // system will let the radio scan; the app never attaches it to a message
        // unless the user asks it to.
        return missing;
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    private void onPermissionsResolved() {
        boolean ready = missingPermissions().isEmpty();
        if (!ready) {
            // The radio cannot run without these, and saying so plainly beats a
            // mesh that appears to be working and silently is not.
            ((TextView) findViewById(R.id.welcomeEmergencyNotice))
                    .setText(R.string.welcome_permission_nearby);
        }
        ResQNetDatabase.get(this).putSetting(SettingsKeys.MESH_ENABLED, "true");
        MeshService.start(this);
        openHome();
    }

    private void openHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
