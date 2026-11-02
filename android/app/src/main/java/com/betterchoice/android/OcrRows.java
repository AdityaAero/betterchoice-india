package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reassembles spatial OCR lines into table rows instead of reading one whole column at a time. */
final class OcrRows {
    static final class Fragment {
        final String text;
        final int left, top, right, bottom;
        Fragment(String text, int left, int top, int right, int bottom) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        int height() { return Math.max(1, bottom - top); }
        int center() { return top + height() / 2; }
    }

    static String assemble(List<Fragment> input) {
        List<Fragment> pending = new ArrayList<>(input.subList(0, Math.min(800, input.size())));
        pending.sort(Comparator.comparingInt(Fragment::center).thenComparingInt(f -> f.left));
        StringBuilder text = new StringBuilder();
        while (!pending.isEmpty() && text.length() < 16000) {
            Fragment first = pending.remove(0);
            List<Fragment> row = new ArrayList<>();
            row.add(first);
            for (int i = 0; i < pending.size();) {
                Fragment next = pending.get(i);
                if (next.center() - first.center() > first.height()) break;
                boolean aligned = Math.abs(next.center() - first.center()) <= Math.min(first.height(), next.height()) * .4;
                boolean separate = true;
                for (Fragment other : row) {
                    if (next.left < other.right && next.right > other.left) { separate = false; break; }
                }
                if (aligned && separate) { row.add(next); pending.remove(i); }
                else i++;
            }
            row.sort(Comparator.comparingInt(f -> f.left));
            for (Fragment part : row) text.append(part.text).append(' ');
            text.append('\n');
        }
        return text.toString();
    }
}
