package com.example.screentimeguard;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserManager;

public final class PolicyUtils {
    private PolicyUtils() {}

    public static final String ADGUARD_PACKAGE = "com.adguard.android";

    public static ComponentName admin(Context c) {
        return new ComponentName(c, GuardDeviceAdminReceiver.class);
    }

    public static DevicePolicyManager dpm(Context c) {
        return (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isDeviceOwner(Context c) {
        DevicePolicyManager d = dpm(c);
        return d != null && d.isDeviceOwnerApp(c.getPackageName());
    }

    public static boolean isAdGuardInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(ADGUARD_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isAdGuardUninstallBlocked(Context c) {
        if (!isDeviceOwner(c)) return false;
        try {
            return dpm(c).isUninstallBlocked(admin(c), ADGUARD_PACKAGE);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The app is now intentionally minimal: its only ongoing Device Owner job is
     * preventing AdGuard (and itself) from being uninstalled. Any old screen-time,
     * lock-task, date/time or global app-control restrictions from previous builds
     * are explicitly cleared here during migration.
     */
    public static void applyAdGuardProtection(Context c) {
        if (!isDeviceOwner(c)) return;

        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);

        // Clear policies that belonged to the old screen-time implementation.
        try { d.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME); } catch (Exception ignored) {}
        try { d.clearUserRestriction(a, UserManager.DISALLOW_APPS_CONTROL); } catch (Exception ignored) {}
        try { d.setLockTaskPackages(a, new String[0]); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_NONE); } catch (Exception ignored) {}
        }

        // Keep this Device Owner app present so the AdGuard uninstall block cannot
        // simply be removed by uninstalling the guardian app first.
        try { d.setUninstallBlocked(a, c.getPackageName(), true); } catch (Exception ignored) {}

        // The only protected third-party package.
        if (isAdGuardInstalled(c)) {
            try { d.setUninstallBlocked(a, ADGUARD_PACKAGE, true); } catch (Exception ignored) {}
        }
    }

    private static void removeAdGuardProtection(Context c) {
        if (!isDeviceOwner(c)) return;
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        try { d.setUninstallBlocked(a, ADGUARD_PACKAGE, false); } catch (Exception ignored) {}
        try { d.setUninstallBlocked(a, c.getPackageName(), false); } catch (Exception ignored) {}
        try { d.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME); } catch (Exception ignored) {}
        try { d.clearUserRestriction(a, UserManager.DISALLOW_APPS_CONTROL); } catch (Exception ignored) {}
        try { d.setLockTaskPackages(a, new String[0]); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_NONE); } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean releaseDeviceOwnerForUninstall(Context c) {
        if (!isDeviceOwner(c)) return true;
        try {
            removeAdGuardProtection(c);
            dpm(c).clearDeviceOwnerApp(c.getPackageName());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
