package dev.resqnet.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import dev.resqnet.android.data.ResQNetDatabase;

/**
 * Restores the mesh after a reboot.
 *
 * <p>Android terminates every app on reboot, so a mesh that only exists in
 * memory is a mesh that stops existing. Restarting the foreground service here
 * is what makes a device that was relaying for others return to the job after a
 * power cut, which in a flood is the common case rather than the exception.
 *
 * <p>Honest about its limits: a rebooted device still needs Nearby Connections to
 * come up, needs the user not to have force-stopped the app, and the OS can
 * still deny the foreground start. The queue is durable either way, so nothing
 * is lost, only delayed.
 */
public final class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "ResQNetBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null
                || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        boolean enabled = ResQNetDatabase.get(context)
                .getSetting(SettingsKeys.MESH_ENABLED)
                .map("true"::equals)
                .orElse(Boolean.FALSE);
        if (!enabled) {
            Log.i(TAG, "Mesh was not running before reboot; staying idle");
            return;
        }
        Log.i(TAG, "Restoring the mesh after reboot");
        MeshService.start(context);
    }
}
