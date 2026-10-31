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
    private static final int BG = UiKit.BG;
    private static final int CARD = UiKit.CARD;
    private static final int BORDER = UiKit.BORDER;
    private static final int TEXT = UiKit.TEXT;
    private static final int MUTED = UiKit.MUTED;
    private static final int ACCENT = UiKit.ACCENT;
    private static final int GOOD = UiKit.GOOD;

    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.styleWindow(this);
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
        LinearLayout root = UiKit.page(this, "App installations",
                "Choose which apps can be installed. Guardian approval is required to allow installs.");
        LinearLayout card = UiKit.card(this);

        card.addView(text("Installation protection", 20, TEXT, true));
        status = text("Checking…", 18, ACCENT, true);
        card.addView(status, full(0, 8, 0, 12));

        Button allow = button("Allow app installations for 15 minutes (Guardian PIN)");
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

        root.addView(card, full(0, 0, 0, 14));
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
            status.setText("App installations enabled · about " + mins + " min left");
            status.setTextColor(GOOD);
        } else {
            status.setText(PolicyUtils.isAppInstallBlocked(this) ? "All app installations blocked" : "Applying block…");
            status.setTextColor(ACCENT);
        }
    }

    private void requirePin(Runnable action) {
        UiKit.requireGuardianPin(this, action);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        return UiKit.text(this, value, sp, color, bold);
    }

    private Button button(String label) {
        return UiKit.button(this, label, label.contains("Allow") ? UiKit.ACCENT : UiKit.GOOD);
    }

    private LinearLayout.LayoutParams buttonLp() {
        return UiKit.buttonMargins(this);
    }

    private LinearLayout.LayoutParams full(int left, int top, int right, int bottom) {
        return UiKit.margins(this, left, top, right, bottom);
    }

    private int dp(int v) {
        return UiKit.dp(this, v);
    }
}
