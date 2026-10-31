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
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class RemoteControlActivity extends Activity {
    private static final int BG = UiKit.BG;
    private static final int CARD = UiKit.CARD;
    private static final int BORDER = UiKit.BORDER;
    private static final int TEXT = UiKit.TEXT;
    private static final int MUTED = UiKit.MUTED;
    private static final int ACCENT = UiKit.ACCENT;
    private static final int GOOD = UiKit.GOOD;
    private static final int DANGER = UiKit.DANGER;

    private EditText relayUrl;
    private TextView status;
    private TextView deviceId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.styleWindow(this);

        RemoteUnlockConfig.ensureCredentials(this);
        buildUi();
        refresh();
        syncRegistrationIfEnabled();
    }

    private void buildUi() {
        LinearLayout root = UiKit.page(this, "Parent remote unlock",
                "Pair guardian devices and securely manage remote screen-time access.");
        LinearLayout card = UiKit.card(this);
        card.addView(text("Secure relay", 20, TEXT, true));
        relayUrl = UiKit.formField(this, "https://your-worker.workers.dev");
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
        disable.setOnClickListener(v -> requirePin(this::disableRemote));
        card.addView(disable, buttonLp());

        card.addView(text("The pairing link can be used on multiple guardian devices. The link contains the random pairing secret, but each browser must also know the guardian PIN. The PIN itself is never stored by the relay.",
                12, MUTED, false), full(0, 10, 0, 0));

        root.addView(card, full(0, 0, 0, 14));
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

        // Changing an active guardian endpoint can break the existing parent's control.
        if (RemoteUnlockConfig.isEnabled(this)
                && !normalized.equals(RemoteUnlockConfig.getBaseUrl(this))) {
            requirePin(() -> applyRemoteConfig(normalized));
        } else {
            applyRemoteConfig(normalized);
        }
    }

    private void applyRemoteConfig(String normalized) {
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

    private void requirePin(Runnable action) {
        UiKit.requireGuardianPin(this, action);
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
        return UiKit.card(this);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        return UiKit.text(this, value, sp, color, bold);
    }

    private Button button(String label, int color) {
        return UiKit.button(this, label, color);
    }

    private LinearLayout.LayoutParams buttonLp() {
        return UiKit.buttonMargins(this);
    }

    private LinearLayout.LayoutParams full(int left, int top, int right, int bottom) {
        return UiKit.margins(this, left, top, right, bottom);
    }

    private int dp(int value) {
        return UiKit.dp(this, value);
    }
}
