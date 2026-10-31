package com.example.screentimeguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.GetChars;
import android.text.InputType;
import android.text.method.TransformationMethod;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

/** A shared, deliberately small design system for every Screen Time Guard screen. */
public final class UiKit {
    private UiKit() {}

    public static final int BG = Color.rgb(5, 9, 15);
    public static final int CARD = Color.argb(220, 20, 34, 51);
    public static final int CARD_SOFT = Color.argb(210, 24, 41, 61);
    public static final int BORDER = Color.argb(120, 122, 168, 207);
    public static final int TEXT = Color.rgb(239, 246, 255);
    public static final int MUTED = Color.rgb(155, 177, 201);
    public static final int ACCENT = Color.rgb(36, 184, 255);
    public static final int GOOD = Color.rgb(73, 231, 140);
    public static final int DANGER = Color.rgb(255, 103, 120);

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static void styleWindow(Activity a) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
    }

    /** A scrollable secondary page using the same spacing, header and colors as home. */
    public static LinearLayout page(Activity activity, String title, String subtitle) {
        styleWindow(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 18), dp(activity, 18),
                dp(activity, 18), dp(activity, 30));
        root.setBackgroundColor(BG);

        TextView brand = text(activity, "SCREEN TIME GUARD", 11, ACCENT, true);
        brand.setLetterSpacing(0.14f);
        root.addView(brand);
        root.addView(text(activity, title, 27, TEXT, true),
                margins(activity, 0, 8, 0, 0));
        root.addView(text(activity, subtitle, 14, MUTED, false),
                margins(activity, 0, 5, 0, 18));

        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        activity.setContentView(scroll);
        return root;
    }

    public static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 18), dp(c, 17), dp(c, 18), dp(c, 17));
        card.setBackground(glass(c, CARD, BORDER));
        return card;
    }

    public static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    public static GradientDrawable glass(Context c, int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(c, 22));
        shape.setStroke(dp(c, 1), stroke);
        return shape;
    }

    public static Button button(Context c, String label, int color) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(color == DANGER ? DANGER : TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        int stroke = color == DANGER ? DANGER : color == ACCENT ? BORDER : BORDER;
        b.setBackground(glass(c, CARD_SOFT, stroke));
        return b;
    }

    public static LinearLayout.LayoutParams margins(Context c, int left, int top,
                                                     int right, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, left), dp(c, top), dp(c, right), dp(c, bottom));
        return lp;
    }

    public static LinearLayout.LayoutParams buttonMargins(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 58));
        lp.setMargins(0, dp(c, 5), 0, dp(c, 5));
        return lp;
    }

    public static EditText formField(Context c, String hint) {
        EditText field = new EditText(c);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
        field.setTextSize(16);
        field.setPadding(dp(c, 13), dp(c, 10), dp(c, 13), dp(c, 10));
        field.setBackground(glass(c, CARD_SOFT, BORDER));
        return field;
    }

    /** Never render a typed digit, even briefly, regardless of Show Password settings. */
    public static EditText pinField(Context c, String hint) {
        EditText field = formField(c, hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setTransformationMethod(DotMask.INSTANCE);
        field.setTextSize(18);
        field.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        return field;
    }

    /** The one PIN authorization dialog used on all application screens. */
    public static void requireGuardianPin(Activity a, Runnable grantedAction) {
        if (!PinStore.hasPin(a)) {
            new AlertDialog.Builder(a).setTitle("Guardian PIN required")
                    .setMessage("Set a guardian PIN first.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (PinStore.isLockedForToday(a)) {
            Toast.makeText(a, "Guardian PIN locked for today. Try again tomorrow.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        EditText pin = pinField(a, "Guardian PIN");
        LinearLayout holder = new LinearLayout(a);
        holder.setPadding(dp(a, 18), dp(a, 6), dp(a, 18), dp(a, 4));
        holder.addView(pin, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 54)));

        new AlertDialog.Builder(a)
                .setTitle("Guardian authorization")
                .setView(holder)
                .setPositiveButton("Continue", (dialog, which) -> {
                    if (PinStore.verify(a, pin.getText().toString())) {
                        grantedAction.run();
                    } else if (!PinStore.isLockedForToday(a)) {
                        Toast.makeText(a, "Wrong PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Masked CharSequence always returns bullets, including in bulk draw operations. */
    private static final class DotMask implements TransformationMethod {
        static final DotMask INSTANCE = new DotMask();
        @Override public CharSequence getTransformation(CharSequence source, View view) {
            return source == null ? null : new Dots(source);
        }
        @Override public void onFocusChanged(View view, CharSequence source,
                boolean focused, int direction, Rect previouslyFocusedRect) {
            // No temporary visible-digit spans, even while the input has focus.
        }
    }

    private static final class Dots implements CharSequence, GetChars {
        private final CharSequence source;
        Dots(CharSequence source) { this.source = source; }
        @Override public int length() { return source.length(); }
        @Override public char charAt(int index) {
            if (index < 0 || index >= length()) throw new IndexOutOfBoundsException();
            return '\u2022';
        }
        @Override public CharSequence subSequence(int start, int end) {
            if (start < 0 || end < start || end > length()) throw new IndexOutOfBoundsException();
            char[] dots = new char[end - start];
            Arrays.fill(dots, '\u2022');
            return new String(dots);
        }
        @Override public void getChars(int start, int end, char[] dest, int destoff) {
            if (start < 0 || end < start || end > length()) throw new IndexOutOfBoundsException();
            Arrays.fill(dest, destoff, destoff + (end - start), '\u2022');
        }
        @Override public String toString() { return subSequence(0, length()).toString(); }
    }
}
