package dev.resqnet.android;

import android.app.Application;

import dev.resqnet.android.service.MeshService;

/**
 * Application entry point.
 *
 * <p>Deliberately does almost nothing. ResQNet must work with the radio off,
 * with no location permission and with the user never having opened the app, so
 * nothing here assumes a granted permission or a started engine. The only work
 * performed is restoring the mesh after a reboot, which the user can turn off.
 */
public class ResQNetApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // The engine is created lazily. Creating it here would generate a device
        // key before the user has seen the welcome screen, which would make the
        // first-run explanation a lie about what happens to their data.
    }

    /** Starts the foreground mesh service if the user left it running. */
    public void resumeMeshIfEnabled() {
        MeshService.start(this);
    }
}
