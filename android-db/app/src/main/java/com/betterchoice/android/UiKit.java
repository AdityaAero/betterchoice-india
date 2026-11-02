package com.betterchoice.android;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared look of every screen. Colours are read from resources rather than held as constants, so
 * Android serves the dark palette (values-night) when the phone is in dark mode: the app follows the
 * device setting with no switch of its own and nothing stored. Views are built fresh on each render,
 * so a theme change picks up the new colours as soon as the screen is rebuilt.
 */
final class UiKit {
    /** Main text. */
    static int ink(Context c) { return c.getColor(R.color.bc_ink); }
    /** Secondary text: explanations, captions, table headings. */
    static int muted(Context c) { return c.getColor(R.color.bc_muted); }
    /** Window background. */
    static int paper(Context c) { return c.getColor(R.color.bc_paper); }
    /** Raised areas that sit on the background: cards, inputs, secondary buttons. */
    static int surface(Context c) { return c.getColor(R.color.bc_surface); }
    /** Hairline borders. */
    static int line(Context c) { return c.getColor(R.color.bc_line); }
    /** Brand accent and the fill of primary buttons. */
    static int green(Context c) { return c.getColor(R.color.bc_green); }
    /** Text and icons drawn on top of {@link #green}: white on light, near-black on dark. */
    static int onGreen(Context c) { return c.getColor(R.color.bc_on_green); }
    static int lime(Context c) { return c.getColor(R.color.bc_lime); }
    static int disabled(Context c) { return c.getColor(R.color.bc_disabled); }
    static int disabledText(Context c) { return c.getColor(R.color.bc_disabled_text); }
    /**
     * Text for what is good about a product. A clear leaf green, distinct from the dark brand ink,
     * and lightened in dark mode; both keep a contrast ratio above 4.5:1 on their card surface.
     */
    static int good(Context c) { return c.getColor(R.color.bc_good); }
    /** Test-build banners and other warnings. */
    static int warning(Context c) { return c.getColor(R.color.bc_warning); }
    /** Text colour for what counts against a product, readable in both themes. */
    static int bad(Context c) { return c.getColor(R.color.bc_bad); }

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable shape(Context c, int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (stroke != 0) d.setStroke(dp(c, 1), stroke); return d;
    }

    static TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(c, 3), 1); return t;
    }

    static Button button(Context c, String value, boolean primary) {
        Button b = new Button(c); b.setText(value); b.setAllCaps(false); b.setTextSize(16);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        // Disabled buttons turn grey so it is obvious they cannot be tapped (e.g. during the resend wait).
        b.setTextColor(new android.content.res.ColorStateList(
            new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
            new int[]{disabledText(c), primary ? onGreen(c) : ink(c)}));
        b.setMinHeight(dp(c, 54));
        b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, shape(c, disabled(c), 0, 18));
        background.addState(new int[]{}, shape(c, primary ? green(c) : surface(c), primary ? 0 : line(c), 18));
        b.setBackground(background);
        SafeTouches.protect(b); return b;
    }

    static EditText input(Context c, String hint, int inputType) {
        EditText e = new EditText(c); e.setTextSize(19); e.setTextColor(ink(c)); e.setHintTextColor(muted(c));
        e.setHint(hint); e.setInputType(inputType); e.setSingleLine(true);
        e.setPadding(dp(c, 17), dp(c, 17), dp(c, 17), dp(c, 17));
        e.setBackground(shape(c, surface(c), line(c), 16)); e.setSaveEnabled(false);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return e;
    }

    static void gap(LinearLayout parent, int height) { View v = new View(parent.getContext()); parent.addView(v, new LinearLayout.LayoutParams(1, dp(parent.getContext(), height))); }

    /** Welcome illustration. Every colour is resolved at draw time, so it repaints for dark mode too. */
    static final class WelcomeArt extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        WelcomeArt(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float w = getWidth(), h = getHeight();
            Context c = getContext();
            paint.setColor(lime(c)); canvas.drawRoundRect(0, 0, w, h, dp(c, 26), dp(c, 26), paint);
            paint.setColor(c.getColor(R.color.bc_lime_deep)); canvas.drawCircle(w * .86f, h * .2f, h * .53f, paint);
            paint.setColor(green(c)); canvas.drawRoundRect(w * .13f, h * .18f, w * .53f, h * .88f, 26, 26, paint);
            paint.setColor(paper(c)); canvas.drawRoundRect(w * .18f, h * .27f, w * .48f, h * .75f, 12, 12, paint);
            paint.setColor(c.getColor(R.color.bc_warm)); canvas.drawCircle(w * .28f, h * .48f, h * .095f, paint);
            paint.setColor(green(c)); canvas.drawOval(w * .34f, h * .37f, w * .43f, h * .57f, paint);
            paint.setColor(c.getColor(R.color.bc_sage)); canvas.drawRoundRect(w * .23f, h * .63f, w * .43f, h * .66f, 5, 5, paint);
            paint.setColor(surface(c)); canvas.drawCircle(w * .64f, h * .57f, h * .25f, paint);
            paint.setColor(green(c)); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD)); paint.setTextSize(h * .17f);
            canvas.drawText("BC", w * .64f, h * .63f, paint);
            paint.setColor(green(c)); canvas.drawCircle(w * .83f, h * .78f, h * .028f, paint);
        }
    }

    /** Dark text used on the yellow B band, which is too light for white text in either theme. */
    static final int ON_BAND_B = Color.rgb(58, 46, 0);

    private UiKit() {}
}
