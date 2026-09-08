package com.example.screentimeguard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.time.ZonedDateTime;

public final class ScreenTimeTracker {
    private static final long LOOKBACK_MS = 24L * 60L * 60L * 1000L;

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
        long queryStart = Math.max(0L, start - LOOKBACK_MS);

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return -1L;

        UsageEvents events = usm.queryEvents(queryStart, now);
        if (events == null) return -1L;

        boolean interactive = false;
        long activeStart = -1L;
        long total = 0L;

        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            long ts = event.getTimeStamp();

            if (type != UsageEvents.Event.SCREEN_INTERACTIVE
                    && type != UsageEvents.Event.SCREEN_NON_INTERACTIVE) continue;

            if (ts < start) {
                interactive = type == UsageEvents.Event.SCREEN_INTERACTIVE;
                continue;
            }

            if (type == UsageEvents.Event.SCREEN_INTERACTIVE) {
                if (!interactive) {
                    interactive = true;
                    activeStart = Math.max(start, ts);
                } else if (activeStart < 0L) {
                    activeStart = start;
                }
            } else {
                if (interactive) {
                    long from = activeStart >= 0L ? activeStart : start;
                    if (ts > from) total += ts - from;
                }
                interactive = false;
                activeStart = -1L;
            }
        }

        if (interactive) {
            long from = activeStart >= 0L ? activeStart : start;
            if (now > from) total += now - from;
        }

        return Math.max(0L, total);
    }

    public static String formatDuration(long millis) {
        if (millis < 0L) return "unknown";
        long totalMinutes = millis / 60_000L;
        return (totalMinutes / 60L) + "h " + (totalMinutes % 60L) + "m";
    }
}
