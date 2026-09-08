package com.example.screentimeguard;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class GuardDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        if (PolicyUtils.isDeviceOwner(context)) PolicyUtils.applyPersistentPolicies(context);
    }
}
