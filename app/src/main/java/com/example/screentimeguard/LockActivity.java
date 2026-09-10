package com.example.screentimeguard;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

public class LockActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View blank = new View(this);
        setContentView(blank);
        engageAndGoHome();
    }

    @Override protected void onResume() {
        super.onResume();
        engageAndGoHome();
    }

    private void engageAndGoHome() {
        PolicyUtils.prepareRestrictedLockTask(this);
        try {
            DevicePolicyManager d = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (d != null && d.isLockTaskPermitted(getPackageName())
                    && !PolicyUtils.isRestrictedModeActive(this)) {
                startLockTask();
            }
        } catch (Exception ignored) {}

        // Keep this task alive in the background, but show the normal launcher.
        // From there Android itself refuses non-allowlisted apps with "App is not available".
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            try { PolicyUtils.openHome(this); } catch (Exception ignored) {}
        }, 150L);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        try { PolicyUtils.openHome(this); } catch (Exception ignored) {}
    }
}
