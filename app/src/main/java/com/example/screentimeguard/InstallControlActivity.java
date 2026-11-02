package com.example.screentimeguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstallControlActivity extends Activity {
    private static final int BG = Color.rgb(5, 9, 15);
    private static final int CARD = Color.argb(220, 20, 34, 51);
    private static final int BORDER = Color.argb(120, 122, 168, 207);
    private static final int TEXT = Color.rgb(239, 246, 255);
    private static final int MUTED = Color.rgb(155, 177, 201);
    private static final int ACCENT = Color.rgb(36, 184, 255);
    private static final int GOOD = Color.rgb(73, 231, 140);

    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        PolicyUtils.applyInstallProtection(this);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        PolicyUtils.applyInstallProtection(this);
        refresh();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(26));
        root.setBackgroundColor(BG);

        root.addView(text("App installations", 28, TEXT, true));
        root.addView(text("Use automatic updates to keep existing apps current without PIN prompts. New installations are hidden until a guardian approves them.",
                14, MUTED, false), full(0, 6, 0, 18));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(cardBg());

        card.addView(text("Installation protection", 20, TEXT, true));
        status = text("Checking…", 18, ACCENT, true);
        card.addView(status, full(0, 8, 0, 12));

        Button allow = button("Enable app installations for 15 minutes");
        allow.setOnClickListener(v -> requirePin(() -> {
            if (!PolicyUtils.isDeviceOwner(this)) {
                Toast.makeText(this, "Device Owner is required", Toast.LENGTH_LONG).show();
                return;
            }
            Prefs.allowAppInstallsForMinutes(this, 15);
            PolicyUtils.applyInstallProtection(this);
            Toast.makeText(this,
                    "App installations enabled for 15 minutes",
                    Toast.LENGTH_LONG).show();
            refresh();
        }));
        card.addView(allow, buttonLp());

        // Tightening installation protection never requires a PIN.
        Button lock = button("Block app installations now");
        lock.setOnClickListener(v -> {
            Prefs.clearInstallWindow(this);
            PolicyUtils.applyInstallProtection(this);
            Toast.makeText(this, "App installations blocked", Toast.LENGTH_SHORT).show();
            refresh();
        });
        card.addView(lock, buttonLp());

        card.addView(text("When enabled, installs from Play Store, other app stores, and APK files are allowed for 15 minutes, then automatically blocked again. Ordinary file downloads are not affected.",
                13, MUTED, false), full(0, 12, 0, 0));

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout updatesCard = new LinearLayout(this);
        updatesCard.setOrientation(LinearLayout.VERTICAL);
        updatesCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        updatesCard.setBackground(cardBg());
        LinearLayout.LayoutParams updateLp = full(0, 16, 0, 0);
        root.addView(updatesCard, updateLp);

        updatesCard.addView(text("Automatic updates + app quarantine", 20, TEXT, true));
        updatesCard.addView(text("Existing apps can update without a PIN. Android may still download and install a new app, but ScreenTimeGuard hides it until guardian approval. There can be a short detection delay.",
                13, MUTED, false), full(0, 7, 0, 10));

        // Automatic updates clears the system-wide app-install restriction.
        // It therefore requires the guardian PIN even though new apps are
        // subsequently quarantined.
        Button enableUpdates = button("Allow updates with quarantine (Guardian PIN)");
        enableUpdates.setOnClickListener(v -> requirePin(() ->
                new AlertDialog.Builder(this)
                        .setTitle("Enable automatic updates?")
                        .setMessage("Android will permit app installations. ScreenTimeGuard will hide new, unapproved apps as soon as it detects them. There may be a short interval before quarantine. Existing apps will update without a PIN.")
                        .setPositiveButton("Enable", (dialog, which) -> {
                            boolean success = PolicyUtils.enableAutomaticUpdates(this);
                            Toast.makeText(this, success
                                            ? "Automatic updates enabled; new apps quarantined"
                                            : "Could not safely enable automatic updates",
                                    Toast.LENGTH_LONG).show();
                            refresh();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()));
        updatesCard.addView(enableUpdates, buttonLp());

        Button review = button("Review quarantined new apps (Guardian PIN)");
        review.setOnClickListener(v -> requirePin(this::showQuarantinedApps));
        updatesCard.addView(review, buttonLp());

        // Switching back from quarantine to the stricter install block is
        // a security-increasing action and must not require a guardian PIN.
        Button strict = button("Restore strict installation block");
        strict.setOnClickListener(v -> {
            boolean ok = PolicyUtils.disableAutomaticUpdates(this);
            Toast.makeText(this, ok ? "Strict installation block restored"
                    : "Could not restore installation policy", Toast.LENGTH_LONG).show();
            refresh();
        });
        updatesCard.addView(strict, buttonLp());

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.addView(root);
        setContentView(scroller);
    }

    private void refresh() {
        if (!PolicyUtils.isDeviceOwner(this)) {
            status.setText("Device Owner not active");
            status.setTextColor(MUTED);
            return;
        }

        if (Prefs.isUpdateOnlyModeEnabled(this)) {
            int pending = Prefs.getQuarantinedPackages(this).size();
            status.setText("Automatic updates ON · " + pending + " new app(s) quarantined");
            status.setTextColor(GOOD);
        } else if (Prefs.isInstallWindowActive(this)) {
            long left = Math.max(0L, Prefs.getInstallAllowedUntil(this) - System.currentTimeMillis());
            long mins = Math.max(1L, (left + 59_999L) / 60_000L);
            status.setText("App installations enabled · about " + mins + " min left");
            status.setTextColor(GOOD);
        } else {
            status.setText(PolicyUtils.isAppInstallBlocked(this) ? "All app installations blocked" : "Applying block…");
            status.setTextColor(ACCENT);
        }
    }

    private void showQuarantinedApps() {
        List<String> pkgs = new ArrayList<>(Prefs.getQuarantinedPackages(this));
        Collections.sort(pkgs);
        if (pkgs.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("New apps")
                    .setMessage("No newly installed apps are awaiting approval.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = new String[pkgs.size()];
        PackageManager pm = getPackageManager();
        for (int i = 0; i < pkgs.size(); i++) {
            String pkg = pkgs.get(i);
            String appName = pkg;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.MATCH_ALL);
                CharSequence name = pm.getApplicationLabel(info);
                if (name != null && name.length() > 0) appName = name.toString();
            } catch (Exception ignored) {}
            labels[i] = appName + " (" + pkg + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle("Approve a quarantined app?")
                .setItems(labels, (dialog, which) -> {
                    String pkg = pkgs.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle("Allow this new app?")
                            .setMessage(pkg + " will be unhidden and may run normally. This change was authorized with the guardian PIN.")
                            .setPositiveButton("Approve", (d, w) -> {
                                boolean success = PolicyUtils.approveInstalledApp(this, pkg);
                                Toast.makeText(this, success ? "App approved"
                                        : "Could not approve app", Toast.LENGTH_LONG).show();
                                refresh();
                            })
                            .setNegativeButton("Cancel", null).show();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void requirePin(Runnable action) {
        if (!PinStore.hasPin(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Guardian PIN required")
                    .setMessage("Set a guardian PIN in Screen Time Guard first.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        if (PinStore.isLockedForToday(this)) {
            Toast.makeText(this,
                    "Guardian PIN locked for today. Try again tomorrow.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        EditText pin = new EditText(this);
        pin.setHint("Guardian PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setSingleLine(true);
        pin.setTextColor(TEXT);
        pin.setHintTextColor(MUTED);
        pin.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(18), 0, dp(18), 0);
        holder.addView(pin, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Guardian authorization")
                .setView(holder)
                .setPositiveButton("Continue", (d, w) -> {
                    if (PinStore.verify(this, pin.getText().toString())) action.run();
                    else if (!PinStore.isLockedForToday(this)) {
                        Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(15), 0, dp(15), 0);
        b.setBackground(cardBg());
        return b;
    }

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), BORDER);
        return g;
    }

    private LinearLayout.LayoutParams buttonLp() {
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
