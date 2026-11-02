package com.betterchoice.android;

/** What one look at the pack photo produced: recognised text, a barcode number, or neither. */
final class PackScan {
    static final PackScan EMPTY = new PackScan(null, null);

    final String text;
    final String gtin;

    PackScan(String text, String gtin) {
        this.text = text;
        this.gtin = gtin;
    }

    boolean hasText() { return text != null && !text.isBlank(); }
    boolean hasGtin() { return gtin != null; }
    boolean isEmpty() { return !hasText() && !hasGtin(); }
}
