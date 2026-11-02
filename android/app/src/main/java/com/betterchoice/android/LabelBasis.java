package com.betterchoice.android;

/**
 * Makes the label's basis agree with what the product is, before anything is scored or shown.
 *
 * <p>The food method is defined per 100 g and the drink method per 100 ml. A label does not always
 * oblige: ice cream is commonly labelled per 100 ml, and a drink's figures sometimes arrive per
 * 100 g (the open food database stores every product that way). Each case is handled on its merits
 * and never silently:
 * <ul>
 *   <li><b>A food labelled per 100 ml</b> — converted to per 100 g when the pack states both its
 *       weight and its volume, because that ratio is its real density. Otherwise the figures are
 *       kept exactly as labelled and the card says so. No density is ever assumed: ice cream is
 *       whipped with air and varies too much between brands for a typical value to be honest.</li>
 *   <li><b>A drink labelled per 100 g</b> — kept as labelled and scored as a drink. For anything
 *       water-based the two bases are within a few percent, well inside the width of a band.</li>
 *   <li><b>A drink sold dry</b> — a health-drink powder or tea leaves — is scored as the thing you
 *       buy, per 100 g, and the card says it is the powder being rated, not the made-up drink.</li>
 *   <li><b>A food whose page never says per what</b> — every Zepto row reads "Energy (kcal) 407.0"
 *       with no basis — is read per 100 g, as Indian labels give them, and the card says so. When
 *       that food is sold by volume (a 1 L tub) and the pack gives no weight, whole-pack amounts are
 *       left out rather than treating a litre as a kilogram.</li>
 * </ul>
 */
final class LabelBasis {
    /** Said on the card whenever a label gives figures without saying per 100 g or per 100 ml. */
    static final String UNKNOWN = "I couldn't find out whether these figures are per 100 g or per 100 ml: the label doesn't say. ";
    static Nutrition settle(Nutrition label, Beverage identity, String packText) {
        if (label == null || identity == null) return label;
        if (identity.definitelyFood && !identity.isDrink() && label.beverage) {
            Nutrition n = label.copy();
            double[] wv = NutritionParser.weightAndVolume(packText);
            double density = wv == null ? 0 : wv[0] / wv[1];
            // Real foods sold by volume sit between whipped (about 0.3) and dense (about 1.4).
            if (density >= 0.3 && density <= 1.4) {
                double factor = 1 / density;
                n.energyKcal = scaled(n.energyKcal, factor);
                n.sugarsG = scaled(n.sugarsG, factor);
                n.saturatedFatG = scaled(n.saturatedFatG, factor);
                n.sodiumMg = scaled(n.sodiumMg, factor);
                n.fibreG = scaled(n.fibreG, factor);
                n.proteinG = scaled(n.proteinG, factor);
                n.totalFatG = scaled(n.totalFatG, factor);
                n.transFatG = scaled(n.transFatG, factor);
                n.addedSugarsG = scaled(n.addedSugarsG, factor);
                n.carbohydrateG = scaled(n.carbohydrateG, factor);
                // Serving and pack are now in grams too, so the per-serving and whole-pack columns
                // stay exactly what they were in absolute terms.
                if (n.servingSize != null) n.servingSize = n.servingSize * density;
                n.netQuantity = wv[0];
                n.netUnit = "g";
                n.beverage = false;
                n.basisNote = "The label is per 100 ml. Converted to per 100 g using the pack's own "
                    + Nutrition.number(wv[0]) + " g in " + Nutrition.number(wv[1]) + " ml.";
            } else {
                n.basisNote = "The label is per 100 ml. Food grading needs a declared mass/volume conversion.";
            }
            return n;
        }
        if (identity.isDrink() && !label.beverage) {
            Nutrition n = label.copy();
            if (!label.basisStated) {
                // A shop page that lists a drink's nutrition without saying per what: drink labels
                // are per 100 ml, and calling it "per 100 g" was simply wrong. Read per 100 ml means
                // shown per 100 ml everywhere on the card, not rated one way and printed another.
                n.beverage = true;
                n.basisNote = UNKNOWN + "Rated as per 100 ml, as drink labels give them.";
            } else {
                n.basisNote = "The figures are per 100 g; a volume conversion needs declared density.";
            }
            return n;
        }
        if (identity.soldDry) {
            Nutrition n = label.copy();
            n.basisNote = (label.basisStated ? "" : UNKNOWN)
                + "Rated as sold — the dry product, per 100 g. Made up with milk or water, "
                + "the drink you pour is a different thing.";
            return n;
        }
        if (!identity.isDrink() && !label.beverage) {
            boolean byVolume = "ml".equals(label.netUnit);
            if (label.basisStated && !byVolume) return label;
            Nutrition n = label.copy();
            double[] wv = byVolume ? NutritionParser.weightAndVolume(packText) : null;
            if (wv != null) {
                n.netQuantity = wv[0];
                n.netUnit = "g";
            }
            if (!label.basisStated) {
                n.basisNote = byVolume && wv == null
                    ? UNKNOWN + "Rated as per 100 g, as food labels give them. The pack is sold by "
                        + "volume, so whole-pack amounts are left out."
                    : UNKNOWN + "Rated as per 100 g, as food labels give them.";
            }
            return n;
        }
        return label;
    }

    private static Double scaled(Double value, double factor) {
        return NutrientMath.portion(value, factor * 100);
    }

    private LabelBasis() {}
}
