package com.example.screentimeguard;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;

public final class QuickControls {
    private QuickControls() {}

    public static boolean isWifiEnabled(Context c) {
        try {
            WifiManager wm = (WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean setWifiEnabled(Context c, boolean enabled) {
        try {
            WifiManager wm = (WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.setWifiEnabled(enabled);
        } catch (Exception e) {
            return false;
        }
    }

    private static BluetoothAdapter bluetoothAdapter(Context c) {
        try {
            BluetoothManager bm = (BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
            return bm == null ? null : bm.getAdapter();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasBluetoothPermission(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isBluetoothEnabled(Context c) {
        if (!hasBluetoothPermission(c)) return false;
        try {
            BluetoothAdapter adapter = bluetoothAdapter(c);
            return adapter != null && adapter.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean setBluetoothEnabled(Context c, boolean enabled) {
        if (!hasBluetoothPermission(c) || !PolicyUtils.isDeviceOwner(c)) return false;
        try {
            BluetoothAdapter adapter = bluetoothAdapter(c);
            if (adapter == null) return false;
            return enabled ? adapter.enable() : adapter.disable();
        } catch (Exception e) {
            return false;
        }
    }

    public static int getBrightness(Context c) {
        try {
            return Math.max(1, Math.min(255,
                    Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)));
        } catch (Exception e) {
            return 128;
        }
    }

    public static boolean setBrightness(Context c, int value) {
        if (!PolicyUtils.isDeviceOwner(c)) return false;
        try {
            int v = Math.max(1, Math.min(255, value));
            DevicePolicyManager d = PolicyUtils.dpm(c);
            if (d == null) return false;
            d.setSystemSetting(PolicyUtils.admin(c), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    String.valueOf(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
            d.setSystemSetting(PolicyUtils.admin(c), Settings.System.SCREEN_BRIGHTNESS,
                    String.valueOf(v));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean canWriteRotation(Context c) {
        return Settings.System.canWrite(c);
    }

    public static boolean isAutoRotateEnabled(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean setAutoRotateEnabled(Context c, boolean enabled) {
        if (!Settings.System.canWrite(c)) return false;
        try {
            return Settings.System.putInt(c.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0);
        } catch (Exception e) {
            return false;
        }
    }

    public static Boolean isMobileDataEnabled(Context c) {
        try {
            TelephonyManager tm = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return null;
            return tm.isDataEnabled();
        } catch (Exception e) {
            return null;
        }
    }
}
