package com.betterchoice.android;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Caffeine and lactose in the product on screen, said as numbers rather than a bare caution.
 *
 * <p>Caffeine: the amount the label declares; failing that, NIN 2024's per-cup ranges for tea and
 * coffee sold dry, or WHO's ceiling for colas; otherwise "contains caffeine, amount not read". Each
 * amount is set against NIN 2024's tolerable limit for a day, and in pregnancy against WHO's antenatal
 * recommendation. Lactose: for a dairy food, the label's sugars that are not added (milk's sugar is
 * lactose, NIN 2024), set against the 12 g most adults with lactose intolerance tolerate at a time
 * (Shaukat et al. 2010 systematic review). Every number comes from {@link Evidence} through
 * {@link NutritionCalculator}; nothing is estimated beyond those sources.
 */
final class CaffeineLactose {
    enum Source { DECLARED, INSTANT_COFFEE, BREWED_COFFEE, COFFEE, TEA, COLA, UNDECLARED }

    /** What is known about the caffeine in one product. */
    static final class Caffeine {
        final Source source;
        /** Declared mg per 100 g or ml, when the label gives it that way. */
        final BigDecimal per100;
        /** Declared mg per serving, when the label gives it that way. */
        final BigDecimal perServing;
        Caffeine(Source source, BigDecimal per100, BigDecimal perServing) {
            this.source = source; this.per100 = per100; this.perServing = perServing;
        }
    }

    private static final Pattern FREE = Pattern.compile("caffeine[\\s-]*free|decaf", Pattern.CASE_INSENSITIVE);
    private static final Pattern MENTION = Pattern.compile("\\bcaffeine\\b|\\bguarana\\b", Pattern.CASE_INSENSITIVE);
    private static final String AMOUNT = "(\\d{1,4}(?:\\.\\d{1,2})?)\\s*mg\\s*(?:/|per)\\s*(100\\s*ml|100\\s*g|1\\s*l|l|litre|liter|serving|can|bottle)\\b";
    /** "Caffeine 32 mg/100 ml", "caffeine content: 320 mg/l", "caffeine (mg) 80 mg per serving". */
    private static final Pattern NAMED_FIRST = Pattern.compile("caffeine[^0-9\\n]{0,25}?" + AMOUNT, Pattern.CASE_INSENSITIVE);
    /** "32 mg caffeine per 100 ml", "80 mg of caffeine per can". */
    private static final Pattern AMOUNT_FIRST = Pattern.compile(
        "(\\d{1,4}(?:\\.\\d{1,2})?)\\s*mg\\s*(?:of\\s+)?caffeine\\s*(?:/|per)\\s*(100\\s*ml|100\\s*g|1\\s*l|l|litre|liter|serving|can|bottle)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final String[] COLA = {"cola", "coke", "pepsi", "thums up", "iced tea", "ice tea"};
    private static final String[] INSTANT = {"instant", "granules", "soluble"};
    private static final String[] BREWED = {"filter", "ground", "beans", "roast", "roasted", "brew", "decoction", "espresso"};
    private static final String[] TEA = {"tea", "chai"};
    /** Infusions without tea leaves, unless the name says green or black tea. */
    private static final String[] HERBAL = {"herbal", "chamomile", "camomile", "rooibos", "hibiscus", "peppermint", "tulsi"};
    private static final String[] TEA_LEAF = {"green tea", "black tea"};
    private static final String[] PLANT = {"almond", "soy", "soya", "oat", "coconut", "cashew", "rice milk", "plant"};

    /** The caffeine in a product, or null when there is no sign of any. */
    static Caffeine caffeine(String name, String labelText, boolean drink) {
        String label = labelText == null ? "" : labelText;
        String lowName = lower(name);
        if (FREE.matcher(label).find() || FREE.matcher(lowName).find()) return null;
        Caffeine declared = declared(label);
        if (declared != null) return declared;
        FoodCategory.Fcs category = FoodCategory.place(name, drink, null).category;
        boolean beverage = category == FoodCategory.Fcs.BEVERAGES;
        if (beverage && Beverage.containsWord(lowName, COLA)) return new Caffeine(Source.COLA, null, null);
        // NIN's per-cup figures are for tea and coffee made up from the dry product ("masala chai" has no
        // category word of its own); never for a biscuit or toffee that only names tea or coffee.
        if (!drink && (beverage || category == null)) {
            if (Beverage.containsWord(lowName, new String[]{"coffee"})) {
                Source s = Beverage.containsWord(lowName, INSTANT) ? Source.INSTANT_COFFEE
                    : Beverage.containsWord(lowName, BREWED) ? Source.BREWED_COFFEE : Source.COFFEE;
                return new Caffeine(s, null, null);
            }
            if (Beverage.containsWord(lowName, TEA)
                    && (!Beverage.containsWord(lowName, HERBAL) || Beverage.containsWord(lowName, TEA_LEAF))) {
                return new Caffeine(Source.TEA, null, null);
            }
        }
        if (MENTION.matcher(label).find() || MENTION.matcher(lowName).find()) return new Caffeine(Source.UNDECLARED, null, null);
        return null;
    }

    private static Caffeine declared(String label) {
        for (Pattern p : new Pattern[]{NAMED_FIRST, AMOUNT_FIRST}) {
            Matcher m = p.matcher(label);
            if (!m.find()) continue;
            BigDecimal mg = new BigDecimal(m.group(1));
            String basis = m.group(2).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            switch (basis) {
                case "100ml": case "100g": return new Caffeine(Source.DECLARED, mg, null);
                case "1l": case "l": case "litre": case "liter":
                    return new Caffeine(Source.DECLARED, mg.divide(NutritionCalculator.THOUSAND, NutritionCalculator.MC)
                        .multiply(NutritionCalculator.HUNDRED, NutritionCalculator.MC), null);
                default: return new Caffeine(Source.DECLARED, null, mg);
            }
        }
        return null;
    }

    /** The card's caffeine sentence for this shopper, or null when the product shows no caffeine. */
    static String caffeineLine(Caffeine c, Nutrition n, boolean pregnant) {
        if (c == null) return null;
        String limit = ev("nin.caffeine.limit");
        String line;
        switch (c.source) {
            case DECLARED: line = declaredLine(c, n, limit); break;
            case INSTANT_COFFEE: line = cupLine("instant coffee", "nin.caffeine.instant.low", "nin.caffeine.instant.high", limit); break;
            case BREWED_COFFEE: line = cupLine("brewed coffee", "nin.caffeine.brewed.low", "nin.caffeine.brewed.high", limit); break;
            case TEA: line = cupLine("tea", "nin.caffeine.tea.low", "nin.caffeine.tea.high", limit); break;
            case COFFEE: {
                NutritionCalculator.Result most = NutritionCalculator.portionsToCaffeineLimit(Evidence.d("nin.caffeine.brewed.high"));
                NutritionCalculator.Result least = NutritionCalculator.portionsToCaffeineLimit(Evidence.d("nin.caffeine.instant.low"));
                line = "Caffeine: about " + ev("nin.caffeine.instant.low") + "-" + ev("nin.caffeine.instant.high") + " mg a cup ("
                    + ev("nin.caffeine.cup.ml") + " ml) if instant, " + ev("nin.caffeine.brewed.low") + "-" + ev("nin.caffeine.brewed.high")
                    + " mg if brewed (NIN 2024). NIN's tolerable limit of " + limit + " mg a day is " + most.shown(1) + "-" + least.shown(1) + " cups.";
                break;
            }
            case COLA: {
                NutritionCalculator.Result mg = new NutritionCalculator.Result(Evidence.d("who.anc.caffeine.soft.drink"), "mg", "WHO",
                    "who.anc.caffeine.soft.drink");
                line = "Caffeine: colas usually have less than " + ev("who.anc.caffeine.soft.drink") + " mg per "
                    + ev("who.anc.caffeine.soft.drink.ml") + " ml (WHO 2016), under " + NutritionCalculator.caffeineShare(mg).shown(0)
                    + "% of the " + limit + " mg a day NIN 2024 gives as the tolerable limit.";
                break;
            }
            default:
                line = "Contains caffeine; the amount wasn't on the label we read. NIN 2024's tolerable limit is " + limit + " mg a day.";
        }
        if (pregnant) line += " In pregnancy, WHO advises cutting down if you take more than " + ev("who.anc.caffeine.pregnancy")
            + " mg a day, and NIN 2024 says to keep caffeine to a minimum.";
        return line;
    }

    private static String declaredLine(Caffeine c, Nutrition n, String limit) {
        if (c.per100 != null) {
            String per = n != null && n.beverage ? "100 ml" : "100 g";
            Double pack = n == null ? null : n.packAmount();
            NutritionCalculator.Result whole = pack == null ? null : NutritionCalculator.portion(c.per100.doubleValue(), pack, "mg");
            NutritionCalculator.Result counted = whole != null ? whole : new NutritionCalculator.Result(c.per100, "mg", "label");
            return "Caffeine: " + plain(c.per100) + " mg per " + per + " (label)"
                + (whole != null ? ", " + whole.shown(0) + " mg in this " + Nutrition.number(pack) + " " + n.measure() + " pack" : "")
                + ": " + NutritionCalculator.caffeineShare(counted).shown(0) + "% of the " + limit
                + " mg a day NIN 2024 gives as the tolerable limit.";
        }
        NutritionCalculator.Result serving = new NutritionCalculator.Result(c.perServing, "mg", "label");
        return "Caffeine: " + plain(c.perServing) + " mg per serving (label): " + NutritionCalculator.caffeineShare(serving).shown(0)
            + "% of the " + limit + " mg a day NIN 2024 gives as the tolerable limit.";
    }

    private static String cupLine(String what, String lowId, String highId, String limit) {
        NutritionCalculator.Result most = NutritionCalculator.portionsToCaffeineLimit(Evidence.d(highId));
        NutritionCalculator.Result least = NutritionCalculator.portionsToCaffeineLimit(Evidence.d(lowId));
        return "Caffeine: about " + ev(lowId) + "-" + ev(highId) + " mg a cup (" + ev("nin.caffeine.cup.ml") + " ml) of " + what
            + " (NIN 2024). NIN's tolerable limit of " + limit + " mg a day is about " + most.shown(1) + "-" + least.shown(1) + " cups.";
    }

    // ---------------------------------------------------------------- lactose

    /** The card's lactose sentence for a shopper with lactose intolerance, or null when it does not apply. */
    static String lactoseLine(String name, Nutrition n, String ingredients, String labelText, boolean drink) {
        String lowName = lower(name), label = lower(labelText);
        if (lowName.contains("lactose free") || lowName.contains("lactose-free") || label.contains("lactose free") || label.contains("lactose-free")) {
            return "Lactose-free: FSSAI allows at most " + ev("fssai.claims.lactose.free") + " g of lactose per 100 g for that claim.";
        }
        FoodCategory.Fcs category = FoodCategory.place(name, drink, null).category;
        if (category != FoodCategory.Fcs.DAIRY && category != FoodCategory.Fcs.EDIBLE_ICES) return null;
        if (Beverage.containsWord(lowName, PLANT)) return null;
        if (n == null || n.sugarsG == null) return "Lactose: the label we read gives no sugars, so the lactose can't be worked out.";
        boolean noAddedSugar = n.addedSugarsG != null
            || (ingredients != null && !ingredients.isBlank() && !IngredientFacts.namesSugar(ingredients));
        if (!noAddedSugar) return "Lactose: this label doesn't separate milk's own sugar from added sugar, so the lactose can't be worked out.";
        NutritionCalculator.Result per100 = NutritionCalculator.lactoseUpTo(n.sugarsG, n.addedSugarsG);
        String measure = n.measure();
        Double serving = PackMath.servingSize(n);
        NutritionCalculator.Result shown = serving == null ? per100 : NutritionCalculator.portion(per100.d(), serving, "g");
        String portion = serving == null ? "per " + n.unit() : "in a " + Nutrition.number(serving) + " " + measure + " serving";
        StringBuilder line = new StringBuilder("Lactose: up to ").append(shown.shown(1)).append(" g ").append(portion)
            .append(" (its sugars that aren't added; milk's sugar is lactose, NIN 2024).");
        NutritionCalculator.Result tolerated = NutritionCalculator.amountHolding(per100, Evidence.d("lactose.tolerated.low"), measure);
        line.append(" Most adults with lactose intolerance manage ").append(ev("lactose.tolerated.low")).append("-")
            .append(ev("lactose.tolerated.high")).append(" g at a time (2010 systematic review)");
        line.append(tolerated == null ? "." : ", about " + tolerated.shown(0) + " " + measure + " of this.");
        line.append(" NIN 2024 suggests less at a time, with a meal, or as curd.");
        return line.toString();
    }

    private static String ev(String id) { return Evidence.d(id).stripTrailingZeros().toPlainString(); }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replace((char) 0xA0, ' '); }

    private CaffeineLactose() {}
}
