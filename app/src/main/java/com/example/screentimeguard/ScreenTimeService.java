package com.example.screentimeguard;

import android.app.ActivityManager;
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
                        CHANNEL,
                        "Screen Time Guard",
                        NotificationManager.IMPORTANCE_LOW
                );
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

        // Monitor-only mode: show the live counter but do not enforce anything.
        if (!Prefs.isEnabled(this)) {
            update(counter);
            return;
        }

        if (!PolicyUtils.isDeviceOwner(this)) {
            update(counter + " · Device Owner not active");
            return;
        }

        PolicyUtils.applyPersistentPolicies(this);

        if (Prefs.isOverrideActive(this)) {
            update(counter + " · Guardian override until midnight");
            return;
        }

        if (used >= limit) {
            update("Daily limit reached · Used: " + ScreenTimeTracker.formatDuration(used));

            // Once restricted Lock Task mode is active, allowed apps are supposed to be
            // usable inside that mode. Do NOT push LockActivity to the foreground every
            // 15 seconds, otherwise a banking/Maps/BudapestGO app gets immediately covered
            // by the limit screen again. If Lock Task mode ever ends unexpectedly, the next
            // poll will see NONE and restore the restriction screen.
            if (!isRestrictedLockTaskActive()) {
                PolicyUtils.launchLockActivity(this);
            }
        } else {
            update(counter);
        }
    }

    private boolean isRestrictedLockTaskActive() {
        try {
            ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            return am != null
                    && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Notification buildNotification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

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
