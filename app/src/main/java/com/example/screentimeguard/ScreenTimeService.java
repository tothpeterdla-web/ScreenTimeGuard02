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

        boolean owner = PolicyUtils.isDeviceOwner(this);
        boolean enabled = Prefs.isEnabled(this);
        boolean usageAccess = ScreenTimeTracker.hasUsageAccess(this);
        boolean override = Prefs.isOverrideActive(this);

        // Recovery invariant: if restricted mode is active, Screen Time Guard itself must
        // remain allowlisted. An active guardian override or disabled protection still exits.
        if (PolicyUtils.isRestrictedModeActive(this)) {
            if (!owner || !enabled || override) {
                PolicyUtils.clearRestrictedMode(this);
            } else {
                try { PolicyUtils.prepareRestrictedLockTask(this); } catch (Exception ignored) {}
                if (!PolicyUtils.isGuardianLockTaskPermitted(this)) {
                    PolicyUtils.clearRestrictedMode(this);
                }
            }
        }

        if (!enabled) {
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            if (!usageAccess) {
                update("Usage Access missing · screen-time protection is off");
                return;
            }
            long used = ScreenTimeTracker.getTodayInteractiveMillis(this);
            if (used < 0L) {
                update("Unable to read today's screen time");
                return;
            }
            long limit = Prefs.getLimitMinutes(this) * 60000L;
            long remaining = Math.max(0L, limit - used);
            update("Used: " + ScreenTimeTracker.formatDuration(used)
                    + " · Remaining: " + ScreenTimeTracker.formatDuration(remaining));
            return;
        }

        if (!owner) {
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            update("Protection enabled · Device Owner not active");
            return;
        }

        PolicyUtils.applyPersistentPolicies(this);

        if (override) {
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            update("Guardian override until midnight");
            return;
        }

        // ANTI-BYPASS: Usage Access cannot be technically locked by DevicePolicyManager.
        // Instead, strong protection fails closed. If the user revokes Usage Access while
        // protection is enabled, restricted mode starts immediately and remains until the
        // guardian restores access or authorizes an override.
        if (!usageAccess) {
            try { PolicyUtils.prepareRestrictedLockTask(this); } catch (Exception ignored) {}
            if (PolicyUtils.isGuardianLockTaskPermitted(this)
                    && !PolicyUtils.isRestrictedModeActive(this)) {
                PolicyUtils.enterRestrictedMode(this);
            }
            update("Usage Access removed · guardian action required");
            return;
        }

        long used = ScreenTimeTracker.getTodayInteractiveMillis(this);
        if (used < 0L) {
            try { PolicyUtils.prepareRestrictedLockTask(this); } catch (Exception ignored) {}
            if (PolicyUtils.isGuardianLockTaskPermitted(this)
                    && !PolicyUtils.isRestrictedModeActive(this)) {
                PolicyUtils.enterRestrictedMode(this);
            }
            update("Usage data unavailable · guardian action required");
            return;
        }

        long limit = Prefs.getLimitMinutes(this) * 60000L;
        long remaining = Math.max(0L, limit - used);
        String counter = "Used: " + ScreenTimeTracker.formatDuration(used)
                + " · Remaining: " + ScreenTimeTracker.formatDuration(remaining);

        if (used >= limit) {
            try { PolicyUtils.prepareRestrictedLockTask(this); } catch (Exception ignored) {}

            if (!PolicyUtils.isGuardianLockTaskPermitted(this)) {
                if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
                update("Safety fallback · Screen Time Guard must remain reachable");
                return;
            }

            update("Daily limit reached · blocked apps are unavailable");
            if (!PolicyUtils.isRestrictedModeActive(this)) {
                PolicyUtils.enterRestrictedMode(this);
            }
        } else {
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
