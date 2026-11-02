package com.betterchoice.android;

import android.view.MotionEvent;
import android.view.View;

final class SafeTouches {
    /** A gesture that becomes obscured stays rejected through UP, even after the overlay leaves. */
    static final class GestureGuard {
        private boolean rejected;
        boolean reject(int action, int flags) {
            if (action == MotionEvent.ACTION_DOWN) rejected = false;
            rejected |= isObscured(flags);
            boolean result = rejected;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) rejected = false;
            return result;
        }
    }
    static boolean isObscured(int flags) {
        return (flags & (MotionEvent.FLAG_WINDOW_IS_OBSCURED | MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) != 0;
    }
    // This filter only consumes rejected touches; View handles clicks and accessibility normally.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void protect(View control) {
        control.setFilterTouchesWhenObscured(true);
        // Returning false for legitimate touches preserves keyboard/TalkBack performClick.
        control.setOnTouchListener((view, event) -> isObscured(event.getFlags()));
    }
    private SafeTouches() {}
}
