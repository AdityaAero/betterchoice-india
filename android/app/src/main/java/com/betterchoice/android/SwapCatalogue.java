package com.betterchoice.android;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The curated catalogue of healthier swaps: substitution groups and the products in them.
 *
 * <p>This is the slow, curated half of the recommendation system. It answers "which products could
 * reasonably replace this one, and why", and nothing about price or stock, which belong to a shop, an
 * address and a moment and are never stored here. It holds products of every colour: whether one is
 * better than the product on screen, for this shopper, is decided on the phone ({@link SwapEngine}).
 *
 * <p>Every product carries a status. {@code verified} means a nutritionist has checked it against the
 * physical pack. {@code draft} means it was drafted by the curation assistant
 * ({@code tools/curate_swaps.py}) from Open Food Facts and is waiting for that check. Drafts are only
 * ever shown as drafts, and only in test builds.
 */
final class SwapCatalogue {
    static final String ASSET = "catalogue/swaps_v1.json";

    static final class Group {
        final String id, name, search;
        final boolean drink;
        /** What a swap must improve, in order of importance: sugars, saturated_fat, sodium, energy, fibre. */
        final List<String> focus;
        final List<Occasion> occasions;
        final String[] match, exclude;
        /**
         * "High" levels a swap in this group may reach that the original did not, set by the
         * nutritionist and always stated on the card. Empty unless there is a reason, e.g. nut butters
         * are more energy-dense than the sweet spreads they replace.
         */
        final List<String> allowNewHigh;

        Group(String id, String name, boolean drink, String search, List<String> focus, List<Occasion> occasions,
              String[] match, String[] exclude, List<String> allowNewHigh) {
            this.id = id; this.name = name; this.drink = drink; this.search = search; this.focus = focus;
            this.occasions = occasions; this.match = match; this.exclude = exclude; this.allowNewHigh = allowNewHigh;
        }

        /** Whole-word match on the product's name, the same rule the curation assistant used. */
        boolean fits(String productName) {
            // Punctuation becomes a space, exactly as the curation script does, so "Kit-Kat" and
            // "Coca-Cola" are read the same way in both places.
            String name = productName == null ? "" : productName.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}&]+", " ");
            return Beverage.containsWord(name, match) && !Beverage.containsWord(name, exclude);
        }
    }

    static final class Product {
        final String id, gtin, brand, name, groupId, unit, ingredients, veg, status, source, sourceUrl;
        /** When the label was last evidenced (pack photo or record date), as yyyy-mm-dd; null when unknown. */
        final String labelDate;
        /** The group Open Food Facts' own categories put it in, when they name one: a second reading of what it is. */
        final String offGroup;
        final Double pack;
        final boolean beverage, sweetener;
        /** True only when Open Food Facts records a halal label for this barcode. */
        final boolean halal;
        final Nutrition per100;
        final List<String> contains, mayContain;

        Product(JSONObject o) {
            id = o.optString("id"); gtin = text(o, "gtin"); brand = text(o, "brand"); name = text(o, "name");
            groupId = o.optString("group"); unit = text(o, "unit"); ingredients = text(o, "ingredients");
            veg = text(o, "veg"); status = o.optString("status", "draft"); source = text(o, "source");
            sourceUrl = text(o, "source_url");
            labelDate = text(o, "label_date");
            offGroup = text(o, "off_group");
            pack = o.isNull("pack") ? null : o.optDouble("pack");
            beverage = o.optBoolean("beverage", false);
            sweetener = o.optBoolean("nss", false);
            JSONObject p = o.optJSONObject("per100");
            per100 = new Nutrition();
            per100.basisStated = true;
            if (p != null) {
                per100.energyKcal = number(p, "energy_kcal"); per100.sugarsG = number(p, "sugars_g");
                per100.saturatedFatG = number(p, "saturated_fat_g"); per100.sodiumMg = number(p, "sodium_mg");
                per100.proteinG = number(p, "protein_g"); per100.fibreG = number(p, "fibre_g");
                per100.totalFatG = number(p, "total_fat_g"); per100.transFatG = number(p, "trans_fat_g");
                per100.carbohydrateG = number(p, "carbohydrate_g"); per100.addedSugarsG = number(p, "added_sugars_g");
            }
            halal = o.optBoolean("halal", false);
            per100.servingSize = o.isNull("serving_g") || !o.has("serving_g") ? null : o.optDouble("serving_g");
            per100.beverage = beverage;
            per100.netQuantity = pack;
            contains = strings(o.optJSONArray("contains"));
            mayContain = strings(o.optJSONArray("may_contain"));
        }

        boolean verified() { return "verified".equals(status); }

        /** "Mr Makhana (Black Salt)" with its brand in front only when the name does not already say it. */
        String displayName() {
            if (brand == null || brand.isEmpty()) return name;
            String lowerName = name.toLowerCase(Locale.ROOT);
            String firstBrandWord = brand.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")[0];
            return !firstBrandWord.isEmpty() && lowerName.contains(firstBrandWord) ? name : brand + " " + name;
        }

        /** "60 g", "500 ml", or empty when the pack size was not recorded. */
        String packText() {
            if (pack == null) return "";
            String u = unit == null ? (beverage ? "ml" : "g") : unit;
            if (pack >= 1000 && pack % 1000 == 0) return Nutrition.number(pack / 1000) + (u.equals("ml") ? " l" : " kg");
            return Nutrition.number(pack) + " " + u;
        }

        /** The allergen declaration as a label would print it, for the safety check. */
        String allergenText() {
            StringBuilder text = new StringBuilder();
            if (!contains.isEmpty()) text.append("Contains: ").append(String.join(", ", contains)).append(". ");
            if (!mayContain.isEmpty()) text.append("May contain: ").append(String.join(", ", mayContain)).append('.');
            return text.toString();
        }
    }

    final String version, attribution;
    final List<Group> groups;
    final List<Product> products;

    private SwapCatalogue(String version, String attribution, List<Group> groups, List<Product> products) {
        this.version = version; this.attribution = attribution;
        this.groups = Collections.unmodifiableList(groups); this.products = Collections.unmodifiableList(products);
    }

    private static volatile SwapCatalogue loaded;

    /** Loaded once from the app's assets; an unreadable catalogue simply means no swaps are offered. */
    static SwapCatalogue get(Context context) {
        SwapCatalogue catalogue = loaded;
        if (catalogue != null) return catalogue;
        synchronized (SwapCatalogue.class) {
            if (loaded != null) return loaded;
            try (InputStream in = context.getAssets().open(ASSET)) {
                loaded = parse(read(in));
            } catch (Exception unreadable) {
                DebugLog.failure("Swap catalogue unavailable");
                loaded = new SwapCatalogue("none", "", new ArrayList<>(), new ArrayList<>());
            }
            return loaded;
        }
    }

    static SwapCatalogue parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        List<Group> groups = new ArrayList<>();
        JSONArray g = root.getJSONArray("groups");
        for (int i = 0; i < g.length(); i++) {
            JSONObject o = g.getJSONObject(i);
            List<Occasion> occasions = new ArrayList<>();
            for (String code : strings(o.optJSONArray("occasions"))) {
                Occasion occasion = Occasion.fromCode(code);
                if (occasion != null) occasions.add(occasion);
            }
            groups.add(new Group(o.getString("id"), o.getString("name"), "drink".equals(o.optString("branch")),
                o.optString("search"), strings(o.optJSONArray("focus")), occasions,
                strings(o.optJSONArray("match")).toArray(new String[0]),
                strings(o.optJSONArray("exclude")).toArray(new String[0]), strings(o.optJSONArray("allow_new_high"))));
        }
        List<Product> products = new ArrayList<>();
        JSONArray p = root.getJSONArray("products");
        for (int i = 0; i < p.length(); i++) {
            Product product = new Product(p.getJSONObject(i));
            if (product.name != null && product.per100.energyKcal != null) products.add(product);
        }
        return new SwapCatalogue(root.optString("version"), root.optString("attribution"), groups, products);
    }

    /**
     * The substitution group the product in front of the shopper belongs to, or null. Groups are
     * tried in the catalogue's order, which puts specific groups (flavoured milk, cereals) before
     * broad ones (salty snacks), and drinks are only ever placed in drink groups.
     */
    Group groupFor(String productName, boolean drink) {
        // Zepto titles add claims after a bar: "Let's Try Rock Salt Potato Wafers | 100% Groundnut Oil".
        // What the product is comes first, so the part before the bar decides; the whole title is read
        // only when that part names no group ("Lay's Classic Salted | Crunchy Potato Chips").
        String head = productName == null ? null : productName.split("\\|", 2)[0];
        if (head != null && head.length() < productName.length()) {
            for (Group group : groups) if (group.drink == drink && group.fits(head)) return group;
        }
        for (Group group : groups) {
            if (group.drink != drink) continue;
            if (group.fits(productName)) return group;
        }
        return null;
    }

    /**
     * The group for the product on screen, using what the shop itself says the product is when the page says it. The
     * declared type decides first ("Product Type: Cookies"); when it names no group, a group found from the product's
     * name stands only if the declared type does not put the product in another FSSAI food category. "ZOFF Soya Chunks -
     * Soya Nuggets" (type "Soya Chunks", a protein product) was being offered frozen chicken nuggets.
     */
    Group placeFor(String productName, String productType, boolean drink) {
        if (productType != null && !productType.isBlank()) {
            Group byType = groupFor(productType, drink);
            if (byType != null) return byType;
        }
        Group byName = groupFor(productName, drink);
        if (byName == null || productType == null || productType.isBlank()) return byName;
        FoodCategory.Fcs declared = FoodCategory.place(productType, false, null).category;
        return declared != null && declared != FoodCategory.fromSwapGroup(byName.id) ? null : byName;
    }

    List<Product> inGroup(String groupId) {
        List<Product> members = new ArrayList<>();
        for (Product product : products) if (product.groupId.equals(groupId)) members.add(product);
        return members;
    }

    boolean hasDrafts() {
        for (Product product : products) if (!product.verified()) return true;
        return false;
    }

    private static String text(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        String value = o.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static Double number(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        double value = o.optDouble(key, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    private static List<String> strings(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, null);
            if (value != null && !value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
