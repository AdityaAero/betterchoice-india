package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Version 4: the BetterChoice database (Supabase) is the only source of product facts. The bubble reads the product's
 * name from the shop's page, the database finds it among its listed products (by trigram and vector similarity, see
 * supabase/migrations/*_v4_catalogue_vector.sql) and returns its record, its category and the category's better
 * candidates in one answer. The rating, the report and the choice of three are then made by the same code as version
 * 3, so a database record is rated exactly as the same label read on screen would be.
 *
 * <p>The shopper's health answers never leave the phone: the database returns what is better in general, and their
 * allergies, diet, conditions and goals are applied here.
 */
final class CatalogueDb {
    /** A product the database recognised, with everything needed to rate it and choose its alternatives. */
    static final class Match {
        final String gtin, method, version;
        final double score;
        final SwapCatalogue.Product product;
        final SwapCatalogue.Group group;
        /** The category's better candidates, best first, as a one-category catalogue for {@link SwapEngine}. */
        final SwapCatalogue alternatives;
        /** The database's own report of the product (colour, letter, lines), from the same engine. */
        final JSONObject report;

        Match(String gtin, String method, String version, double score, SwapCatalogue.Product product,
              SwapCatalogue.Group group, SwapCatalogue alternatives, JSONObject report) {
            this.gtin = gtin; this.method = method; this.version = version; this.score = score; this.product = product;
            this.group = group; this.alternatives = alternatives; this.report = report;
        }
    }

    /** The answer to one lookup: a match, a definite "not listed", or no answer at all (offline, timed out). */
    static final class Lookup {
        final Match match;
        final boolean listed;
        final boolean failed;
        final String version;
        /** The nearest listed names on a miss, for diagnostics; never shown as if they were this product. */
        final List<String> nearest;
        final boolean fromCache;

        private Lookup(Match match, boolean listed, boolean failed, String version, List<String> nearest, boolean fromCache) {
            this.match = match; this.listed = listed; this.failed = failed; this.version = version;
            this.nearest = nearest; this.fromCache = fromCache;
        }
        static Lookup failed() { return new Lookup(null, false, true, null, Collections.emptyList(), false); }
        Lookup cached() { return new Lookup(match, listed, failed, version, nearest, true); }
    }

    /** Reads one answer of public.bc_lookup. Throws on a malformed answer, so it is never cached. */
    static Lookup parse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        String version = root.optString("v", null);
        JSONObject match = root.optJSONObject("match");
        if (match == null) {
            List<String> nearest = new ArrayList<>();
            JSONArray near = root.optJSONArray("nearest");
            if (near != null) for (int i = 0; i < near.length(); i++) nearest.add(near.getJSONObject(i).optString("name"));
            return new Lookup(null, false, false, version, nearest, false);
        }
        SwapCatalogue.Product product = new SwapCatalogue.Product(root.getJSONObject("product"));
        SwapCatalogue.Group group = SwapCatalogue.group(root.getJSONObject("group"));
        List<SwapCatalogue.Product> candidates = new ArrayList<>();
        JSONArray alternatives = root.optJSONArray("alternatives");
        if (alternatives != null) {
            for (int i = 0; i < alternatives.length(); i++) candidates.add(new SwapCatalogue.Product(alternatives.getJSONObject(i)));
        }
        SwapCatalogue catalogue = SwapCatalogue.of(version, Collections.singletonList(group), candidates);
        Match m = new Match(match.getString("gtin"), match.optString("method"), version, match.optDouble("score", 0), product, group,
            catalogue, root.optJSONObject("report"));
        return new Lookup(m, true, false, version, Collections.emptyList(), false);
    }

    /** The declared ingredients and allergen statement, written as a label prints them, for the safety checks. */
    static String labelText(SwapCatalogue.Product p) {
        StringBuilder text = new StringBuilder();
        if (p.ingredients != null && !p.ingredients.isBlank()) text.append("Ingredients: ").append(p.ingredients).append('\n');
        String allergens = p.allergenText();
        if (!allergens.isEmpty()) text.append("Allergen Information: ").append(allergens).append('\n');
        return text.toString();
    }

    /**
     * Rates a database record as the same label read on screen would be rated, under the record's own name (so the
     * phone and the database's own report agree), then shows it for the page the shopper is on.
     */
    static GradeResult grade(SwapCatalogue.Product p, ProductPage shown) {
        ProductPage record = ProductPage.of(p.displayName(), shown == null ? null : shown.price,
            shown == null ? null : shown.retailer);
        String label = labelText(p);
        Beverage identity = Beverage.classify(p.displayName(), label, p.per100.beverage);
        if (p.sweetener) identity = identity.withSweetener();
        GradeResult rated = GradeResult.graded(p.per100.copy(), GradeResult.Source.DATABASE, record, p.gtin, identity, label);
        return shown == null ? rated : rated.onPage(shown);
    }

    /**
     * The rating for the page, with the database match attached for the card's choices. A record our nutritionist has
     * not yet checked against the pack is rated as version 3 rates Open Food Facts data, and says so.
     */
    static GradeResult rate(ProductPage shown, Match m) {
        GradeResult rated = grade(m.product, shown);
        rated.db = m;
        if (!m.product.verified()) {
            List<String> caveats = new ArrayList<>(rated.caveats);
            caveats.add("From Open Food Facts: our nutritionist has not yet checked this record against the pack.");
            rated.caveats = Collections.unmodifiableList(caveats);
        }
        return rated;
    }

    private CatalogueDb() {}
}
