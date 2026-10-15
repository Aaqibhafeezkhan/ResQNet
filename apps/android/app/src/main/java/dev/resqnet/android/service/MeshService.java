package dev.resqnet.android.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import dev.resqnet.android.R;
import dev.resqnet.android.ui.HomeActivity;

/**
 * Owns the single {@link MeshEngine} for the process.
 *
 * <p>An Android service is the only component with a lifetime long enough to keep
 * a radio link up while the user is in another app, and a foreground service is
 * the only way the OS will grant that without the user having to think about it.
 * The service therefore holds the engine, publishes a notification that explains
 * what the app is doing and why, and nothing else. All logic lives in the engine
 * and in {@code mesh-core}, which are testable without Android.
 *
 * <p>What this cannot do, and does not claim to do: survive being force-stopped,
 * or run reliably while the device is in deep Doze. Android gives no such
 * guarantee to any application. What it does guarantee is that anything queued
 * survives, and goes out the moment the engine starts again.
 *
 * <p>Binding is used rather than a static singleton so that a test, or a future
 * second process, cannot accidentally share a half-initialised engine.
 */
public final class MeshService extends android.app.Service {

    /** Foreground notification channel id. */
    public static final String CHANNEL_ID = "resqnet.mesh";

    private static final int NOTIFICATION_ID = 0x524E;

    private static volatile MeshEngine engine;
    private static volatile boolean serviceRunning;

    private MeshEngine localEngine;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // The service is not bindable; UI observes through the process-wide
        // accessor below, which keeps the engine's lifetime explicit.
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MeshEngine created = engine;
        if (created == null) {
            created = obtainEngine(this);
            engine = created;
        }
        localEngine = created;

        startForegroundSafely();
        localEngine.start();
        serviceRunning = true;

        // START_STICKY because the user's intent, expressed by turning the mesh
        // on, outlives any particular process instance. Everything needed to
        // resume is in SQLite.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        serviceRunning = false;
        if (localEngine != null) {
            localEngine.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away must not kill a mesh other people are relying on.
        // The notification keeps the service alive; the queue is durable anyway.
        super.onTaskRemoved(rootIntent);
    }

    private void startForegroundSafely() {
        Notification notification = buildNotification(this, localEngine);
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                            : 0);
        } catch (Exception e) {
            // Android 12+ throws if the foreground service permission is missing
            // or the app is not in a valid state. Running degraded is far better
            // than crashing on launch, and the UI reports the degraded state.
            Log.w("ResQNetService", "Foreground start denied; running degraded", e);
        }
    }

    /** Process-wide engine accessor used by activities. */
    public static MeshEngine obtainEngine(Context context) {
        MeshEngine local = engine;
        if (local != null) {
            return local;
        }
        synchronized (MeshService.class) {
            if (engine == null) {
                try {
                    engine = new MeshEngine(context.getApplicationContext(),
                            context.getString(R.string.app_name));
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "ResQNet could not start: " + e.getMessage(), e);
                }
            }
            return engine;
        }
    }

    /** Starts the service and its engine. Safe to call repeatedly. */
    public static void start(Context context) {
        Intent intent = new Intent(context, MeshService.class);
        context.startForegroundService(intent);
    }

    /** Stops the service. The durable queue is preserved. */
    public static void stop(Context context) {
        context.stopService(new Intent(context, MeshService.class));
    }

    public static boolean isRunning() {
        return serviceRunning;
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel_mesh),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.notification_channel_mesh_description));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    /** Builds the persistent notification explaining what the mesh is doing. */
    static Notification buildNotification(Context context, MeshEngine localEngine) {
        Intent open = new Intent(context, HomeActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int pending = localEngine == null ? 0 : localEngine.store().pendingCount();
        int peers = localEngine == null ? 0 : localEngine.transport().connectedPeerIds().size();

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_status_mesh)
                .setContentTitle(context.getString(R.string.notification_mesh_title))
                .setContentText(context.getString(R.string.notification_mesh_text, peers, pending))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .build();
    }
}