package com.example.screentimeguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenTimeService extends Service {
    private static final String CHANNEL = "screen_time_guard";
    private static final int ID = 1001;
    private static final long POLL_MS = 5_000L;
    private static final long WIDGET_UPDATE_MS = 30_000L;
    private long lastWidgetUpdate = 0L;
    private long lastQuarantineAuditMs = -1L;
    private final ExecutorService quarantineWorker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean quarantineJobRunning = new AtomicBoolean(false);
    private final BroadcastReceiver packageChangeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
                enqueueQuarantineAudit();
            }
        }
    };

    private void enqueueQuarantineAudit() {
        if (!Prefs.isUpdateOnlyModeEnabled(this)) return;
        if (!quarantineJobRunning.compareAndSet(false, true)) return;
        quarantineWorker.execute(() -> {
            try { PolicyUtils.quarantineUnapprovedApps(getApplicationContext()); }
            finally { quarantineJobRunning.set(false); }
        });
    }
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, "Screen Time Guard", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Shows today's locally measured unlocked screen time.");
                nm.createNotificationChannel(channel);
            }
        }

        Notification n = buildNotification("Starting screen-time monitor…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID, n);
        }
        IntentFilter appChanges = new IntentFilter();
        appChanges.addAction(Intent.ACTION_PACKAGE_ADDED);
        appChanges.addAction(Intent.ACTION_PACKAGE_REPLACED);
        appChanges.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, appChanges, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageChangeReceiver, appChanges);
        }
        enqueueQuarantineAudit();
        handler.post(poll);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void tick() {
        PolicyUtils.applyAdGuardProtection(this);
        long currentElapsed = SystemClock.elapsedRealtime();
        if (lastQuarantineAuditMs < 0L || currentElapsed - lastQuarantineAuditMs >= 10_000L) {
            lastQuarantineAuditMs = currentElapsed;
            enqueueQuarantineAudit();
        }

        boolean owner = PolicyUtils.isDeviceOwner(this);
        boolean enabled = Prefs.isEnabled(this);
        if (owner) RemoteUnlockClient.poll(this);

        boolean override = Prefs.isOverrideActive(this);
        long used = ScreenTimeTracker.sample(this);
        long limit = Prefs.getLimitMinutes(this) * 60_000L;
        long remaining = Math.max(0L, limit - used);
        String counter = "Used: " + ScreenTimeTracker.formatDuration(used)
                + " · Remaining: " + ScreenTimeTracker.formatDuration(remaining);

        long now = System.currentTimeMillis();
        if (now - lastWidgetUpdate >= WIDGET_UPDATE_MS) {
            ScreenTimeWidgetProvider.updateAll(this);
            lastWidgetUpdate = now;
        }

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
            update(counter + " · protection off");
            return;
        }

        if (!owner) {
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            update(counter + " · Device Owner not active");
            return;
        }

        PolicyUtils.applyPersistentPolicies(this);

        if (override) {
            if (PolicyUtils.isRestrictedModeActive(this)) PolicyUtils.clearRestrictedMode(this);
            update(counter + " · guardian override active");
            return;
        }

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

        Intent installIntent = new Intent(this, InstallControlActivity.class);
        PendingIntent installPi = PendingIntent.getActivity(this, 1, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String installAction = Prefs.isInstallWindowActive(this)
                ? "App installs: enabled"
                : "App installations";

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Screen Time Guard")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_manage, installAction, installPi).build())
                .build();
    }

    private void update(String text) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID, buildNotification(text));
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(packageChangeReceiver); } catch (Exception ignored) {}
        quarantineWorker.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
