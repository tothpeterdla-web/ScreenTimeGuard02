package com.example.screentimeguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class RemoteControlActivity extends Activity {
    private static final int BG = Color.rgb(5, 9, 15);
    private static final int CARD = Color.argb(220, 20, 34, 51);
    private static final int BORDER = Color.argb(120, 122, 168, 207);
    private static final int TEXT = Color.rgb(239, 246, 255);
    private static final int MUTED = Color.rgb(155, 177, 201);
    private static final int ACCENT = Color.rgb(36, 184, 255);
    private static final int GOOD = Color.rgb(73, 231, 140);
    private static final int DANGER = Color.rgb(255, 103, 120);

    private EditText relayUrl;
    private TextView status;
    private TextView deviceId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        RemoteUnlockConfig.ensureCredentials(this);
        buildUi();
        refresh();
        syncRegistrationIfEnabled();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(26));
        root.setBackgroundColor(BG);

        root.addView(text("Parent remote unlock", 28, TEXT, true));
        root.addView(text("Pair guardian phones so they can grant temporary screen-time overrides or a 15-minute app-install window. Opening this setup on the protected phone does not require the guardian PIN. The guardian PIN is still required on the parent web page before any remote command can be sent.",
                14, MUTED, false), full(0, 6, 0, 18));

        LinearLayout card = card();
        card.addView(text("Secure relay", 20, TEXT, true));
        relayUrl = new EditText(this);
        relayUrl.setHint("https://your-worker.workers.dev");
        relayUrl.setSingleLine(true);
        relayUrl.setTextColor(TEXT);
        relayUrl.setHintTextColor(MUTED);
        relayUrl.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.addView(relayUrl, full(0, 8, 0, 8));

        status = text("Not configured", 16, MUTED, true);
        card.addView(status, full(0, 2, 0, 10));

        deviceId = text("", 12, MUTED, false);
        card.addView(deviceId, full(0, 0, 0, 10));

        Button enable = button("Save & enable remote unlock", ACCENT);
        enable.setOnClickListener(v -> enableRemote());
        card.addView(enable, buttonLp());

        Button copy = button("Copy guardian pairing link", ACCENT);
        copy.setOnClickListener(v -> copyPairingLink());
        card.addView(copy, buttonLp());

        Button share = button("Share guardian pairing link", ACCENT);
        share.setOnClickListener(v -> sharePairingLink());
        card.addView(share, buttonLp());

        Button disable = button("Disable & revoke remote pairing", DANGER);
        disable.setOnClickListener(v -> disableRemote());
        card.addView(disable, buttonLp());

        card.addView(text("The pairing link can be used on multiple guardian devices. The link contains the random pairing secret, but each browser must also know the guardian PIN. The PIN itself is never stored by the relay.",
                12, MUTED, false), full(0, 10, 0, 0));

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void enableRemote() {
        String normalized = RemoteUnlockConfig.normalizeBaseUrl(relayUrl.getText().toString());
        if (normalized.isEmpty()) {
            Toast.makeText(this, "Enter a valid HTTPS relay URL", Toast.LENGTH_LONG).show();
            return;
        }
        if (!PinStore.hasPin(this)) {
            Toast.makeText(this, "Set a guardian PIN in Screen Time Guard first", Toast.LENGTH_LONG).show();
            return;
        }

        RemoteUnlockConfig.setBaseUrl(this, normalized);
        RemoteUnlockConfig.ensureCredentials(this);
        status.setText("Registering…");
        status.setTextColor(MUTED);
        RemoteUnlockClient.registerAsync(this, (ok, message) -> {
            if (ok) {
                RemoteUnlockConfig.setEnabled(this, true);
                refresh();
            } else {
                status.setText(message);
                status.setTextColor(DANGER);
            }
        });
    }

    private void syncRegistrationIfEnabled() {
        if (!RemoteUnlockConfig.isEnabled(this) || !PinStore.hasPin(this)) return;
        status.setText("Refreshing remote pairing…");
        status.setTextColor(MUTED);
        RemoteUnlockClient.registerAsync(this, (ok, message) -> {
            if (ok) {
                status.setText("Remote unlock enabled");
                status.setTextColor(GOOD);
            } else {
                status.setText(message);
                status.setTextColor(DANGER);
            }
        });
    }

    private void copyPairingLink() {
        if (!RemoteUnlockConfig.isEnabled(this)) {
            Toast.makeText(this, "Enable remote unlock first", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "Screen Time Guard guardian pairing", RemoteUnlockConfig.pairingUrl(this)));
            Toast.makeText(this, "Pairing link copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePairingLink() {
        if (!RemoteUnlockConfig.isEnabled(this)) {
            Toast.makeText(this, "Enable remote unlock first", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, RemoteUnlockConfig.pairingUrl(this));
        startActivity(Intent.createChooser(send, "Send guardian pairing link"));
    }

    private void disableRemote() {
        if (!RemoteUnlockConfig.isEnabled(this)) {
            RemoteUnlockConfig.setEnabled(this, false);
            RemoteUnlockConfig.rotateCredentials(this);
            refresh();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Disable remote unlock?")
                .setMessage("The current guardian pairing will stop working and a new secret will be generated.")
                .setPositiveButton("Disable", (d, w) -> {
                    RemoteUnlockConfig.setEnabled(this, false);
                    status.setText("Revoking…");
                    status.setTextColor(MUTED);
                    RemoteUnlockClient.revokeAsync(this, (ok, message) -> {
                        RemoteUnlockConfig.rotateCredentials(this);
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        refresh();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refresh() {
        String base = RemoteUnlockConfig.getBaseUrl(this);
        if (!base.isEmpty() && !base.equals(relayUrl.getText().toString())) relayUrl.setText(base);
        deviceId.setText("Device ID: " + RemoteUnlockConfig.getDeviceId(this));
        boolean enabled = RemoteUnlockConfig.isEnabled(this);
        status.setText(enabled ? "Remote unlock enabled" : "Remote unlock disabled");
        status.setTextColor(enabled ? GOOD : MUTED);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(cardBg());
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

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(15), 0, dp(15), 0);
        GradientDrawable g = cardBg();
        g.setStroke(dp(1), color);
        b.setBackground(g);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
