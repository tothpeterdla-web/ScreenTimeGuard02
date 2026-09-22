package com.example.screentimeguard;

import android.accessibilityservice.AccessibilityServiceInfo;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.telecom.TelecomManager;
import android.view.accessibility.AccessibilityManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PolicyUtils {
    private PolicyUtils() {}

    public static final String ADGUARD_PACKAGE = "com.adguard.android";
    public static final String MESSENGER_PACKAGE = "com.facebook.orca";

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

    public static boolean isMessengerLinkGuardEnabled(Context c) {
        try {
            AccessibilityManager manager =
                    (AccessibilityManager)c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager == null) return false;

            List<AccessibilityServiceInfo> enabled =
                    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

            ComponentName expected = new ComponentName(c, MessengerLinkGuardService.class);
            for (AccessibilityServiceInfo info : enabled) {
                if (info == null || info.getResolveInfo() == null
                        || info.getResolveInfo().serviceInfo == null) continue;

                ComponentName actual = new ComponentName(
                        info.getResolveInfo().serviceInfo.packageName,
                        info.getResolveInfo().serviceInfo.name);
                if (expected.equals(actual)) return true;
            }
        } catch (Exception ignored) {}
        return false;
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

    public static boolean isFactoryResetBlocked(Context c) {
        if (!isDeviceOwner(c)) return false;
        try {
            Bundle restrictions = dpm(c).getUserRestrictions(admin(c));
            return restrictions != null
                    && restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAppInstallBlocked(Context c) {
        if (!isDeviceOwner(c)) return false;
        try {
            Bundle restrictions = dpm(c).getUserRestrictions(admin(c));
            return restrictions != null
                    && restrictions.getBoolean(UserManager.DISALLOW_INSTALL_APPS, false);
        } catch (Exception e) {
            return false;
        }
    }

    // App installation is a master guardian-controlled gate independent of screen time.
    // When disabled, installs are blocked from every source, including Play Store and APK files.
    // When enabled, both store installs and unknown-source installs are permitted.
    public static void applyInstallProtection(Context c) {
        if (!isDeviceOwner(c)) return;
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        try {
            if (Prefs.isInstallWindowActive(c)) {
                d.clearUserRestriction(a, UserManager.DISALLOW_INSTALL_APPS);
                d.clearUserRestriction(a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    d.clearUserRestriction(a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);
                }
            } else {
                Prefs.clearInstallWindow(c);
                d.addUserRestriction(a, UserManager.DISALLOW_INSTALL_APPS);
                d.addUserRestriction(a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    d.addUserRestriction(a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);
                }
            }
        } catch (Exception ignored) {}
    }

    // Adult-content protection, app-install protection and anti-reset policy are independent of
    // screen time. They stay active whenever this package remains Device Owner.
    public static void applyAdGuardProtection(Context c) {
        if (!isDeviceOwner(c)) return;
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);

        try { d.clearUserRestriction(a, UserManager.DISALLOW_APPS_CONTROL); } catch (Exception ignored) {}

        try { d.setUninstallBlocked(a, c.getPackageName(), true); } catch (Exception ignored) {}
        if (isAdGuardInstalled(c)) {
            try { d.setUninstallBlocked(a, ADGUARD_PACKAGE, true); } catch (Exception ignored) {}

            // Keep AdGuard selected as the system always-on VPN, but deliberately do NOT enable
            // VPN lockdown. This avoids cutting off all internet if AdGuard fails to start.
            // Only lock VPN settings after Android confirms the always-on assignment succeeded.
            boolean alwaysOnApplied = false;
            try {
                d.setAlwaysOnVpnPackage(a, ADGUARD_PACKAGE, false);
                alwaysOnApplied = ADGUARD_PACKAGE.equals(d.getAlwaysOnVpnPackage(a));
            } catch (Exception ignored) {}

            if (alwaysOnApplied) {
                try { d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_VPN); } catch (Exception ignored) {}
            }
        }
        try { d.addUserRestriction(a, UserManager.DISALLOW_FACTORY_RESET); } catch (Exception ignored) {}
        applyInstallProtection(c);
    }

    public static void applyPersistentPolicies(Context c) {
        if (!isDeviceOwner(c)) return;
        applyAdGuardProtection(c);
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        try { d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME); } catch (Exception ignored) {}
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
            if (ALWAYS_BLOCKED.contains(pkg) || pm.getLaunchIntentForPackage(pkg) == null) continue;

            // Messenger's in-app browser is hosted inside com.facebook.orca, so package-level
            // browser blocking cannot distinguish it from chat. Fail closed: if our dedicated
            // Accessibility guard is off, Messenger is not permitted in restricted mode.
            if (MESSENGER_PACKAGE.equals(pkg) && !isMessengerLinkGuardEnabled(c)) continue;

            result.add(pkg);
        }
        return result;
    }

    public static void prepareRestrictedLockTask(Context c) {
        if (!isDeviceOwner(c)) return;

        PackageManager pm = c.getPackageManager();
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.add(c.getPackageName());

        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.MATCH_ALL);
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

        if (!isGuardianLockTaskPermitted(c)) {
            clearRestrictedMode(c);
            return;
        }

        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLockTaskEnabled(true);
                c.startActivity(i, options.toBundle());
            } else {
                c.startActivity(i);
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { openHome(c); } catch (Exception ignored) {}
            }, 350L);
        } catch (Exception e) {
            clearRestrictedMode(c);
        }
    }

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
            Prefs.clearInstallWindow(c);
            try { d.clearUserRestriction(a, UserManager.DISALLOW_FACTORY_RESET); } catch (Exception ignored) {}
            try { d.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_VPN); } catch (Exception ignored) {}
            try { d.setAlwaysOnVpnPackage(a, null, false); } catch (Exception ignored) {}
            try { d.clearUserRestriction(a, UserManager.DISALLOW_INSTALL_APPS); } catch (Exception ignored) {}
            try { d.clearUserRestriction(a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES); } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { d.clearUserRestriction(a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY); } catch (Exception ignored) {}
            }
            try { d.setUninstallBlocked(a, ADGUARD_PACKAGE, false); } catch (Exception ignored) {}
            try { d.setUninstallBlocked(a, c.getPackageName(), false); } catch (Exception ignored) {}
            d.clearDeviceOwnerApp(c.getPackageName());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
