package com.example.screentimeguard;

import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
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

    private static final String ADGUARD_PACKAGE = "com.adguard.android";

    private static final Set<String> BLOCKED = new HashSet<>(Arrays.asList(
            "com.android.chrome","com.sec.android.app.sbrowser","org.mozilla.firefox",
            "org.mozilla.fenix","com.microsoft.emmx","com.opera.browser","com.brave.browser",
            "com.vivaldi.browser","com.duckduckgo.mobile.android","com.android.vending",
            "com.sec.android.app.samsungapps","com.huawei.appmarket","com.xiaomi.mipicks",
            "com.amazon.venezia","com.oppo.market","com.heytap.market","com.bbk.appstore",
            "com.vivo.appstore"));

    // Third-party apps that remain usable after the daily limit is reached.
    private static final Set<String> ALLOWED_AFTER_LIMIT = new HashSet<>(Arrays.asList(
            "com.google.android.apps.maps",   // Google Maps
            "hu.webvalto.bkkfutar"           // BudapestGO
    ));

    public static ComponentName admin(Context c) { return new ComponentName(c, GuardDeviceAdminReceiver.class); }
    public static DevicePolicyManager dpm(Context c) { return (DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE); }
    public static boolean isDeviceOwner(Context c) { DevicePolicyManager d=dpm(c); return d!=null && d.isDeviceOwnerApp(c.getPackageName()); }

    public static void applyPersistentPolicies(Context c) {
        if (!isDeviceOwner(c)) return;
        DevicePolicyManager d=dpm(c); ComponentName a=admin(c);
        d.setUninstallBlocked(a,c.getPackageName(),true);

        // AdGuard provides the always-on Family Protection DNS filtering on this device.
        // When strong protection is enabled, prevent it from being uninstalled through
        // normal Android package-management paths. DISALLOW_APPS_CONTROL below also blocks
        // force-stop, clear-data and disable actions in Settings.
        try { d.setUninstallBlocked(a,ADGUARD_PACKAGE,true); } catch(Exception ignored) {}

        try { d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_DATE_TIME); } catch(Exception ignored) {}
        try { d.addUserRestriction(a, UserManager.DISALLOW_APPS_CONTROL); } catch(Exception ignored) {}
    }

    public static void removePersistentPolicies(Context c) {
        if (!isDeviceOwner(c)) return;
        DevicePolicyManager d=dpm(c); ComponentName a=admin(c);
        try { d.setUninstallBlocked(a,c.getPackageName(),false); } catch(Exception ignored) {}
        try { d.setUninstallBlocked(a,ADGUARD_PACKAGE,false); } catch(Exception ignored) {}
        try { d.clearUserRestriction(a,UserManager.DISALLOW_CONFIG_DATE_TIME); } catch(Exception ignored) {}
        try { d.clearUserRestriction(a,UserManager.DISALLOW_APPS_CONTROL); } catch(Exception ignored) {}
        try { d.setLockTaskPackages(a,new String[0]); } catch(Exception ignored) {}
    }

    private static boolean system(ApplicationInfo i) {
        int f=ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return i!=null && (i.flags&f)!=0;
    }

    public static void prepareRestrictedLockTask(Context c) {
        if (!isDeviceOwner(c)) return;
        LinkedHashSet<String> allowed=new LinkedHashSet<>();
        allowed.add(c.getPackageName());
        PackageManager pm=c.getPackageManager();
        List<ApplicationInfo> apps=pm.getInstalledApplications(PackageManager.MATCH_ALL);

        // Keep Android system apps usable (except browsers/app stores), plus the explicit
        // travel/navigation exceptions requested for restricted mode.
        for(ApplicationInfo i:apps) {
            if(system(i) && !BLOCKED.contains(i.packageName)) allowed.add(i.packageName);
            if(ALLOWED_AFTER_LIMIT.contains(i.packageName)) allowed.add(i.packageName);
        }

        TelecomManager tm=(TelecomManager)c.getSystemService(Context.TELECOM_SERVICE);
        if(tm!=null && tm.getDefaultDialerPackage()!=null) allowed.add(tm.getDefaultDialerPackage());
        DevicePolicyManager d=dpm(c); ComponentName a=admin(c);
        d.setLockTaskPackages(a,allowed.toArray(new String[0]));
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) {
            int f=DevicePolicyManager.LOCK_TASK_FEATURE_HOME |
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS |
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO |
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW |
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS |
                    DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
            d.setLockTaskFeatures(a,f);
        }
    }

    public static void launchLockActivity(Context c) {
        if(!isDeviceOwner(c)) return;
        prepareRestrictedLockTask(c);
        Intent i=new Intent(c,LockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) {
                ActivityOptions o=ActivityOptions.makeBasic(); o.setLockTaskEnabled(true); c.startActivity(i,o.toBundle());
            } else c.startActivity(i);
        } catch(Exception e) { try { c.startActivity(i); } catch(Exception ignored) {} }
    }

    public static void openDialer(Context c) { c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    public static void openHome(Context c) { c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

    @SuppressWarnings("deprecation")
    public static boolean releaseDeviceOwnerForUninstall(Context c) {
        if(!isDeviceOwner(c)) return true;
        try {
            removePersistentPolicies(c);
            dpm(c).clearDeviceOwnerApp(c.getPackageName());
            return true;
        }
        catch(Exception e) { return false; }
    }
}
