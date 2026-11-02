package com.betterchoice.android;

/** Chooses focus over activity; IME/our overlays cannot steal foreground ownership. */
final class WindowSelection<T> {
    private T focused;
    private T active;
    private boolean overlayShown;
    void consider(T window, boolean ignored, boolean isFocused, boolean isActive) {
        if (ignored) return;
        if (isFocused) focused = window;
        if (isActive) active = window;
    }
    /** Records that an accessibility overlay (BetterChoice's bubble or card) is on screen. */
    void overlay() { overlayShown = true; }
    T selected() { return focused != null ? focused : active; }
    /**
     * True when no window but our overlay is in front: our open card takes key focus (so Back closes it) and Android
     * then reports no window focused or active at all. The shopper is still in the app beneath the card, which the
     * caller keeps only if that already-verified window is still on screen. A real change (home screen, another
     * app, the notification shade) always brings a focused or active window of its own.
     */
    boolean onlyOverlayInFront() { return selected() == null && overlayShown; }
}
