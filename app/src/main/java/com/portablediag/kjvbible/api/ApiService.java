package com.portablediag.kjvbible.api;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.portablediag.kjvbible.R;

import java.net.BindException;

/** Runs the server while the API is on; a quiet notification says so and has Stop. */
public class ApiService extends Service {

    private static final String CHANNEL = "local_api";
    private static final String STOP = "com.portablediag.kjvbible.API_STOP";
    private static final String RESTART = "com.portablediag.kjvbible.API_RESTART";
    private static final int NOTIFY_ON = 10;
    private static final int NOTIFY_FAILED = 11;

    private ApiServer server;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            LocalApi.setEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && RESTART.equals(intent.getAction()) && server != null) {
            server.stop();
            server = null;
            LocalApi.running = false;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                getString(R.string.api_channel), NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        int port = LocalApi.port(this);
        String bind = LocalApi.bindAddress(this);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ApiService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2,
                new Intent(this, ApiActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_book)
                .setContentTitle(getString(R.string.api_running_title))
                .setContentText(getString(LocalApi.lan(this) ? R.string.api_running_lan
                        : R.string.api_running_local, port))
                .setContentIntent(open)
                .addAction(0, getString(R.string.api_stop), stop)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFY_ON, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFY_ON, n);
        }

        if (server == null || !server.isRunning()) {
            Context app = getApplicationContext();
            PhoneApiAccess.revokeIfGone(app);
            server = new ApiServer(req -> LocalApi.handle(app, req),
                    (req, res) -> PhoneApiAccess.seal(app, req, res), bind, port);
            try {
                server.start();
                LocalApi.running = true;
                LocalApi.changed();
            } catch (Exception e) {
                Log.w("KjvApi", "could not listen on " + port + ": " + e.getMessage());
                String reason = (e instanceof BindException)
                        ? getString(R.string.api_port_in_use, port)
                        : getString(R.string.api_listen_failed, port, String.valueOf(e.getMessage()));
                LocalApi.failed(this, reason);
                nm.notify(NOTIFY_FAILED, new NotificationCompat.Builder(this, CHANNEL)
                        .setSmallIcon(R.drawable.ic_book)
                        .setContentTitle(getString(R.string.api_off_title))
                        .setContentText(reason)
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .build());
                server = null;
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        server = null;
        LocalApi.running = false;
        LocalApi.changed();
        super.onDestroy();
    }

    /** Start or stop the service to match the switch. */
    static void sync(Context ctx) {
        sync(ctx, new Intent(ctx, ApiService.class));
    }

    private static void sync(Context ctx, Intent i) {
        if (LocalApi.enabled(ctx)) {
            try {
                ctx.startForegroundService(i);
            } catch (Exception e) {
                Log.w("KjvApi", "could not start: " + e.getMessage());
            }
        } else {
            ctx.stopService(i);
        }
    }

    /** A port or LAN change only lands on a fresh socket. */
    static void restart(Context ctx) {
        if (!LocalApi.enabled(ctx)) return;
        sync(ctx, new Intent(ctx, ApiService.class).setAction(RESTART));
    }
}
