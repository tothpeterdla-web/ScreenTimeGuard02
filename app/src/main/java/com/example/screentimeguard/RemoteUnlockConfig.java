package com.example.screentimeguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import java.security.SecureRandom;
import java.util.UUID;

public final class RemoteUnlockConfig {
    private static final String NAME = "remote_unlock";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_COMMAND = "last_command";

    private RemoteUnlockConfig() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getBaseUrl(Context context) {
        return prefs(context).getString(KEY_BASE_URL, "");
    }

    public static boolean setBaseUrl(Context context, String value) {
        String normalized = normalizeBaseUrl(value);
        if (normalized.isEmpty()) return false;
        prefs(context).edit().putString(KEY_BASE_URL, normalized).apply();
        return true;
    }

    public static String normalizeBaseUrl(String value) {
        if (value == null) return "";
        String v = value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        try {
            Uri uri = Uri.parse(v);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return "";
            return v;
        } catch (Exception e) {
            return "";
        }
    }

    public static void ensureCredentials(Context context) {
        SharedPreferences p = prefs(context);
        String device = p.getString(KEY_DEVICE_ID, "");
        String secret = p.getString(KEY_SECRET, "");
        if (device != null && !device.isEmpty() && secret != null && !secret.isEmpty()) return;

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String newSecret = Base64.encodeToString(random,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String newDevice = UUID.randomUUID().toString();
        p.edit()
                .putString(KEY_DEVICE_ID, newDevice)
                .putString(KEY_SECRET, newSecret)
                .remove(KEY_LAST_COMMAND)
                .apply();
    }

    public static void rotateCredentials(Context context) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        prefs(context).edit()
                .putString(KEY_DEVICE_ID, UUID.randomUUID().toString())
                .putString(KEY_SECRET, Base64.encodeToString(random,
                        Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING))
                .remove(KEY_LAST_COMMAND)
                .apply();
    }

    public static String getDeviceId(Context context) {
        ensureCredentials(context);
        return prefs(context).getString(KEY_DEVICE_ID, "");
    }

    public static String getSecret(Context context) {
        ensureCredentials(context);
        return prefs(context).getString(KEY_SECRET, "");
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false)
                && !getBaseUrl(context).isEmpty();
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String getLastCommandId(Context context) {
        return prefs(context).getString(KEY_LAST_COMMAND, "");
    }

    public static void setLastCommandId(Context context, String id) {
        prefs(context).edit().putString(KEY_LAST_COMMAND, id == null ? "" : id).apply();
    }

    public static String pairingUrl(Context context) {
        String base = getBaseUrl(context);
        if (base.isEmpty()) return "";
        return base + "/parent#device=" + Uri.encode(getDeviceId(context))
                + "&secret=" + Uri.encode(getSecret(context));
    }
}
