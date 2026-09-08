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
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Screen Time Guard",NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = buildNotification("Starting…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID,n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        } else startForeground(ID,n);
        handler.post(poll);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    private void tick() {
        if (!Prefs.isEnabled(this)) { stopSelf(); return; }
        if (!PolicyUtils.isDeviceOwner(this)) { update("Device Owner not active"); return; }
        PolicyUtils.applyPersistentPolicies(this);
        if (!ScreenTimeTracker.hasUsageAccess(this)) { update("Usage Access missing"); return; }
        if (Prefs.isOverrideActive(this)) { update("Guardian override active until midnight"); return; }
        long used = ScreenTimeTracker.getTodayInteractiveMillis(this);
        long limit = Prefs.getLimitMinutes(this) * 60000L;
        if (used >= limit) {
            update("Daily limit reached");
            PolicyUtils.launchLockActivity(this);
        } else update("Remaining: " + ScreenTimeTracker.formatDuration(limit-used));
    }

    private Notification buildNotification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Screen Time Guard")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void update(String text) {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null) nm.notify(ID,buildNotification(text));
    }

    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
