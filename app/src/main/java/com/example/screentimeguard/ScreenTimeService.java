package com.example.screentimeguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class ScreenTimeService extends Service {
    private static final String CHANNEL = "screen_time_guard";
    private static final int ID = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 15000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, "Screen Time Guard", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Shows today's used and remaining screen time.");
                nm.createNotificationChannel(channel);
            }
        }

        Notification n = buildNotification("Starting screen-time monitor…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID, n);
        }
        handler.post(poll);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void tick() {
        PolicyUtils.applyAdGuardProtection(this);

        if (!ScreenTimeTracker.hasUsageAccess(this)) {
            update("Usage Access missing");
            return;
        }

        long used = ScreenTimeTracker.getTodayInteractiveMillis(this);
        if (used < 0L) {
            update("Unable to read today's screen time");
            return;
        }

        long limit = Prefs.getLimitMinutes(this) * 60000L;
        long remaining = Math.max(0L, limit - used);
        String counter = "Used: " + ScreenTimeTracker.formatDuration(used)
                + " · Remaining: " + ScreenTimeTracker.formatDuration(remaining);

        if (!Prefs.isEnabled(this)) {
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            update(counter);
            return;
        }

        if (!PolicyUtils.isDeviceOwner(this)) {
            update(counter + " · Device Owner not active");
            return;
        }

        PolicyUtils.applyPersistentPolicies(this);

        if (Prefs.isOverrideActive(this)) {
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            update(counter + " · Guardian override until midnight");
            return;
        }

        if (used >= limit) {
            // Refresh the Device Owner allowlist on every poll. This makes the restricted
            // state self-healing after an APK update and guarantees this guardian package,
            // the launcher/dialer and the guardian-selected apps remain authorized even if
            // Android retained a stale lock-task package list from an older build.
            try { PolicyUtils.prepareRestrictedLockTask(this); } catch (Exception ignored) {}

            update("Daily limit reached · blocked apps are unavailable");
            if (!PolicyUtils.isRestrictedModeActive(this)) {
                PolicyUtils.enterRestrictedMode(this);
            }
        } else {
            // Important for midnight: yesterday's restricted mode must not survive into a new day.
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            update(counter);
        }
    }

    private Notification buildNotification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Screen Time Guard")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    private void update(String text) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID, buildNotification(text));
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
