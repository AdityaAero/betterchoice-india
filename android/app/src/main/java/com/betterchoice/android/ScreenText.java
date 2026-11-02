package com.betterchoice.android;

/** One visible text item from a shopping app window, held in memory only for a single analysis. */
final class ScreenText {
    final String text;
    final int top;
    /** Left edge on screen; 0 when unknown. Used to pair a grid's label row with its value row. */
    final int left;
    final boolean clickable;

    ScreenText(String text, int top, boolean clickable) {
        this(text, top, 0, clickable);
    }

    ScreenText(String text, int top, int left, boolean clickable) {
        this.text = text;
        this.top = top;
        this.left = left;
        this.clickable = clickable;
    }
}
