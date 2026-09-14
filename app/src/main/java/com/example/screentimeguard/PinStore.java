package com.example.screentimeguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.widget.Toast;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PinStore {
    private static final String NAME = "guardian_pin";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_ATTEMPT_DAY = "attempt_day";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final int MAX_FAILED_ATTEMPTS_PER_DAY = 3;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private PinStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static void ensureAttemptDay(Context context) {
        SharedPreferences p = prefs(context);
        String today = todayKey();
        String savedDay = p.getString(KEY_ATTEMPT_DAY, "");
        if (!today.equals(savedDay)) {
            p.edit()
                    .putString(KEY_ATTEMPT_DAY, today)
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .apply();
        }
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
                    .putString(KEY_ATTEMPT_DAY, todayKey())
                    .putInt(KEY_FAILED_ATTEMPTS, 0)
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isLockedForToday(Context context) {
        ensureAttemptDay(context);
        return prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0) >= MAX_FAILED_ATTEMPTS_PER_DAY;
    }

    public static int attemptsRemainingToday(Context context) {
        ensureAttemptDay(context);
        int failed = prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0);
        return Math.max(0, MAX_FAILED_ATTEMPTS_PER_DAY - failed);
    }

    public static boolean verify(Context context, String pin) {
        if (!hasPin(context) || pin == null) return false;
        ensureAttemptDay(context);

        if (isLockedForToday(context)) {
            Toast.makeText(context,
                    "Guardian PIN locked for today. Try again tomorrow.",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        try {
            byte[] salt = Base64.decode(prefs(context).getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(prefs(context).getString(KEY_HASH, ""), Base64.NO_WRAP);
            byte[] actual = derive(pin, salt);
            boolean ok = constantTimeEquals(expected, actual);

            if (ok) {
                prefs(context).edit()
                        .putString(KEY_ATTEMPT_DAY, todayKey())
                        .putInt(KEY_FAILED_ATTEMPTS, 0)
                        .apply();
                return true;
            }

            int failed = prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
            prefs(context).edit()
                    .putString(KEY_ATTEMPT_DAY, todayKey())
                    .putInt(KEY_FAILED_ATTEMPTS, failed)
                    .apply();

            int remaining = Math.max(0, MAX_FAILED_ATTEMPTS_PER_DAY - failed);
            if (remaining == 0) {
                Toast.makeText(context,
                        "3 wrong PIN attempts. Guardian PIN is locked until tomorrow.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context,
                        remaining + (remaining == 1 ? " attempt" : " attempts") + " remaining today",
                        Toast.LENGTH_SHORT).show();
            }
            return false;
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
