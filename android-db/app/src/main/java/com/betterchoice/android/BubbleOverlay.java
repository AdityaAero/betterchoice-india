package com.betterchoice.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

/** Only the 56dp bubble intercepts touches. All other app controls remain usable. */
final class BubbleOverlay {
    interface TapListener { void onTap(int x, int y, int size); }

    private final Context context;
    private final WindowManager manager;
    private final SharedPreferences preferences;
    private BubbleView view;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int originX, originY;
    private boolean dragged;
    private boolean touchRejected;
    private Toast statusToast;
    private TapListener tapListener;
    private BubbleStyle.Look look = BubbleStyle.neutral(null, false);

    BubbleOverlay(Context context) {
        this(context, context.getSystemService(WindowManager.class));
    }
    BubbleOverlay(Context context, WindowManager manager) {
        this.context = context;
        this.manager = manager;
        preferences = context.getSharedPreferences("bubble_position", Context.MODE_PRIVATE);
    }

    void setTapListener(TapListener listener) { tapListener = listener; }

    /** Updates colour and label without re-attaching the window. */
    void setLook(BubbleStyle.Look next) {
        look = next;
        if (view != null) {
            view.setContentDescription(description(next));
            view.invalidate();
        }
    }

    BubbleStyle.Look look() { return look; }

    // Absolute screen coordinates are intentional: dragging must not reverse in RTL.
    @SuppressLint({"RtlHardcoded", "ClickableViewAccessibility"})
    void show(long eventTime) {
        if (view != null) return;
        int size = Math.round(56 * context.getResources().getDisplayMetrics().density);
        params = new WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        try {
            params.x = preferences.getInt("x", 16);
            params.y = preferences.getInt("y", 240);
        } catch (ClassCastException corruptedPosition) {
            params.x = 16; params.y = 240;
        }
        clamp();
        BubbleView candidate = new BubbleView(context);
        candidate.setContentDescription(description(look));
        candidate.setOnClickListener(v -> {
            if (view == null) return;
            if (tapListener != null) { tapListener.onTap(params.x, params.y, params.width); return; }
            if (statusToast != null) statusToast.cancel();
            statusToast = Toast.makeText(context, R.string.bubble_status, Toast.LENGTH_SHORT);
            statusToast.show();
        });
        candidate.setOnTouchListener((v, event) -> {
            if (view == null) return false;
            if (SafeTouches.isObscured(event.getFlags())) { touchRejected = true; return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchRejected = false;
            if (touchRejected) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    originX = params.x; originY = params.y; dragged = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    dragged |= Math.hypot(dx, dy) > ViewConfiguration.get(context).getScaledTouchSlop();
                    if (dragged && view != null) {
                        params.x = originX + Math.round(dx); params.y = originY + Math.round(dy);
                        clamp();
                        try { manager.updateViewLayout(view, params); }
                        catch (RuntimeException detached) { DebugLog.failure("Bubble move unavailable"); hide(); }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragged) v.performClick();
                    else preferences.edit().putInt("x", params.x).putInt("y", params.y).apply();
                    return true;
                case MotionEvent.ACTION_CANCEL: touchRejected = true; return true;
                default: return false;
            }
        });
        try {
            manager.addView(candidate, params);
            view = candidate;
            candidate.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener() {
                private boolean recorded;
                @Override public void onDraw() {
                    if (recorded) return;
                    recorded = true;
                    if (BuildConfig.DEBUG && eventTime > 0) DebugLog.event("bubble_first_draw event_to_draw_ms=" +
                        (SystemClock.uptimeMillis() - eventTime));
                }
            });
        } catch (RuntimeException error) {
            // A failed add may have attached the view before throwing. Remove that exact view.
            try { manager.removeViewImmediate(candidate); }
            catch (RuntimeException detached) { /* Nothing left attached. */ }
            view = null;
            DebugLog.failure("Unable to attach bubble");
        }
    }
    private void clamp() {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        params.x = Math.max(0, Math.min(params.x, metrics.widthPixels - params.width));
        params.y = Math.max(0, Math.min(params.y, metrics.heightPixels - params.height - 80));
    }
    boolean isShowing() { return view != null; }
    int x() { return params == null ? 0 : params.x; }
    int y() { return params == null ? 0 : params.y; }
    int size() { return params == null ? 0 : params.width; }
    void hide() {
        if (statusToast != null) { statusToast.cancel(); statusToast = null; }
        if (view == null) return;
        try { manager.removeViewImmediate(view); }
        catch (RuntimeException detached) { DebugLog.failure("Bubble removal unavailable"); }
        finally { view = null; }
        DebugLog.event("bubble_hidden");
    }

    private static String description(BubbleStyle.Look look) {
        if (look.band == null) return "BetterChoice DB bubble. No food product rated. Drag to move, tap for details.";
        return "BetterChoice rating " + look.band.name() + ": " + BubbleStyle.meaning(look.band) + ". Tap for reasons.";
    }

    /** Circle with the label and a small band of three dots: green, yellow, red. The active grade is solid. */
    private final class BubbleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BubbleView(Context context) { super(context); }
        @Override public boolean performClick() { return super.performClick(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), density = getResources().getDisplayMetrics().density;
            float radius = Math.min(w, h) / 2f - density;
            paint.setStyle(Paint.Style.FILL); paint.setColor(look.fill);
            canvas.drawCircle(w / 2f, h / 2f, radius, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f * density); paint.setColor(look.stroke);
            canvas.drawCircle(w / 2f, h / 2f, radius, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(look.textColor);
            paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize((look.band == null ? 17 : 22) * density);
            canvas.drawText(look.label, w / 2f, h / 2f + 3 * density, paint);
            NutriGrade.Band[] bands = NutriGrade.Band.values();
            float dot = 3.2f * density, gap = 9 * density, y = h / 2f + 14 * density;
            for (int i = 0; i < bands.length; i++) {
                int alpha = look.band == null ? 0x70 : look.band == bands[i] ? 0xFF : 0x55;
                paint.setColor(BubbleStyle.argb(alpha, BubbleStyle.bandColor(bands[i])));
                canvas.drawCircle(w / 2f + (i - 1) * gap, y, look.band == bands[i] ? dot * 1.25f : dot, paint);
            }
        }
    }
}
