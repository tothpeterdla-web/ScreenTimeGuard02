package com.example.screentimeguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PinStore {
    private static final String NAME = "guardian_pin";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private PinStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean hasPin(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_SALT) && p.contains(KEY_HASH);
    }

    public static boolean setPin(Context context, String pin) {
        if (pin == null || pin.length() < 4) return false;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(pin, salt);
            prefs(context).edit()
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verify(Context context, String pin) {
        if (!hasPin(context) || pin == null) return false;
        try {
            byte[] salt = Base64.decode(prefs(context).getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(prefs(context).getString(KEY_HASH, ""), Base64.NO_WRAP);
            byte[] actual = derive(pin, salt);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
