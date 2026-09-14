package com.example.screentimeguard;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class ScreenTimeWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_REFRESH =
            "com.example.screentimeguard.ACTION_REFRESH_WIDGET";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) updateAll(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, ScreenTimeWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        for (int id : ids) updateWidget(context, manager, id);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        long used = ScreenTimeTracker.getTodayInteractiveMillis(context);
        long limit = Prefs.getLimitMinutes(context) * 60_000L;
        long remaining = Math.max(0L, limit - used);
        int progress = limit <= 0L ? 0 : Math.min(1000, Math.round(used * 1000f / limit));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_screen_time);
        views.setTextViewText(R.id.widget_used, ScreenTimeTracker.formatDuration(used));
        views.setTextViewText(R.id.widget_remaining, ScreenTimeTracker.formatDuration(remaining));
        views.setTextViewText(R.id.widget_limit, ScreenTimeTracker.formatDuration(limit));
        views.setTextViewText(R.id.widget_percent, Math.round(progress / 10f) + "% used");
        views.setProgressBar(R.id.widget_progress, 1000, progress, false);

        boolean enabled = Prefs.isEnabled(context);
        views.setTextViewText(R.id.widget_status,
                enabled ? (remaining > 0L ? "Keep going!" : "Daily limit reached") : "Protection off");

        Intent refresh = new Intent(context, ScreenTimeWidgetProvider.class)
                .setAction(ACTION_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, 21, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // The widget is display-only: tapping anywhere simply refreshes its data.
        views.setOnClickPendingIntent(R.id.widget_root, refreshPi);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi);

        manager.updateAppWidget(appWidgetId, views);
    }
}
