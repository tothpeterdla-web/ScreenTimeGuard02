package com.example.screentimeguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        PolicyUtils.applyAdGuardProtection(this);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        PolicyUtils.applyAdGuardProtection(this);
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("AdGuard Guardian");
        title.setTextSize(30);
        root.addView(title, full());

        TextView info = new TextView(this);
        info.setText("This app now only keeps AdGuard installed using Android Device Owner. Screen-time limits, lock mode, Usage Access monitoring and app blocking have been removed. AdGuard settings can remain protected separately with ASUS AppLock.");
        info.setTextSize(16);
        root.addView(info, full());

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status, full());

        Button reapply = new Button(this);
        reapply.setText("Reapply AdGuard uninstall protection");
        reapply.setOnClickListener(v -> {
            PolicyUtils.applyAdGuardProtection(this);
            Toast.makeText(this, "AdGuard protection reapplied", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(reapply, full());

        Button pin = new Button(this);
        pin.setText("Set / change guardian PIN");
        pin.setOnClickListener(v -> {
            if (PinStore.hasPin(this)) requirePin(this::setPin);
            else setPin();
        });
        root.addView(pin, full());

        Button release = new Button(this);
        release.setText("Release Device Owner / allow uninstall");
        release.setOnClickListener(v -> requirePin(this::release));
        root.addView(release, full());

        setContentView(scroll);
    }

    private void refresh() {
        status.setText("\nDevice Owner: " + yn(PolicyUtils.isDeviceOwner(this))
                + "\nGuardian PIN: " + yn(PinStore.hasPin(this))
                + "\nAdGuard installed: " + yn(PolicyUtils.isAdGuardInstalled(this))
                + "\nAdGuard uninstall blocked: " + yn(PolicyUtils.isAdGuardUninstallBlocked(this))
                + "\n");
    }

    private void release() {
        boolean ok = PolicyUtils.releaseDeviceOwnerForUninstall(this);
        Toast.makeText(this,
                ok ? "Device Owner released; uninstall is allowed" : "Release failed; factory reset may be required",
                Toast.LENGTH_LONG).show();
        refresh();
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
            new AlertDialog.Builder(this)
                    .setTitle("No guardian PIN")
                    .setMessage("Set a guardian PIN first.")
                    .setPositiveButton("OK", null)
                    .show();
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

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    private String yn(boolean b) { return b ? "YES" : "NO"; }

    private LinearLayout.LayoutParams full() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
