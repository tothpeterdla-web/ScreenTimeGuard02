package com.example.screentimeguard;

import android.graphics.Rect;
import android.text.method.TransformationMethod;
import android.view.View;

import java.util.Arrays;

/**
 * A PIN display transformation that never briefly exposes the last typed digit.
 *
 * Android's default password transformation can temporarily reveal the latest
 * character when the device's "show passwords" setting is enabled. This class
 * only changes the view of the input; EditText.getText() still contains the
 * genuine digits for existing guardian-PIN verification.
 */
public final class ImmediatePinMask implements TransformationMethod {
    private static final ImmediatePinMask INSTANCE = new ImmediatePinMask();
    private static final char DOT = '\u2022';

    private ImmediatePinMask() {}

    public static ImmediatePinMask getInstance() {
        return INSTANCE;
    }

    @Override
    public CharSequence getTransformation(CharSequence source, View view) {
        return source == null ? null : new MaskedText(source);
    }

    @Override
    public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                               int direction, Rect previouslyFocusedRect) {
        // Input characters are masked irrespective of focus.
    }

    private static final class MaskedText implements CharSequence {
        private final CharSequence original;

        MaskedText(CharSequence original) {
            this.original = original;
        }

        @Override
        public int length() {
            // Must mirror editable source dynamically while the user types.
            return original.length();
        }

        @Override
        public char charAt(int index) {
            if (index < 0 || index >= original.length()) {
                throw new IndexOutOfBoundsException("PIN display index " + index);
            }
            return DOT;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            if (start < 0 || end < start || end > original.length()) {
                throw new IndexOutOfBoundsException();
            }
            char[] masked = new char[end - start];
            Arrays.fill(masked, DOT);
            return new String(masked);
        }

        @Override
        public String toString() {
            char[] masked = new char[original.length()];
            Arrays.fill(masked, DOT);
            return new String(masked);
        }
    }
}
