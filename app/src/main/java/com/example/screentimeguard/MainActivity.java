package com.example.screentimeguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private TextView status;
    private EditText hours;
    private EditText minutes;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        PolicyUtils.applyAdGuardProtection(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        PolicyUtils.applyAdGuardProtection(this);
        refresh();
        if (ScreenTimeTracker.hasUsageAccess(this)) startMonitorService();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Screen Time Guard");
        title.setTextSize(30);
        root.addView(title, full());

        TextView info = new TextView(this);
        info.setText("After the daily limit, Android itself blocks non-approved apps with its 'App is not available' message. There is no daily-limit page anymore. The phone/launcher, Maps, BudapestGO and guardian-approved apps remain available. Preinstalled launchable apps can also be chosen explicitly. AdGuard uninstall protection stays active independently of screen-time protection.");
        info.setTextSize(16);
        root.addView(info, full());

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status, full());

        Button usage = new Button(this);
        usage.setText("Open Usage Access settings");
        usage.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        root.addView(usage, full());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hours = num("hours");
        minutes = num("minutes");
        row.addView(hours, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(minutes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row, full());

        Button save = new Button(this);
        save.setText("Save daily limit");
        save.setOnClickListener(v -> requirePin(this::saveLimit));
        root.addView(save, full());

        Button apps = new Button(this);
        apps.setText("Choose apps allowed after limit");
        apps.setOnClickListener(v -> requirePin(this::chooseAllowedApps));
        root.addView(apps, full());

        Button unlock = new Button(this);
        unlock.setText("Guardian unlock until midnight");
        unlock.setOnClickListener(v -> requirePin(this::unlockUntilMidnight));
        root.addView(unlock, full());

        Button pin = new Button(this);
        pin.setText("Set / change guardian PIN");
        pin.setOnClickListener(v -> {
            if (PinStore.hasPin(this)) requirePin(this::setPin);
            else setPin();
        });
        root.addView(pin, full());

        Button enable = new Button(this);
        enable.setText("Enable strong protection");
        enable.setOnClickListener(v -> enable());
        root.addView(enable, full());

        Button disable = new Button(this);
        disable.setText("Disable screen-time protection (guardian PIN)");
        disable.setOnClickListener(v -> requirePin(this::disable));
        root.addView(disable, full());

        Button reapplyAdGuard = new Button(this);
        reapplyAdGuard.setText("Reapply AdGuard uninstall protection");
        reapplyAdGuard.setOnClickListener(v -> {
            PolicyUtils.applyAdGuardProtection(this);
            Toast.makeText(this, "AdGuard protection reapplied", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(reapplyAdGuard, full());

        Button release = new Button(this);
        release.setText("Release Device Owner / allow uninstall");
        release.setOnClickListener(v -> requirePin(this::release));
        root.addView(release, full());

        setContentView(scroll);
    }

    private void refresh() {
        int limit = Prefs.getLimitMinutes(this);
        hours.setText(String.valueOf(limit / 60));
        minutes.setText(String.valueOf(limit % 60));
        long used = ScreenTimeTracker.hasUsageAccess(this)
                ? ScreenTimeTracker.getTodayInteractiveMillis(this) : -1L;
        long remaining = used < 0 ? -1L : Math.max(0L, limit * 60000L - used);

        status.setText("\nDevice Owner: " + yn(PolicyUtils.isDeviceOwner(this))
                + "\nUsage Access: " + yn(ScreenTimeTracker.hasUsageAccess(this))
                + "\nGuardian PIN: " + yn(PinStore.hasPin(this))
                + "\nAdGuard installed: " + yn(PolicyUtils.isAdGuardInstalled(this))
                + "\nAdGuard uninstall blocked: " + yn(PolicyUtils.isAdGuardUninstallBlocked(this))
                + "\nProtection enabled: " + yn(Prefs.isEnabled(this))
                + "\nRestricted mode active: " + yn(PolicyUtils.isRestrictedModeActive(this))
                + "\nExtra apps allowed after limit: " + Prefs.getExtraAllowedPackages(this).size()
                + "\nToday's screen time: " + ScreenTimeTracker.formatDuration(used)
                + "\nRemaining today: " + ScreenTimeTracker.formatDuration(remaining)
                + "\n");
    }

    private void startMonitorService() {
        Intent service = new Intent(this, ScreenTimeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start screen-time monitor", Toast.LENGTH_SHORT).show();
        }
    }

    private void enable() {
        if (!PolicyUtils.isDeviceOwner(this)) {
            msg("Device Owner required", "Provision the app as Android Device Owner first.");
            return;
        }
        if (!ScreenTimeTracker.hasUsageAccess(this)) {
            msg("Usage Access required", "Grant Usage Access first.");
            return;
        }
        if (!PinStore.hasPin(this)) {
            msg("Guardian PIN required", "Set a guardian PIN first.");
            return;
        }
        Prefs.clearOverride(this);
        Prefs.setEnabled(this, true);
        PolicyUtils.applyPersistentPolicies(this);
        startMonitorService();
        Toast.makeText(this, "Protection enabled", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void disable() {
        Prefs.setEnabled(this, false);
        Prefs.clearOverride(this);
        PolicyUtils.guardianExitRestrictedMode(this);
        PolicyUtils.removeScreenTimePolicies(this);
        PolicyUtils.applyAdGuardProtection(this);
        Toast.makeText(this, "Screen-time protection disabled; AdGuard remains protected", Toast.LENGTH_LONG).show();
        refresh();
    }

    private void unlockUntilMidnight() {
        Prefs.unlockUntilMidnight(this);
        PolicyUtils.guardianExitRestrictedMode(this);
        Toast.makeText(this, "Unlocked until midnight", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void release() {
        Prefs.setEnabled(this, false);
        Prefs.clearOverride(this);
        PolicyUtils.guardianExitRestrictedMode(this);
        boolean ok = PolicyUtils.releaseDeviceOwnerForUninstall(this);
        Toast.makeText(this,
                ok ? "Device Owner released; uninstall is allowed" : "Release failed; factory reset may be required",
                Toast.LENGTH_LONG).show();
        refresh();
    }

    private void saveLimit() {
        try {
            int h = hours.getText().toString().isEmpty() ? 0 : Integer.parseInt(hours.getText().toString());
            int m = minutes.getText().toString().isEmpty() ? 0 : Integer.parseInt(minutes.getText().toString());
            int total = h * 60 + m;
            if (h < 0 || m < 0 || m > 59 || total < 1) throw new Exception();
            Prefs.setLimitMinutes(this, total);
            Prefs.clearOverride(this);
            if (ScreenTimeTracker.hasUsageAccess(this)) startMonitorService();
            Toast.makeText(this, "Daily limit saved", Toast.LENGTH_SHORT).show();
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "Enter valid hours/minutes", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseAllowedApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.MATCH_ALL);
        ArrayList<ApplicationInfo> candidates = new ArrayList<>();

        for (ApplicationInfo app : installed) {
            if (PolicyUtils.isAlwaysBlockedPackage(app.packageName)) continue;
            if (PolicyUtils.isAutomaticallyAllowedVisiblePackage(this, app.packageName)) continue;
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue;
            candidates.add(app);
        }

        candidates.sort((a, b) -> String.valueOf(pm.getApplicationLabel(a))
                .compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));

        CharSequence[] labels = new CharSequence[candidates.size()];
        boolean[] checked = new boolean[candidates.size()];
        Set<String> current = Prefs.getExtraAllowedPackages(this);
        Set<String> working = new HashSet<>(current);

        for (int i = 0; i < candidates.size(); i++) {
            ApplicationInfo app = candidates.get(i);
            labels[i] = pm.getApplicationLabel(app);
            checked[i] = current.contains(app.packageName);
        }

        new AlertDialog.Builder(this)
                .setTitle("Apps allowed after the limit")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    String pkg = candidates.get(which).packageName;
                    if (isChecked) working.add(pkg);
                    else working.remove(pkg);
                })
                .setPositiveButton("Save", (d, w) -> {
                    Prefs.setExtraAllowedPackages(this, working);
                    if (Prefs.isEnabled(this) && PolicyUtils.isRestrictedModeActive(this)) {
                        PolicyUtils.prepareRestrictedLockTask(this);
                    }
                    Toast.makeText(this, "Allowed apps updated", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setPin() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText a = pinField("New guardian PIN");
        EditText b = pinField("Confirm guardian PIN");
        box.addView(a);
        box.addView(b);
        new AlertDialog.Builder(this)
                .setTitle("Guardian PIN")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String x = a.getText().toString();
                    if (x.length() >= 4 && x.equals(b.getText().toString()) && PinStore.setPin(this, x)) {
                        Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "PINs must match and be at least 4 digits", Toast.LENGTH_LONG).show();
                    }
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requirePin(Runnable action) {
        if (!PinStore.hasPin(this)) {
            msg("No guardian PIN", "Set a guardian PIN first.");
            return;
        }
        EditText p = pinField("Guardian PIN");
        new AlertDialog.Builder(this)
                .setTitle("Guardian authorization")
                .setView(p)
                .setPositiveButton("Continue", (d, w) -> {
                    if (PinStore.verify(this, p.getText().toString())) action.run();
                    else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText num(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private EditText pinField(String hint) {
        EditText e = num(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    private String yn(boolean b) { return b ? "YES" : "NO"; }

    private void msg(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private LinearLayout.LayoutParams full() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
