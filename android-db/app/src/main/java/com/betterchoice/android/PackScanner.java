package com.betterchoice.android;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.hardware.HardwareBuffer;
import android.view.Display;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the pack photo showing on screen: one screenshot of the shopping app, then on-device text
 * recognition and barcode scanning, then the image is discarded. Only ever called on a product page
 * of a supported app, never on payment screens, and never more often than the interval below.
 *
 * <p>The picture is of the shopping app's window, not the whole phone screen. On Android 14 and
 * later the system captures that one window, so the status bar, a message notification that drops
 * down, another app in split screen, and BetterChoice's own bubble are simply not in it. Android 12
 * and 13 can only capture the whole display, so the picture is cut down to the window's area before
 * anything reads it, and a capture whose window area is unknown is not used at all.
 */
final class PackScanner implements AutoCloseable {
    private static final long MIN_INTERVAL_MS = 1200;
    private final AccessibilityService service;
    private final Executor executor;
    private TextRecognizer recognizer;
    private BarcodeScanner barcodes;
    private long lastCapture;
    /** DEBUG ONLY: the photo text last logged, so the same photo is not logged twice. */
    private String lastLoggedText;

    PackScanner(AccessibilityService service, Executor executor) {
        this.service = service;
        this.executor = executor;
    }

    /**
     * Blocking; call from the analysis thread only. `force` ignores the interval for a user tap.
     *
     * @param windowId the verified shopping window, as the accessibility framework numbers it
     * @param bounds   that window's area on screen, used to cut down a whole-display capture
     */
    PackScan scan(boolean force, int windowId, Rect bounds) {
        if (windowId < 0 || bounds == null) return PackScan.EMPTY;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && now - lastCapture < MIN_INTERVAL_MS) return PackScan.EMPTY;
        lastCapture = now;
        Bitmap bitmap = capture(windowId, bounds, false);
        if (bitmap == null) return PackScan.EMPTY;
        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            String gtin = barcode(image);
            String text = text(image);
            if (Build.VERSION.SDK_INT >= 34 && (text == null || text.trim().length() < 25)) {
                // A window capture leaves out anything drawn on its own surface. Flipkart's full-screen
                // photo viewer is one: its capture held only the close button, "X", so a zoomed
                // nutrition table was never read. The whole display, cut to the shop's window, has it.
                Bitmap display = capture(windowId, bounds, true);
                if (display != null) {
                    InputImage whole = InputImage.fromBitmap(display, 0);
                    String wholeText = text(whole);
                    if (wholeText != null && (text == null || wholeText.trim().length() > text.trim().length())) {
                        text = wholeText;
                        if (gtin == null) gtin = barcode(whole);
                        bitmap.recycle();
                        bitmap = display;
                    } else {
                        display.recycle();
                    }
                }
            }
            DebugLog.diagnostic("scan " + bitmap.getWidth() + "x" + bitmap.getHeight()
                + " text=" + (text == null ? "none" : text.length() + " chars")
                + " barcode=" + (gtin == null ? "none" : "found"));
            // DEBUG BUILDS ONLY, for the accuracy audit: what was read from the photo, so a missed
            // panel can be told apart from a misread one. Guarded here so release builds carry none of it.
            if (BuildConfig.DEBUG && text != null && !text.equals(lastLoggedText)) {
                lastLoggedText = text;
                DebugLog.diagnostic("scan text: " + text.substring(0, Math.min(700, text.length())).replace('\n', '|'));
            }
            return new PackScan(text, gtin);
        } finally {
            bitmap.recycle();
        }
    }

    private static final java.util.regex.Pattern PANEL_WORD = java.util.regex.Pattern.compile(
        "(?i)nutrition|energy|protein|carbohydrate|sugar|fat|sodium|ingredient");

    /**
     * DEBUG BUILDS ONLY: where on screen the nutrition or ingredient lines are, so an accuracy run
     * can zoom into that part of the photo the way a shopper would.
     */
    private static void logPanelBox(java.util.List<OcrRows.Fragment> fragments) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = 0, bottom = 0, lines = 0;
        for (OcrRows.Fragment f : fragments) {
            if (!PANEL_WORD.matcher(f.text).find()) continue;
            lines++;
            left = Math.min(left, f.left); top = Math.min(top, f.top);
            right = Math.max(right, f.right); bottom = Math.max(bottom, f.bottom);
        }
        if (lines >= 3) DebugLog.diagnostic("scan panel box: " + left + "," + top + "," + right + "," + bottom + " lines=" + lines);
    }

    private String text(InputImage image) {
        try {
            if (recognizer == null) recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text result = Tasks.await(recognizer.process(image), 5, TimeUnit.SECONDS);
            java.util.ArrayList<OcrRows.Fragment> fragments = new java.util.ArrayList<>();
            for (Text.TextBlock block : result.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    Rect box = line.getBoundingBox();
                    if (box != null) fragments.add(new OcrRows.Fragment(line.getText(), box.left, box.top, box.right, box.bottom));
                }
            }
            if (BuildConfig.DEBUG) logPanelBox(fragments);
            return fragments.isEmpty() ? result.getText() : OcrRows.assemble(fragments);
        } catch (Exception | LinkageError unavailable) {
            DebugLog.diagnostic("text recognition failed: " + unavailable.getClass().getSimpleName()
                + " " + String.valueOf(unavailable.getMessage()));
            return null;
        }
    }

    /**
     * The product number on the pack: the striped barcode, or a QR code that carries a GS1 Digital
     * Link. A QR that is only a link to the brand's Instagram or website holds no nutrition data and
     * is ignored — the app never opens an address printed on packaging.
     */
    private String barcode(InputImage image) {
        try {
            if (barcodes == null) {
                barcodes = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_ITF,
                        Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
                    .build());
            }
            List<Barcode> found = Tasks.await(barcodes.process(image), 5, TimeUnit.SECONDS);
            String qrSeen = null;
            for (Barcode code : found) {
                String raw = code.getRawValue();
                if (code.getFormat() == Barcode.FORMAT_QR_CODE || code.getFormat() == Barcode.FORMAT_DATA_MATRIX) {
                    String linked = Gtin.fromDigitalLink(raw);
                    if (linked != null) return linked;
                    qrSeen = "qr";
                    continue;
                }
                String value = Gtin.normalize(raw);
                if (value != null) return value;
            }
            if (qrSeen != null) DebugLog.diagnostic("qr found but it is a link, not a product number");
            return null;
        } catch (Exception | LinkageError unavailable) {
            DebugLog.diagnostic("barcode scan failed: " + unavailable.getClass().getSimpleName());
            return null;
        }
    }

    /** @param wholeDisplay capture the display and cut it to the window, even where a window capture exists */
    private Bitmap capture(int windowId, Rect bounds, boolean wholeDisplay) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Bitmap> result = new AtomicReference<>();
        AtomicInteger failure = new AtomicInteger(-1);
        boolean windowOnly = Build.VERSION.SDK_INT >= 34 && !wholeDisplay;
        try {
            AccessibilityService.TakeScreenshotCallback callback = new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                    HardwareBuffer buffer = screenshot.getHardwareBuffer();
                    try {
                        Bitmap wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                        if (wrapped != null) {
                            result.set(wrapped.copy(Bitmap.Config.ARGB_8888, false));
                            wrapped.recycle();
                        } else {
                            DebugLog.diagnostic("screenshot buffer could not be read");
                        }
                    } catch (RuntimeException unreadable) {
                        DebugLog.diagnostic("screenshot copy failed: " + unreadable.getClass().getSimpleName());
                    } finally {
                        buffer.close();
                        done.countDown();
                    }
                }
                @Override public void onFailure(int errorCode) {
                    failure.set(errorCode);
                    done.countDown();
                }
            };
            if (windowOnly) service.takeScreenshotOfWindow(windowId, executor, callback);
            else service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback);
            if (!done.await(3, TimeUnit.SECONDS)) DebugLog.diagnostic("screenshot timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException unavailable) {
            DebugLog.diagnostic("takeScreenshot threw " + unavailable.getClass().getSimpleName());
        }
        if (result.get() == null && failure.get() >= 0) DebugLog.diagnostic("screenshot error code " + failure.get());
        Bitmap picture = result.get();
        if (picture == null || windowOnly) return picture;
        return cropToWindow(picture, bounds);
    }

    /** Cuts a whole-display capture down to the shopping window. Null, and nothing kept, if it can't. */
    static Bitmap cropToWindow(Bitmap full, Rect bounds) {
        Rect area = cropArea(full.getWidth(), full.getHeight(), bounds);
        if (area == null) { full.recycle(); return null; }
        if (area.left == 0 && area.top == 0 && area.width() == full.getWidth() && area.height() == full.getHeight()) return full;
        Bitmap cropped = Bitmap.createBitmap(full, area.left, area.top, area.width(), area.height());
        if (cropped != full) full.recycle();
        return cropped;
    }

    /** The part of a width x height capture that the window covers, or null if it covers none. */
    static Rect cropArea(int width, int height, Rect bounds) {
        if (bounds == null || width <= 0 || height <= 0) return null;
        Rect area = new Rect(bounds);
        if (!area.intersect(0, 0, width, height) || area.isEmpty()) return null;
        return area;
    }

    @Override public void close() {
        if (recognizer != null) { try { recognizer.close(); } catch (RuntimeException ignored) { /* released */ } recognizer = null; }
        if (barcodes != null) { try { barcodes.close(); } catch (RuntimeException ignored) { /* released */ } barcodes = null; }
    }
}
