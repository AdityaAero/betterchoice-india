package com.betterchoice.android;

/** Bounded ingredient/allergen evidence only; never combines nutrient panels from different images. */
final class LabelEvidence {
    private String ingredients;
    private String allergens;
    private String basis;
    /**
     * The pack size, remembered from the screen that showed it. Zepto prints "Net quantity: 1 pack
     * (48.1 g)" under the title and the nutrition table far below; by the time the table is on
     * screen the size has scrolled away, and the card had no whole-pack column.
     */
    private String pack;
    private final java.util.Map<String, String> rows = new java.util.LinkedHashMap<>();
    private static final java.util.regex.Pattern ROW = java.util.regex.Pattern.compile(
        "(?im)^\\s*(protein|total sugars?|added sugars?|total fat|saturated fat|trans fat|sodium|energy|dietary fib(?:re|er))"
        + "\\s+per\\s+100\\s*(g|ml)\\s+(\\d+(?:\\.\\d+)?)\\s*(g|mg|kcal)\\s*$");

    /** Zepto's table rows, label line then value line, remembered as they scroll past. */
    private static final java.util.regex.Pattern TABLE_ROW = java.util.regex.Pattern.compile(
        "(?im)^[ \\t]*(calories|energy|proteins?|total sugars?|added sugars?|sugars?|total fats?|fats?|saturated fats?|"
        + "trans ?fats?|sodium|dietary fib(?:re|er)|carbohydrates?)[ \\t]*\\n[ \\t]*(\\d+(?:\\.\\d+)?[ \\t]*(?:kcal|mg|g))[ \\t]*$");
    private static final java.util.regex.Pattern EVERY_100 = java.util.regex.Pattern.compile(
        "(?i)in every 100\\s*(g|ml)\\b");
    private final java.util.Map<String, String> table = new java.util.LinkedHashMap<>();
    private String tableBasis;

    void observe(String text) {
        if (text == null) return;
        java.util.regex.Matcher cell = TABLE_ROW.matcher(text);
        while (cell.find()) {
            String label = cell.group(1).toLowerCase(java.util.Locale.ROOT);
            if (!table.containsKey(label)) table.put(label, cell.group(1) + "\n" + cell.group(2));
        }
        java.util.regex.Matcher every = EVERY_100.matcher(text);
        if (every.find() && tableBasis == null) tableBasis = "Values per 100 " + every.group(1).toLowerCase(java.util.Locale.ROOT);
        Double quantity = NutritionParser.netQuantity(text);
        if (quantity != null && pack == null) {
            pack = "Net quantity: " + Nutrition.number(quantity) + " " + NutritionParser.netQuantityUnit(text);
        }
        java.util.regex.Matcher row = ROW.matcher(text);
        while (row.find()) {
            String unit = row.group(2).toLowerCase(java.util.Locale.ROOT);
            if (basis != null && !basis.equals(unit)) rows.clear();
            basis = unit;
            rows.put(row.group(1).toLowerCase(java.util.Locale.ROOT), row.group().trim());
        }
        String section = IngredientFacts.section(text);
        if (section != null && (ingredients == null || section.length() > ingredients.length() - 13))
            ingredients = "Ingredients: " + section;
        StringBuilder declared = new StringBuilder();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String lower = line.toLowerCase(java.util.Locale.ROOT).trim();
            if (lower.startsWith("allergen") || lower.startsWith("contains ") || lower.startsWith("contains:")
                    || lower.startsWith("may contain")) {
                if (declared.length() < 1200) declared.append(line.substring(0, Math.min(400, line.length()))).append('\n');
                // A shop row: "Allergen Information" on one line, "Milk, Soy" on the next.
                if (ALLERGEN_HEADING.matcher(lower).matches() && i + 1 < lines.length && declared.length() < 1200) {
                    String value = lines[++i];
                    declared.append(value.substring(0, Math.min(400, value.length()))).append('\n');
                }
            }
        }
        if (declared.length() > 0) allergens = declared.toString();
    }

    private static final java.util.regex.Pattern ALLERGEN_HEADING = java.util.regex.Pattern.compile(
        "allergens?(?: information| info| advice| declaration| statement)?\\s*:?");

    /** The evidence in one text, for a caller that has no memory of earlier screens. */
    static LabelEvidence of(String text) {
        LabelEvidence evidence = new LabelEvidence();
        evidence.observe(text);
        return evidence;
    }

    /** "Ingredients: …" as declared, or null when no list was read. */
    String ingredients() { return ingredients; }

    /** The allergen declaration lines ("Contains …", "May contain …"), or null. */
    String allergens() { return allergens; }

    /**
     * The remembered declarations, each closed by a blank line. Without it a remembered list ran on
     * into whatever text followed it — "…Antioxidant [300] Back Search Share Product Name is Bonn…",
     * or a later "Total Sugar" row that made a plain green tea look sweetened.
     */
    String text() {
        return (ingredients == null ? "" : ingredients + "\n\n") + (allergens == null ? "" : allergens + "\n");
    }

    /**
     * Remembered per-100 rows, table rows and their basis, then the pack size, for a later screen that
     * lacks them. A table split across two screens (energy above, sodium below) is read whole.
     */
    String panel() {
        StringBuilder out = new StringBuilder(String.join("\n", rows.values()));
        for (String row : table.values()) out.append('\n').append(row);
        if (tableBasis != null && !table.isEmpty()) out.append('\n').append(tableBasis);
        if (pack != null) out.append('\n').append(pack);
        return out.toString();
    }
}
