package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort recognition of a single product page from visible text. Retailer layouts change,
 * so every rule here is heuristic and must be checked against real screens on a device.
 */
final class ProductPage {
    final String name;
    final Double price;
    final String retailer;
    final String text;
    final boolean food;
    /** A full-screen photo of this product, rather than its page. */
    final boolean viewer;

    private ProductPage(String name, Double price, String retailer, String text, boolean food) {
        this(name, price, retailer, text, food, false);
    }

    private ProductPage(String name, Double price, String retailer, String text, boolean food, boolean viewer) {
        this.name = name; this.price = price; this.retailer = retailer; this.text = text; this.food = food;
        this.viewer = viewer;
    }

    /** Rebuilds a page identity passed to the add-details screen (no screen text). */
    static ProductPage of(String name, Double price, String retailer) {
        return new ProductPage(name, price, retailer, "", true);
    }

    /** Stable identity used to avoid re-analysing the same product and to remember "skip". */
    String key() {
        return normalize(name) + "|" + (price == null ? "" : Nutrition.number(price)) + "|" + retailer;
    }

    private static final String[] DETAIL_MARKERS = {
        "product details", "view product details", "view details", "view more details", "product information",
        "about the product", "about this item", "highlights", "ingredients", "nutrition", "nutritional",
        "shelf life", "country of origin", "manufacturer details", "fssai", "key features", "marketed by"};
    private static final String[] FOOD_MARKERS = {"ingredients", "nutrition", "nutritional", "fssai", "energy (kcal)", "veg", "best before"};
    private static final String[] PAYMENT_MARKERS = {
        "upi pin", "card number", "cvv", "proceed to pay", "payment options", "select payment", "pay using",
        "net banking", "enter otp", "place order"};
    private static final Pattern NOT_TITLE = Pattern.compile("\\b(search|delivery|deliver to|cart|login|sign in|" +
        "offers?|add|share|similar products|you might also like|ratings?|reviews|see all|view all)\\b|% off");
    /**
     * The shop's own spoken summary of the product at the top of the page: "Amul Lassi, 1 pack
     * (200 ml), MRP is ₹20, Price with Zepto Now is ₹19, …". A short name like "Amul Lassi" is on
     * screen nowhere else, and "View all Amul products" was taken for it.
     */
    private static final Pattern SHOP_SUMMARY = Pattern.compile(
        "^(.{3,140}?),\\s*\\d+\\s*(?:x\\s*)?(?:packs?|pcs?|pieces?|units?)\\s*\\([^)]*\\),\\s*mrp is", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_PRICE = Pattern.compile(
        "price with [\\p{L} ]{2,30} is\\s*₹\\s?(\\d[\\d,]{0,6}(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile("₹\\s?(\\d[\\d,]{0,6}(?:\\.\\d{1,2})?)");
    private static final Pattern TABLE_ROW = Pattern.compile("\\((?:g|mg|kcal|kj|mcg)\\)|\\b\\d+(?:\\.\\d+)?\\s*kcal\\b");

    static boolean isPaymentScreen(List<ScreenText> items) {
        String joined = join(items);
        for (String marker : PAYMENT_MARKERS) if (joined.contains(marker)) return true;
        return false;
    }

    static ProductPage detect(List<ScreenText> items, int screenHeight, String retailer) {
        return detect(items, screenHeight, retailer, null, null);
    }

    static ProductPage detect(List<ScreenText> items, int screenHeight, String retailer, StringBuilder notes) {
        return detect(items, screenHeight, retailer, notes, null);
    }

    /**
     * Headings of the shop's own recommendation rails. Everything after one of these belongs to other
     * products, so neither the title nor the price is ever taken from there. Reading "the first price
     * on screen" once scrolled picked a neighbouring product's ₹48 for a ₹60 Appy Fizz.
     */
    private static final String[] RAIL_HEADINGS = {"similar products", "you might also like", "you may also like",
        "people also bought", "customers also bought", "frequently bought together", "bought together",
        "related products", "similar items", "top products in this category", "more from", "explore more",
        "recommended for you", "complete your cart", "top picks"};

    /**
     * @param anchor the product this same screen was already identified as, or null. Scrolling a
     *               product page moves its title off screen; without the anchor, a description line
     *               ("Refreshing sparkling apple beverage, caffeine-free…") was taken for the name, and
     *               the product was treated as a new one.
     */
    static ProductPage detect(List<ScreenText> items, int screenHeight, String retailer, StringBuilder notes,
                              ProductPage anchor) {
        return detect(items, screenHeight, retailer, notes, anchor, null);
    }

    /**
     * @param photosOf the product whose page was on screen just before a new window opened, or null.
     *                 A full-screen photo viewer carries almost no text, so on its own it identifies
     *                 nothing, and the back-of-pack photo a shopper opened to be read was dropped.
     *                 Only a screen that looks like a photo viewer is given this product's identity;
     *                 any real page is identified from its own text as before.
     */
    static ProductPage detect(List<ScreenText> items, int screenHeight, String retailer, StringBuilder notes,
                              ProductPage anchor, ProductPage photosOf) {
        if (items != null && (join(items).contains("show all results for") || join(items).contains("recent searches"))) {
            note(notes, "search suggestions, not a photo viewer or product page");
            return null;
        }
        ProductPage viewer = anchor != null ? anchor : photosOf;
        if (viewer != null && items != null && photoViewer(items)) {
            note(notes, "photo viewer of '" + viewer.name + "', texts=" + items.size());
            return new ProductPage(viewer.name, viewer.price, retailer, joinOriginal(items), viewer.food, true);
        }
        if (items == null || items.isEmpty() || isPaymentScreen(items)) {
            note(notes, items == null || items.isEmpty() ? "no text" : "payment screen");
            return null;
        }
        // Accessibility traversal is breadth-first, not visual order. A toolbar title
        // may arrive after a recommendation rail even though it is at the very top.
        items = new ArrayList<>(items);
        items.sort(Comparator.comparingInt(item -> item.top));
        if (FLIPKART.equals(retailer)) items = pairGridRows(items);
        String joined = join(items);
        if (joined.contains("show all results for") || joined.contains("recent searches")) {
            note(notes, "search suggestions, not a product page");
            return null;
        }
        boolean marker = false;
        for (String m : DETAIL_MARKERS) if (joined.contains(m)) { marker = true; break; }
        boolean nutritionVisible = joined.contains("nutrition") || joined.contains("ingredients")
            || Pattern.compile("(?:sugars?|saturated fat|sodium|energy)\\s+per\\s*100\\s*(?:g|ml)\\b").matcher(joined).find()
            || nutrientRows(joined) >= 3;
        if (nutritionVisible) marker = true;
        // Zepto labels its product title for screen readers: "Product Name is …". Only product pages do.
        if (joined.contains("product name is")) marker = true;
        int addButtons = 0;
        for (ScreenText item : items) {
            String t = item.text.trim().toLowerCase(Locale.ROOT);
            if (t.equals("add") || t.equals("add to cart") || t.equals("add to basket")) addButtons++;
        }
        // Listing and search pages show many ADD buttons; a product page shows its own plus a short carousel.
        // A page that names its own product for screen readers is a product page, however many ADD
        // buttons its rails below carry; judging it a listing lost the product halfway down the page.
        boolean labelledPage = joined.contains("product name is");
        if (!marker || (addButtons > 6 && !nutritionVisible && !labelledPage)) {
            note(notes, (marker ? "listing: " + addButtons + " ADD buttons" : "no product-detail marker")
                + ", texts=" + items.size());
            return null;
        }

        int rail = railStart(items);
        ScreenText titleItem = title(items, screenHeight, rail);
        String name = titleItem == null ? null : cleanTitle(titleItem.text.trim());
        if (FLIPKART.equals(retailer) && titleItem != null) name = withBrand(items, titleItem, name);
        Double price = priceAfter(items, titleItem, rail);
        // A title the shop itself labels ("Product Name is …", or its spoken summary) is the name.
        boolean labelled = titleItem != null && TITLE_PREFIX.matcher(titleItem.text.trim()).find();
        if (!labelled) {
            for (int i = 0; i < Math.min(rail, items.size()); i++) {
                ScreenText item = items.get(i);
                Matcher summary = SHOP_SUMMARY.matcher(item.text.trim());
                if (item.clickable || !summary.find()) continue;
                name = summary.group(1).trim();
                titleItem = item;
                labelled = true;
                Matcher own = SUMMARY_PRICE.matcher(item.text);
                if (own.find()) price = Double.parseDouble(own.group(1).replace(",", ""));
                break;
            }
        }
        if (anchor != null && labelled && name != null) {
            // Same product as before: keep its price, so its key (and what was learnt under it) holds.
            if (name.equals(anchor.name) && anchor.price != null) price = anchor.price;
        } else if (anchor != null) {
            ScreenText anchorItem = find(items, anchor.name);
            // A different product's page shows its own full title in the upper part of the screen.
            // Short title-case scraps ("Pack of 2") and "Label: value" lines never count as one.
            boolean newTitleAtTop = titleItem != null && name != null && !name.equals(anchor.name)
                && titleLike(titleItem.text) && name.length() >= 15 && name.split("\\s+").length >= 3
                && name.indexOf(':') < 0 && (screenHeight <= 0 || titleItem.top <= screenHeight * 0.6);
            if (anchorItem != null) {
                // Still on screen: same product. Its price is the one printed after its own title.
                name = anchor.name;
                Double own = priceAfter(items, anchorItem, rail);
                price = own != null ? own : anchor.price;
            } else if (!newTitleAtTop) {
                // Scrolled down the same product page: keep what it was identified as.
                name = anchor.name;
                price = anchor.price;
            }
        }
        if (name == null) { note(notes, "no title candidate, texts=" + items.size()); return null; }
        // Everything about the product is read from its own text. The shop's rails of other
        // products are left out: on Zepto's oats page "Yoga Bar … Almond" under "Similar Products"
        // made the oats "contain tree nuts", and "No Added Sugar / 1 pack (250 g)" became 1 g of sugar.
        List<ScreenText> own = ownItems(items);
        String ownJoined = join(own);
        boolean food = nutritionVisible || FoodWords.isFood(name) || containsAny(ownJoined, FOOD_MARKERS);
        // Once a product's own page has shown it is food, it stays food while the shopper scrolls past the label:
        // the lower part of a page ("Packaging Type", "Storage") says nothing either way, and "ZOFF Soya Chunks"
        // turned "not food" there.
        if (anchor != null && anchor.food && name.equals(anchor.name)) food = true;
        if (FoodWords.isNonFood(name)) food = false;
        note(notes, "product '" + name + "' price=" + price + " food=" + food
            + " nutritionText=" + nutritionVisible + " texts=" + items.size() + " own=" + own.size());
        return new ProductPage(name, price, retailer, joinOriginal(own), food);
    }

    /**
     * Headings of the product's own sections. A rail ends at one of these, for layouts that put a
     * rail between the title and the details.
     */
    private static final Pattern OWN_SECTION = Pattern.compile(
        "(?:product details|view product details|view details|product information|product description|"
        + "description|about (?:the|this) (?:product|item)|highlights|information|ingredients?|"
        + "nutrition(?:al)? (?:information|facts|info)|key features|more details)\\s*:?");

    /**
     * A full-screen photo viewer: a few short labels ("1/6", "Close"), no price, no add button, no
     * search box. Deliberately narrow, so a listing, a results page or a sheet is never mistaken for one.
     */
    static boolean photoViewer(List<ScreenText> items) {
        if (items.size() > 8) return false;
        int letters = 0;
        for (ScreenText item : items) {
            String t = item.text.trim().toLowerCase(Locale.ROOT);
            if (t.indexOf('₹') >= 0 || t.equals("add") || t.startsWith("add to") || t.contains("search")
                    || t.contains("cart") || t.contains("deliver")) return false;
            letters += t.replaceAll("[^\\p{L}]", "").length();
        }
        return letters <= 120;
    }

    /**
     * Another product's card, as Zepto's newer layout describes it to accessibility: one tappable
     * item reading "₹50, Yoga Bar Chocolate Chunk …, Almonds, High Dietary Fibre, 1 pc (35 g)". These
     * rails carry no heading, so the first card is where the rail starts.
     */
    private static final Pattern CARD = Pattern.compile(
        "^(?:add to cart\\s*,\\s*)?₹\\s?[\\d,]+(?:\\.\\d+)?\\s*,\\s*(?:₹|\\p{L})");

    static final String FLIPKART = "com.flipkart.android";

    /**
     * Flipkart prints the brand on its own line above the rest of the name: "Sunfeast Dark Fantasy"
     * over "Bourbon Biscotti Biscuit (99 g)", "Bikaji" over "Classic Salted Chips (85 g)". Without it
     * the product was called "Bourbon Biscotti Biscuit (99 g)". Only a short line directly above,
     * with no price or digits, is taken as the brand.
     */
    private static String withBrand(List<ScreenText> items, ScreenText title, String name) {
        int at = items.indexOf(title);
        for (int i = at - 1; i >= 0; i--) {
            ScreenText above = items.get(i);
            int gap = title.top - above.top;
            if (gap <= 0) continue;
            if (gap > 110) break;
            String t = above.text.trim();
            if (t.isEmpty() || t.length() > 40 || t.matches(".*[\\d₹%|].*") || t.split("\\s+").length > 4
                    || NOT_TITLE.matcher(t.toLowerCase(Locale.ROOT)).find() || !titleLike(t)) break;
            return name.toLowerCase(Locale.ROOT).startsWith(t.toLowerCase(Locale.ROOT)) ? name : t + " " + name;
        }
        return name;
    }

    /**
     * Flipkart lays "Product highlights" and "Specifications" out as a grid: a row of two labels
     * ("Pack of", "Brand"), then a row of their two values ("1", "Bikaji"). Read top to bottom that
     * became "Pack of / Brand / 1 / Bikaji", pairing each label with the wrong value. A label row
     * followed within 60 px by a value row with the same columns is re-ordered label, value, label,
     * value. Rows further apart are left alone.
     */
    static List<ScreenText> pairGridRows(List<ScreenText> sorted) {
        List<List<ScreenText>> rows = new ArrayList<>();
        for (ScreenText item : sorted) {
            List<ScreenText> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (last != null && Math.abs(last.get(0).top - item.top) <= 6) last.add(item);
            else { List<ScreenText> row = new ArrayList<>(); row.add(item); rows.add(row); }
        }
        for (List<ScreenText> row : rows) row.sort(Comparator.comparingInt(item -> item.left));
        List<ScreenText> out = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<ScreenText> labels = rows.get(r);
            List<ScreenText> values = r + 1 < rows.size() ? rows.get(r + 1) : null;
            if (values != null && labels.size() >= 2 && labels.size() == values.size()
                    && values.get(0).top - labels.get(0).top <= 60 && sameColumns(labels, values)) {
                for (int c = 0; c < labels.size(); c++) { out.add(labels.get(c)); out.add(values.get(c)); }
                r++;
            } else {
                out.addAll(labels);
            }
        }
        return out;
    }

    /** Both rows have the same distinct columns (at least 100 px apart), so they are one grid. */
    private static boolean sameColumns(List<ScreenText> a, List<ScreenText> b) {
        for (int i = 0; i < a.size(); i++) {
            if (Math.abs(a.get(i).left - b.get(i).left) > 30) return false;
            if (i > 0 && a.get(i).left - a.get(i - 1).left < 100) return false;
        }
        return true;
    }

    /** The screen without recommendation rails: from a rail heading, or the first product card, to the next own section. */
    static List<ScreenText> ownItems(List<ScreenText> sorted) {
        List<ScreenText> own = new ArrayList<>();
        boolean inRail = false;
        for (ScreenText item : sorted) {
            String t = item.text.trim().toLowerCase(Locale.ROOT);
            if (t.length() <= 40 && containsAny(t, RAIL_HEADINGS)) { inRail = true; continue; }
            if (item.clickable && CARD.matcher(t).find()) { inRail = true; continue; }
            if (inRail && OWN_SECTION.matcher(t).matches()) inRail = false;
            if (!inRail) own.add(item);
        }
        return own;
    }

    /**
     * A table of nutrient rows with no heading on screen: Zepto's expanded "View More" shows
     * "Total Sugar / 24.4g", "Saturated Fat / 32.1g", "Sodium / 18mg" and nothing that says
     * "nutrition", so the screen with the full table was not recognised as a product page at all.
     */
    private static final Pattern NUTRIENT_ROW = Pattern.compile("(?m)^\\s*(total sugars?|added sugars?|sugars?|"
        + "saturated fat|trans fat|sodium|calories|energy|protein|carbohydrates?|total fat|fats?|dietary fib(?:re|er))\\s*$");

    static int nutrientRows(String joinedLower) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        Matcher m = NUTRIENT_ROW.matcher(joinedLower);
        while (m.find()) seen.add(m.group(1));
        return seen.size();
    }

    private static ScreenText title(List<ScreenText> items, int screenHeight, int rail) {
        List<ScreenText> candidates = new ArrayList<>();
        int minTop = screenHeight > 0 ? (int) (screenHeight * 0.05) : Integer.MIN_VALUE;
        int maxTop = screenHeight > 0 ? (int) (screenHeight * 0.75) : Integer.MAX_VALUE;
        // The shop naming its own title for screen readers settles it.
        for (int i = 0; i < Math.min(rail, items.size()); i++) {
            ScreenText item = items.get(i);
            if (TITLE_PREFIX.matcher(item.text.trim()).find() && item.top <= maxTop
                    && cleanTitle(item.text.trim()).length() >= 6) return item;
        }
        for (int i = 0; i < Math.min(rail, items.size()); i++) {
            ScreenText item = items.get(i);
            String t = item.text.trim();
            String lower = t.toLowerCase(Locale.ROOT);
            if (t.length() < 6 || t.length() > 140 || t.indexOf('₹') >= 0 || !t.matches(".*\\p{L}.*")) continue;
            if (t.split("\\s+").length < 2 || item.top < minTop || item.top > maxTop) continue;
            if (NOT_TITLE.matcher(lower).find() || containsAny(lower, DETAIL_MARKERS)) continue;
            if (lower.matches(".*\\bper\\s*100\\s*(?:g|ml)\\b.*")) continue;
            // A nutrition row, an ingredient list or a pack line is never the name, however long.
            if (TABLE_ROW.matcher(lower).find() || listLike(lower) || PACK_LINE.matcher(lower).find()) continue;
            if (lower.matches("\\d+\\s*(?:minutes?|mins?)(?:\\s.*)?")) continue;
            // Flipkart's "Expiry 26 Nov 2026" badge sits beside the title.
            if (lower.matches("(?:expiry\\s*)?\\d{1,2}\\s+[a-z]{3,9}\\s+\\d{4}")) continue;
            candidates.add(item);
        }
        if (candidates.isEmpty()) return null;
        // A product name is written like one: mostly capitalised words, not a sentence. Preferring
        // these stops a long description from winning the "longest of the first few" rule below.
        List<ScreenText> named = new ArrayList<>();
        for (ScreenText candidate : candidates) if (titleLike(candidate.text)) named.add(candidate);
        if (!named.isEmpty()) candidates = named;
        candidates.sort(Comparator.comparingInt(c -> c.top));
        // Product titles sit near the top and are usually the longest of the first few text items.
        ScreenText best = candidates.get(0);
        for (int i = 1; i < Math.min(3, candidates.size()); i++) {
            if (candidates.get(i).text.trim().length() > best.text.trim().length()) best = candidates.get(i);
        }
        return best;
    }

    private static final Pattern PACK_LINE = Pattern.compile(
        "^(?:net (?:quantity|qty|weight)\\s*:?\\s*)?\\d+\\s*(?:x\\s*)?(?:packs?|pcs?|pieces?|units?|pouch|bottles?|jars?|box)\\b");

    /**
     * Four or more short comma-separated items: "Sugar, Milk Solids (25%), Coffee (13%), Hydrogenated
     * Vegetable Oil". A title with commas has long parts ("Yoga Bar Muesli Dark Chocolate + Cranberry,
     * Omega - 3, Nuts & Seeds, Fibre Rich"), and rejecting any title with commas made that muesli
     * "1 pack (700 g)".
     */
    static boolean listLike(String lower) {
        String[] parts = lower.split(",");
        if (parts.length < 4) return false;
        for (String part : parts) if (part.trim().split("\\s+").length > 4) return false;
        return true;
    }

    /** At least half the words start with a capital or a digit, and it does not read as a sentence. */
    static boolean titleLike(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.endsWith(".")) return false;
        String[] words = t.split("\\s+");
        if (words.length > 18) return false;
        int counted = 0, capital = 0;
        for (String word : words) {
            int i = 0;
            while (i < word.length() && !Character.isLetterOrDigit(word.charAt(i))) i++;
            if (i >= word.length()) continue;
            counted++;
            if (Character.isUpperCase(word.charAt(i)) || Character.isDigit(word.charAt(i))) capital++;
        }
        return counted > 0 && capital * 2 >= counted;
    }

    /** Index of the first recommendation-rail heading, or the end of the list when there is none. */
    private static int railStart(List<ScreenText> items) {
        for (int i = 0; i < items.size(); i++) {
            if (containsAny(items.get(i).text.toLowerCase(Locale.ROOT), RAIL_HEADINGS)) return i;
        }
        return items.size();
    }

    /** The first price printed after the title and before any recommendation rail. */
    private static Double priceAfter(List<ScreenText> items, ScreenText title, int rail) {
        int start = title == null ? 0 : Math.max(0, items.indexOf(title));
        for (int i = start; i < Math.min(rail, items.size()); i++) {
            Double price = priceAt(items, i);
            if (price != null) return price;
        }
        // No price after the title (some layouts print it above): the first one above any rail.
        for (int i = 0; i < Math.min(start, items.size()); i++) {
            Double price = priceAt(items, i);
            if (price != null) return price;
        }
        return null;
    }

    private static final Pattern BARE_AMOUNT = Pattern.compile("^\\d[\\d,]{0,6}(?:\\.\\d{1,2})?$");

    /**
     * The price in one item, or split over two: Zepto prints the selling price as "₹" and "183"
     * side by side and the struck-out MRP as "₹299" in one piece, so reading whole items only took
     * the MRP, and the product's key changed once the sticky header showed "₹183".
     */
    private static Double priceAt(List<ScreenText> items, int i) {
        String text = items.get(i).text.trim();
        if (text.equals("₹") && i + 1 < items.size()) {
            String next = items.get(i + 1).text.trim();
            if (BARE_AMOUNT.matcher(next).matches()) return Double.parseDouble(next.replace(",", ""));
        }
        Matcher m = PRICE.matcher(text);
        return m.find() ? Double.parseDouble(m.group(1).replace(",", "")) : null;
    }

    private static ScreenText find(List<ScreenText> items, String name) {
        if (name == null) return null;
        for (ScreenText item : items) if (name.equals(cleanTitle(item.text.trim()))) return item;
        return null;
    }

    /**
     * Screen readers get the title with a spoken prefix — Blinkit's reads "Product Name is Cadbury
     * Dairy Milk…" — and the card was printing it word for word. Only a prefix that is plainly a
     * label is removed; anything else in the title is left alone.
     */
    private static final Pattern TITLE_PREFIX = Pattern.compile(
        "^(?:product\\s+(?:name|title)|item\\s+name|name)\\s*(?:is\\b|[:\\-–])\\s*", Pattern.CASE_INSENSITIVE);

    static String cleanTitle(String title) {
        if (title == null) return null;
        String cleaned = TITLE_PREFIX.matcher(title).replaceFirst("").trim();
        return cleaned.length() >= 3 ? cleaned : title;
    }

    static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static void note(StringBuilder notes, String text) { if (notes != null) notes.append(text); }

    private static boolean containsAny(String text, String[] needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static String join(List<ScreenText> items) { return joinOriginal(items).toLowerCase(Locale.ROOT); }

    /** The shop's own "Product Type" line (Zepto: "Product Type / Soya Chunks"), read from the page's own text. */
    private static final Pattern PRODUCT_TYPE = Pattern.compile("(?i)\\bproduct type\\s*[,:\\n]\\s*([^,\\n|]{2,40})");

    /** What the shop itself says this product is, or null when the page does not say. */
    String productType() {
        Matcher m = PRODUCT_TYPE.matcher(text == null ? "" : text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String joinOriginal(List<ScreenText> items) {
        StringBuilder builder = new StringBuilder();
        for (ScreenText item : items) builder.append(item.text).append('\n');
        return builder.toString();
    }
}
