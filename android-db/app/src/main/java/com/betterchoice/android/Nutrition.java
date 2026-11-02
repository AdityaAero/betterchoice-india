package com.betterchoice.android;

import java.util.Locale;

/**
 * Nutrients per 100 g (food) or per 100 ml (beverage). Null means "not found".
 * Pack size and serving size are kept alongside so the same values can be shown per serving and
 * per whole pack without ever changing the comparison basis.
 */
final class Nutrition {
    Double energyKcal, sugarsG, saturatedFatG, sodiumMg, fibreG, proteinG;
    /**
     * Total and trans fat. Neither is part of the grade; they are read so that the fat which is not
     * saturated can be told apart. Nuts are high in fat and most of it is the unsaturated kind, and
     * without these figures the app could only ever say the bad half of that.
     */
    Double totalFatG, transFatG;
    /**
     * Added sugar, when the label declares it apart from total sugar (FSSAI labels increasingly do).
     * Not part of the grade, which uses total sugar as the published method requires; it is what
     * tells a juice from a sugared fruit drink, and it is said on the card.
     */
    Double addedSugarsG;
    Double carbohydrateG;
    /** The label said what its figures are per (per 100 g/ml or per serving). False when it did not. */
    boolean basisStated;
    boolean beverage;
    /** Declared net quantity in g or ml, e.g. 150 for a 150 g packet. */
    Double netQuantity;
    /** "g" or "ml": how the pack is sold, when the text said. Null when unknown. */
    String netUnit;
    /** Declared serving size in g or ml, e.g. 30. */
    Double servingSize;
    /** Declared servings per pack, when the label states it. */
    Double servingsPerPack;
    /**
     * Set when the basis had to be settled rather than read straight off the label — an ice cream
     * labelled per 100 ml, a drink labelled per 100 g — so the card can say what was done.
     */
    String basisNote;
    /**
     * Some figures were repaired because the text they came from could not be right as read: a
     * photo's lost decimal point ("Total Fat 324g"), or g read as 9 or q. Figures that only look
     * plausible cannot be checked the same way ("Total Sugars 12g" for 1.2 g), so a repaired
     * reading is treated as uncertain as a whole: the card asks for a closer look.
     */
    boolean ocrRepaired;
    /**
     * The label gave protein, carbohydrate and fat but no energy (Zepto lists some products so), and the energy was
     * worked out from them with FSSAI's own factors. Said on the card; never set when the label states its energy.
     */
    boolean energyFromMacros;

    /** Energy plus at least two of sugars, saturated fat and sodium are needed to grade honestly. */
    boolean usable() {
        int known = (sugarsG != null ? 1 : 0) + (saturatedFatG != null ? 1 : 0) + (sodiumMg != null ? 1 : 0);
        return energyKcal != null && known >= 2;
    }

    String missingNegatives() {
        StringBuilder missing = new StringBuilder();
        if (sugarsG == null) missing.append("sugars");
        if (saturatedFatG == null) missing.append(missing.length() > 0 ? ", " : "").append("saturated fat");
        if (sodiumMg == null) missing.append(missing.length() > 0 ? ", " : "").append("sodium");
        return missing.toString();
    }

    String unit() { return beverage ? "100 ml" : "100 g"; }

    /**
     * The pack's size in the same measure as the figures, or null. A 1 L ice-cream tub rated per
     * 100 g has no honest whole-pack amount: a litre of ice cream weighs about half a kilogram, and
     * multiplying by 1000 as if it were grams doubled every whole-pack and daily-impact figure.
     */
    Double packAmount() {
        if (netQuantity == null) return null;
        if (netUnit != null && !netUnit.equals(measure())) return null;
        return netQuantity;
    }

    String measure() { return beverage ? "ml" : "g"; }

    /** Copies the values, so a stored result cannot be changed by a later screen read. */
    Nutrition copy() {
        Nutrition n = new Nutrition();
        n.energyKcal = energyKcal; n.sugarsG = sugarsG; n.saturatedFatG = saturatedFatG;
        n.sodiumMg = sodiumMg; n.fibreG = fibreG; n.proteinG = proteinG; n.beverage = beverage;
        n.totalFatG = totalFatG; n.transFatG = transFatG;
        n.addedSugarsG = addedSugarsG; n.basisStated = basisStated;
        n.carbohydrateG = carbohydrateG;
        n.netQuantity = netQuantity; n.netUnit = netUnit; n.servingSize = servingSize; n.servingsPerPack = servingsPerPack;
        n.basisNote = basisNote;
        n.ocrRepaired = ocrRepaired;
        n.energyFromMacros = energyFromMacros;
        return n;
    }

    static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
