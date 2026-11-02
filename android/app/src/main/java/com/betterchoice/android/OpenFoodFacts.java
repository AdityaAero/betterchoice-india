package com.betterchoice.android;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/**
 * Looks a barcode up in Open Food Facts, the open food database, so a scan returns something on the
 * very first use instead of waiting for the BetterChoice database to fill up.
 *
 * <p>Only ever called with a checksum-valid GTIN, and only after our own database has missed. One
 * request, one product, nothing about the shopper is sent: no account, no location, no product name.
 *
 * <p>Open Food Facts data is licensed under the ODbL. Looking a product up live and showing the
 * result is ordinary use and is credited on the card. Copying their records into our own tables
 * would trigger the share-alike terms, so nothing from here is written to Supabase.
 */
final class OpenFoodFacts {
    /** Only the fields the rating needs; a narrow query keeps the response small and the scan fast. */
    private static final String FIELDS =
        "product_name,quantity,serving_size,nutriments,categories_tags";
    private static final int MAX_BYTES = 262144;

    interface Fetcher { String get(String url) throws Exception; }

    private final Fetcher fetcher;

    OpenFoodFacts() { this(OpenFoodFacts::https); }
    OpenFoodFacts(Fetcher fetcher) { this.fetcher = fetcher; }

    /** Per-100 nutrition for this pack, or null when it is unknown or the values are unusable. */
    Nutrition find(String gtin) {
        String normalized = Gtin.normalize(gtin);
        if (normalized == null) return null;
        // Their keys are the barcode as printed, so the padding we add for our own storage comes off.
        String barcode = Gtin.display(normalized);
        try {
            String body = fetcher.get("https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json?fields=" + FIELDS);
            if (body == null || body.isBlank()) return null;
            JSONObject response = new JSONObject(body);
            if (response.optInt("status", 0) != 1) { DebugLog.diagnostic("off: not in the open database"); return null; }
            Nutrition nutrition = read(response.optJSONObject("product"));
            DebugLog.diagnostic("off: " + (nutrition == null ? "found but not enough values" : "usable values"));
            return nutrition;
        } catch (Exception unavailable) {
            DebugLog.diagnostic("off lookup failed: " + unavailable.getClass().getSimpleName());
            return null;
        }
    }

    private static Nutrition read(JSONObject product) {
        if (product == null) return null;
        JSONObject n = product.optJSONObject("nutriments");
        if (n == null) return null;
        Nutrition nutrition = new Nutrition();
        nutrition.basisStated = true;   // their figures are per 100 g or 100 ml by definition
        Double energy = value(n, "energy-kcal_100g");
        if (energy == null) {
            Double kj = value(n, "energy_100g");
            if (kj != null) energy = kj / 4.184; // Their fallback field is kilojoules.
        }
        if (energy == null) return null;
        nutrition.energyKcal = energy;
        nutrition.sugarsG = value(n, "sugars_100g");
        nutrition.saturatedFatG = value(n, "saturated-fat_100g");
        nutrition.fibreG = value(n, "fiber_100g");
        nutrition.proteinG = value(n, "proteins_100g");
        // Total and trans fat are not graded; they let the card say when most of the fat is the
        // unsaturated kind. Kept only when they agree with the saturated figure.
        nutrition.totalFatG = value(n, "fat_100g");
        nutrition.transFatG = value(n, "trans-fat_100g");
        if (nutrition.totalFatG != null && (nutrition.totalFatG > 100 || (nutrition.saturatedFatG != null
                && nutrition.totalFatG < nutrition.saturatedFatG))) nutrition.totalFatG = null;
        if (nutrition.transFatG != null && (nutrition.transFatG > 100 || (nutrition.totalFatG != null
                && nutrition.transFatG > nutrition.totalFatG))) nutrition.transFatG = null;
        Double sodium = value(n, "sodium_100g");          // grams in their schema, milligrams in ours
        if (sodium != null) nutrition.sodiumMg = sodium * 1000;
        else {
            Double salt = value(n, "salt_100g");
            if (salt != null) nutrition.sodiumMg = salt * 1000 / 2.5; // salt = sodium x 2.5
        }
        nutrition.beverage = beverage(product);
        nutrition.netQuantity = amount(product.optString("quantity", ""), 20000);
        nutrition.servingSize = amount(product.optString("serving_size", ""), 1000);
        return nutrition.usable() ? nutrition : null;
    }

    /**
     * "150 g", "1 kg", "500 ml", "1 L" -> grams or millilitres. These arrive as a bare amount with
     * no label, so NutritionParser's patterns, which anchor on "Net Qty" and similar, do not apply.
     */
    static Double amount(String text, double limit) {
        if (text == null) return null;
        java.util.regex.Matcher m = AMOUNT.matcher(text.toLowerCase(java.util.Locale.ROOT));
        if (!m.find()) return null;
        double value;
        try { value = Double.parseDouble(m.group(1).replace(',', '.')); }
        catch (NumberFormatException notANumber) { return null; }
        String unit = m.group(2);
        if (unit.startsWith("kg") || unit.equals("l") || unit.startsWith("lit")) value *= 1000;
        return value > 0 && value <= limit ? value : null;
    }

    private static final java.util.regex.Pattern AMOUNT =
        java.util.regex.Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*(kg|g|gm|grams?|ml|l|lit(?:re|er)s?)\\b");

    /**
     * Whether their per-100 figures are per 100 ml. The pack's own unit decides first — chocolate is
     * sold in grams, milk in millilitres — because category tags are too loose: "beverages" also
     * tags drink powders, which are labelled per 100 g. Frozen desserts are the one exception to the
     * unit: sold by volume, but their figures in this database are per 100 g.
     */
    static boolean beverage(JSONObject product) {
        String tags = product.optString("categories_tags", "");
        if (tags.contains("ice-creams") || tags.contains("frozen-desserts") || tags.contains("chocolates")) return false;
        java.util.regex.Matcher m = AMOUNT.matcher(product.optString("quantity", "").toLowerCase(java.util.Locale.ROOT));
        if (m.find()) {
            String unit = m.group(2);
            return unit.startsWith("ml") || unit.equals("l") || unit.startsWith("lit");
        }
        if (tags.contains("preparations") || tags.contains("powders")) return false;
        return DRINK_TAG.matcher(tags).find();
    }

    /**
     * A whole drink tag. Matching "beverages" anywhere took "en:plant-based-foods-and-beverages",
     * which Open Food Facts puts on potato chips, and rated Bingo's chips per 100 ml.
     */
    private static final java.util.regex.Pattern DRINK_TAG = java.util.regex.Pattern.compile(
        "\"en:(?:[a-z-]*-)?(?<!foods-and-)(?:beverages|drinks|waters|juices|sodas|milks)\"");

    /** Their numbers arrive as numbers or as strings, and an empty string means "not declared". */
    private static Double value(JSONObject n, String key) {
        if (n.isNull(key)) return null;
        double parsed = n.optDouble(key, Double.NaN);
        if (!Double.isNaN(parsed)) return parsed < 0 ? null : parsed;
        try { return Double.parseDouble(n.optString(key, "").trim()); }
        catch (NumberFormatException notANumber) { return null; }
    }

    private static String https(String url) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            // Their guidance is to identify the caller so abusive clients can be told apart.
            connection.setRequestProperty("User-Agent", "BetterChoiceIndia/" + BuildConfig.VERSION_NAME + " (Android)");
            int status = connection.getResponseCode();
            if (status != 200) return null;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_BYTES) return null;
                    bytes.write(buffer, 0, count);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
}
