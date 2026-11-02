package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The live check for one healthier swap, done on the shop's own screen.
 *
 * <p>Blinkit and Zepto offer no product or stock API. So when the shopper taps a swap, BetterChoice
 * opens the shop's own search for it, and this class reads the result the shop shows — for this
 * shopper's delivery address, at this moment — the same way the product page is read. Nothing read
 * here is stored or sent anywhere; it answers one question for one person and is forgotten.
 *
 * <p>This is the equivalent of a travel site's "check the price again before you book". The catalogue
 * says a product is a good swap; only the shop's screen can say it is in stock here, now, at what price.
 */
final class SwapCheck {
    /** How long a tap on a swap stays open for the result screen to appear. */
    static final long TIMEOUT_MS = 180_000;
    /** After this long on a results screen without the product, say so rather than wait. */
    static final long NOT_FOUND_AFTER_MS = 8_000;

    enum State {
        IN_STOCK("In stock on this screen"),
        OUT_OF_STOCK("Out of stock here right now"),
        SEEN("On this screen; stock not shown"),
        NOT_FOUND("Not in these results");
        final String label;
        State(String label) { this.label = label; }
    }

    /** A swap the shopper tapped, with the original it would replace. */
    static final class Pending {
        final SwapEngine.Swap swap;
        final String retailer;
        final Double originalPrice;
        final Double originalQuantity;
        final String originalName;
        final long startedAt;
        Pending(SwapEngine.Swap swap, String retailer, Double originalPrice, Double originalQuantity,
                String originalName, long startedAt) {
            this.swap = swap; this.retailer = retailer; this.originalPrice = originalPrice;
            this.originalQuantity = originalQuantity; this.originalName = originalName; this.startedAt = startedAt;
        }
        boolean expired(long now) { return now - startedAt > TIMEOUT_MS; }
    }

    static final class Result {
        final State state;
        final Double price;
        /** The pack size shown next to it, in g or ml, when shown. */
        final Double pack;
        /** Packs needed to replace one of the original. */
        final int packs;
        final Double combinedCost, ceiling, per100;
        final Boolean withinBudget;
        /** True when the pack on screen is the size the catalogue knows; otherwise the shop sells another size. */
        final boolean samePack;

        Result(State state, Double price, Double pack, int packs, Double combinedCost, Double ceiling,
               Double per100, Boolean withinBudget, boolean samePack) {
            this.state = state; this.price = price; this.pack = pack; this.packs = packs;
            this.combinedCost = combinedCost; this.ceiling = ceiling; this.per100 = per100;
            this.withinBudget = withinBudget; this.samePack = samePack;
        }

        static Result notFound() { return new Result(State.NOT_FOUND, null, null, 1, null, null, null, null, false); }
    }

    // --- Finding the product on the shop's screen --------------------------------------------------

    private static final Pattern PRICE = Pattern.compile("₹\\s?(\\d[\\d,]{0,6}(?:\\.\\d{1,2})?)");
    private static final Pattern PACK = Pattern.compile(
        "(?<![0-9.])(\\d{1,4}(?:\\.\\d{1,2})?)\\s*(kg|g|gm|gms|grams?|ml|l|ltr|litres?|liters?)(?![a-z])");
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "the", "and", "of", "with", "in", "a", "an", "for", "pack", "combo", "g", "gm", "gms", "kg", "ml", "l",
        "pc", "pcs", "piece", "pieces", "x", "new", "flavour", "flavor", "flavoured", "flavored"));
    private static final String[] OUT_WORDS = {"out of stock", "sold out", "notify me", "notify", "currently unavailable",
        "not available"};

    /**
     * Looks for the swap among the texts on screen. A text counts as the product when it carries the
     * brand and at least three fifths of the product's name words; price, pack size and stock are then
     * read from the texts around it, which is how every shop lays out a product tile.
     */
    static Result find(List<ScreenText> screen, Pending pending) {
        if (screen == null || pending == null) return Result.notFound();
        SwapCatalogue.Product product = pending.swap.product;
        List<String> brandWords = words(product.brand);
        List<String> nameWords = words(product.name);
        nameWords.removeAll(brandWords);
        if (nameWords.isEmpty() && brandWords.isEmpty()) return Result.notFound();

        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < screen.size(); i++) {
            String text = normalize(screen.get(i).text);
            if (PRICE.matcher(screen.get(i).text).find()) continue;
            // The search box and "Showing results for …" repeat the words searched for; they are
            // not a product, and reading prices after them picked up the tile below.
            if (text.contains("results for") || text.contains("search for") || text.startsWith("showing")
                || text.startsWith("search") || screen.get(i).text.contains("\"")) continue;
            boolean brand = brandWords.isEmpty() || containsWord(text, brandWords.get(0));
            int hits = 0;
            for (String word : nameWords) if (containsWord(text, word)) hits++;
            double share = nameWords.isEmpty() ? (brand ? 1 : 0) : (double) hits / nameWords.size();
            boolean match = brand ? share >= 0.6 : share >= 0.8 && hits >= 2;
            if (match && share > bestScore) { best = i; bestScore = share; }
        }
        if (best < 0) return Result.notFound();

        Double price = null, pack = null;
        State state = State.SEEN;
        int from = Math.max(0, best - 3), to = Math.min(screen.size(), best + 9);
        Matcher own = PACK.matcher(screen.get(best).text.toLowerCase(Locale.ROOT));
        if (own.find()) pack = grams(own);
        for (int i = best + 1; i < to && price == null; i++) {
            Matcher m = PRICE.matcher(screen.get(i).text);
            if (m.find()) price = Double.parseDouble(m.group(1).replace(",", ""));
        }
        for (int i = best - 1; i >= from && price == null; i--) {
            Matcher m = PRICE.matcher(screen.get(i).text);
            if (m.find()) price = Double.parseDouble(m.group(1).replace(",", ""));
        }
        // Stock is read from this product's own tile: forward from its name until the button, and
        // never past the next product's name, so a neighbour's ADD button is not taken for its own.
        Boolean inStock = null;
        boolean seenPrice = false;
        for (int i = best + 1; i < to && inStock == null; i++) {
            String raw = screen.get(i).text.trim();
            String t = raw.toLowerCase(Locale.ROOT);
            if (PRICE.matcher(raw).find()) { seenPrice = true; continue; }
            if (pack == null) {
                Matcher m = PACK.matcher(t);
                if (m.find()) pack = grams(m);
            }
            Boolean marker = stockMarker(t);
            if (marker != null) { inStock = marker; break; }
            if (seenPrice && raw.length() >= 12 && ProductPage.titleLike(raw)) break;   // the next product's tile
        }
        // Some shops print the button above the name: look just before it, never further.
        for (int i = best - 1; i >= Math.max(from, best - 2) && inStock == null; i--) {
            inStock = stockMarker(screen.get(i).text.trim().toLowerCase(Locale.ROOT));
        }
        if (Boolean.FALSE.equals(inStock)) state = State.OUT_OF_STOCK;
        else if (Boolean.TRUE.equals(inStock)) state = State.IN_STOCK;
        return budget(state, price, pack != null ? pack : product.pack, pending, product.pack);
    }

    /**
     * The 2× rule, on what the shopper would really pay: if the swap comes in a smaller pack, as many
     * packs as it takes to replace one original are counted together. The selling price is used, never
     * the MRP, and delivery or handling charges are not part of it.
     */
    static Result budget(State state, Double price, Double pack, Pending pending, Double catalogueP) {
        int packs = 1;
        Double original = pending.originalQuantity;
        if (original != null && pack != null && pack > 0 && pack < original) {
            packs = Math.max(1, (int) Math.round(original / pack));
        }
        Double combined = price == null ? null : price * packs;
        Double ceiling = pending.originalPrice == null ? null : pending.originalPrice * 2;
        Boolean within = combined == null || ceiling == null ? null : combined <= ceiling + 0.001;
        Double per100 = price == null || pack == null || pack <= 0 ? null : Math.round(price / pack * 1000) / 10.0;
        boolean samePack = pack != null && catalogueP != null && Math.abs(pack - catalogueP) <= catalogueP * 0.05;
        return new Result(state, price, pack, packs, combined, ceiling, per100, within, samePack);
    }

    /**
     * What to type into the shop's search: the product's name as sold, brand included, without the
     * pack size, at most six words. Dropping part of the brand gave searches like "mr black salt".
     */
    static String query(SwapCatalogue.Product product) {
        List<String> words = words(product.displayName());
        return String.join(" ", words.subList(0, Math.min(6, words.size())));
    }

    /** True for an add button, false for an out-of-stock marker, null for anything else. */
    private static Boolean stockMarker(String t) {
        if (t.equals("add") || t.equals("add to cart") || t.equals("+ add") || t.equals("add to basket")) return true;
        for (String word : OUT_WORDS) if (t.equals(word) || t.startsWith(word)) return false;
        return null;
    }

    private static Double grams(Matcher m) {
        double value = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        if (unit.equals("kg") || unit.equals("l") || unit.startsWith("lit") || unit.equals("ltr")) value *= 1000;
        return value;
    }

    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String w : normalize(text).split(" ")) {
            if (w.length() < 2 || STOP.contains(w) || w.matches("\\d+")) continue;
            if (!out.contains(w)) out.add(w);
        }
        return out;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace("'s", "s").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static boolean containsWord(String text, String word) {
        return (" " + text + " ").contains(" " + word + " ") || (" " + text + " ").contains(" " + word + "s ");
    }

    private SwapCheck() {}
}
