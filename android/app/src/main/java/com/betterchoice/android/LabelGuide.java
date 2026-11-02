package com.betterchoice.android;

import java.util.Locale;

/** Retailer instructions and category-specific basis checks, without guessing product contents. */
final class LabelGuide {
    static String details(ProductPage page) {
        String path = page != null && "com.zeptoconsumerapp".equals(page.retailer)
            ? "Open Highlights, then View more. Scroll to Nutrition Information and Ingredients."
            : "Open View details and look for Nutritional Information and Ingredients.";
        return path + " I'll read the details as you scroll. If the basis or panel is missing, open a clear back-of-pack photo and tap Scan.";
    }
    /** For a product not rated yet: the category's advice, or what any label needs before a band can be shown. */
    static String category(ProductPage page) {
        String advice = categoryAdvice(page);
        return advice != null ? advice : "A label must state per 100 g/ml or a measured serving before a nutrient band can be shown.";
    }

    /** Advice specific to the kind of product, or null; also shown on a rated card, where the general line would contradict it. */
    static String categoryAdvice(ProductPage page) {
        String name = page == null ? "" : page.name.toLowerCase(Locale.ROOT);
        if (name.contains("ice cream") || name.contains("icecream") || name.contains("kulfi") || name.contains("frozen dessert"))
            return "Ice cream is food. A tub sold in ml needs a declared weight/volume ratio for per-100-g comparison. The tub is not assumed to be one serving.";
        if (name.contains("chocolate")) return "Chocolate: check the declared portion in grams. Compare the same portion, not different pack sizes.";
        if (name.contains("coffee") || name.contains("tea")) return "Check whether the panel describes dry powder/leaves or a prepared drink. A cup also depends on the recipe, milk and sugar; no cup values are inferred from the dry pack.";
        return null;
    }
    private LabelGuide() {}
}
