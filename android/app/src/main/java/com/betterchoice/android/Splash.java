package com.betterchoice.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Two-second opening screen: the BetterChoice India first-page artwork. */
final class Splash {
    static final long DURATION_MS = 2000;
    static final String TAGLINE = "Smarter choices, healthier India";

    static View view(Context c) {
        View art = new Artwork(c);
        art.setContentDescription("BetterChoice India. " + TAGLINE);
        // Gentle fade so the screen feels deliberate rather than a loading delay.
        art.setAlpha(0f);
        art.animate().alpha(1f).setDuration(320).start();
        return art;
    }

    /**
     * Draws the artwork across the full width with its bottom edge on the bottom of the screen.
     *
     * <p>Phones are taller than the artwork, and the obvious choice, centre-crop, scales to fill and
     * trims the sides: the wordmark reaches to within a tenth of the artwork's width, so on a 20:9
     * screen it loses its first and last letters. Matching the width instead means nothing is ever
     * trimmed sideways. The wash above is continued from the artwork's own top row, so the saffron
     * ribbon runs off the top corner on a tall screen instead of ending at a seam.
     */
    private static final class Artwork extends View {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Rect source = new Rect();
        private final Rect target = new Rect();

        Artwork(Context c) {
            super(c);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Two seconds of gradient; half the memory.
            bitmap = BitmapFactory.decodeResource(c.getResources(), R.drawable.splash_first_page, options);
            setBackgroundColor(UiKit.paper(c));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            int w = getWidth(), h = getHeight();
            float scale = (float) w / bitmap.getWidth();
            int drawn = Math.round(bitmap.getHeight() * scale);
            int top = h - drawn;          // negative on a short screen: the plain top wash is cropped

            if (top > 0) {
                // Continue the artwork's top row upwards to fill the gap on a tall screen.
                source.set(0, 0, bitmap.getWidth(), 1);
                target.set(0, 0, w, top + 1);
                canvas.drawBitmap(bitmap, source, target, paint);
            }
            source.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            target.set(0, top, w, top + drawn);
            canvas.drawBitmap(bitmap, source, target, paint);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private Splash() {}
}
