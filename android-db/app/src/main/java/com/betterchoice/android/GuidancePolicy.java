package com.betterchoice.android;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides when the bubble should ask the user to open the label. A food product page gets a couple of
 * seconds to settle first (details often load late), then the card opens once per product, and after
 * that the bubble simply stays in its asking state without interrupting again.
 */
final class GuidancePolicy {
    /** How long an unreadable food page is given before the bubble switches to its asking state. */
    static final long AFTER_MS = 2000;

    enum Decision {
        /** Too early: stay quiet and look again. */ WAIT,
        /** Open the card once, to teach the gesture. */ ANNOUNCE,
        /** Asking state only: the bubble shows "?" and waits for a tap. */ QUIET
    }

    private final Map<String, Long> firstSeen = new HashMap<>();
    private final Set<String> rated = new HashSet<>();
    private boolean announcedThisSession;

    /**
     * The card opens by itself only once per shopping session. Every later item just shows the "?"
     * bubble, which the user taps when they want the hint.
     */
    synchronized Decision decide(String key, long nowMs) {
        if (rated.contains(key)) return Decision.QUIET;
        Long since = firstSeen.putIfAbsent(key, nowMs);
        if (since == null) since = nowMs;
        if (nowMs - since < AFTER_MS) return Decision.WAIT;
        if (announcedThisSession) return Decision.QUIET;
        announcedThisSession = true;
        return Decision.ANNOUNCE;
    }

    /** Remaining wait for this product, so the watcher can re-check exactly when it is due. */
    synchronized long waitRemainingMs(String key, long nowMs) {
        Long since = firstSeen.get(key);
        if (since == null) return AFTER_MS;
        return Math.max(0, AFTER_MS - (nowMs - since));
    }

    /** A rating arrived: this product never needs guidance again. */
    synchronized void rated(String key) { firstSeen.remove(key); rated.add(key); }

    /** New shopping session (screen off, service restart): the hint may be shown once more. */
    synchronized void clear() { firstSeen.clear(); rated.clear(); announcedThisSession = false; }
}
