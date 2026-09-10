package com.example.screentimeguard;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

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

    private static String activityKey(UsageEvents.Event event) {
        String pkg = event.getPackageName();
        String cls = event.getClassName();
        if (pkg == null) pkg = "";
        if (cls == null) cls = "";
        return pkg + "\n" + cls;
    }

    @SuppressWarnings("deprecation")
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

        // Track the UNION of foreground/resumed activities instead of summing each app's
        // UsageStats.getTotalTimeInForeground(). Android can report overlapping foreground
        // time for the launcher, System UI, overlays and the actual app; summing those values
        // therefore double-counted time on the Zenfone 8.
        //
        // Using activity transitions also avoids depending solely on SCREEN_INTERACTIVE /
        // SCREEN_NON_INTERACTIVE, whose stream was incomplete on this device. A screen-off
        // event is still used when present as an extra safety boundary.
        Set<String> activeActivities = new HashSet<>();
        long total = 0L;
        long cursor = start;

        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            long ts = event.getTimeStamp();
            int type = event.getEventType();

            boolean resumed = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
            boolean paused = type == UsageEvents.Event.MOVE_TO_BACKGROUND;
            boolean screenOff = type == UsageEvents.Event.SCREEN_NON_INTERACTIVE;

            if (ts < start) {
                if (resumed) activeActivities.add(activityKey(event));
                else if (paused) activeActivities.remove(activityKey(event));
                else if (screenOff) activeActivities.clear();
                continue;
            }

            if (ts > cursor && !activeActivities.isEmpty()) {
                total += ts - cursor;
            }
            if (ts > cursor) cursor = ts;

            if (resumed) {
                activeActivities.add(activityKey(event));
            } else if (paused) {
                activeActivities.remove(activityKey(event));
            } else if (screenOff) {
                activeActivities.clear();
            }
        }

        if (now > cursor && !activeActivities.isEmpty()) {
            total += now - cursor;
        }

        long elapsedToday = Math.max(0L, now - start);
        return Math.max(0L, Math.min(total, elapsedToday));
    }

    public static String formatDuration(long millis) {
        if (millis < 0L) return "unknown";
        long totalMinutes = millis / 60_000L;
        return (totalMinutes / 60L) + "h " + (totalMinutes % 60L) + "m";
    }
}
