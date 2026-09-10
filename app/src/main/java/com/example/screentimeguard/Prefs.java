package com.example.screentimeguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.ZonedDateTime;

public final class Prefs {
    private static final String NAME = "screen_time_guard";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LIMIT_MINUTES = "limit_minutes";
    private static final String KEY_OVERRIDE_UNTIL = "override_until";

    private Prefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int getLimitMinutes(Context context) {
        return prefs(context).getInt(KEY_LIMIT_MINUTES, 120);
    }

    public static void setLimitMinutes(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_LIMIT_MINUTES, minutes).apply();
    }

    public static long getOverrideUntil(Context context) {
        return prefs(context).getLong(KEY_OVERRIDE_UNTIL, 0L);
    }

    public static boolean isOverrideActive(Context context) {
        return System.currentTimeMillis() < getOverrideUntil(context);
    }

    public static void clearOverride(Context context) {
        prefs(context).edit().remove(KEY_OVERRIDE_UNTIL).apply();
    }

    public static void unlockUntilMidnight(Context context) {
        ZonedDateTime nextMidnight = ZonedDateTime.now()
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZonedDateTime.now().getZone());
        prefs(context).edit()
                .putLong(KEY_OVERRIDE_UNTIL, nextMidnight.toInstant().toEpochMilli())
                .apply();
    }
}
