package com.example.screentimeguard;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

import java.time.LocalDate;

public final class ScreenTimeTracker {
    private static final String PREFS = "local_screen_time_tracker";
    private static final String KEY_DATE = "date";
    private static final String KEY_TOTAL = "total_ms";
    private static final String KEY_LAST_SAMPLE = "last_sample_ms";
    private static final long MAX_SAMPLE_GAP_MS = 60_000L;

    private ScreenTimeTracker() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String todayKey() {
        return LocalDate.now().toString();
    }

    private static boolean isActivelyUsed(Context c) {
        PowerManager pm = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean interactive = pm != null && pm.isInteractive();
        boolean locked = km != null && km.isKeyguardLocked();
        return interactive && !locked;
    }

    public static synchronized long sample(Context c) {
        SharedPreferences p = prefs(c);
        String today = todayKey();
        String storedDate = p.getString(KEY_DATE, "");
        long now = System.currentTimeMillis();

        if (!today.equals(storedDate)) {
            p.edit()
                    .putString(KEY_DATE, today)
                    .putLong(KEY_TOTAL, 0L)
                    .putLong(KEY_LAST_SAMPLE, now)
                    .apply();
            return 0L;
        }

        long total = Math.max(0L, p.getLong(KEY_TOTAL, 0L));
        long last = p.getLong(KEY_LAST_SAMPLE, 0L);
        long delta = last > 0L ? now - last : 0L;

        // Only count short, continuously observed intervals while the phone is both
        // interactive and unlocked. A long gap (service stopped/rebooted) is never guessed.
        if (delta > 0L && delta <= MAX_SAMPLE_GAP_MS && isActivelyUsed(c)) {
            total += delta;
        }

        p.edit()
                .putString(KEY_DATE, today)
                .putLong(KEY_TOTAL, total)
                .putLong(KEY_LAST_SAMPLE, now)
                .apply();
        return total;
    }

    public static long getTodayInteractiveMillis(Context c) {
        return sample(c);
    }

    public static synchronized void resetToday(Context c) {
        long now = System.currentTimeMillis();
        prefs(c).edit()
                .putString(KEY_DATE, todayKey())
                .putLong(KEY_TOTAL, 0L)
                .putLong(KEY_LAST_SAMPLE, now)
                .apply();
    }

    public static String formatDuration(long millis) {
        if (millis < 0L) return "unknown";
        long totalMinutes = millis / 60_000L;
        return (totalMinutes / 60L) + "h " + (totalMinutes % 60L) + "m";
    }
}
