package dev.resqnet.android.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

import dev.resqnet.android.R;
import dev.resqnet.android.service.MeshEngine;
import dev.resqnet.android.service.MeshService;
import dev.resqnet.android.transport.NearbyTransport;

/**
 * What the mesh is actually doing, on one screen.
 *
 * <p>Written for the first hardware test, not for a product. The previous
 * releases had no equivalent: every event, state change and transport error was
 * published to listeners that no activity had registered, so a handset that
 * received a perfectly good SOS and discarded it as untrusted was visually
 * identical to a handset with no peers at all.
 *
 * <p>Four questions a tester needs answered live, and nothing else:
 *
 * <ol>
 *   <li>Is the radio actually up?</li>
 *   <li>Which peers can this phone see, and which are connected?</li>
 *   <li>How many messages moved in each direction, and how many were relayed?</li>
 *   <li>What did the mesh refuse, and why?</li>
 * </ol>
 *
 * <p>Everything is selectable so the screen can be copied into a bug report.
 * The alternative, a screenshot, loses the ids that make a mesh problem
 * reproducible.
 */
public class DiagnosticsActivity extends Activity {

    private static final long REFRESH_INTERVAL_MILLIS = 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MeshEngine engine;
    private NearbyTransport transport;

    private TextView radio;
    private TextView counters;
    private TextView peers;
    private TextView feed;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        radio = findViewById(R.id.diagnosticsRadio);
        counters = findViewById(R.id.diagnosticsCounters);
        peers = findViewById(R.id.diagnosticsPeers);
        feed = findViewById(R.id.diagnosticsFeed);

        // The same entry point every other screen uses, so this activity sees
        // exactly the engine the rest of the app is looking at rather than a
        // second one.
        engine = MeshService.obtainEngine(this);
        transport = engine == null ? null : engine.transport();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        // Stop polling when off screen. A diagnostics screen that keeps waking
        // the CPU in a pocket is worse than useless during a long test.
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void render() {
        if (engine == null) {
            radio.setText(getString(R.string.diagnostics_engine_absent));
            counters.setText("");
            peers.setText("");
            feed.setText("");
            return;
        }
        MeshEngine.DiagnosticsSummary summary = engine.diagnosticsSummary();

        radio.setText(String.format(Locale.ROOT,
                "radio   %s\nready   %s\ndevice  %s",
                summary.radioStarted() ? "started" : "STOPPED",
                summary.radioReady() ? "yes" : "NO",
                dev.resqnet.mesh.crypto.DeviceIdentity.shortDeviceId(engine.deviceId())));

        counters.setText(String.format(Locale.ROOT,
                "seen %d   connected %d\nsent %d   received %d\nrelayed %d   errors %d\n"
                        + "log %d lines%s",
                summary.peersSeen(), summary.peersConnected(),
                summary.messagesSent(), summary.messagesReceived(),
                summary.messagesRelayed(), summary.errorsLogged(),
                summary.lineCount(),
                summary.linesDropped() > 0
                        ? "  (" + summary.linesDropped() + " dropped)" : ""));

        peers.setText(describePeers());

        List<String> lines = engine.diagnostics();
        feed.setText(lines.isEmpty() ? getString(R.string.diagnostics_no_events)
                : String.join("\\n", lines));
    }

    private String describePeers() {
        if (transport == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<String> discovered = transport.discoveredEndpoints();
        java.util.Collection<String> bound = transport.bindings().values();
        sb.append("discovered ").append(discovered.size()).append("  bound ")
                .append(bound.size()).append('\n');
        for (String endpoint : discovered) {
            // A leading asterisk marks a peer that is actually connected.
            // "Discovered but never connected" is the signature of a cluster
            // owner that will not accept, or of a permission that is granted but
            // ineffective, and it is a different fault from "never discovered".
            sb.append(bound.contains(endpoint) ? "* " : "  ")
                    .append(endpoint).append('\n');
        }
        return sb.toString();
    }
}