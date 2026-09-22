package com.example.screentimeguard;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Closes webpages rendered inside Messenger while Screen Time Guard restricted mode is active.
 *
 * Browser packages are already excluded from the Lock Task allowlist, but Messenger can host a
 * webpage in its own WebView. Because that WebView still belongs to com.facebook.orca, package
 * allowlisting alone cannot distinguish it from normal chat UI.
 */
public class MessengerLinkGuardService extends AccessibilityService {
    private static final String MESSENGER_PACKAGE = "com.facebook.orca";
    private static final long ACTION_COOLDOWN_MS = 900L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBlockAt = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !shouldEnforce()) return;

        CharSequence packageName = event.getPackageName();
        if (packageName == null || !MESSENGER_PACKAGE.contentEquals(packageName)) return;

        boolean browserLikeEvent = looksLikeBrowserClass(event.getClassName());
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean hasWebView = containsWebView(root);

        if (!browserLikeEvent && !hasWebView) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastBlockAt < ACTION_COOLDOWN_MS) return;
        lastBlockAt = now;

        // First try Back so the user returns naturally to the Messenger conversation.
        performGlobalAction(GLOBAL_ACTION_BACK);

        // Messenger Browser Lite can occasionally keep the WebView alive for one more frame.
        // If that happens, issue one more Back rather than closing Messenger itself.
        handler.postDelayed(() -> {
            if (!shouldEnforce()) return;
            AccessibilityNodeInfo current = getRootInActiveWindow();
            if (containsWebView(current)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }, 300L);
    }

    private boolean shouldEnforce() {
        return Prefs.isEnabled(this)
                && !Prefs.isOverrideActive(this)
                && PolicyUtils.isRestrictedModeActive(this);
    }

    private static boolean looksLikeBrowserClass(CharSequence className) {
        if (className == null) return false;
        String value = className.toString().toLowerCase();
        return value.contains("browserlite")
                || value.contains("browser_lite")
                || value.contains("webview");
    }

    private static boolean containsWebView(AccessibilityNodeInfo root) {
        if (root == null) return false;

        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int inspected = 0;

        while (!queue.isEmpty() && inspected < 400) {
            AccessibilityNodeInfo node = queue.removeFirst();
            inspected++;

            CharSequence className = node.getClassName();
            if (className != null) {
                String value = className.toString().toLowerCase();
                if (value.equals("android.webkit.webview")
                        || value.endsWith(".webview")
                        || value.contains("browserlitewebview")) {
                    return true;
                }
            }

            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
