package com.betterchoice.android;

import java.util.List;
import java.util.Locale;

/** Identifies dry tea/coffee and declared plain ingredients; never invents brewed nutrients.
 * Water added during preparation does not establish final drink volume or nutrient extraction.
 */
final class Brew {
    private static final String[] BASE = {"tea", "coffee", "chai", "chicory", "espresso"};
    /** Ready to drink: already a drink, handled by the drink branch. */
    private static final String[] READY = {"cold coffee", "iced coffee", "iced tea", "ice tea", "cold brew",
        "frappe", "ready to drink", "can", "bottle"};
    /** Things that are not tea or coffee to drink, even with the word in the name. */
    private static final String[] NOT_A_BREW = {"biscuit", "cookie", "cake", "rusk", "bar", "candy", "candies",
        "toffee", "chocolate", "wafer", "bun", "bread", "cream", "creamer", "tea tree"};
    /** In a plain list these would mean it is not plain: sugar, milk, creamer and the rest. */
    private static final String[] ADDITIONS = {"sugar", "sugars", "jaggery", "honey", "glucose", "dextrose",
        "maltodextrin", "sucrose", "fructose", "syrup", "milk", "milk solids", "milk powder", "creamer", "whitener",
        "cream", "dairy", "cocoa", "chocolate", "oil", "fat", "sweetener", "sucralose", "aspartame", "acesulfame",
        // Salt dissolves into the cup: Girnar's Kahwa tea bags are 12% rock salt.
        "salt",
        "stevia", "steviol", "saccharin", "malt", "starch", "whey", "caseinate"};
    /** Dry tea or coffee by its name and pack: not sold by volume, not a ready drink, not a food with the word. */
    static boolean isDryBrew(String name, String packUnit) {
        String title = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return Beverage.containsWord(title, BASE) && !"ml".equals(packUnit)
            && !Beverage.containsWord(title, READY) && !Beverage.containsWord(title, NOT_A_BREW);
    }

    /** True when the declared list is tea, coffee or chicory with nothing added that the cup would carry. */
    static boolean plain(String ingredients) {
        List<String> items = IngredientFacts.parts(ingredients);
        if (items.isEmpty() || items.size() > 12) return false;
        String all = String.join(" , ", items).toLowerCase(Locale.ROOT);
        return Beverage.containsWord(all, BASE) && !Beverage.containsWord(all, ADDITIONS);
    }

    private Brew() {}
}
