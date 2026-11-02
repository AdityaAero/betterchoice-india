package com.betterchoice.android;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a nutrition table from on-screen or photo text. Indian (FSSAI) labels list values per
 * 100 g / 100 ml; per-serving tables are scaled only when the serving size is stated.
 */
final class NutritionParser {
    // Bounded on both sides so "12500 mg" is never read as 1250, and "2025.64" survives intact.
    private static final String NUM = "(?<![0-9.])\\d{1,5}(?:[.,]\\d{1,2})?(?![0-9])";
    private static final String NUMBER = "(" + NUM + ")";
    /** Units as Indian packs actually write them: "g", "gm", "gms", "grams", "ml", "mls". */
    private static final String UNITS = "(?:grams?|gms?|millilitres?|mls?|g|ml)(?![a-z])";
    /**
     * Label, then up to 30 non-digit characters (bracketed units, separators, a line break), then the
     * value. An "% RDA" column is skipped if it comes first, and a number that is itself a percentage
     * is never taken as a value: on a two-column Indian panel that would silently report 1.48 g of
     * protein instead of 3.7 g.
     */
    private static Pattern field(String labels) {
        return Pattern.compile("(?:" + labels + ")" +
            "([^0-9\\n]{0,30}\\n?[^0-9\\n]{0,12})" +
            "(?:" + NUM + "\\s*%[^0-9\\n]{0,12})?" +
            // The unit may follow in brackets, as many packs print it: "Energy 136 (kcal)".
            NUMBER + "\\s*\\(?\\s*(kcal|kj|mg|mcg|µg|g|gm)?\\s*\\)?(?!\\s*%)");
    }

    /**
     * "12 g of added sugar", "4.1 g of protein": a number, its unit, "of", the nutrient. The "of" makes it
     * unambiguous, so it is read only when no table row or label gave the figure (a run-on row such as
     * "Total Sugars 15.6 Added Sugars 14.2" must never lend 15.6 to added sugar).
     */
    private static Pattern ofPhrase(String labels) {
        return Pattern.compile(NUMBER + "[ \\t]*(kcal|kj|mg|mcg|µg|g|gm)[ \\t]+of[ \\t]+(?:" + labels + ")(?![a-z])");
    }

    /** The figure from an "of" phrase, in grams (or milligrams when {@code milligrams}), or null. */
    private static Double ofValue(Pattern of, String text, double scale, boolean milligrams) {
        Matcher m = of.matcher(text);
        if (!m.find()) return null;
        double value = number(m.group(1));
        String unit = m.group(2);
        if (milligrams) return ("g".equals(unit) || "gm".equals(unit) ? value * 1000 : value) * scale;
        return ("mg".equals(unit) ? value / 1000 : value) * scale;
    }

    /**
     * The other way round: <b>"10.6g protein"</b>, where the value comes before its label. Retailer
     * summary rows are written like this, and reading them label-first is actively dangerous — on a
     * line reading "10.6g protein, 56.8g carbs" the label-first pattern walks past "protein" and
     * returns the carbohydrate figure, which then flatters the score, because protein counts in the
     * product's favour.
     *
     * <p>Spaces and tabs only, never a line break: "700\nDietary Fibre" must not read 700 as fibre.
     */
    private static Pattern valueFirst(String labels) {
        return Pattern.compile(NUMBER + "[ \\t]*(kcal|kj|mg|mcg|µg|g|gm)?[ \\t]{0,3}(?:" + labels + ")(?![a-z])");
    }

    /**
     * The label with its own value straight after it on the same line, as in "Protein 13 g", or with
     * its unit in brackets between, as Zepto writes every row: "Protein (g) 11.8".
     *
     * <p>This is the tie-breaker between the two readings above, and it is needed in both
     * directions. On one line reading "Dietary Fibre 10 g Protein 13 g" the value-first pattern
     * would hand back fibre's 10 g as the protein; on "10.6g protein, 56.8g carbs" the label-first
     * pattern would hand back the carbohydrates. So: if the label owns a number on its own line,
     * that number wins. Only when it does not is the value-first reading used.
     */
    private static Pattern ownValue(String labels) {
        // Skips a leading "% RDA" column and refuses a percentage as the value, exactly as field() does.
        return Pattern.compile("(?:" + labels + ")[ \\t:]{0,3}" +
            "(?:\\((?:kcal|kj|mg|mcg|µg|g|gm)\\)[ \\t:]{0,3})?" +
            "(?:" + NUM + "[ \\t]*%[ \\t]{0,3})?" +
            NUMBER + "[ \\t]*\\(?(kcal|kj|mg|mcg|µg|g|gm)?\\)?(?![ \\t]*%)");
    }

    /**
     * A table row: the label alone on its line, the value alone on the next. Zepto's expanded table
     * reads "Dietary Fiber / 1.7g". It outranks every other reading, because a name or a tag on the
     * same page ("…Soda Cranberry Lime, 5g Fibre") is a claim, often per can, not the table.
     */
    private static Pattern tableRow(String labels) {
        return Pattern.compile("(?m)^[ \\t]*(?:" + labels + ")[ \\t]*:?[ \\t]*\\n[ \\t]*" + NUMBER
            + "[ \\t]*(kcal|kj|mg|mcg|µg|g|gm)?[ \\t]*$");
    }

    private static final Pattern TOTAL_SUGARS_ROW = tableRow("total sugars?|sugars?");
    private static final Pattern ADDED_SUGARS_ROW = tableRow("added sugars?");
    private static final Pattern SATURATED_ROW = tableRow("saturated fatty acids?|saturated fats?|saturates|sat\\.? fat");
    private static final Pattern SODIUM_ROW = tableRow("sodium");
    private static final Pattern FIBRE_ROW = tableRow("total dietary fib(?:re|er)|dietary fib(?:re|er)|total fib(?:re|er)|fib(?:re|er)");
    private static final Pattern PROTEIN_ROW = tableRow("proteins?");
    private static final Pattern TOTAL_FAT_ROW = tableRow("total fats?|fats?");
    private static final Pattern TRANS_FAT_ROW = tableRow("trans ?fats?|trans fatty acids?");
    private static final Pattern ENERGY = field("energy|calories|calorie|energy value");
    /** "568 calories" and "568 kcal" both state energy; the word itself carries the unit. */
    private static final Pattern ENERGY_VALUE_FIRST = Pattern.compile(NUMBER + "[ \\t]*(?:kcal|calories|calorie)(?![a-z])");
    private static final Pattern TOTAL_SUGARS_OWN = ownValue("total sugars?");
    private static final Pattern SUGARS_OWN = ownValue("(?<!added )(?<!free )sugars?");
    private static final Pattern SATURATED_OWN = ownValue("saturated fatty acids?|saturated fats?|saturates|sat\\.? fat");
    private static final Pattern SODIUM_OWN = ownValue("sodium");
    private static final Pattern FIBRE_OWN = ownValue("dietary fib(?:re|er)|total fib(?:re|er)|fib(?:re|er)");
    private static final Pattern PROTEIN_OWN = ownValue("proteins?");
    private static final Pattern TOTAL_SUGARS_VF = valueFirst("total sugars?");
    private static final Pattern SUGARS_VF = valueFirst("(?<!added )(?<!free )sugars?");
    private static final Pattern SATURATED_VF = valueFirst("saturated fatty acids?|saturated fats?|saturates|sat\\.? fat");
    private static final Pattern SODIUM_VF = valueFirst("sodium");
    private static final Pattern FIBRE_VF = valueFirst("dietary fib(?:re|er)|total fib(?:re|er)|fib(?:re|er)");
    private static final Pattern PROTEIN_VF = valueFirst("proteins?");
    private static final Pattern KCAL = Pattern.compile(NUMBER + "\\s*kcal");
    private static final Pattern TOTAL_SUGARS = field("total sugars?|sugars?,? total");
    private static final Pattern SUGARS = field("(?<!added )(?<!free )sugars?");
    private static final Pattern ADDED_SUGARS = field("added sugars?");
    // "x g of y" phrases, read after every strict form and before the loose label-then-number form.
    private static final Pattern OF_SUGARS = ofPhrase("total sugars?|(?<!added )(?<!free )sugars?");
    private static final Pattern OF_ADDED_SUGARS = ofPhrase("added sugars?");
    private static final Pattern OF_SATURATED = ofPhrase("saturated fatty acids?|saturated fats?");
    private static final Pattern OF_SODIUM = ofPhrase("sodium");
    private static final Pattern OF_FIBRE = ofPhrase("dietary fib(?:re|er)|fib(?:re|er)");
    private static final Pattern OF_PROTEIN = ofPhrase("proteins?");
    private static final Pattern CARBS = field("carbohydrates?|carbs?");
    private static final Pattern SATURATED = field("saturated fatty acids?|saturated fats?|saturates|sat\\.? fat|sfa");
    private static final Pattern SODIUM = field("sodium");
    private static final Pattern SALT = field("(?<![a-z])(?<!sea )salt(?![a-z])");
    private static final Pattern FIBRE = field("dietary fib(?:re|er)|total fib(?:re|er)|fib(?:re|er)");
    private static final Pattern PROTEIN = field("proteins?");
    /**
     * Total fat, and trans fat, so the fat that is not saturated can be worked out. The bare word
     * "fat" appears inside "saturated fat", "trans fat", "monounsaturated fat" and "calories from
     * fat", so each of those is excluded by name rather than trusted to be absent.
     */
    private static final String TOTAL_FAT_LABELS =
        "total fats?|fats?,? total|(?<!saturated )(?<!trans )(?<!trans)(?<!unsaturated )(?<!from )(?<!low )(?<!no )fats?(?!ty)";
    private static final Pattern TOTAL_FAT_OWN = ownValue(TOTAL_FAT_LABELS);
    private static final Pattern TOTAL_FAT_VF = valueFirst(TOTAL_FAT_LABELS);
    private static final Pattern TOTAL_FAT = field(TOTAL_FAT_LABELS);
    private static final Pattern TRANS_FAT_OWN = ownValue("trans ?fats?|trans fatty acids?");
    private static final Pattern TRANS_FAT = field("trans ?fats?|trans fatty acids?");
    /**
     * The basis the numbers are printed against. "Per 100gms" is extremely common on Indian packs and
     * used to be missed, because a word boundary after "g" or "gm" does not exist inside "gms". The
     * cost of missing it was not a missing field: the parser fell through to the serving size and
     * multiplied a whole per-100 panel by five, which then failed its own sanity check and returned
     * nothing at all. That is why a readable pack photo produced no rating.
     */
    private static final Pattern PER_100 = Pattern.compile(
        "(?:per|/|\\(|every)\\s*100\\s*" + UNITS + "|100\\s*" + UNITS + "\\s*(?:\\)|serving|serve|contains|provides)");
    private static final Pattern SERVING = Pattern.compile(
        "(?:serving size|per serve|per serving|serve size)([^0-9\\n]{0,15}\\n?[^0-9\\n]{0,10})(\\d{1,4}(?:\\.\\d)?)\\s*(g|gm|gms|ml|mls)(?![a-z])");
    private static final Pattern SERVINGS_PER_PACK = Pattern.compile(
        "(?:servings?|serves)[^0-9\\n]{0,20}(?:per|in|a)\\s*(?:pack|packet|container|box)[^0-9\\n]{0,10}(\\d{1,3}(?:\\.\\d)?)" +
        "|(\\d{1,3})\\s*servings? per (?:pack|packet|container)");
    /**
     * "Net Qty 150 g", and also the shop's own form "Net quantity: 1 pc (20.2 g)", where a piece
     * count comes before the weight. That form used to defeat the pattern entirely, so a chocolate
     * bar had no pack size and no whole-pack column. Shops also put the label and the amount on
     * separate lines ("Unit" / "20.2 g"), so one line break is allowed between them.
     */
    private static final Pattern NET_QUANTITY = Pattern.compile(
        "(?:net (?:qty|quantity|weight|wt|content)|net vol(?:ume)?|pack size|(?<![a-z])unit(?=\\s*[:\\n])"
        + "|(?<![a-z])weight(?=\\s*[:\\n]))"
        + "[^0-9\\n]{0,15}\\n?[^0-9\\n]{0,6}" +
        "(?:\\d{1,3}\\s*(?:pc|pcs|piece|pieces|unit|units|nos?|n|packs?|pouch|jar|tub|bottle|box)\\s*[\\(\\[]?\\s*)?" +
        "(\\d{1,4}(?:\\.\\d{1,2})?)\\s*(kgs?|gms?|grams?|g|ml|litres?|liters?|l)\\b");

    /**
     * The pack line a shop prints under the title, with no "net quantity" word: "1 pc (300 ml)",
     * "1 pack (48.1 g)". Used only when no labelled net quantity exists. "450 or 487.5 g" is left alone.
     */
    private static final Pattern PACK_LINE = Pattern.compile(
        "(?m)^\\s*\\d{1,3}\\s*(?:pc|pcs|piece|pieces|pack|packs|unit|units|pouch|jar|tub|bottle|box)\\s*\\(\\s*"
        + "(\\d{1,4}(?:\\.\\d{1,2})?)\\s*(kgs?|gms?|grams?|g|ml|litres?|liters?|l)\\s*\\)");

    private static Matcher quantity(String lowerText) {
        Matcher m = NET_QUANTITY.matcher(lowerText);
        if (m.find()) return m;
        m = PACK_LINE.matcher(lowerText);
        return m.find() ? m : null;
    }

    /** A shop's price per unit, "(₹33/100g)", is not a nutrition basis. */
    private static final Pattern UNIT_PRICE = Pattern.compile("₹\\s*[\\d,]+(?:\\.\\d+)?\\s*/\\s*\\d+\\s*(?:g|gm|kg|ml|l)\\b");
    /** "saturated fat (9) 13.4": OCR reads the bracketed unit g as 9. Only directly after a word. */
    private static final Pattern NINE_FOR_G = Pattern.compile("([a-z])\\s*\\(9\\)");
    /** "25.79," or "19.59 (5.8%)": a one-decimal value whose unit g was read as 9. */
    private static final Pattern G_AS_NINE = Pattern.compile("(?<![\\d.])(\\d{1,3}\\.\\d)9(?=\\s*[,(;]|\\s*$)", Pattern.MULTILINE);
    private static final Pattern G_AS_Q = Pattern.compile("(\\d)\\s*q(?![a-z])");
    private static final Pattern LEADING_ZERO = Pattern.compile("(?<![\\d.])0(\\d)(?=\\s*g\\b)");
    private static final Pattern O_AS_ZERO_AFTER = Pattern.compile("(\\d)\\.o(?=\\s*mg)");
    private static final Pattern O_AS_ZERO_BEFORE = Pattern.compile("(?<![a-z0-9])o\\.(\\d)");
    private static final Pattern FIRST_NUTRIENT = Pattern.compile(
        "(?:energy|calories|protein|carbohydrate|total fat|sugar|sodium)");
    private static final Pattern HEADER_100 = Pattern.compile("(?<![0-9.])100\\s*(?:g|gm|gms|grams?|ml)\\b");

    /**
     * Lower case, accents dropped ("SATURÄTED" from a photo is "saturated"), OCR's "(9)" for "(g)"
     * repaired, and unit prices removed so "(₹33/100g)" under a photo cannot set the basis.
     */
    static String clean(String raw) { return repairDigits(plain(raw)); }

    /** Lower case, accents dropped, unit prices removed, "(9)" for "(g)" repaired. */
    private static String plain(String raw) {
        String text = java.text.Normalizer.normalize(raw.replace((char) 0xA0, ' '), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        text = UNIT_PRICE.matcher(text).replaceAll(" ");
        return NINE_FOR_G.matcher(text).replaceAll("$1 (g)");
    }

    /** Photo text: the unit g read as 9 or q ("25.79," for 25.7g; "9.8 q("), o read for 0. */
    private static String repairDigits(String text) {
        text = gReadAsNine(text);
        text = G_AS_Q.matcher(text).replaceAll("$1 g");
        text = LEADING_ZERO.matcher(text).replaceAll("0.$1");   // "Added Sugars 03g": 0.3 g
        text = text.replaceAll("s{2,}ugar", "sugar");          // "ADDED SsUGARS" from a photo
        text = O_AS_ZERO_AFTER.matcher(text).replaceAll("$1.0");
        return O_AS_ZERO_BEFORE.matcher(text).replaceAll("0.$1");
    }

    /**
     * A two-column panel's header names the per-100 column without "per": "NUTRITIONAL INFORMATION /
     * SERVE SIZE 12.5g / PER SERVE / 100 g / % RDA". Missing it, the parser fell back to the serving
     * size (misread as 125 g) and scaled a correct per-100 column by 0.8. The header is the text
     * between the words "nutrition" and the first nutrient row; a net weight there is not counted.
     */
    static boolean per100InHeader(String text) {
        int start = text.indexOf("nutrition");
        if (start < 0) return false;
        Matcher first = FIRST_NUTRIENT.matcher(text);
        if (!first.find(start)) return false;
        String header = text.substring(start, first.start());
        Matcher m = HEADER_100.matcher(header);
        while (m.find()) {
            String before = header.substring(Math.max(0, m.start() - 20), m.start());
            if (!before.matches("(?s).*(?:net|wt|weight|quantity|size|contents?)\\W*$")) return true;
        }
        return false;
    }

    /** The "100 g" or "100 ml" the header named, for its unit. */
    private static String headerBasis(String text) {
        Matcher m = HEADER_100.matcher(text.substring(Math.max(0, text.indexOf("nutrition"))));
        return m.find() ? m.group() : "100 g";
    }

    private static final Pattern TWO_FIGURE_ROW = Pattern.compile(
        "(?m)(?:energy|protein|carbohydrate|sugars?|fat|sodium)[^0-9\\n]{0,30}\\d[\\d.,]*\\s*(?:kcal|mg|g)?\\s*\\(?\\d");

    /** At least two nutrient rows with two figures each, and a % RDA column: a per-100 first column. */
    static boolean per100Columns(String text) {
        if (!text.contains("rda")) return false;
        Matcher m = TWO_FIGURE_ROW.matcher(text);
        int rows = 0;
        while (m.find() && rows < 2) rows++;
        return rows >= 2;
    }

    /**
     * "Total Sugars 25.79," from a photo is 25.7 g with its g read as 9. Repaired only in a row that
     * names no unit of its own: "Protein (g) 6.89," on a shop page is 6.89 and stays so.
     */
    private static String gReadAsNine(String text) {
        Matcher m = G_AS_NINE.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int rowStart = Math.max(Math.max(text.lastIndexOf(',', m.start()), text.lastIndexOf('\n', m.start())),
                text.lastIndexOf(';', m.start())) + 1;
            String row = text.substring(rowStart, m.start());
            out.append(text, last, m.start());
            out.append(row.matches("(?s).*\\((?:g|gm|mg|kcal)\\).*") ? m.group() : m.group(1) + " g");
            last = m.end();
        }
        return out.append(text.substring(last)).toString();
    }

    /**
     * Lay's prints saturated fat per oil: "14.9 g OR 7.6 g" in one cell, which a photo reads as three
     * lines around the label ("14.9 g 14%" / "Saturated Fat OR OR" / "7.6 g 7%"). The higher value is
     * taken: the shopper cannot tell which oil their pack used, and the method must not flatter it.
     */
    private static final Pattern OIL_BLEND = Pattern.compile(
        "(\\d{1,2}(?:\\.\\d{1,2})?)\\s*g[^\\n]*\\n[^\\n]*saturated fat[^\\n]*\\bor\\b[^\\n]*\\n(?:[^\\n]*\\n)?"
        + "[^\\n0-9]*(\\d{1,2}(?:\\.\\d{1,2})?)\\s*g");

    private static Double oilBlendSaturatedFat(String text, double scale) {
        Matcher m = OIL_BLEND.matcher(text);
        if (!m.find()) return null;
        return Math.max(number(m.group(1)), number(m.group(2))) * scale;
    }

    private static Double lostDecimal(Double grams) {
        if (grams == null || grams <= 100 || grams >= 1000 || grams != Math.rint(grams)) return grams;
        LOST_DECIMAL.set(true);
        return grams / 10;
    }

    /** Set when parse() restored a lost decimal point; read back into Nutrition.ocrRepaired. */
    private static final ThreadLocal<Boolean> LOST_DECIMAL = ThreadLocal.withInitial(() -> false);

    static Nutrition parse(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        String unrepaired = plain(rawText);
        String original = repairDigits(unrepaired);
        String text = panel(original);
        Nutrition n = new Nutrition();
        Matcher per100 = PER_100.matcher(text);
        boolean stated = per100.find();
        boolean hasPer100 = stated || per100InHeader(text);
        double scale = 1;
        if (hasPer100) {
            String basis = stated ? per100.group() : headerBasis(text);
            n.beverage = basis.contains("ml") || basis.contains("litre");
            n.basisStated = true;
        } else if (Pattern.compile("\\b(?:amounts?|values?)\\s+per\\s+(?:pack|container)\\b").matcher(text).find()) {
            Double pack = netQuantity(original);
            String unit = netQuantityUnit(original);
            if (pack == null) {
                Double serving = servingSize(original), count = servingsPerPack(original);
                Matcher declaredServing = SERVING.matcher(original);
                if (NutrientMath.positive(serving) && NutrientMath.positive(count) && declaredServing.find()) {
                    pack = serving * count;
                    unit = declaredServing.group(3).startsWith("ml") ? "ml" : "g";
                }
            }
            if (NutrientMath.positive(pack) && unit != null) {
                scale = 100 / pack;
                n.beverage = unit.equals("ml");
                n.basisStated = true;
            }
        } else {
            // No stated basis: the numbers are per serving, so scale them up to per 100 — unless the
            // table carries two figures a row and a % RDA column. Indian labels must give per 100 g;
            // that is the first column, and a photo's lost "100 g" header does not make it per serve.
            Matcher serving = SERVING.matcher(text);
            if (serving.find() && !per100Columns(text)) {
                double size = Double.parseDouble(serving.group(2));
                if (size <= 0 || size > 1000) return null;
                scale = 100.0 / size;
                n.beverage = serving.group(3).startsWith("ml");
                n.basisStated = true;
            }
        }
        // Retailer accessibility rows repeat the basis between each label and its value:
        // "Total Sugar Per 100 g 24.6 g". Establish the basis above, then remove these
        // annotations before reading amounts; 100 is never the nutrient amount.
        text = text.replaceAll("\\bper\\s*100\\s*" + UNITS, " ");
        n.energyKcal = energy(text, scale);
        n.carbohydrateG = grams(null, ownValue("carbohydrates?|carbs?"), valueFirst("carbohydrates?|carbs?"), CARBS, text, scale);
        Double total = grams(TOTAL_SUGARS_ROW, TOTAL_SUGARS_OWN, TOTAL_SUGARS_VF, OF_SUGARS, TOTAL_SUGARS, text, scale);
        n.sugarsG = total != null ? total : grams(null, SUGARS_OWN, SUGARS_VF, OF_SUGARS, SUGARS, text, scale);
        n.saturatedFatG = grams(SATURATED_ROW, SATURATED_OWN, SATURATED_VF, OF_SATURATED, SATURATED, text, scale);
        if (n.saturatedFatG == null) n.saturatedFatG = oilBlendSaturatedFat(text, scale);
        n.sodiumMg = sodium(text, scale);
        n.fibreG = grams(FIBRE_ROW, FIBRE_OWN, FIBRE_VF, OF_FIBRE, FIBRE, text, scale);
        n.proteinG = grams(PROTEIN_ROW, PROTEIN_OWN, PROTEIN_VF, OF_PROTEIN, PROTEIN, text, scale);
        n.totalFatG = grams(TOTAL_FAT_ROW, TOTAL_FAT_OWN, TOTAL_FAT_VF, TOTAL_FAT, text, scale);
        n.transFatG = grams(TRANS_FAT_ROW, TRANS_FAT_OWN, null, TRANS_FAT, text, scale);
        LOST_DECIMAL.set(false);
        // A photo read at small size drops decimal points: "Total Fat 324g" for 32.4 g. More than
        // 100 g in 100 g cannot be, so a three- or four-digit whole number there lost its point.
        n.totalFatG = lostDecimal(n.totalFatG);
        n.saturatedFatG = lostDecimal(n.saturatedFatG);
        n.sugarsG = lostDecimal(n.sugarsG);
        n.proteinG = lostDecimal(n.proteinG);
        n.fibreG = lostDecimal(n.fibreG);
        n.ocrRepaired = LOST_DECIMAL.get() || !original.equals(unrepaired);
        // Saturated fat cannot exceed total fat. A conflicting pair often means OCR
        // lost a decimal point; neither number can be trusted enough to grade.
        if (n.totalFatG != null && n.saturatedFatG != null
                && n.saturatedFatG > n.totalFatG + 0.1) return null;
        if (n.totalFatG != null && n.totalFatG > 100) n.totalFatG = null;
        if (n.transFatG != null && (n.transFatG > 100
                || (n.totalFatG != null && n.transFatG > n.totalFatG))) n.transFatG = null;
        // A label that declares 0 g total fat has declared its saturated fat too: none. Reporting it
        // as "not listed" made a fat-free mango drink look incompletely labelled.
        if (n.saturatedFatG == null && n.totalFatG != null && n.totalFatG == 0) n.saturatedFatG = 0.0;
        // Added sugar, separately from total sugar, when the label declares it. Kept only when it
        // fits inside the total: "Added Sugars (as Sucrose) (g) 14.2" of "Total Sugars (g) 15.6".
        n.addedSugarsG = grams(ADDED_SUGARS_ROW, null, null, OF_ADDED_SUGARS, ADDED_SUGARS, text, scale);
        if (n.addedSugarsG != null && (n.addedSugarsG > 100
                || (n.sugarsG != null && n.addedSugarsG > n.sugarsG + 0.5))) n.addedSugarsG = null;
        n.netQuantity = netQuantity(original);
        n.netUnit = n.netQuantity == null ? null : netQuantityUnit(original);
        n.servingSize = servingSize(original);
        n.servingsPerPack = servingsPerPack(original);
        Matcher servingUnit = SERVING.matcher(original);
        if (servingUnit.find() && servingUnit.group(3).startsWith("ml") != n.beverage) n.servingSize = null;
        // A label that gives protein, carbohydrate and fat but no energy has given its energy too: FSSAI fixes how it
        // is worked out from them. Zepto lists some products this way ("Protein(52.5g), Carbohydrate(31.5g), ...,
        // Fat(0.8g)"); reading the energy as missing sent the bubble to the pack photo instead. Said on the card.
        if (n.energyKcal == null && n.proteinG != null && n.carbohydrateG != null && n.totalFatG != null) {
            n.energyKcal = NutritionCalculator.energyFromMacros(n.proteinG, n.carbohydrateG, n.totalFatG);
            n.energyFromMacros = true;
        }
        if (!sane(n)) return null;
        return n.usable() ? n : null;
    }

    /** A declared table outranks preceding claims such as 'high fibre' or nearby product cards. */
    private static String panel(String text) {
        Matcher heading = Pattern.compile("\\b(?:nutrition(?:al)?\\s+(?:information|values|facts))\\b").matcher(text);
        if (!heading.find()) return text;
        String block = text.substring(heading.start());
        Matcher end = Pattern.compile("\\n\\s*(?:ingredients?\\b|allergen\\b|storage\\b|shelf life\\b|manufacturer\\b|"
            + "country of origin\\b|similar products\\b|you might also like\\b|recommended for you\\b)").matcher(block);
        return end.find() ? block.substring(0, end.start()) : block;
    }

    /** "Net Qty 150 g", "Pack size 1 kg", "Net volume 500 ml" -> grams or millilitres. */
    static Double netQuantity(String rawText) {
        if (rawText == null) return null;
        Matcher m = quantity(rawText.toLowerCase(Locale.ROOT));
        if (m == null) return null;
        if (rawText.substring(m.end()).toLowerCase(Locale.ROOT).matches("(?s)^\\s*or\\s+\\d.*")) return null;
        double value = number(m.group(1));
        String unit = m.group(2);
        if (unit.startsWith("kg") || unit.equals("l") || unit.startsWith("litre") || unit.startsWith("liter")) value *= 1000;
        return value > 0 && value <= 20000 ? value : null;
    }

    /** "g" or "ml": how the pack is sold, from its stated net quantity. Null when nothing says. */
    static String netQuantityUnit(String rawText) {
        if (rawText == null) return null;
        Matcher m = quantity(rawText.toLowerCase(Locale.ROOT));
        if (m == null) return null;
        if (rawText.substring(m.end()).toLowerCase(Locale.ROOT).matches("(?s)^\\s*or\\s+\\d.*")) return null;
        String unit = m.group(2);
        return unit.startsWith("m") || unit.startsWith("l") ? "ml" : "g";
    }

    /**
     * The pack's declared weight and volume, when it states both — ice cream tubs often print
     * "Net vol 700 ml" and "Net wt 350 g" side by side. Returns {grams, millilitres}, or null unless
     * both are there. That ratio is the only honest way to turn a per-100-ml panel into per 100 g.
     */
    static double[] weightAndVolume(String rawText) {
        if (rawText == null) return null;
        Matcher m = NET_QUANTITY.matcher(rawText.toLowerCase(Locale.ROOT));
        Double grams = null, millilitres = null;
        while (m.find()) {
            double value = number(m.group(1));
            String unit = m.group(2);
            boolean volume = unit.startsWith("m") || unit.startsWith("l");
            if (unit.startsWith("kg") || unit.equals("l") || unit.startsWith("litre") || unit.startsWith("liter")) value *= 1000;
            if (value <= 0 || value > 20000) continue;
            if (volume && millilitres == null) millilitres = value;
            if (!volume && grams == null) grams = value;
        }
        return grams == null || millilitres == null ? null : new double[] {grams, millilitres};
    }

    static Double servingSize(String rawText) {
        if (rawText == null) return null;
        Matcher m = SERVING.matcher(rawText.toLowerCase(Locale.ROOT));
        if (!m.find()) return null;
        double value = number(m.group(2));
        return value > 0 && value <= 1000 ? value : null;
    }

    static Double servingsPerPack(String rawText) {
        if (rawText == null) return null;
        Matcher m = SERVINGS_PER_PACK.matcher(rawText.toLowerCase(Locale.ROOT));
        if (!m.find()) return null;
        String raw = m.group(1) != null ? m.group(1) : m.group(2);
        if (raw == null) return null;
        double value = number(raw);
        return value >= 1 && value <= 200 ? value : null;
    }

    private static Double energy(String text, double scale) {
        Matcher m = ENERGY.matcher(text);
        while (m.find()) {
            double value = number(m.group(2));
            String unit = unit(m);
            if ("kj".equals(unit)) {
                // "2092 kJ / 500 kcal": prefer the kcal value written alongside.
                Matcher kcal = KCAL.matcher(text.substring(m.end(), Math.min(text.length(), m.end() + 25)));
                return kcal.find() ? number(kcal.group(1)) * scale : value / 4.184 * scale;
            }
            // A unit is required: "Energy drink 250 ml" must never become 250 kcal.
            if ("kcal".equals(unit)) return value * scale;
        }
        // "568 calories" states energy outright, and the word supplies the unit. Only used when no
        // labelled row exists: a page's "only 99 kcal a serve" must not outrank its own table.
        Matcher direct = ENERGY_VALUE_FIRST.matcher(text);
        if (direct.find()) return number(direct.group(1)) * scale;
        return null;
    }

    private static Double sodium(String text, double scale) {
        for (Pattern candidate : new Pattern[]{SODIUM_ROW, SODIUM_OWN, SODIUM_VF}) {
            Double direct = valueOf(candidate, text);
            if (direct == null) continue;
            String unit = unitOf(candidate, text);
            if ("g".equals(unit) || "gm".equals(unit)) return direct * 1000 * scale;
            return direct * scale;
        }
        Double phrased = ofValue(OF_SODIUM, text, scale, true);
        if (phrased != null) return phrased;
        Matcher m = SODIUM.matcher(text);
        while (m.find()) {
            if (!tableGap(m.group(1))) continue;
            double value = number(m.group(2));
            String unit = unit(m);
            if (unit == null && wordFollows(text, m.end(2))) continue;
            if ("g".equals(unit) || "gm".equals(unit)) return value * 1000 * scale;
            if (unit == null || "mg".equals(unit)) return value * scale;
        }
        Double salt = grams(SALT, text, scale);
        return salt == null ? null : salt / 2.5 * 1000;
    }

    /**
     * A label's own same-line value wins; failing that the value-first reading; failing that the
     * general label-first pattern, which may cross one line break.
     */
    private static Double grams(Pattern row, Pattern own, Pattern valueFirst, Pattern pattern, String text, double scale) {
        return grams(row, own, valueFirst, null, pattern, text, scale);
    }

    /**
     * The most unambiguous reading wins: a table row, the label's own value, the value before its label, an
     * "x g of y" phrase, and only then the loose label-then-number form, which in running prose ("12 g of protein
     * and 9 g of fibre") walks past a comma into the next nutrient's number.
     */
    private static Double grams(Pattern row, Pattern own, Pattern valueFirst, Pattern of, Pattern pattern, String text, double scale) {
        for (Pattern candidate : new Pattern[]{row, own, valueFirst}) {
            if (candidate == null) continue;
            Double direct = valueOf(candidate, text);
            if (direct == null) continue;
            String unit = unitOf(candidate, text);
            if ("mg".equals(unit)) return direct / 1000 * scale;
            if (unit == null || "g".equals(unit) || "gm".equals(unit)) return direct * scale;
        }
        if (of != null) {
            Double phrased = ofValue(of, text, scale, false);
            if (phrased != null) return phrased;
        }
        return grams(pattern, text, scale);
    }

    /** Group 1 of a value-first match: the number. */
    private static Double valueOf(Pattern valueFirst, String text) {
        Matcher m = valueFirst.matcher(text);
        if (!m.find()) return null;
        // Label-first only: in "10.6g protein" the word after the number is the label itself.
        boolean labelFirst = Character.isLetter(m.group().charAt(0));
        if (labelFirst && m.group(2) == null && m.group().indexOf('(') < 0 && wordFollows(text, m.end(1))) return null;
        return number(m.group(1));
    }

    /**
     * A number without a unit that runs straight on into a word is part of a sentence, not a table:
     * "Cooks in just 3 minutes", "No Added Sugar / 1 pack (250 g)". A line break ends the question.
     */
    private static boolean wordFollows(String text, int end) {
        int i = end;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return i < text.length() && Character.isLetter(text.charAt(i));
    }

    /** Words that may sit between a nutrient's name and its value in a real table row. */
    private static final Pattern TABLE_WORDS = Pattern.compile(
        "total|value|content|approx|approximately|as|sucrose|of|which|per|serve|serving|equivalent|kcal|kj|mg|mcg|gm|gms|rda");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("[,;.!]\\s*[a-z]");
    private static final Pattern WORD = Pattern.compile("[a-z]{2,}");

    /**
     * What lies between a nutrient's name and a number, judged as a table cell. A comma or full stop
     * followed by more words means the name was in a sentence: Key Features' "High in protein and
     * dietary fiber, Cooks in just 3 minutes" read 3 g of fibre ahead of the table's 10 g.
     */
    private static boolean tableGap(String between) {
        if (between == null) return true;
        String gap = between.replaceAll("\\([^)]*\\)", " ");
        if (SENTENCE_BREAK.matcher(gap).find()) return false;
        int words = 0;
        Matcher w = WORD.matcher(gap);
        while (w.find()) if (!TABLE_WORDS.matcher(w.group()).matches()) words++;
        return words < 2;
    }

    /** Group 2 of a value-first match: the unit, or the bracketed "(g)" before it, or null. */
    private static String unitOf(Pattern valueFirst, String text) {
        Matcher m = valueFirst.matcher(text);
        if (!m.find()) return null;
        if (m.group(2) != null) return m.group(2);
        String matched = m.group();
        if (matched.contains("(mg)")) return "mg";
        if (matched.contains("(mcg)") || matched.contains("(µg)")) return "mcg";
        if (matched.contains("(kcal)")) return "kcal";
        if (matched.contains("(kj)")) return "kj";
        if (matched.contains("(g)") || matched.contains("(gm)")) return "g";
        return null;
    }

    private static Double grams(Pattern pattern, String text, double scale) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (!tableGap(m.group(1))) continue;
            String unit = unit(m);
            if (unit == null && wordFollows(text, m.end(2))) continue;
            if (unit == null || "g".equals(unit) || "gm".equals(unit)) return number(m.group(2)) * scale;
            if ("mg".equals(unit)) return number(m.group(2)) / 1000 * scale;
        }
        return null;
    }

    /** Unit after the number, or a bracketed unit between label and number such as "Protein (g) 6.5". */
    private static String unit(Matcher m) {
        if (m.group(3) != null) return m.group(3);
        String between = m.group(1);
        if (between.contains("(kcal)")) return "kcal";
        if (between.contains("(kj)")) return "kj";
        if (between.contains("(mg)")) return "mg";
        if (between.contains("(g)") || between.contains("(gm)")) return "g";
        return null;
    }

    private static double number(String raw) { return Double.parseDouble(raw.replace(',', '.')); }

    private static boolean sane(Nutrition n) {
        return in(n.energyKcal, 0, 950) && in(n.sugarsG, 0, 100) && in(n.saturatedFatG, 0, 100) &&
            in(n.sodiumMg, 0, 40000) && in(n.fibreG, 0, 100) && in(n.proteinG, 0, 100)
            && in(n.carbohydrateG, 0, 100);
    }

    private static boolean in(Double value, double min, double max) { return value == null || (value >= min && value <= max); }

    private NutritionParser() {}
}
