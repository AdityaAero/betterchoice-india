package com.betterchoice.android;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.LruCache;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs product analysis off the main thread with debouncing. Only the latest request for the
 * current window may update the bubble; skips and user details live in memory for this session.
 */
class ProductWatcher {
    interface Source {
        /** Visible text of the selected supported window, or null if it is no longer available. */
        List<ScreenText> screen();
        int screenHeight();
        /** One look at the pack photo on screen: its barcode and its text. */
        PackScan packScan(boolean userAsked);
        Nutrition databaseLookup(ProductPage page);
        Nutrition gtinLookup(String gtin);
        Nutrition openDataLookup(String gtin);
        /** Version 4: the BetterChoice database, the only source of product facts when present. */
        default CatalogueClient catalogue() { return null; }
    }
    interface Listener { void onResult(GradeResult result, int generation); }

    static final long DEBOUNCE_MS = 450;
    private static final long PHOTO_RETRY_MS = 2500;
    /**
     * Distinct photos read per window. Flipkart galleries run to eight or nine photos, with the
     * ingredient list and the barcode in the middle; at four, the ones opened first spent the budget
     * and the middle photos were never read. Re-reading the same photo costs nothing (see scanOnce).
     */
    private static final int PHOTO_ATTEMPTS = 8;
    /** The per-product capture budget refills after this long, so a late swipe is still read. */
    private static final long PHOTO_WINDOW_MS = 25_000;
    private static final long VIEWER_RELOOK_MS = 3_000;
    private static final long DATABASE_MISS_TTL_MS = 10 * 60 * 1000;

    private final Source source;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("bc-analysis");
    private final Handler worker;
    private final AtomicInteger generation = new AtomicInteger();
    private final Set<String> skipped = new HashSet<>();
    private final Map<String, Nutrition> userDetails = new HashMap<>();
    private final LruCache<String, Object> database = new LruCache<>(64);
    private final Map<String, Long> photoTried = new HashMap<>();
    private final Map<String, Integer> photoAttempts = new HashMap<>();
    /** When each product's current photo window began; the budget refills when it ends. */
    private final Map<String, Long> photoWindowStart = new HashMap<>();
    /** The text last read from a photo of each product, so the same photo is not paid for twice. */
    private final Map<String, String> lastPhotoText = new HashMap<>();
    private final Set<String> rated = new HashSet<>();
    private final Map<String, GradeResult> best = new HashMap<>();
    private final LruCache<String, LabelEvidence> labelEvidence = new LruCache<>(16);
    private final GuidancePolicy guidance = new GuidancePolicy();
    private final Map<String, String> barcodes = new HashMap<>();
    /**
     * Set when the user taps Scan. Deliberately not keyed by product: the page key is rebuilt from
     * what is on screen, and a price or title that renders differently by the time the scan runs
     * would not match, so the tap would be silently ignored. The user tapped for whatever is in
     * front of them now, so the very next scan is the forced one.
     */
    private volatile boolean forceNextScan;
    private final Object databaseMiss = new Object();
    private final Map<String, Long> missAt = new HashMap<>();
    private volatile String retailer;
    /**
     * The product this window was identified as. Kept while the shopper scrolls the same page, so a
     * description line or a neighbouring product's price is never mistaken for this product's.
     * Cleared when the window changes.
     */
    private volatile ProductPage anchor;
    /** The product left when a new window opened, kept briefly in case that window is its photo viewer. */
    private volatile ProductPage photosOf;
    private volatile long photosUntil;
    private static final long PHOTO_VIEWER_MS = 90_000;
    /** A healthier swap the shopper tapped, waiting for the shop's screen to show it. */
    private volatile SwapCheck.Pending pendingCheck;
    private volatile long resultsSeenAt;

    ProductWatcher(Source source, Listener listener) {
        this.source = source;
        this.listener = listener;
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    int generation() { return generation.get(); }

    /** A new window or app: cancel pending work so stale results can never recolour the bubble. */
    int reset(String retailerPackage) {
        retailer = retailerPackage;
        anchor = null;
        photosOf = null;
        resultsSeenAt = 0;
        worker.removeCallbacksAndMessages(null);
        return generation.incrementAndGet();
    }

    void schedule(long delayMs) {
        int expected = generation.get();
        worker.removeCallbacksAndMessages(null);
        worker.postDelayed(() -> run(expected), delayMs);
    }

    void skip(ProductPage page) { synchronized (skipped) { skipped.add(page.key()); } }

    /** The shop opened a new screen (Android reported a window-state change): identify it afresh. */
    int newScreen() {
        ProductPage last = anchor;
        if (last != null) {
            photosOf = last;
            photosUntil = android.os.SystemClock.elapsedRealtime() + PHOTO_VIEWER_MS;
        }
        anchor = null;
        worker.removeCallbacksAndMessages(null);
        return generation.incrementAndGet();
    }

    /** The shopper tapped a swap: watch the shop's next screens for it. */
    void startCheck(SwapCheck.Pending pending) { pendingCheck = pending; resultsSeenAt = 0; anchor = null; photosOf = null; }

    /** "Scan the pack" or "I can't find it": read whatever is on screen now, budget ignored. */
    void allowAnotherPhoto(ProductPage page) {
        forceNextScan = true;
        synchronized (photoTried) {
            photoTried.remove(page.key());
            photoAttempts.put(page.key(), 0);
        }
    }

    /** The barcode number seen for this product in this session, or null. */
    String barcodeFor(ProductPage page) {
        if (page == null) return null;
        synchronized (photoTried) { return barcodes.get(page.key()); }
    }

    void rememberUserDetails(ProductPage page, Nutrition nutrition) { synchronized (userDetails) { userDetails.put(page.key(), nutrition); } }

    /** Screen off or service stop: forget skips, ratings and user-entered details. */
    void forgetSession() {
        forceNextScan = false;
        anchor = null;
        photosOf = null;
        pendingCheck = null;
        synchronized (skipped) { skipped.clear(); }
        synchronized (userDetails) { userDetails.clear(); }
        synchronized (photoTried) {
            photoTried.clear(); photoAttempts.clear(); rated.clear(); best.clear();
            barcodes.clear();
            labelEvidence.evictAll();
        }
        guidance.clear();
        worker.post(() -> { database.evictAll(); missAt.clear(); });
    }

    void stop() {
        generation.incrementAndGet();
        worker.removeCallbacksAndMessages(null);
        thread.quitSafely();
    }

    private void run(int expected) {
        if (expected != generation.get() || retailer == null) return;
        List<ScreenText> screen = source.screen();
        if (screen == null || expected != generation.get()) return;
        final String currentRetailer = retailer;
        CatalogueClient catalogue = source.catalogue();
        boolean userAsked = forceNextScan;
        if (catalogue != null) forceNextScan = false;
        GradeResult result = catalogue != null
            ? ProductAnalyzer.fromCatalogue(screen, source.screenHeight(), currentRetailer, catalogue, new ProductAnalyzer.Memory() {
                @Override public boolean skipped(String key) { synchronized (skipped) { return skipped.contains(key); } }
                @Override public Nutrition userDetails(String key) { return null; }
                @Override public String evidence(String key, String text) { return text; }
                @Override public String nutritionText(String key, String text) { return text; }
            }, anchor, android.os.SystemClock.elapsedRealtime() < photosUntil ? photosOf : null, userAsked)
            : ProductAnalyzer.analyze(screen, source.screenHeight(), currentRetailer,
            this::scanOnce, database(), new ProductAnalyzer.Memory() {
                @Override public boolean skipped(String key) { synchronized (skipped) { return skipped.contains(key); } }
                @Override public Nutrition userDetails(String key) { synchronized (userDetails) { return userDetails.get(key); } }
                @Override public String evidence(String key, String text) {
                    synchronized (photoTried) {
                        LabelEvidence facts = labelEvidence.get(key);
                        if (facts == null) { facts = new LabelEvidence(); labelEvidence.put(key, facts); }
                        facts.observe(text);
                        return facts.text() + "\n" + (text == null ? "" : text);
                    }
                }
                @Override public String nutritionText(String key, String text) {
                    synchronized (photoTried) {
                        LabelEvidence facts = labelEvidence.get(key);
                        return text + "\n" + (facts == null ? "" : facts.panel());
                    }
                }
            }, anchor, android.os.SystemClock.elapsedRealtime() < photosUntil ? photosOf : null);
        if (expected != generation.get()) return;
        // A results page, a listing or anything else breaks the chain: the next product page is
        // identified from scratch, so an old name can never be carried onto a new product.
        anchor = result.page;
        if (checkSwap(screen, result, currentRetailer, expected)) return;
        GradeResult shown = result;
        synchronized (photoTried) {
            // Only a product page has a key; a listing or any other screen has no page at all.
            GradeResult earlier = result.page == null ? null : best.get(result.page.key());
            boolean fromLabel = earlier != null && (earlier.source == GradeResult.Source.PACK_PHOTO
                || earlier.source == GradeResult.Source.LABEL_TEXT);
            boolean fromRecord = result.source == GradeResult.Source.OPEN_FOOD_FACTS
                || result.source == GradeResult.Source.DATABASE || result.source == GradeResult.Source.BARCODE;
            if (result.state == GradeResult.State.GRADED && fromLabel && fromRecord) {
                // The pack's own label, read earlier, stands over a database record found later from
                // another photo's barcode (Bingo: its nutrition photo, then Open Food Facts).
                shown = earlier;
            } else if (result.state == GradeResult.State.GRADED && result.source == GradeResult.Source.PACK_PHOTO
                    && earlier != null && earlier.source == GradeResult.Source.LABEL_TEXT && !earlier.nutrition.ocrRepaired
                    && completeness(earlier.nutrition) >= completeness(result.nutrition)) {
                // The page's own text, read exactly, stands over a photo read by OCR that is no fuller. On ZOFF Soya
                // Chunks the photo's "SODIUM (g) 0.6" (600 mg, against the page's 108 mg and the photo's own %RDA
                // column) had turned a 52 g-protein food red.
                shown = earlier;
            } else if (result.state == GradeResult.State.GRADED && result.source == GradeResult.Source.PACK_PHOTO
                    && earlier != null && earlier.source == GradeResult.Source.PACK_PHOTO
                    && (completeness(earlier.nutrition) > completeness(result.nutrition)
                        || completeness(earlier.nutrition) == completeness(result.nutrition)
                            && !earlier.nutrition.ocrRepaired && result.nutrition.ocrRepaired)) {
                // A second, noisier reading of the pack ("FAT 195g", "SATURATED FAT9.8 q") must not
                // replace a fuller one. Photo panels are never merged; the fuller one stands.
                shown = GradeResult.graded(earlier.nutrition, earlier.source, result.page, earlier.gtin,
                    ProductAnalyzer.identity(result.page, earlier.labelText, earlier.nutrition,
                        Beverage.classify(earlier.page.name, earlier.page.text, earlier.nutrition.beverage)),
                    earlier.labelText + "\n" + (result.labelText == null ? "" : result.labelText));
                best.put(result.page.key(), shown);
            } else if (result.state == GradeResult.State.GRADED) {
                rated.add(result.page.key());
                best.put(result.page.key(), result);
                guidance.rated(result.page.key());
            } else if (result.state == GradeResult.State.NEEDS_INPUT && best.containsKey(result.page.key())) {
                // A photo rating found earlier stays valid after the photo is swiped away.
                GradeResult previous = best.get(result.page.key());
                LabelEvidence facts = labelEvidence.get(result.page.key());
                String evidence = previous.labelText == null ? "" : previous.labelText;
                String remembered = facts == null ? "" : facts.text();
                if (!remembered.isEmpty() && !evidence.contains(remembered)) evidence += "\n" + remembered;
                if (evidence.length() > 16000) evidence = evidence.substring(evidence.length() - 16000);
                shown = GradeResult.graded(previous.nutrition, previous.source, result.page, previous.gtin,
                    ProductAnalyzer.identity(result.page, evidence, previous.nutrition,
                        Beverage.classify(previous.page.name, previous.page.text, previous.nutrition.beverage)), evidence);
                best.put(result.page.key(), shown);
            } else if (result.state == GradeResult.State.NEEDS_INPUT && result.database != null) {
                // Version 4: there is no label to open. Say at once what the database answered.
                shown = result;
            } else if (result.state == GradeResult.State.NEEDS_INPUT) {
                // Give the page a moment to settle; only then ask the user to open the label.
                String key = result.page.key();
                long now = android.os.SystemClock.elapsedRealtime();
                switch (guidance.decide(key, now)) {
                    case WAIT:
                        shown = GradeResult.neutral(GradeResult.State.NOT_PRODUCT, result.page);
                        worker.postDelayed(() -> run(expected), guidance.waitRemainingMs(key, now) + 50);
                        break;
                    case ANNOUNCE: shown = GradeResult.needsLabel(result.page, true).withGtin(barcodes.get(key)); break;
                    default: shown = GradeResult.needsLabel(result.page, false).withGtin(barcodes.get(key)); break;
                }
                shown.labelText = result.labelText;
                shown.note = result.note;
            }
        }
        final GradeResult update = shown;
        audit(update);
        main.post(() -> listener.onResult(update, expected));
        // In a full-screen photo the shopper zooms and pans, and an image view announces none of it.
        // Look again shortly while the photo stays open; a new screen cancels this (generation).
        if (result.page != null && result.page.viewer) worker.postDelayed(() -> run(expected), VIEWER_RELOOK_MS);
        // Nutrition may appear later (photo swiped, "View details" opened): content events re-run analysis.
    }

    /** How many of the figures a reading has; used to keep the fuller of two photo readings. */
    static int completeness(Nutrition n) {
        if (n == null) return 0;
        int known = 0;
        for (Double v : new Double[] {n.energyKcal, n.sugarsG, n.saturatedFatG, n.sodiumMg, n.totalFatG,
                n.proteinG, n.fibreG, n.addedSugarsG}) if (v != null) known++;
        return known;
    }

    /**
     * DEBUG BUILDS ONLY: one line per answer, so an on-phone accuracy run can compare what the bubble
     * concluded with what the page said. Product facts only; nothing about the shopper.
     */
    private static void audit(GradeResult r) {
        if (!BuildConfig.DEBUG || r == null || r.page == null) return;
        if (r.state != GradeResult.State.GRADED) {
            DebugLog.diagnostic("audit unrated '" + r.page.name + "' state=" + r.state);
            return;
        }
        Nutrition n = r.nutrition;
        DebugLog.diagnostic("audit graded '" + r.page.name + "' band=" + r.band + "/" + r.letter + " source=" + r.source
            + " kind=" + (r.drinkKind == null ? "food" : r.drinkKind) + " unit=" + n.unit()
            + " kcal=" + n.energyKcal + " sugar=" + n.sugarsG + " added=" + n.addedSugarsG + " sat=" + n.saturatedFatG
            + " fat=" + n.totalFatG + " sodium=" + n.sodiumMg + " fibre=" + n.fibreG + " protein=" + n.proteinG
            + " repaired=" + n.ocrRepaired
            + " | " + String.join(" ; ", r.concerns) + " | " + String.join(" ; ", r.positives)
            + " | " + String.join(" ; ", r.caveats));
        // What the allergen block will be built from, so an audit can check it against the page.
        LabelEvidence label = LabelEvidence.of(r.labelText);
        Allergen.Scan scan = Allergen.scan(label.ingredients(), label.allergens());
        String ingredients = label.ingredients() == null ? "none" : label.ingredients();
        DebugLog.diagnostic("audit label '" + r.page.name + "' pack=" + n.netQuantity + n.netUnit
            + " basisStated=" + n.basisStated + " ingredients=" + ingredients.substring(0, Math.min(160, ingredients.length()))
            + " contains=" + scan.declared + scan.fromIngredients + " traces=" + scan.traces
            + " noInfo=" + scan.noInformation);
    }

    /**
     * While a tapped swap is pending, looks for it on this screen. A shop's search results page
     * answers with a card of its own; the swap's product page keeps its normal rating and carries the
     * price and budget with it. True when this run has been answered and needs nothing more.
     */
    private boolean checkSwap(List<ScreenText> screen, GradeResult result, String currentRetailer, int expected) {
        SwapCheck.Pending check = pendingCheck;
        if (check == null) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        if (check.expired(now)) { pendingCheck = null; return false; }
        if (!check.retailer.equals(currentRetailer)) return false;
        SwapCheck.Result found = SwapCheck.find(screen, check);
        if (result.state == GradeResult.State.GRADED || result.state == GradeResult.State.NEEDS_INPUT) {
            if (found.state == SwapCheck.State.NOT_FOUND) return false;
            // The shopper opened the swap's own page: rate it as usual, with the budget attached.
            pendingCheck = null;
            result.check = check;
            result.checkResult = found;
            result.announce = true;
            return false;
        }
        if (found.state != SwapCheck.State.NOT_FOUND) {
            pendingCheck = null;
            GradeResult answer = GradeResult.swapChecked(check, found);
            main.post(() -> listener.onResult(answer, expected));
            return true;
        }
        if (result.state == GradeResult.State.NOT_PRODUCT) {
            // A results or listing screen without the product yet: give it a few seconds to load
            // and scroll, then say so instead of leaving the shopper guessing.
            if (resultsSeenAt == 0) resultsSeenAt = now;
            if (now - resultsSeenAt >= SwapCheck.NOT_FOUND_AFTER_MS) {
                pendingCheck = null;
                GradeResult answer = GradeResult.swapChecked(check, SwapCheck.Result.notFound());
                main.post(() -> listener.onResult(answer, expected));
                return true;
            }
            worker.postDelayed(() -> run(expected), 1500);
        }
        return false;
    }

    private PackScan scanOnce(ProductPage page) {
        // Capture is expensive: PHOTO_ATTEMPTS per window, spaced out. A later image
        // can supply ingredients even when a previous image already supplied nutrition.
        // The budget refills after PHOTO_WINDOW_MS, because the label a shopper wants read is often
        // reached long after the page opened: read the reviews, then swipe to the back-of-pack photo.
        // Spending the whole budget in the first seconds is why the pack used to go unread.
        // A scan the user asked for skips all of this, because they are holding the pack on screen.
        String key = page.key();
        long now = android.os.SystemClock.elapsedRealtime();
        boolean userAsked = forceNextScan;
        forceNextScan = false;
        synchronized (photoTried) {
            if (!userAsked) {
                Long last = photoTried.get(key);
                if (last != null && now - last < PHOTO_RETRY_MS) return PackScan.EMPTY;
                // A rolling window. It used to refill only after 25 s with no scanning at all, so a
                // shopper swiping steadily through Lay's thirteen photos spent it by the eighth and the
                // nutrition table, ninth, was never read.
                Long start = photoWindowStart.get(key);
                if (start == null || now - start >= PHOTO_WINDOW_MS) {
                    photoWindowStart.put(key, now);
                    photoAttempts.put(key, 0);
                }
                if (photoAttempts.getOrDefault(key, 0) >= PHOTO_ATTEMPTS) return PackScan.EMPTY;
            }
            photoTried.put(key, now);
            photoAttempts.put(key, photoAttempts.getOrDefault(key, 0) + 1);
        }
        PackScan scan = source.packScan(userAsked);
        if (scan == null || scan.isEmpty()) {
            // Nothing came back at all: the screen was not captured, so it should not cost an attempt.
            synchronized (photoTried) { photoAttempts.put(key, Math.max(0, photoAttempts.getOrDefault(key, 1) - 1)); }
            return PackScan.EMPTY;
        }
        if (scan.hasGtin()) synchronized (photoTried) { barcodes.put(key, scan.gtin); }
        synchronized (photoTried) {
            // The same photo read again (a content event while nothing moved): refund the attempt.
            if (!userAsked && scan.text != null && scan.text.equals(lastPhotoText.get(key))) {
                photoAttempts.put(key, Math.max(0, photoAttempts.getOrDefault(key, 1) - 1));
            }
            if (scan.text != null) lastPhotoText.put(key, scan.text);
        }
        return scan;
    }

    private ProductAnalyzer.Database database() {
        return new ProductAnalyzer.Database() {
            @Override public Nutrition find(ProductPage page) { return lookup(page); }
            @Override public Nutrition byGtin(String gtin) { return byNumber(gtin); }
            @Override public Nutrition byGtinOpenData(String gtin) { return byOpenData(gtin); }
        };
    }

    /** Same caching as our own lookup, so a miss is not asked again for every content event. */
    private Nutrition byOpenData(String gtin) {
        if (gtin == null) return null;
        String key = "open:" + gtin;
        Object cached = database.get(key);
        if (cached instanceof Nutrition) return (Nutrition) cached;
        Long miss = missAt.get(key);
        if (cached == databaseMiss && miss != null && android.os.SystemClock.elapsedRealtime() - miss < DATABASE_MISS_TTL_MS) return null;
        Nutrition found = source.openDataLookup(gtin);
        if (found != null) database.put(key, found);
        else { database.put(key, databaseMiss); missAt.put(key, android.os.SystemClock.elapsedRealtime()); }
        return found;
    }

    /** Cached like the name lookup, but keyed by the pack's own number so it is shared across apps. */
    private Nutrition byNumber(String gtin) {
        if (gtin == null) return null;
        String key = "gtin:" + gtin;
        Object cached = database.get(key);
        if (cached instanceof Nutrition) return (Nutrition) cached;
        Long miss = missAt.get(key);
        if (cached == databaseMiss && miss != null && android.os.SystemClock.elapsedRealtime() - miss < DATABASE_MISS_TTL_MS) return null;
        Nutrition found = source.gtinLookup(gtin);
        if (found != null) database.put(key, found);
        else { database.put(key, databaseMiss); missAt.put(key, android.os.SystemClock.elapsedRealtime()); }
        return found;
    }

    private Nutrition lookup(ProductPage page) {
        String key = page.key();
        Object cached = database.get(key);
        if (cached instanceof Nutrition) return (Nutrition) cached;
        Long miss = missAt.get(key);
        if (cached == databaseMiss && miss != null && android.os.SystemClock.elapsedRealtime() - miss < DATABASE_MISS_TTL_MS) return null;
        Nutrition found = source.databaseLookup(page);
        if (found != null) database.put(key, found);
        else { database.put(key, databaseMiss); missAt.put(key, android.os.SystemClock.elapsedRealtime()); }
        return found;
    }
}
