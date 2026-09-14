package com.example.screentimeguard;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.TextView;
import android.widget.Toast;

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

        root.addView(text("APK sideloading", 28, TEXT, true));
        root.addView(text("Play Store installs stay allowed. APK installs from browsers, file managers, and other unknown sources are blocked unless a guardian temporarily allows them.",
                14, MUTED, false), full(0, 6, 0, 18));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(cardBg());

        card.addView(text("Sideload protection", 20, TEXT, true));
        status = text("Checking…", 18, ACCENT, true);
        card.addView(status, full(0, 8, 0, 12));

        Button allow = button("Allow APK installs for 10 minutes");
        allow.setOnClickListener(v -> requirePin(() -> {
            if (!PolicyUtils.isDeviceOwner(this)) {
                Toast.makeText(this, "Device Owner is required", Toast.LENGTH_LONG).show();
                return;
            }
            Prefs.allowAppInstallsForMinutes(this, 10);
            PolicyUtils.applyInstallProtection(this);
            Toast.makeText(this, "APK installs allowed for 10 minutes", Toast.LENGTH_LONG).show();
            refresh();
        }));
        card.addView(allow, buttonLp());

        Button lock = button("Block APK installs now");
        lock.setOnClickListener(v -> requirePin(() -> {
            Prefs.clearInstallWindow(this);
            PolicyUtils.applyInstallProtection(this);
            Toast.makeText(this, "APK sideloading blocked", Toast.LENGTH_SHORT).show();
            refresh();
        }));
        card.addView(lock, buttonLp());

        card.addView(text("This does not block ordinary file downloads or normal Play Store installs.",
                13, MUTED, false), full(0, 12, 0, 0));

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void refresh() {
        if (!PolicyUtils.isDeviceOwner(this)) {
            status.setText("Device Owner not active");
            status.setTextColor(MUTED);
            return;
        }

        if (Prefs.isInstallWindowActive(this)) {
            long left = Math.max(0L, Prefs.getInstallAllowedUntil(this) - System.currentTimeMillis());
            long mins = Math.max(1L, (left + 59_999L) / 60_000L);
            status.setText("APK installs temporarily allowed · about " + mins + " min left");
            status.setTextColor(GOOD);
        } else {
            status.setText(PolicyUtils.isAppInstallBlocked(this) ? "APK installs blocked" : "Applying block…");
            status.setTextColor(ACCENT);
        }
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
                    else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
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
