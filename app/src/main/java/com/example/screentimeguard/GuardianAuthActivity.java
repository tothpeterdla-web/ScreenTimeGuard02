package com.example.screentimeguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;

public class GuardianAuthActivity extends Activity {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "stg_guardian_auth_v1";
    private static final String PREFIX = "STG-AUTH-V1\n";

    private static final int BG = Color.rgb(5, 9, 15);
    private static final int CARD = Color.argb(220, 20, 34, 51);
    private static final int BORDER = Color.argb(120, 122, 168, 207);
    private static final int TEXT = Color.rgb(239, 246, 255);
    private static final int MUTED = Color.rgb(155, 177, 201);
    private static final int ACCENT = Color.rgb(36, 184, 255);
    private static final int GOOD = Color.rgb(73, 231, 140);

    private EditText challenge;
    private TextView fingerprint;
    private TextView publicKey;
    private TextView proof;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        try {
            ensureKey();
        } catch (Exception e) {
            Toast.makeText(this, "Could not initialize guardian authorization key", Toast.LENGTH_LONG).show();
        }

        buildUi();
        refreshPublicInfo();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(30));
        root.setBackgroundColor(BG);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("Guardian authorization", 28, TEXT, true));
        root.addView(text(
                "Use this when a Screen Time Guard protection change needs guardian approval. " +
                "The Guardian PIN never leaves this phone. Instead, the app signs a one-time challenge after the correct PIN is entered.",
                14, MUTED, false), full(0, 6, 0, 18));

        LinearLayout card = card();
        card.addView(text("One-time challenge", 18, TEXT, true));

        challenge = new EditText(this);
        challenge.setHint("Paste the challenge here");
        challenge.setHintTextColor(MUTED);
        challenge.setTextColor(TEXT);
        challenge.setTextSize(15);
        challenge.setSingleLine(false);
        challenge.setMinLines(2);
        challenge.setMaxLines(4);
        challenge.setGravity(Gravity.TOP | Gravity.START);
        challenge.setPadding(dp(14), dp(12), dp(14), dp(12));
        challenge.setBackground(innerBg());
        card.addView(challenge, full(0, 8, 0, 10));

        Button generate = button("Generate guardian approval proof");
        generate.setOnClickListener(v -> requirePin(this::generateProof));
        card.addView(generate, buttonLp());

        proof = text("No proof generated yet.", 13, MUTED, false);
        proof.setTextIsSelectable(true);
        card.addView(proof, full(0, 12, 0, 8));

        Button copyProof = button("Copy approval proof");
        copyProof.setOnClickListener(v -> {
            String value = proof.getText().toString();
            if (!value.startsWith("STG-AUTH-V1")) {
                Toast.makeText(this, "Generate a proof first", Toast.LENGTH_SHORT).show();
                return;
            }
            copy("Guardian approval proof", value);
        });
        card.addView(copyProof, buttonLp());

        root.addView(card, full(0, 0, 0, 14));

        LinearLayout pairing = card();
        pairing.addView(text("Pairing information", 18, TEXT, true));
        pairing.addView(text(
                "The public key is safe to share. Pair it once with the trusted reviewer. " +
                "Never share the Guardian PIN or any private key.",
                13, MUTED, false), full(0, 4, 0, 10));

        pairing.addView(text("Key fingerprint", 12, MUTED, false));
        fingerprint = text("Loading…", 14, GOOD, true);
        fingerprint.setTextIsSelectable(true);
        pairing.addView(fingerprint, full(0, 2, 0, 10));

        pairing.addView(text("Public key", 12, MUTED, false));
        publicKey = text("Loading…", 11, TEXT, false);
        publicKey.setTextIsSelectable(true);
        pairing.addView(publicKey, full(0, 2, 0, 8));

        Button copyKey = button("Copy public key");
        copyKey.setOnClickListener(v -> copy("Guardian authorization public key",
                publicKey.getText().toString()));
        pairing.addView(copyKey, buttonLp());

        root.addView(pairing);

        setContentView(scroll);
    }

    private void generateProof() {
        String value = challenge.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Paste the one-time challenge first", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            ensureKey();
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            PrivateKey privateKey = (PrivateKey) ks.getKey(KEY_ALIAS, null);

            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update((PREFIX + value).getBytes(StandardCharsets.UTF_8));
            String signature = Base64.encodeToString(signer.sign(), Base64.NO_WRAP);

            String result = "STG-AUTH-V1\n"
                    + "challenge=" + value + "\n"
                    + "fingerprint=" + getFingerprint() + "\n"
                    + "signature=" + signature;
            proof.setText(result);
            proof.setTextColor(GOOD);
            Toast.makeText(this, "Guardian approval proof generated", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            proof.setText("Could not generate approval proof.");
            proof.setTextColor(MUTED);
            Toast.makeText(this, "Authorization proof failed", Toast.LENGTH_LONG).show();
        }
    }

    private void requirePin(Runnable action) {
        if (!PinStore.hasPin(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Guardian PIN required")
                    .setMessage("Set a Guardian PIN in Screen Time Guard first.")
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
                .setMessage("Enter the Guardian PIN to sign this one-time challenge.")
                .setView(holder)
                .setPositiveButton("Authorize", (d, w) -> {
                    if (PinStore.verify(this, pin.getText().toString())) {
                        action.run();
                    } else if (!PinStore.isLockedForToday(this)) {
                        Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshPublicInfo() {
        try {
            fingerprint.setText(getFingerprint());
            publicKey.setText(getPublicKeyBase64());
        } catch (Exception e) {
            fingerprint.setText("Unavailable");
            publicKey.setText("Unavailable");
        }
    }

    private static void ensureKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return;

        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build();
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    private static String getPublicKeyBase64() throws Exception {
        ensureKey();
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        Certificate cert = ks.getCertificate(KEY_ALIAS);
        return Base64.encodeToString(cert.getPublicKey().getEncoded(), Base64.NO_WRAP);
    }

    private static String getFingerprint() throws Exception {
        byte[] pub = Base64.decode(getPublicKeyBase64(), Base64.NO_WRAP);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(pub);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) out.append(':');
            out.append(String.format("%02X", digest[i]));
        }
        return out.toString();
    }

    private void copy(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(cardBg());
        return card;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(15), 0, dp(15), 0);
        b.setBackground(innerBg());
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(22));
        g.setStroke(dp(1), BORDER);
        return g;
    }

    private GradientDrawable innerBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(210, 24, 41, 61));
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), BORDER);
        return g;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
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
