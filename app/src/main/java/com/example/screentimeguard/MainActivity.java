package com.example.screentimeguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(5, 9, 15);
    private static final int CARD = Color.argb(220, 20, 34, 51);
    private static final int CARD_SOFT = Color.argb(210, 24, 41, 61);
    private static final int BORDER = Color.argb(120, 122, 168, 207);
    private static final int TEXT = Color.rgb(239, 246, 255);
    private static final int MUTED = Color.rgb(155, 177, 201);
    private static final int ACCENT = Color.rgb(36, 184, 255);
    private static final int GOOD = Color.rgb(73, 231, 140);
    private static final int WARN = Color.rgb(255, 184, 77);
    private static final int DANGER = Color.rgb(255, 103, 120);

    private EditText hours;
    private EditText minutes;

    private TextView usedValue;
    private TextView remainingValue;
    private TextView dailyLimitValue;
    private TextView progressCaption;
    private TextView protectionPill;
    private TextView ownerValue;
    private TextView pinValue;
    private TextView adguardValue;
    private TextView restrictedValue;
    private TextView allowedCount;
    private ProgressBar dailyProgress;

    private Button bluetoothButton;
    private Button mobileDataButton;
    private Button rotationButton;
    private TextView brightnessLabel;
    private SeekBar brightnessBar;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        PolicyUtils.applyAdGuardProtection(this);
        requestRuntimePermissionsIfNeeded();
        startMonitorService();
    }

    @Override protected void onResume() {
        super.onResume();
        PolicyUtils.applyAdGuardProtection(this);
        ScreenTimeTracker.sample(this);
        refresh();
        startMonitorService();
    }

    private void requestRuntimePermissionsIfNeeded() {
        ArrayList<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 100);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        root.setBackgroundColor(BG);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(8));

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.drawable.ic_guardian_mascot);
        mascot.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        iconLp.setMargins(0, 0, dp(14), 0);
        header.addView(mascot, iconLp);

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Screen Time Guard", 28, TEXT, true);
        TextView subtitle = text("Less screen. A brighter you.", 14, MUTED, false);
        headerText.addView(title);
        headerText.addView(subtitle);
        header.addView(headerText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, full(0, 0, 0, 16));

        LinearLayout hero = card();
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout usedCol = new LinearLayout(this);
        usedCol.setOrientation(LinearLayout.VERTICAL);
        usedCol.addView(text("Today's screen time", 15, TEXT, false));
        usedValue = text("0h 0m", 38, TEXT, true);
        usedCol.addView(usedValue);
        usedCol.addView(text("Keep going!", 13, MUTED, false));
        heroRow.addView(usedCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f));

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(90, 120, 160, 200));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(90));
        divLp.setMargins(dp(8), 0, dp(14), 0);
        heroRow.addView(divider, divLp);

        LinearLayout remainingCol = new LinearLayout(this);
        remainingCol.setOrientation(LinearLayout.VERTICAL);
        remainingCol.addView(text("Remaining today", 14, TEXT, false));
        remainingValue = text("0m", 30, ACCENT, true);
        remainingCol.addView(remainingValue);
        remainingCol.addView(text("Daily limit", 12, MUTED, false));
        dailyLimitValue = text("0h 0m", 16, TEXT, false);
        remainingCol.addView(dailyLimitValue);
        heroRow.addView(remainingCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f));

        LinearLayout encouragement = new LinearLayout(this);
        encouragement.setOrientation(LinearLayout.VERTICAL);
        encouragement.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        encouragement.setPadding(dp(4), 0, 0, 0);
        TextView quote = text("You\ngot this!", 14, MUTED, false);
        quote.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        quote.setGravity(Gravity.CENTER);
        encouragement.addView(quote);
        ImageView heroMascot = new ImageView(this);
        heroMascot.setImageResource(R.drawable.ic_guardian_figure_only);
        heroMascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        encouragement.addView(heroMascot, new LinearLayout.LayoutParams(dp(72), dp(86)));
        heroRow.addView(encouragement, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.62f));

        hero.addView(heroRow);

        dailyProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        dailyProgress.setMax(1000);
        dailyProgress.setProgress(0);
        dailyProgress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        dailyProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(54, 70, 88)));
        hero.addView(dailyProgress, full(dp(2), 12, dp(2), 0));

        progressCaption = text("0% of daily limit used", 13, MUTED, false);
        hero.addView(progressCaption, full(dp(2), 4, dp(2), 0));
        root.addView(hero, full(0, 0, 0, 14));

        LinearLayout protection = card();
        LinearLayout protectionTop = new LinearLayout(this);
        protectionTop.setOrientation(LinearLayout.HORIZONTAL);
        protectionTop.setGravity(Gravity.CENTER_VERTICAL);
        protectionTop.addView(text("Protection status", 21, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        protectionPill = pill("DISABLED", false);
        protectionTop.addView(protectionPill);
        protection.addView(protectionTop, full(0, 0, 0, 12));

        LinearLayout statusRow1 = new LinearLayout(this);
        statusRow1.setOrientation(LinearLayout.HORIZONTAL);
        ownerValue = statusTile(statusRow1, "Device Owner");
        pinValue = statusTile(statusRow1, "Guardian PIN");
        protection.addView(statusRow1, full(0, 0, 0, 8));

        LinearLayout statusRow2 = new LinearLayout(this);
        statusRow2.setOrientation(LinearLayout.HORIZONTAL);
        adguardValue = statusTile(statusRow2, "AdGuard protected");
        restrictedValue = statusTile(statusRow2, "Restricted mode");
        protection.addView(statusRow2);
        root.addView(protection, full(0, 0, 0, 14));

        LinearLayout limitCard = card();
        limitCard.addView(text("Daily limit", 21, TEXT, true));
        limitCard.addView(text("Set how much screen time is allowed per day.", 13, MUTED, false),
                full(0, 2, 0, 10));
        LinearLayout limitRow = new LinearLayout(this);
        limitRow.setOrientation(LinearLayout.HORIZONTAL);
        hours = num("Hours");
        minutes = num("Minutes");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(62), 1f);
        half.setMargins(0, 0, dp(6), 0);
        limitRow.addView(hours, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(62), 1f);
        half2.setMargins(dp(6), 0, 0, 0);
        limitRow.addView(minutes, half2);
        limitCard.addView(limitRow);
        root.addView(limitCard, full(0, 0, 0, 14));

        LinearLayout allowedCard = card();
        LinearLayout allowedTop = new LinearLayout(this);
        allowedTop.setOrientation(LinearLayout.HORIZONTAL);
        allowedTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout allowedTextBox = new LinearLayout(this);
        allowedTextBox.setOrientation(LinearLayout.VERTICAL);
        allowedTextBox.addView(text("Allowed after limit", 20, TEXT, true));
        allowedTextBox.addView(text("Maps and BudapestGO stay available, plus your selected apps.",
                13, MUTED, false));
        allowedTop.addView(allowedTextBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        allowedCount = pill("0 extra", true);
        allowedTop.addView(allowedCount);
        allowedCard.addView(allowedTop);
        root.addView(allowedCard, full(0, 0, 0, 14));

        root.addView(sectionTitle("Controls"), full(4, 3, 0, 5));
        Button save = actionButton("✓   Save daily limit", ACCENT);
        save.setOnClickListener(v -> requirePin(this::saveLimit));
        root.addView(save, actionLp());

        Button apps = actionButton("▦   Choose apps allowed after limit", ACCENT);
        apps.setOnClickListener(v -> requirePin(this::chooseAllowedApps));
        root.addView(apps, actionLp());

        Button installControl = actionButton("▣   App installation control", ACCENT);
        installControl.setOnClickListener(v ->
                startActivity(new Intent(this, InstallControlActivity.class)));
        root.addView(installControl, actionLp());

        Button messengerGuard = actionButton("⊘   Messenger link blocker", ACCENT);
        messengerGuard.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Messenger link blocker")
                    .setMessage("Enable Screen Time Guard in Accessibility. During restricted mode it will immediately close web pages opened inside Messenger while leaving chats usable. If you disable this Accessibility service, Messenger itself will be blocked after the daily limit, so disabling it cannot restore web access.")
                    .setPositiveButton("Open Accessibility", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        root.addView(messengerGuard, actionLp());

        Button unlock = actionButton("▣   Guardian unlock until midnight", ACCENT);
        unlock.setOnClickListener(v -> requirePin(this::unlockUntilMidnight));
        root.addView(unlock, actionLp());

        Button pin = actionButton("◇   Set / change guardian PIN", ACCENT);
        pin.setOnClickListener(v -> {
            if (PinStore.hasPin(this)) requirePin(this::setPin);
            else setPin();
        });
        root.addView(pin, actionLp());

        Button enable = actionButton("◈   Enable strong protection", ACCENT);
        enable.setOnClickListener(v -> enable());
        root.addView(enable, actionLp());

        Button disable = actionButton("◯   Disable screen-time protection", MUTED);
        disable.setOnClickListener(v -> requirePin(this::disable));
        root.addView(disable, actionLp());

        LinearLayout quickCard = card();
        quickCard.addView(text("Quick controls", 21, TEXT, true));
        quickCard.addView(text("Available even when restricted mode hides Quick Settings.",
                13, MUTED, false), full(0, 2, 0, 10));

        bluetoothButton = smallButton();
        bluetoothButton.setOnClickListener(v -> toggleBluetooth());
        quickCard.addView(bluetoothButton, actionLp());

        mobileDataButton = smallButton();
        mobileDataButton.setOnClickListener(v -> openInternetPanel());
        quickCard.addView(mobileDataButton, actionLp());

        brightnessLabel = text("Screen brightness", 14, MUTED, false);
        quickCard.addView(brightnessLabel, full(4, 8, 4, 0));
        brightnessBar = new SeekBar(this);
        brightnessBar.setMin(1);
        brightnessBar.setMax(255);
        brightnessBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        brightnessBar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessLabel.setText("Screen brightness: " + Math.round(progress * 100f / 255f) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!QuickControls.setBrightness(MainActivity.this, seekBar.getProgress())) {
                    Toast.makeText(MainActivity.this, "Could not change brightness", Toast.LENGTH_SHORT).show();
                }
                refreshQuickControls();
            }
        });
        quickCard.addView(brightnessBar);

        rotationButton = smallButton();
        rotationButton.setOnClickListener(v -> toggleAutoRotation());
        quickCard.addView(rotationButton, actionLp());
        root.addView(quickCard, full(0, 9, 0, 14));

        root.addView(sectionTitle("Safety & maintenance"), full(4, 3, 0, 5));

        Button reapplyAdGuard = actionButton("↻   Reapply AdGuard / anti-reset protection", MUTED);
        reapplyAdGuard.setOnClickListener(v -> {
            PolicyUtils.applyAdGuardProtection(this);
            Toast.makeText(this, "Protection reapplied", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(reapplyAdGuard, actionLp());

        Button release = actionButton("!   Release Device Owner / allow uninstall", DANGER);
        release.setOnClickListener(v -> requirePin(this::release));
        root.addView(release, actionLp());

        TextView footer = text("A CALMER MIND  ·  A BRIGHTER TOMORROW", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.22f);
        root.addView(footer, full(0, 18, 0, 0));

        setContentView(scroll);
    }

    private void refresh() {
        int limit = Prefs.getLimitMinutes(this);
        hours.setText(String.valueOf(limit / 60));
        minutes.setText(String.valueOf(limit % 60));

        long used = ScreenTimeTracker.getTodayInteractiveMillis(this);
        long limitMs = limit * 60_000L;
        long remaining = Math.max(0L, limitMs - used);
        float ratio = limitMs <= 0 ? 0f : Math.min(1f, used / (float)limitMs);

        usedValue.setText(ScreenTimeTracker.formatDuration(used));
        remainingValue.setText(ScreenTimeTracker.formatDuration(remaining));
        dailyLimitValue.setText(ScreenTimeTracker.formatDuration(limitMs));
        dailyProgress.setProgress(Math.round(ratio * 1000f));
        progressCaption.setText(Math.round(ratio * 100f) + "% of daily limit used  ·  "
                + ScreenTimeTracker.formatDuration(limitMs) + " total");

        boolean enabled = Prefs.isEnabled(this);
        protectionPill.setText(enabled ? "ENABLED" : "DISABLED");
        protectionPill.setTextColor(enabled ? GOOD : MUTED);
        protectionPill.setBackground(pillBg(enabled ? GOOD : MUTED));

        setState(ownerValue, PolicyUtils.isDeviceOwner(this), "Yes", "No");
        setState(pinValue, PinStore.hasPin(this), "Set", "Missing");
        setState(adguardValue,
                PolicyUtils.isAdGuardInstalled(this) && PolicyUtils.isAdGuardUninstallBlocked(this),
                "Yes", "No");
        setState(restrictedValue, PolicyUtils.isRestrictedModeActive(this), "On", "Off");

        int extra = Prefs.getExtraAllowedPackages(this).size();
        allowedCount.setText(extra + (extra == 1 ? " extra" : " extra"));
        refreshQuickControls();
    }

    private void refreshQuickControls() {
        if (bluetoothButton == null) return;

        if (!QuickControls.hasBluetoothPermission(this)) {
            bluetoothButton.setText("Bluetooth   permission required");
        } else {
            bluetoothButton.setText("Bluetooth   " + (QuickControls.isBluetoothEnabled(this) ? "ON" : "OFF"));
        }

        Boolean mobile = QuickControls.isMobileDataEnabled(this);
        mobileDataButton.setText("Internet   " + (mobile == null ? "open settings" : (mobile ? "mobile data on" : "mobile data off"))
                + "   ›");

        int brightness = QuickControls.getBrightness(this);
        brightnessBar.setProgress(brightness);
        brightnessLabel.setText("Screen brightness: " + Math.round(brightness * 100f / 255f) + "%");

        if (QuickControls.canWriteRotation(this)) {
            rotationButton.setText("Auto rotation   "
                    + (QuickControls.isAutoRotateEnabled(this) ? "ON" : "OFF"));
        } else {
            rotationButton.setText("Auto rotation   grant control permission ›");
        }
    }

    private void toggleBluetooth() {
        if (!QuickControls.hasBluetoothPermission(this)) {
            requestRuntimePermissionsIfNeeded();
            Toast.makeText(this, "Allow Nearby devices, then tap Bluetooth again", Toast.LENGTH_LONG).show();
            return;
        }
        boolean target = !QuickControls.isBluetoothEnabled(this);
        boolean ok = QuickControls.setBluetoothEnabled(this, target);
        Toast.makeText(this, ok ? "Bluetooth changing…" : "Could not change Bluetooth", Toast.LENGTH_SHORT).show();
        bluetoothButton.postDelayed(this::refreshQuickControls, 900L);
    }

    private void openInternetPanel() {
        try {
            Intent i = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    : new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            startActivity(i);
        } catch (Exception e) {
            msg("Mobile data control unavailable",
                    "Android does not give Device Owner apps permission to directly toggle cellular data. The Internet panel could not be opened on this device.");
        }
    }

    private void toggleAutoRotation() {
        if (!QuickControls.canWriteRotation(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("One-time rotation permission")
                    .setMessage("Grant 'Modify system settings' once so Screen Time Guard can toggle auto rotation.")
                    .setPositiveButton("Open permission", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not open permission setting", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        boolean target = !QuickControls.isAutoRotateEnabled(this);
        if (!QuickControls.setAutoRotateEnabled(this, target)) {
            Toast.makeText(this, "Could not change auto rotation", Toast.LENGTH_SHORT).show();
        }
        refreshQuickControls();
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
        Toast.makeText(this,
                "Screen-time protection disabled; AdGuard and Settings factory-reset protection remain active",
                Toast.LENGTH_LONG).show();
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
                ok ? "Device Owner released; uninstall and Settings factory reset are allowed"
                        : "Release failed; recovery may be required",
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
            startMonitorService();
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
        box.setPadding(dp(16), 0, dp(16), 0);
        EditText a = pinField("New guardian PIN");
        EditText b = pinField("Confirm guardian PIN");
        box.addView(a, full(0, 4, 0, 4));
        box.addView(b, full(0, 4, 0, 4));
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
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(18), 0, dp(18), 0);
        holder.addView(p, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Guardian authorization")
                .setView(holder)
                .setPositiveButton("Continue", (d, w) -> {
                    if (PinStore.verify(this, p.getText().toString())) action.run();
                    else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(glassBg(CARD));
        return card;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView sectionTitle(String value) {
        TextView t = text(value.toUpperCase(), 11, MUTED, true);
        t.setLetterSpacing(0.16f);
        return t;
    }

    private TextView pill(String value, boolean accent) {
        TextView t = text(value, 12, accent ? ACCENT : MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(pillBg(accent ? ACCENT : MUTED));
        return t;
    }

    private TextView statusTile(LinearLayout row, String label) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(13), dp(12), dp(13), dp(12));
        tile.setBackground(glassBg(CARD_SOFT));
        tile.addView(text(label, 13, MUTED, false));
        TextView value = text("—", 17, TEXT, true);
        tile.addView(value, full(0, 3, 0, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(tile, lp);
        return value;
    }

    private void setState(TextView view, boolean ok, String yes, String no) {
        view.setText(ok ? yes : no);
        view.setTextColor(ok ? GOOD : MUTED);
    }

    private Button actionButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(color == DANGER ? DANGER : TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setAllCaps(false);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackground(glassBg(CARD_SOFT));
        return b;
    }

    private Button smallButton() {
        Button b = new Button(this);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(glassBg(CARD_SOFT));
        return b;
    }

    private EditText num(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(22);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(glassBg(CARD_SOFT));
        e.setPadding(dp(10), 0, dp(10), 0);
        return e;
    }

    private EditText pinField(String hint) {
        EditText e = num(hint);
        e.setTextSize(18);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    private GradientDrawable glassBg(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1), BORDER);
        return g;
    }

    private GradientDrawable pillBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
        g.setCornerRadius(dp(30));
        g.setStroke(dp(1), Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)));
        return g;
    }

    private LinearLayout.LayoutParams actionLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private LinearLayout.LayoutParams full(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private void msg(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
