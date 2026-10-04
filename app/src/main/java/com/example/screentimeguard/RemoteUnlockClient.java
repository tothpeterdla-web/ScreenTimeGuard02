package com.example.screentimeguard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteUnlockClient {
    public interface Callback {
        void onResult(boolean ok, String message);
    }

    private static final AtomicBoolean POLL_IN_FLIGHT = new AtomicBoolean(false);
    private static final String PIN_PROOF_PREFIX = "stg-remote-pin-v1|";

    private RemoteUnlockClient() {}

    public static void poll(Context context) {
        Context app = context.getApplicationContext();
        if (!RemoteUnlockConfig.isEnabled(app)) return;
        if (!POLL_IN_FLIGHT.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                String base = RemoteUnlockConfig.getBaseUrl(app);
                String deviceId = RemoteUnlockConfig.getDeviceId(app);
                HttpURLConnection c = connection(
                        base + "/v1/command?deviceId=" + java.net.URLEncoder.encode(deviceId, "UTF-8"),
                        "GET", RemoteUnlockConfig.getSecret(app));
                int code = c.getResponseCode();
                if (code == 204) return;
                if (code != 200) return;

                JSONObject command = new JSONObject(readBody(c));
                String id = command.optString("id", "");
                if (id.isEmpty()) return;

                if (id.equals(RemoteUnlockConfig.getLastCommandId(app))) {
                    acknowledge(app, id);
                    return;
                }

                long expiresAt = command.optLong("expiresAt", 0L);
                if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) return;

                String action = command.optString("action", "");
                String toast;
                boolean clearRestrictedMode = false;

                if ("unlock_for_minutes".equals(action)) {
                    int minutes = command.optInt("minutes", 0);
                    if (minutes != 15 && minutes != 30 && minutes != 60) return;
                    Prefs.unlockForMinutes(app, minutes);
                    clearRestrictedMode = true;
                    toast = "Parent remotely unlocked for " + minutes + " minutes";
                } else if ("unlock_until_midnight".equals(action)) {
                    Prefs.unlockUntilMidnight(app);
                    clearRestrictedMode = true;
                    toast = "Parent remotely unlocked until midnight";
                } else if ("allow_app_installs_15".equals(action)) {
                    Prefs.allowAppInstallsForMinutes(app, 15);
                    PolicyUtils.applyInstallProtection(app);
                    toast = "Parent allowed app installations for 15 minutes";
                } else {
                    return;
                }

                RemoteUnlockConfig.setLastCommandId(app, id);
                if (clearRestrictedMode) PolicyUtils.clearRestrictedMode(app);
                acknowledge(app, id);

                String finalToast = toast;
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(app, finalToast, Toast.LENGTH_LONG).show());
            } catch (Exception ignored) {
                // Remote control is fail-closed: a network error never unlocks the device.
            } finally {
                POLL_IN_FLIGHT.set(false);
            }
        }, "remote-unlock-poll").start();
    }

    public static void registerAsync(Context context, String guardianPin, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            String message;
            try {
                RemoteUnlockConfig.ensureCredentials(app);
                String secret = RemoteUnlockConfig.getSecret(app);
                JSONObject body = new JSONObject();
                body.put("deviceId", RemoteUnlockConfig.getDeviceId(app));
                body.put("guardianProof", guardianProof(secret, guardianPin));
                HttpURLConnection c = connection(
                        RemoteUnlockConfig.getBaseUrl(app) + "/v1/register",
                        "POST", secret);
                writeJson(c, body);
                int code = c.getResponseCode();
                ok = code == 200 || code == 201;
                if (ok) {
                    message = "Remote unlock is ready";
                } else if (code == 409) {
                    message = "Remote PIN changed. Revoke the old pairing, then enable it again.";
                } else {
                    message = "Relay rejected registration (" + code + ")";
                }
            } catch (Exception e) {
                message = "Could not reach the remote unlock relay";
            }
            boolean result = ok;
            String resultMessage = message;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result, resultMessage));
        }, "remote-unlock-register").start();
    }

    public static void revokeAsync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            String message;
            try {
                JSONObject body = new JSONObject();
                body.put("deviceId", RemoteUnlockConfig.getDeviceId(app));
                HttpURLConnection c = connection(
                        RemoteUnlockConfig.getBaseUrl(app) + "/v1/revoke",
                        "POST", RemoteUnlockConfig.getSecret(app));
                writeJson(c, body);
                int code = c.getResponseCode();
                ok = code == 200;
                message = ok ? "Remote pairing revoked" : "Local remote access disabled; relay revoke failed";
            } catch (Exception e) {
                message = "Local remote access disabled; relay could not be reached";
            }
            boolean result = ok;
            String resultMessage = message;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result, resultMessage));
        }, "remote-unlock-revoke").start();
    }

    private static String guardianProof(String secret, String pin) throws Exception {
        String input = PIN_PROOF_PREFIX + secret + "|" + pin;
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private static void acknowledge(Context context, String commandId) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", RemoteUnlockConfig.getDeviceId(context));
            body.put("id", commandId);
            HttpURLConnection c = connection(
                    RemoteUnlockConfig.getBaseUrl(context) + "/v1/ack",
                    "POST", RemoteUnlockConfig.getSecret(context));
            writeJson(c, body);
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {}
    }

    private static HttpURLConnection connection(String url, String method, String secret) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setUseCaches(false);
        c.setRequestProperty("Authorization", "Bearer " + secret);
        c.setRequestProperty("Accept", "application/json");
        if ("POST".equals(method)) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        return c;
    }

    private static void writeJson(HttpURLConnection c, JSONObject body) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(bytes);
        }
    }

    private static String readBody(HttpURLConnection c) throws Exception {
        InputStream in = c.getInputStream();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) b.append(line);
            return b.toString();
        }
    }
}
