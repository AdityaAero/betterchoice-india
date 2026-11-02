package com.betterchoice.android;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects visible text from a supported shopping app's window for one analysis. Password and
 * editable fields are skipped; nodes are released immediately and nothing is stored or logged.
 */
final class ScreenReader {
    static final int MAX_NODES = 900;
    static final int MAX_DEPTH = 40;

    static List<ScreenText> read(AccessibilityNodeInfo root) {
        List<ScreenText> items = new ArrayList<>();
        if (root == null) return items;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depths = new ArrayDeque<>();
        queue.add(root); depths.add(0);
        Rect bounds = new Rect();
        int visited = 0;
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            int depth = depths.poll();
            try {
                if (++visited > MAX_NODES) continue;
                if (!node.isVisibleToUser() || node.isPassword()) continue;
                if (!node.isEditable()) {
                    CharSequence text = node.getText();
                    if (text == null || text.length() == 0) text = node.getContentDescription();
                    if (text != null && text.length() > 0 && text.length() <= 2000) {
                        node.getBoundsInScreen(bounds);
                        items.add(new ScreenText(text.toString(), bounds.top, bounds.left, node.isClickable()));
                    }
                }
                if (depth >= MAX_DEPTH) continue;
                for (int i = 0; i < node.getChildCount() && visited + queue.size() < MAX_NODES; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) { queue.add(child); depths.add(depth + 1); }
                }
            } catch (RuntimeException windowGone) {
                // The window changed mid-read; return what was collected so far.
            } finally {
                node.recycle();
            }
        }
        DebugLog.diagnostic("read " + items.size() + " texts from " + visited + " nodes");
        return items;
    }

    private ScreenReader() {}
}
