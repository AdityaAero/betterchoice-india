package com.betterchoice.android;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;

/**
 * System-bound lifetime. Shows the bubble in supported shopping apps and, on food product pages,
 * rates the item from on-screen nutrition, pack-photo text or the BetterChoice database.
 * Window focus checks run only while the bubble is visible.
 *
 * <p><b>Scope: the supported shopping apps and nothing else.</b> Three layers, each enough alone:
 * <ol>
 *   <li>The operating system filters events by package ({@code packageNames} in
 *       {@code res/xml/bubble_service.xml}), so no other app's activity is ever delivered here.</li>
 *   <li>A window is opened, even just to read its package name, only after one of the supported
 *       apps has itself announced that window in an event ({@link #shoppingWindows}). Whatever else
 *       is in front, such as a chat or a banking app, is recognised as "not ours" from the window
 *       list alone and is never touched.</li>
 *   <li>Text is read, and screenshots are taken, only from the one verified shopping window, and a
 *       screenshot is thrown away if the shopper left that window while it was being taken.</li>
 * </ol>
 */
public class BubbleService extends AccessibilityService {
    private static WeakReference<BubbleService> active = new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BubbleOverlay bubble;
    private GradeCard card;
    private ProductWatcher watcher;
    private PackScanner packScanner;
    private OpenFoodFacts openData;
    private CatalogueClient catalogue;
    private ConsentStore consent;
    private AccountStore account;
    private boolean registered;
    private boolean observing;
    private boolean connected;
    static final long VISIBLE_CHECK_MS = 100;
    // One live window's verified package, never a history or a retained node.
    private volatile int verifiedWindowId = -1;
    private volatile String verifiedPackage;
    /**
     * Windows a supported shopping app has announced through its own events, by window id. Window
     * ids are never reused, so an id heard from Blinkit can only ever be Blinkit's. Main thread only;
     * holds ids and package names, nothing from the screen, and is capped so it cannot grow.
     */
    private final Map<Integer, String> shoppingWindows = new LinkedHashMap<Integer, String>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) { return size() > 16; }
    };
    private int analysisGeneration = -1;
    private GradeResult current;
    /**
     * The card used to be closed by the next analysis result, and analysis re-runs on every content
     * event while the user scrolls, so a card could vanish a moment after appearing. It now stays up
     * for at least this long before any automatic refresh or close. A tap still closes it at once.
     */
    private static final long CARD_HOLD_MS = 3000;
    /** If a requested scan produces nothing at all, say so rather than leaving the card spinning. */
    private static final long SCAN_TIMEOUT_MS = 7000;
    private long cardShownAt;
    private String cardKey;
    private boolean awaitingScan;
    private final Runnable scanGaveUp = () -> {
        if (!awaitingScan) return;
        awaitingScan = false;
        if (current != null && current.state == GradeResult.State.SCANNING) {
            boolean database = current.database == GradeResult.Database.ASKING;
            current = database ? GradeResult.needsInput(current.page, null,
                    "The BetterChoice database did not answer in time. Check your connection, then tap Try again.")
                : GradeResult.needsInput(current.page, current.gtin);
            if (database) current.database = GradeResult.Database.UNREACHABLE;
            openCard();
        }
    };
    private final Runnable accountChanged = () -> {
        if (!this.connected && active.get() == this && accountAllowsBubble()) onServiceConnected();
        else reconcile(0);
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener consentChanged = (prefs, key) -> {
        if (consent.isCurrent()) return;
        CatalogueClient answers = catalogue;
        if (answers != null) new Thread(answers::forget, "catalogue-forget").start();
        stopCompanion();
    };
    private final Runnable reconcile = () -> reconcile(0);
    private final Runnable visibleCheck = () -> reconcile(0);
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handler.removeCallbacksAndMessages(null);
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                hideAndForget();
                if (watcher != null) watcher.forgetSession();
            }
            else reconcile(0);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        consent = new ConsentStore(this);
        account = AccountStore.get(this);
    }

    static boolean isConnected() {
        BubbleService service = active.get();
        return service != null && service.connected;
    }

    static void turnOffConnectedService() {
        BubbleService service = active.get();
        if (service != null) service.stopCompanion();
    }

    /** Called by the add-details screen so the new values rate the item immediately. */
    static void userDetailsAdded(ProductPage page, Nutrition nutrition) {
        BubbleService service = active.get();
        if (service == null || service.watcher == null) return;
        service.watcher.rememberUserDetails(page, nutrition);
        service.watcher.schedule(0);
    }

    @Override protected void onServiceConnected() {
        active = new WeakReference<>(this);
        if (!consent.isCurrent() || platformAccess() != PlatformAccess.State.AVAILABLE) {
            stopCompanion();
            return;
        }
        if (!accountAllowsBubble()) {
            // Signed-in users whose session merely needs renewal wait for the account to recover.
            if (account.hasSession() && account.onboardingDone()) {
                account.observe(accountChanged);
                AuthController.get(this).resume();
            } else stopCompanion();
            return;
        }
        if (!preparePrivateWindowAccess()) { stopCompanion(); return; }
        connected = true;
        account.observe(accountChanged);
        AuthController.get(this).resume();
        if (!observing) { consent.observe(consentChanged); observing = true; }
        if (BuildConfig.DEBUG) DebugLog.auditFile = new java.io.File(getFilesDir(), "audit.log");
        if (bubble == null) {
            bubble = createBubble();
            bubble.setTapListener(this::showCard);
        }
        if (card == null) {
            card = createCard();
        }
        if (watcher == null) watcher = createWatcher();
        if (!registered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen, filter);
            registered = true;
        }
        reconcile(0);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!connected || bubble == null) return;
        if (!consent.isCurrent()) { stopCompanion(); return; }
        int type = event == null ? AccessibilityEvent.TYPE_WINDOWS_CHANGED : event.getEventType();
        CharSequence eventPackage = event == null ? null : event.getPackageName();
        // Android already filters by package; this is the same rule again in code, in case a
        // device's accessibility framework does not honour the filter. Events without a package are
        // the system's window-list notices, which carry no content.
        if (eventPackage != null && !ForegroundPolicy.SUPPORTED.contains(eventPackage.toString())) return;
        boolean newShoppingWindow = rememberShoppingWindow(event);
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            // A shopping app just announced a window we had not seen: check whether it is in front.
            if (!bubble.isShowing()) { if (newShoppingWindow) reconcile(event.getEventTime()); return; }
            // Same window, new content (scrolling, photo carousel, "View details"): re-rate, don't re-resolve focus.
            if (verifiedPackage != null && eventPackage != null && verifiedPackage.contentEquals(eventPackage)) {
                scheduleAnalysis(ProductWatcher.DEBOUNCE_MS);
            }
            return;
        }
        handler.removeCallbacks(reconcile);
        if (event != null && type == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            (event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0 &&
            event.getWindowId() == verifiedWindowId) hideAndForget();
        // Resolve current windows, never trust a delayed event's package as foreground.
        reconcile(event == null ? 0 : event.getEventTime());
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && watcher != null && verifiedPackage != null
                && event != null && verifiedPackage.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) {
            analysisGeneration = watcher.newScreen();
            current = GradeResult.neutral(GradeResult.State.NOT_PRODUCT, null);
            card.hide();
            if (bubble.isShowing()) bubble.setLook(BubbleStyle.neutral(verifiedPackage, false));
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && bubble.isShowing()) scheduleAnalysis(ProductWatcher.DEBOUNCE_MS);
        // One bounded follow-up covers a window/root not yet published during a transition.
        if (connected) handler.postDelayed(reconcile, 40);
    }

    /** Records the window a supported app's event came from. True when it was not known before. */
    private boolean rememberShoppingWindow(AccessibilityEvent event) {
        if (event == null) return false;
        CharSequence source = event.getPackageName();
        int windowId = event.getWindowId();
        if (source == null || windowId < 0) return false;
        String packageName = source.toString();
        if (!ForegroundPolicy.SUPPORTED.contains(packageName)) return false;
        return shoppingWindows.put(windowId, packageName) == null;
    }

    private void reconcile(long eventTime) {
        if (!connected || bubble == null) return;
        handler.removeCallbacks(visibleCheck);
        // This gate MUST run before querying windows, including delayed and reconnect paths.
        if (!consent.isCurrent() || platformAccess() != PlatformAccess.State.AVAILABLE) {
            stopCompanion(); return;
        }
        if (!accountAllowsBubble()) { handler.removeCallbacks(reconcile); hideAndForget(); return; }
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        String foreground = null;
        boolean interactive = power.isInteractive();
        boolean locked = keyguard.isKeyguardLocked();
        if (interactive && !locked) {
            try { foreground = foregroundPackage(queryWindows()); }
            catch (RuntimeException error) { hideAndForget(); DebugLog.failure("Window resolution unavailable"); }
        }
        if (ForegroundPolicy.shouldShow(foreground, interactive, locked)) {
            boolean wasShowing = bubble.isShowing();
            if (!wasShowing) {
                current = null;
                bubble.setLook(BubbleStyle.neutral(foreground, false));
                if (watcher != null) analysisGeneration = watcher.reset(foreground);
            }
            bubble.show(eventTime);
            if (!wasShowing && bubble.isShowing()) scheduleAnalysis(250);
        }
        else hideAndForget();
        // OEM exit animations can publish focus after the final accessibility event.
        // Do not poll while hidden; steady windows reuse only their verified package.
        if (connected && bubble.isShowing()) handler.postDelayed(visibleCheck, VISIBLE_CHECK_MS);
    }

    private void scheduleAnalysis(long delayMs) {
        if (watcher != null && verifiedPackage != null) watcher.schedule(delayMs);
    }

    private void onGrade(GradeResult result, int generation) {
        if (!connected || bubble == null || !bubble.isShowing() || watcher == null || generation != analysisGeneration) return;
        boolean userAsked = awaitingScan;
        // The answer to a swap the shopper tapped is always shown, like a scan they asked for.
        boolean swapAnswer = result.state == GradeResult.State.SWAP_CHECKED || result.checkResult != null;
        // A scan the user asked for always reports back, even when the answer has not changed.
        if (result.sameAs(current) && !userAsked && !swapAnswer) return;
        current = result;
        bubble.setLook(result.state == GradeResult.State.GRADED ? BubbleStyle.graded(result.band)
            : result.state == GradeResult.State.NEEDS_LABEL || result.state == GradeResult.State.SCANNING
                ? BubbleStyle.asking(verifiedPackage)
            : BubbleStyle.neutral(verifiedPackage, false));
        if (swapAnswer) { openCard(); return; }
        if (userAsked) {
            awaitingScan = false;
            handler.removeCallbacks(scanGaveUp);
            openCard();
            return;
        }
        if (card != null && card.isShowing()) {
            boolean sameProduct = cardKey != null && result.page != null && cardKey.equals(result.page.key());
            long shownFor = SystemClock.elapsedRealtime() - cardShownAt;
            if (!sameProduct) card.hide();
            else if (shownFor >= CARD_HOLD_MS) openCard();  // same item, fresher answer: refresh in place
            return;                                         // otherwise leave it up to be read
        }
        // Unreadable labels change the small bubble only. Opening guidance automatically
        // obstructs View details and the shopper's scroll; a bubble tap opens it instead.
    }

    /** Bubble tap: open the card, or close it if it is already up. */
    private void showCard(int x, int y, int size) {
        if (card == null) return;
        if (card.isShowing()) { card.hide(); return; }
        openCard();
    }

    private void openCard() {
        if (card == null || bubble == null || !bubble.isShowing()) return;
        int x = bubble.x(), y = bubble.y(), size = bubble.size();
        cardShownAt = SystemClock.elapsedRealtime();
        GradeResult result = current != null ? current : GradeResult.neutral(GradeResult.State.NOT_PRODUCT, null);
        cardKey = result.page == null ? null : result.page.key();
        card.show(result, verifiedPackage, x, y, size, new GradeCard.Actions() {
            @Override public void skip(ProductPage page) {
                if (watcher == null || page == null) return;
                watcher.skip(page);
                watcher.schedule(0);
            }
            @Override public void addDetails(ProductPage page) {
                if (page == null) return;
                startActivity(ContributeActivity.intent(BubbleService.this, page, barcodeFor(page)));
            }
            @Override public void scanNow(ProductPage page) {
                // The user has the pack on screen: read it straight away, no waiting, no budget.
                if (watcher == null || page == null) return;
                awaitingScan = true;
                current = GradeResult.scanning(page, barcodeFor(page));
                if (catalogue != null) current.database = GradeResult.Database.ASKING;
                openCard();
                watcher.allowAnotherPhoto(page);
                watcher.schedule(0);
                handler.removeCallbacks(scanGaveUp);
                handler.postDelayed(scanGaveUp, SCAN_TIMEOUT_MS);
            }
            @Override public void checkSwap(ProductPage page, GradeResult rated, SwapEngine.Swap swap) {
                // The shopper asked: open the shop's own search for the swap, then read the answer
                // from the shop's screen. Nothing is typed, tapped or added to a cart for them.
                String retailer = verifiedPackage;
                if (watcher == null || page == null || retailer == null) return;
                Double quantity = rated == null || rated.nutrition == null ? null : rated.nutrition.netQuantity;
                watcher.startCheck(new SwapCheck.Pending(swap, retailer, page.price, quantity, page.name,
                    SystemClock.elapsedRealtime()));
                String query = SwapCheck.query(swap.product);
                if (!RetailerSearch.open(BubbleService.this, retailer, query)) {
                    android.widget.Toast.makeText(BubbleService.this, "Search words copied: paste \"" + query + "\" into "
                        + GradeCard.shopName(retailer) + "'s search", android.widget.Toast.LENGTH_LONG).show();
                }
            }
            @Override public void cannotFind(ProductPage page) {
                // One more read of whatever is on screen now, then the form if it is still unreadable.
                if (watcher == null || page == null) return;
                watcher.allowAnotherPhoto(page);
                watcher.schedule(0);
                handler.postDelayed(() -> {
                    if (current != null && current.state == GradeResult.State.GRADED) return;
                    startActivity(ContributeActivity.intent(BubbleService.this, page, barcodeFor(page)));
                }, 1200);
            }
        });
    }

    private String barcodeFor(ProductPage page) {
        if (current != null && current.gtin != null && current.page != null
            && current.page.key().equals(page.key())) return current.gtin;
        return watcher == null ? null : watcher.barcodeFor(page);
    }

    // Package-private seams for Android lifecycle tests; no exported IPC or dynamic policy.
    List<AccessibilityWindowInfo> queryWindows() { return getWindows(); }
    boolean accountAllowsBubble() { return account.canUseBubble(); }
    PlatformAccess.State platformAccess() { return PlatformAccess.check(this); }
    BubbleOverlay createBubble() { return new BubbleOverlay(this); }
    GradeCard createCard() { return new GradeCard(this, getSystemService(WindowManager.class)); }

    ProductWatcher createWatcher() {
        // Version 4: product facts come from the BetterChoice database, with answers cached on the phone.
        catalogue = new CatalogueClient(CatalogueClient.https(), new java.io.File(getCacheDir(), "catalogue-v4"), null);
        packScanner = new PackScanner(this, Executors.newSingleThreadExecutor());
        openData = new OpenFoodFacts();
        return new ProductWatcher(new ProductWatcher.Source() {
            @Override public List<ScreenText> screen() { return readSelectedWindow(); }
            @Override public int screenHeight() { return getResources().getDisplayMetrics().heightPixels; }
            @Override public PackScan packScan(boolean userAsked) {
                int window = verifiedWindowId;
                if (window < 0 || verifiedPackage == null) return PackScan.EMPTY;
                Rect bounds = shoppingWindowBounds(window);
                if (bounds == null) return PackScan.EMPTY;
                PackScan scan = packScanner.scan(userAsked, window, bounds);
                // If the shopper left the shopping app while the picture was taken, it may show
                // something else: the reading is thrown away rather than used.
                if (window != verifiedWindowId || shoppingWindowBounds(window) == null) return PackScan.EMPTY;
                return scan;
            }
            @Override public Nutrition databaseLookup(ProductPage page) {
                if (!AuthController.available()) return null;
                try { return AuthController.get(BubbleService.this).api().matchProduct(page); }
                catch (Exception unavailable) { DebugLog.failure("Product database unavailable"); return null; }
            }
            @Override public Nutrition gtinLookup(String gtin) {
                if (!AuthController.available()) return null;
                try { return AuthController.get(BubbleService.this).api().matchByGtin(gtin); }
                catch (Exception unavailable) { DebugLog.failure("Barcode lookup unavailable"); return null; }
            }
            @Override public Nutrition openDataLookup(String gtin) { return openData.find(gtin); }
            @Override public CatalogueClient catalogue() { return catalogue; }
        }, this::onGrade);
    }

    /** Runs on the analysis thread: reads the verified shopping window only, never any other app. */
    private List<ScreenText> readSelectedWindow() {
        String expectedPackage = verifiedPackage;
        int expectedWindow = verifiedWindowId;
        if (expectedPackage == null || expectedWindow < 0) return null;
        List<AccessibilityWindowInfo> windows;
        try { windows = getWindows(); } catch (RuntimeException unavailable) { return null; }
        for (AccessibilityWindowInfo window : windows) {
            if (window.getId() != expectedWindow) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) return null;
            CharSequence rootPackage = root.getPackageName();
            if (rootPackage == null || !expectedPackage.contentEquals(rootPackage)) { root.recycle(); return null; }
            return ScreenReader.read(root);
        }
        return null;
    }

    boolean preparePrivateWindowAccess() {
        // Android 13+ can disable the framework's node cache and speculative prefetch, so window
        // content is fetched only when BetterChoice reads a product page and is not retained.
        if (Build.VERSION.SDK_INT < 33) return true;
        try { return setCacheEnabled(false); }
        catch (RuntimeException unavailable) {
            DebugLog.failure("Private window access unavailable");
            return false;
        }
    }

    private String foregroundPackage(List<AccessibilityWindowInfo> windows) {
        if (windows == null) return null;
        WindowSelection<AccessibilityWindowInfo> selection = new WindowSelection<>();
        boolean verifiedStillThere = false;
        for (AccessibilityWindowInfo window : windows) {
            int type = window.getType();
            selection.consider(window, type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                type == AccessibilityWindowInfo.TYPE_INPUT_METHOD, window.isFocused(), window.isActive());
            if (type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) selection.overlay();
            if (window.getId() == verifiedWindowId && type == AccessibilityWindowInfo.TYPE_APPLICATION) verifiedStillThere = true;
        }
        // Our open card holds key focus so Back closes it: the shop window beneath is still the foreground.
        if (selection.onlyOverlayInFront() && verifiedStillThere && verifiedPackage != null) return verifiedPackage;
        AccessibilityWindowInfo selected = selection.selected();
        if (BuildConfig.DEBUG && (selected == null || selected.getId() != verifiedWindowId)) {
            StringBuilder seen = new StringBuilder();
            for (AccessibilityWindowInfo w : windows) {
                seen.append(w.getId()).append(':').append(w.getType()).append(w.isFocused() ? "F" : "").append(w.isActive() ? "A" : "").append(' ');
            }
            DebugLog.diagnostic("foreground change: verified=" + verifiedWindowId + " onlyOverlay=" + selection.onlyOverlayInFront()
                + " stillThere=" + verifiedStillThere + " windows=" + seen);
        }
        if (selected == null || selected.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) return null;
        int id = selected.getId();
        if (id >= 0 && id == verifiedWindowId && verifiedPackage != null) return verifiedPackage;
        // Remove the old bubble BEFORE a different app's root IPC can block.
        hideAndForget();
        // A window no supported app has announced belongs to some other app. It is not opened at
        // all, not even to read its package name, so BetterChoice never touches it.
        String announcedBy = id >= 0 ? shoppingWindows.get(id) : null;
        if (announcedBy == null) return null;
        String packageName = readRootPackage(selected);
        // Confirmed from the window itself, so a stale or mistaken id can never widen the scope.
        if (!announcedBy.equals(packageName)) { shoppingWindows.remove(id); return null; }
        verifiedWindowId = id;
        verifiedPackage = packageName;
        return packageName;
    }

    /**
     * The verified shopping window's area on screen, or null when that window is no longer there.
     * Runs on the analysis thread, like {@link #readSelectedWindow}.
     */
    Rect shoppingWindowBounds(int windowId) {
        if (windowId < 0) return null;
        List<AccessibilityWindowInfo> windows;
        try { windows = getWindows(); } catch (RuntimeException unavailable) { return null; }
        if (windows == null) return null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getId() != windowId || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            return bounds.isEmpty() ? null : bounds;
        }
        return null;
    }

    String readRootPackage(AccessibilityWindowInfo selected) {
        AccessibilityNodeInfo root = Build.VERSION.SDK_INT >= 33 ? selected.getRoot(0) : selected.getRoot();
        if (root == null) return null;
        try {
            CharSequence name = root.getPackageName();
            return name == null ? null : name.toString();
        } finally { root.recycle(); }
    }

    private void hideAndForget() {
        awaitingScan = false; cardKey = null; cardShownAt = 0;
        handler.removeCallbacks(scanGaveUp);
        handler.removeCallbacks(visibleCheck);
        verifiedWindowId = -1;
        verifiedPackage = null;
        current = null;
        if (watcher != null) analysisGeneration = watcher.reset(null);
        if (card != null) card.hide();
        if (bubble != null) bubble.hide();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (bubble != null) { hideAndForget(); reconcile(0); }
    }
    @Override public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
        hideAndForget();
    }
    private void stopCompanion() { cleanup(); disableSelf(); }
    @Override public boolean onUnbind(Intent intent) { cleanup(); return super.onUnbind(intent); }
    @Override public void onDestroy() { cleanup(); super.onDestroy(); }
    private void cleanup() {
        connected = false;
        shoppingWindows.clear();
        if (account != null) account.unobserve(accountChanged);
        handler.removeCallbacksAndMessages(null);
        hideAndForget();
        bubble = null;
        card = null;
        if (watcher != null) { watcher.forgetSession(); watcher.stop(); watcher = null; }
        if (packScanner != null) { packScanner.close(); packScanner = null; }
        if (registered) {
            try { unregisterReceiver(screen); }
            catch (IllegalArgumentException detached) { DebugLog.failure("Receiver already detached"); }
            registered = false;
        }
        if (observing) { consent.removeObserver(consentChanged); observing = false; }
        if (active.get() == this) active.clear();
    }
}
