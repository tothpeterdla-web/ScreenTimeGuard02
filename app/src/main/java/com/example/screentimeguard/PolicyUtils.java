package com.example.screentimeguard;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserManager;
import android.telecom.TelecomManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PolicyUtils {
    private PolicyUtils() {}

    public static final String ADGUARD_PACKAGE = "com.adguard.android";

    private static final Set<String> ALWAYS_BLOCKED = new HashSet<>(Arrays.asList(
            "com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox",
            "org.mozilla.fenix", "com.microsoft.emmx", "com.opera.browser", "com.brave.browser",
            "com.vivaldi.browser", "com.duckduckgo.mobile.android", "com.android.vending",
            "com.sec.android.app.samsungapps", "com.huawei.appmarket", "com.xiaomi.mipicks",
            "com.amazon.venezia", "com.oppo.market", "com.heytap.market", "com.bbk.appstore",
            "com.vivo.appstore"));

    private static final Set<String> BUILT_IN_ALLOWED_AFTER_LIMIT = new HashSet<>(Arrays.asList(
            "com.google.android.apps.maps",
            "hu.webvalto.bkkfutar"
    ));

    public static ComponentName admin(Context c) {
        return new ComponentName(c, GuardDeviceAdminReceiver.class);
    }

    public static DevicePolicyManager dpm(Context c) {
        return (DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isDeviceOwner(Context c) {
        DevicePolicyManager d = dpm(c);
        return d != null && d.isDeviceOwnerApp(c.getPackageName());
    }

    public static boolean isGuardianLockTaskPermitted(Context c) {
        if (!isDeviceOwner(c)) return false;
        try {
            DevicePolicyManager d = dpm(c);
            return d != null && d.isLockTaskPermitted(c.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAlwaysBlockedPackage(String packageName) {
        return ALWAYS_BLOCKED.contains(packageName);
    }

    public static boolean isBuiltInAllowedPackage(String packageName) {
        return BUILT_IN_ALLOWED_AFTER_LIMIT.contains(packageName);
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

    // Adult-content protection is independent of the screen-time feature.
    // AdGuard and this guardian app stay uninstall-blocked whenever Device Owner is active.
    public static void applyAdGuardProtection(Context c) {
        if (!isDeviceOwner(c)) return;
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        try { d.setUninstallBlocked(a, c.getPackageName(), true); } catch (Exception ignored) {}
        if (isAdGuardInstalled(c)) {
            try { d.setUninstallBlocked(a, ADGUARD_PACKAGE, true); } catch (Exception ignored) {}
        }
    }

    public static void applyPersistentPolicies(Context c) {
        if (!isDeviceOwner(c)) return;
        applyAdGuardProtection(c);
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        try { d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME); } catch (Exception ignored) {}
        try { d.addUserRestriction(a, UserManager.DISALLOW_APPS_CONTROL); } catch (Exception ignored) {}
    }

    public static void removeScreenTimePolicies(Context c) {
        if (!isDeviceOwner(c)) return;
        clearRestrictedMode(c);
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        try { d.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME); } catch (Exception ignored) {}
        try { d.clearUserRestriction(a, UserManager.DISALLOW_APPS_CONTROL); } catch (Exception ignored) {}
        applyAdGuardProtection(c);
    }

    private static boolean system(ApplicationInfo i) {
        int flags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return i != null && (i.flags & flags) != 0;
    }

    private static String homePackage(Context c) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null ? ri.activityInfo.packageName : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String dialerPackage(Context c) {
        try {
            TelecomManager tm = (TelecomManager)c.getSystemService(Context.TELECOM_SERVICE);
            return tm == null ? null : tm.getDefaultDialerPackage();
        } catch (Exception ignored) {
            return null;
        }
    }

    // Visible apps that are always usable and therefore should not appear as guardian choices.
    public static boolean isAutomaticallyAllowedVisiblePackage(Context c, String packageName) {
        if (packageName == null) return false;
        if (packageName.equals(c.getPackageName())) return true;
        if (BUILT_IN_ALLOWED_AFTER_LIMIT.contains(packageName)) return true;
        String home = homePackage(c);
        if (packageName.equals(home)) return true;
        String dialer = dialerPackage(c);
        return packageName.equals(dialer);
    }

    public static Set<String> getUserAllowedPackages(Context c) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        PackageManager pm = c.getPackageManager();
        for (String pkg : BUILT_IN_ALLOWED_AFTER_LIMIT) {
            if (!ALWAYS_BLOCKED.contains(pkg) && pm.getLaunchIntentForPackage(pkg) != null) result.add(pkg);
        }
        for (String pkg : Prefs.getExtraAllowedPackages(c)) {
            if (!ALWAYS_BLOCKED.contains(pkg) && pm.getLaunchIntentForPackage(pkg) != null) result.add(pkg);
        }
        return result;
    }

    public static void prepareRestrictedLockTask(Context c) {
        if (!isDeviceOwner(c)) return;

        PackageManager pm = c.getPackageManager();
        LinkedHashSet<String> allowed = new LinkedHashSet<>();

        // SAFETY INVARIANT: the guardian package is always first in the allowlist.
        // If Android ever refuses to confirm that this package is permitted, restricted mode
        // will not be entered (or will be cleared by the monitor service).
        allowed.add(c.getPackageName());

        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.MATCH_ALL);

        // Keep non-launchable Android/OEM infrastructure available so core phone behavior is
        // not broken. Launchable preinstalled apps must still be selected by the guardian.
        for (ApplicationInfo app : apps) {
            if (system(app)
                    && !ALWAYS_BLOCKED.contains(app.packageName)
                    && pm.getLaunchIntentForPackage(app.packageName) == null) {
                allowed.add(app.packageName);
            }
        }

        allowed.addAll(getUserAllowedPackages(c));

        String home = homePackage(c);
        if (home != null) allowed.add(home);

        String dialer = dialerPackage(c);
        if (dialer != null) allowed.add(dialer);

        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        d.setLockTaskPackages(a, allowed.toArray(new String[0]));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int features = DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                    | DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
                    | DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                    | DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
                    | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                    | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                features |= DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK;
            }
            d.setLockTaskFeatures(a, features);
        }
    }

    public static boolean isRestrictedModeActive(Context c) {
        try {
            ActivityManager am = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            return am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
        } catch (Exception e) {
            return false;
        }
    }

    public static void enterRestrictedMode(Context c) {
        if (!isDeviceOwner(c) || isRestrictedModeActive(c)) return;

        try {
            prepareRestrictedLockTask(c);
        } catch (Exception ignored) {
            return;
        }

        // HARD FAIL-SAFE: never start restricted mode unless Android confirms that
        // Screen Time Guard itself is allowed. This prevents a repeat of the self-lockout.
        if (!isGuardianLockTaskPermitted(c)) {
            clearRestrictedMode(c);
            return;
        }

        Intent i = new Intent(c, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLockTaskEnabled(true);
                c.startActivity(i, options.toBundle());
            } else {
                c.startActivity(i);
            }
        } catch (Exception e) {
            try { c.startActivity(i); } catch (Exception ignored) {}
        }
    }

    // Remove only the temporary restricted mode. AdGuard uninstall protection stays active.
    public static void clearRestrictedMode(Context c) {
        if (!isDeviceOwner(c)) return;
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        try { d.setLockTaskPackages(a, new String[0]); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_NONE); } catch (Exception ignored) {}
        }
    }

    public static void guardianExitRestrictedMode(Activity activity) {
        try { activity.stopLockTask(); } catch (Exception ignored) {}
        clearRestrictedMode(activity);
    }

    public static void openHome(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(home);
    }

    @SuppressWarnings("deprecation")
    public static boolean releaseDeviceOwnerForUninstall(Context c) {
        if (!isDeviceOwner(c)) return true;
        try {
            DevicePolicyManager d = dpm(c);
            ComponentName a = admin(c);
            removeScreenTimePolicies(c);
            try { d.setUninstallBlocked(a, ADGUARD_PACKAGE, false); } catch (Exception ignored) {}
            try { d.setUninstallBlocked(a, c.getPackageName(), false); } catch (Exception ignored) {}
            d.clearDeviceOwnerApp(c.getPackageName());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
