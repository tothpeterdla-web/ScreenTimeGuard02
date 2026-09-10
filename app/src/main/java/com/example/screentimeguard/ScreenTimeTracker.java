package com.example.screentimeguard;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.time.ZonedDateTime;
import java.util.Map;

public final class ScreenTimeTracker {
    private ScreenTimeTracker() {}

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static long startOfTodayMillis() {
        ZonedDateTime now = ZonedDateTime.now();
        return now.toLocalDate()
                .atStartOfDay(now.getZone())
                .toInstant()
                .toEpochMilli();
    }

    public static long getTodayInteractiveMillis(Context context) {
        if (!hasUsageAccess(context)) return -1L;

        long now = System.currentTimeMillis();
        long start = startOfTodayMillis();

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return -1L;

        // Use Android's aggregated foreground-app usage instead of trying to reconstruct
        // screen-on time from SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE events.
        // Some devices (including the Zenfone 8) can omit/misorder those screen events,
        // which made the previous implementation count several hours while the screen was off.
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(start, now);
        if (stats == null) return -1L;

        long total = 0L;
        for (UsageStats s : stats.values()) {
            if (s == null) continue;
            long foreground = s.getTotalTimeInForeground();
            if (foreground > 0L) total += foreground;
        }

        // Aggregated per-app data can overlap slightly on some Android builds (for example
        // during activity transitions or split-screen). Never allow the result to exceed
        // the amount of real time that has elapsed since midnight.
        long elapsedToday = Math.max(0L, now - start);
        return Math.max(0L, Math.min(total, elapsedToday));
    }

    public static String formatDuration(long millis) {
        if (millis < 0L) return "unknown";
        long totalMinutes = millis / 60_000L;
        return (totalMinutes / 60L) + "h " + (totalMinutes % 60L) + "m";
    }
}
