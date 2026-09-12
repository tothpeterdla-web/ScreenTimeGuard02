package com.example.screentimeguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PolicyUtils.applyAdGuardProtection(context);

        // If strong protection is enabled, the service must start even when Usage Access was
        // revoked so it can fail closed instead of treating the missing access as a bypass.
        if (!Prefs.isEnabled(context) && !ScreenTimeTracker.hasUsageAccess(context)) return;

        Intent service = new Intent(context, ScreenTimeService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception ignored) {}
    }
}
