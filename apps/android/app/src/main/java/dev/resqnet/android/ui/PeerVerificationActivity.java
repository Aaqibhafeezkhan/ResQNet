package dev.resqnet.android.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import dev.resqnet.android.R;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.mesh.crypto.TrustStore;

import java.util.Optional;

/**
 * The in-person verification screen.
 *
 * <p>One code, one decision. The code on this screen must be read aloud and
 * compared with the code on the other device. If they match, the key is stored
 * and the peer can send trusted content. If they do not, nothing is stored and
 * the two devices stay strangers.
 *
 * <p>There is no timeout, no "remind me later" that silently trusts, and no way
 * to complete this without looking at it.
 */
public final class PeerVerificationActivity extends AppCompatActivity {

    private static final String EXTRA_DEVICE_ID = "deviceId";
    private static final String EXTRA_CODE = "code";
    private static final String EXTRA_NAME = "name";

    static Intent intentFor(Context context, String deviceId, String code, String name) {
        Intent intent = new Intent(context, PeerVerificationActivity.class);
        intent.putExtra(EXTRA_DEVICE_ID, deviceId);
        intent.putExtra(EXTRA_CODE, code);
        intent.putExtra(EXTRA_NAME, name);
        return intent;
    }

    private MeshEngine engine;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peer_verification);
        engine = MeshService.obtainEngine(this);

        String deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        String code = getIntent().getStringExtra(EXTRA_CODE);
        String name = getIntent().getStringExtra(EXTRA_NAME);

        ((TextView) findViewById(R.id.peerCodeLarge)).setText(code);
        ((TextView) findViewById(R.id.peerName)).setText(name);

        findViewById(R.id.verifyConfirm).setOnClickListener(v -> verify(deviceId, name));
        findViewById(R.id.verifyCancel).setOnClickListener(v -> finish());
    }

    private void verify(String deviceId, String name) {
        engine.trustStore().markVerified(deviceId, java.time.Instant.now());
        engine.database().putSetting("peer.verified." + deviceId, "true");
        TrustStore.Entry entry = engine.trustStore().entry(deviceId).orElse(null);
        if (entry != null) {
            engine.database().savePeer(entry);
        }
        Toast.makeText(this, name + " verified", Toast.LENGTH_SHORT).show();
        finish();
    }
}
