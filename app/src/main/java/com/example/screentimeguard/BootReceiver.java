package com.example.screentimeguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.isEnabled(context)) return;
        Intent s = new Intent(context, ScreenTimeService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(s);
            else context.startService(s);
        } catch (Exception ignored) {}
    }
}
