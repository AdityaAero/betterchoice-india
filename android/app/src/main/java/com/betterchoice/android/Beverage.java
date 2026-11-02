package com.betterchoice.android;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a product is — a drink, or a food — decided before anything is scored, because the two are
 * scored by different methods and a wrong call here makes every number after it wrong.
 *
 * <h3>How the call is made</h3>
 * <ol>
 *   <li><b>Words are matched whole.</b> Substring matching was the root of the worst mistakes this
 *       class has made: "chocolate" contains "cola", so every chocolate was a soft drink; and "milk "
 *       matched Dairy Milk, Milk Bikis and coconut milk, so all three were a glass of milk.</li>
 *   <li><b>A food word vetoes a drink.</b> Chocolate, biscuit, ice cream, kulfi, paneer, powder and
 *       the rest are foods whatever else their name contains, and whatever unit their label uses —
 *       an ice cream labelled per 100 ml is still eaten, not drunk.</li>
 *   <li><b>A product is only a drink when something positively says so</b>: a drink noun (juice,
 *       lassi, milkshake, soda, toned milk…), a pack sold in ml or litres, or a label stated per
 *       100 ml. A sweetener on its own says nothing — sugar-free chocolate is still chocolate.</li>
 *   <li><b>The pack size breaks ties.</b> "Chocolate milk 200 ml" is a drink and "milk chocolate
 *       20 g" is a food. A drink noun on a pack sold in grams — a health-drink powder, tea leaves —
 *       is scored as the dry product it is when sold, and says so.</li>
 * </ol>
 *
 * <p>The kind of drink is then checked against the label itself ({@link #checkedAgainst}), so a
 * sentence like "its sugar is lactose, not added" is only ever printed when the numbers bear it out.
 */
final class Beverage {
    enum Kind {
        WATER("Water", "Nothing beats water."),
        UNSWEETENED_BREW("Unsweetened tea or coffee", "No sugar, near enough no energy."),
        MILK("Milk", "Its sugar is lactose, not added."),
        PLANT_MILK("Plant milk", "Made from plants, so there is no lactose in it."),
        JUICE("Fruit juice", "No added sugar, but fruit sugars still count: WHO calls these free sugars."),
        COCONUT_WATER("Coconut water", "Its sugar occurs naturally, and it still counts."),
        JUICE_DRINK("Juice drink", "Part juice, part sugar."),
        /**
         * Called juice or fruit by the shop, with nothing on the page to say whether sugar was
         * added. No claim is made either way, and no fruit share is credited.
         */
        FRUIT_DRINK("Fruit drink", null),
        SWEETENED("Sweetened drink", "Sugar is the main thing to watch in a drink."),
        SWEETENER_ONLY("Zero-sugar drink", "Better than the sugary version. Still not water."),
        OTHER("Drink", null);

        final String label;
        final String note;
        Kind(String label, String note) { this.label = label; this.note = note; }
    }

    /** Null for a food. */
    final Kind kind;
    /** A non-sugar sweetener is declared. Worth four adverse points on the beverage scale. */
    final boolean hasSweetener;
    /**
     * For a drink, its fruit share. For a food, its nut and pulse share. Either way it is never
     * guessed: it comes from a stated percentage, or from an identity that fixes it — a product sold
     * as juice is fruit, and a bag of salted peanuts is peanuts.
     */
    final int fruitPercent;
    /** Positively identified as a food, so a per-100-ml label must not turn it into a drink. */
    final boolean definitelyFood;
    /** A drink sold dry — a powder or leaves — and therefore scored as sold, not as made up. */
    final boolean soldDry;

    private Beverage(Kind kind, boolean hasSweetener, int fruitPercent, boolean definitelyFood, boolean soldDry) {
        this.kind = kind; this.hasSweetener = hasSweetener; this.fruitPercent = fruitPercent;
        this.definitelyFood = definitelyFood; this.soldDry = soldDry;
    }

    /** Nothing identified this as a drink, so the food branch handles it. */
    static final Beverage NOT_A_DRINK = new Beverage(null, false, 0, false, false);

    static Beverage of(Kind kind) { return new Beverage(kind, false, defaultFruit(kind), false, false); }

    /**
     * This drink with a non-sugar sweetener declared, for catalogue records whose curator has marked
     * it (a diet cola whose ingredient list was never entered is still sweetened by something). The
     * label's own sugar figure then decides between zero-sugar and sugar-plus-sweetener.
     */
    Beverage withSweetener() {
        if (!isDrink() || hasSweetener) return this;
        Kind sweetened = kind == Kind.SWEETENED || kind == Kind.JUICE_DRINK ? kind : Kind.SWEETENER_ONLY;
        return new Beverage(sweetened, true, fruitPercent, false, false);
    }

    private static Beverage food(boolean definite, int nutShare, boolean soldDry) {
        return new Beverage(null, false, nutShare, definite, soldDry);
    }

    private static int defaultFruit(Kind kind) {
        return kind == Kind.JUICE || kind == Kind.COCONUT_WATER ? 100 : 0;
    }

    // --- Words ---------------------------------------------------------------------------------------

    /** Nouns that name a drink outright. Plain "milk" is not here: "milk chocolate" is not a drink. */
    private static final String[] DRINK_FORM = {
        "milkshake", "shake", "lassi", "buttermilk", "butter milk", "chaas", "chhach", "smoothie",
        "flavoured milk", "flavored milk",
        "chocolate milk", "badam milk", "rose milk", "cold coffee", "iced coffee", "iced tea", "ice tea",
        "juice", "nectar", "soft drink", "energy drink", "sports drink", "drink", "beverage", "cola",
        "coke", "soda", "tonic water", "lemonade", "nimbu pani", "squash", "sharbat", "kombucha",
        "coconut water", "tender coconut", "mineral water", "drinking water", "spring water",
        "sparkling water", "toned milk", "double toned", "full cream milk", "skimmed milk", "slim milk",
        "cow milk", "buffalo milk", "soy milk", "soya milk", "almond milk", "oat milk", "rice milk",
        "cashew milk",
        "yogurt drink", "yoghurt drink", "green tea", "black tea", "black coffee", "herbal tea", "kahwa"};

    /** Nouns that name a food outright, and win over any drink word in the same name. */
    private static final String[] FOOD_FORM = {
        "chocolate", "dairy milk", "silk", "bar", "biscuit", "bikis", "cookie", "cracker", "wafer",
        "cake", "rusk", "bread", "bun", "ice cream", "icecream", "frozen dessert", "kulfi", "cone",
        "cornetto", "sundae", "gelato", "ice candy", "popsicle", "candy", "candies", "toffee", "eclair",
        "lollipop", "gum", "jelly", "jellies", "gummy", "gummies", "marshmallow", "peda", "barfi",
        "burfi", "halwa", "laddoo", "ladoo", "laddu", "mithai", "rasgulla", "gulab jamun", "kalakand",
        "milk cake", "powder", "spread", "peanut butter", "butter", "ghee", "cheese", "paneer", "khoa",
        "khoya", "mawa", "curd", "dahi", "yogurt", "yoghurt", "condensed milk", "milkmaid",
        "coconut milk", "cream", "chips", "crisps", "namkeen", "bhujia", "mixture", "peanut", "nut",
        "almond", "cashew", "makhana", "cereal", "muesli", "granola", "oats", "cornflakes", "flakes",
        "noodles", "pasta", "sauce", "ketchup", "pickle", "jam", "atta", "flour",
        // Sold by the litre and still not drunk: shop pages now give the pack size even when the
        // name does not, so "Sunflower Oil" with "Weight 1 L" must not become a drink.
        "oil", "vinegar", "honey", "mayonnaise", "mayo", "dressing", "dip", "chutney", "paste", "custard",
        "pudding", "dessert", "mousse"};

    private static final String[] WATER_WORDS = {"packaged drinking water", "mineral water", "drinking water",
        "natural mineral water", "spring water", "soda water", "club soda", "sparkling water"};
    private static final String[] FLAVOUR_WORDS = {"flavoured", "flavored", "lime water", "jeera", "masala soda"};
    private static final String[] MILK_WORDS = {"milk", "toned milk", "double toned", "full cream milk",
        "skimmed milk", "slim milk", "cow milk", "buffalo milk"};
    /** Checked before plain milk: these carry no lactose, and saying they do would be false. */
    private static final String[] PLANT_MILK_WORDS = {"soy milk", "soya milk", "almond milk", "oat milk",
        "rice milk", "cashew milk"};
    private static final String[] SWEET_MILK_WORDS = {"flavoured milk", "flavored milk", "milkshake", "shake",
        "lassi", "cold coffee", "badam milk", "rose milk", "chocolate milk"};
    private static final String[] COCONUT_WATER_WORDS = {"coconut water", "tender coconut"};
    private static final String[] JUICE_WORDS = {"juice", "fruit juice"};
    private static final String[] JUICE_DRINK_WORDS = {"nectar", "juice drink", "fruit drink", "fruit beverage",
        "squash", "sharbat", "crush"};
    private static final String[] SWEET_WORDS = {"cola", "coke", "soft drink", "carbonated", "energy drink",
        "sports drink", "iced tea", "ice tea", "lemonade", "nimbu pani", "aerated", "soda"};
    private static final String[] ZERO_WORDS = {"diet", "zero sugar", "sugar free", "sugar-free", "no sugar", "zero"};
    private static final String[] BREW_WORDS = {"green tea", "black tea", "black coffee", "herbal tea", "kahwa"};
    /** Sweeteners FSSAI labels name, plus the INS numbers they are declared under. */
    private static final String[] SWEETENERS = {"aspartame", "sucralose", "acesulfame", "saccharin", "stevia",
        "steviol", "neotame", "ins 950", "ins 951", "ins 952", "ins 954", "ins 955", "ins 960", "ins 961"};

    /**
     * Pulses. Nutri-Score 2023 credits pulses with fruit and vegetables, but no longer nuts: nuts, seeds
     * and peanuts are scored on the fats, oils, nuts and seeds table instead (report recap PDF p131, p133).
     */
    private static final String[] NUT_PULSE_WORDS = {"roasted chana", "chana", "chickpea", "moong", "rajma",
        "lentil", "masoor", "lobia", "cowpea", "dal"};
    /**
     * Words that mean the nuts are only part of the product. Masala peanuts are coated in batter,
     * chikki is half jaggery and kaju katli is half sugar, so the name alone does not establish the
     * share; only a stated percentage can.
     */
    private static final String[] COMPOSITE_WORDS = {"chocolate", "bar", "biscuit", "cookie", "cake",
        "butter", "spread", "chikki", "brittle", "masala", "coated", "besan", "namkeen", "bhujia",
        "mixture", "mix", "honey", "sweet", "ladoo", "laddoo", "barfi", "burfi", "halwa", "cream", "oil",
        "flour", "atta", "milk", "katli", "roll", "pak", "sattu"};

    // --- Classification -----------------------------------------------------------------------------

    /**
     * @param name             the product's name as the shop shows it
     * @param text             everything else read from the page or the pack: ingredients, net
     *                         quantity, allergen statements
     * @param nutritionIsPerMl the label states its values per 100 ml
     */
    static Beverage classify(String name, String text, boolean nutritionIsPerMl) {
        String title = lower(name);
        String all = title + " \n " + lower(text);
        boolean foodForm = containsWord(title, FOOD_FORM);
        boolean drinkForm = containsWord(title, DRINK_FORM);
        String unit = packUnit(name, text);
        int nutShare = nutShare(title, lower(text));

        if (foodForm && !drinkForm) return food(true, nutShare, false);
        if (drinkForm) {
            // A drink noun on a pack sold by weight: a powder, a mix, tea leaves. Scored as sold.
            if ("g".equals(unit)) return food(true, nutShare, true);
            return drink(title, all);
        }
        if ("ml".equals(unit) || nutritionIsPerMl) return drink(title, all);
        return nutShare > 0 ? food(false, nutShare, false) : NOT_A_DRINK;
    }

    /** Which kind of drink, once it is established that this is one. */
    private static Beverage drink(String title, String all) {
        // Named, or declared in the ingredient list by class and number: "Sweeteners (968, 955)".
        boolean sweetener = containsWord(all, SWEETENERS) || IngredientFacts.hasSweetener(IngredientFacts.section(all));
        if (containsWord(title, WATER_WORDS) && !containsWord(title, FLAVOUR_WORDS) && !sweetener) return of(Kind.WATER);
        if (containsWord(title, SWEET_MILK_WORDS)) return new Beverage(Kind.SWEETENED, sweetener, 0, false, false);
        if (containsWord(title, BREW_WORDS) && !sweetener && !containsWord(title, SWEET_WORDS)) return of(Kind.UNSWEETENED_BREW);
        if (containsWord(title, COCONUT_WATER_WORDS) && !sweetener) return of(Kind.COCONUT_WATER);
        if (containsWord(title, PLANT_MILK_WORDS)) return new Beverage(Kind.PLANT_MILK, sweetener, 0, false, false);
        if (containsWord(title, MILK_WORDS) && !sweetener) return of(Kind.MILK);
        if (containsWord(title, JUICE_DRINK_WORDS)) return withFruit(Kind.JUICE_DRINK, all, sweetener, 0);
        if (containsWord(title, JUICE_WORDS)) {
            // The shop's word "juice" is not evidence. Frooti is sold as "Mango Fruit Juice" and is
            // 11.2% mango pulp with sugar as its second ingredient. Real juice has to be shown: a
            // "100%" claim or a "no added sugar" statement. Sugar in the ingredient list settles
            // it the other way, and with neither, nothing is claimed.
            if (IngredientFacts.addsSugar(IngredientFacts.section(all))) return withFruit(Kind.JUICE_DRINK, all, sweetener, 0);
            if (HUNDRED_PERCENT.matcher(all).find()) return withFruit(Kind.JUICE, all, sweetener, 100);
            if (containsWord(all, NO_ADDED_SUGAR)) return withFruit(Kind.JUICE, all, sweetener, 0);
            return withFruit(Kind.FRUIT_DRINK, all, sweetener, 0);
        }
        // A zero-sugar soft drink is sweetened by something, even when the ingredients are not on screen.
        if (sweetener || (containsWord(title, ZERO_WORDS) && containsWord(title, SWEET_WORDS))) {
            return new Beverage(Kind.SWEETENER_ONLY, true, 0, false, false);
        }
        if (containsWord(title, SWEET_WORDS)) return new Beverage(Kind.SWEETENED, false, 0, false, false);
        return new Beverage(Kind.OTHER, sweetener, 0, false, false);
    }

    /**
     * Checks the kind of drink against the label's own numbers. A "milk" carrying 57 g of sugar is
     * not plain milk, and "its sugar is lactose, not added" would be false; a "sweetened drink" with
     * 2 g of sugar is not really sweetened. The kind is corrected so every sentence printed about it
     * is one the numbers support.
     */
    Beverage checkedAgainst(Nutrition n) {
        if (kind == null || n == null) return this;
        Double sugar = n.sugarsG;
        Double energy = n.energyKcal;
        switch (kind) {
            case MILK:
                // Plain milk carries about 4.5 to 5 g of lactose per 100 ml and nothing more.
                if (sugar != null && sugar > 6) return new Beverage(Kind.SWEETENED, hasSweetener, 0, false, false);
                break;
            case WATER:
                if (sugar != null && sugar > 0.5) return new Beverage(Kind.SWEETENED, hasSweetener, 0, false, false);
                if (energy != null && energy > 5) return new Beverage(Kind.OTHER, hasSweetener, 0, false, false);
                break;
            case PLANT_MILK:
                // Unsweetened soy, almond or oat drinks carry little sugar; above the "low sugar"
                // level for a drink, sugar has been added and that is what matters about it.
                if (sugar != null && sugar > 2.5) return new Beverage(Kind.SWEETENED, hasSweetener, 0, false, false);
                break;
            case UNSWEETENED_BREW:
                if (sugar != null && sugar > 0.5) return new Beverage(Kind.SWEETENED, hasSweetener, 0, false, false);
                break;
            case SWEETENER_ONLY:
                // Sugar and a sweetener together: common in reduced-sugar colas. Both still count.
                if (sugar != null && sugar > 0.5) return new Beverage(Kind.SWEETENED, true, 0, false, false);
                break;
            case JUICE:
            case FRUIT_DRINK:
                // The label's own added-sugar line is the strongest evidence there is.
                if (n.addedSugarsG != null && n.addedSugarsG > 0.5) {
                    return new Beverage(Kind.JUICE_DRINK, hasSweetener, kind == Kind.JUICE && fruitPercent == 100 ? 0 : fruitPercent, false, false);
                }
                if (kind == Kind.FRUIT_DRINK && n.addedSugarsG != null) return new Beverage(Kind.JUICE, hasSweetener, fruitPercent, false, false);
                break;
            case SWEETENED:
                if (sugar != null && sugar <= 0.5 && hasSweetener) return new Beverage(Kind.SWEETENER_ONLY, true, 0, false, false);
                // At or below the "low sugar" claim level, calling it a sweetened drink misleads.
                if (sugar != null && sugar <= 2.5 && !hasSweetener) return new Beverage(Kind.OTHER, false, 0, false, false);
                break;
            default:
                break;
        }
        return this;
    }

    // --- Pack size and shares -------------------------------------------------------------------------

    private static final Pattern NAME_SIZE = Pattern.compile(
        "(?<![0-9.])(\\d{1,4}(?:\\.\\d{1,2})?)\\s*(ml|l|ltr|litres?|liters?|kg|g|gm|gms|grams?)(?![a-z])");

    /** A size in a name followed by one of these is a nutrient claim ("5g Fibre"), not the pack. */
    private static final Pattern CLAIM_AFTER = Pattern.compile(
        "^\\s*(?:of\\s+)?(?:high\\s+)?(?:protein|fib(?:re|er)|sugars?|fats?|carbs?|caffeine|prebiotic|probiotic)");

    /**
     * "g" when the pack is sold by weight, "ml" by volume, null when nothing says. The page's own
     * pack size comes first ("1 pc (300 ml)", "Weight 300 ml"). A size in the name counts only
     * when it is not a nutrient claim: "Paper Boat Zero Prebiotic Soda … 5g Fibre" was taken as
     * sold in grams, so a can of soda was rated as a dry powder.
     */
    static String packUnit(String name, String text) {
        String unit = NutritionParser.netQuantityUnit(text);
        if (unit == null) {
            String title = lower(name);
            Matcher m = NAME_SIZE.matcher(title);
            while (m.find()) {
                if (CLAIM_AFTER.matcher(title.substring(m.end())).find()) continue;
                unit = m.group(2);    // the last size in a name is the pack size
            }
        }
        if (unit == null) return null;
        return unit.startsWith("m") || unit.startsWith("l") ? "ml" : "g";
    }

    private static final Pattern STATED_NUT_SHARE = Pattern.compile(
        "(?:chana|chickpeas?|moong|rajma|lentils?|masoor|lobia|cowpeas?)"
        + "\\s*(?:dal)?\\s*[\\(\\[]?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)\\s*%");

    /**
     * The pulse share of a food: a stated percentage if the label gives one, otherwise 90 for a
     * product that is plainly just pulses by name, otherwise nothing. Nuts no longer count (2023).
     */
    private static int nutShare(String title, String text) {
        Matcher stated = STATED_NUT_SHARE.matcher(text);
        if (stated.find()) {
            double value = Double.parseDouble(stated.group(1));
            if (value > 0 && value <= 100) return (int) Math.round(value);
        }
        if (containsWord(title, NUT_PULSE_WORDS) && !containsWord(title, COMPOSITE_WORDS)) return 90;
        return 0;
    }

    /** Real juice evidence: "100% juice", "100% fruit", "juice … 100%". */
    private static final Pattern HUNDRED_PERCENT = Pattern.compile(
        "100\\s*%\\s*(?:[a-z]+\\s+){0,3}(?:fruit|juice)|(?:juice|fruit)[^\\n0-9]{0,15}100\\s*%");
    private static final String[] NO_ADDED_SUGAR = {"no added sugar", "no sugar added", "without added sugar",
        "not from concentrate"};

    /**
     * @param fallback the fruit share to assume when none is stated: 100 only with a "100%" claim,
     *                 otherwise 0, so a share is never credited from a name alone.
     */
    private static Beverage withFruit(Kind kind, String text, boolean sweetener, int fallback) {
        int percent = fallback;
        // Read the share from the ingredient list first, where "Mango Pulp (11.2%)" lives; the rest
        // of a shop page is full of other percentages.
        String ingredients = IngredientFacts.section(text);
        Matcher m = FRUIT_PERCENT.matcher(ingredients != null ? ingredients.toLowerCase(Locale.ROOT) : text);
        if (ingredients != null && !m.find()) m = FRUIT_PERCENT.matcher(text);
        else m.reset();
        if (m.find()) {
            String stated = m.group(1) != null ? m.group(1) : m.group(2);
            if (stated != null) {
                int value = (int) Math.round(Double.parseDouble(stated));
                if (value >= 0 && value <= 100) percent = value;
            }
        }
        return new Beverage(kind, sweetener, percent, false, false);
    }

    private static final Pattern FRUIT_PERCENT = Pattern.compile(
        "(\\d{1,3}(?:\\.\\d)?)\\s*%\\s*(?:fruit|juice|pulp|mango|orange|apple)"
        + "|(?:fruit|juice|pulp)[^0-9\\n]{0,12}(\\d{1,3}(?:\\.\\d)?)\\s*%");

    boolean isDrink() { return kind != null; }

    /** A dry product rated as sold, per 100 g: a tea or coffee premix with no water instruction. */
    static Beverage dryProduct() { return food(true, 0, true); }

    /** A premix made up with water: sweetened when its list adds sugar, zero-sugar with only a sweetener. */
    static Beverage madeUpDrink(boolean sugar, boolean sweetener) {
        if (sugar) return new Beverage(Kind.SWEETENED, sweetener, 0, false, false);
        if (sweetener) return new Beverage(Kind.SWEETENER_ONLY, true, 0, false, false);
        return new Beverage(Kind.OTHER, false, 0, false, false);
    }

    /** True when this is water and therefore fine without any nutrition panel at all. */
    boolean isPlainWater() { return kind == Kind.WATER; }

    /**
     * Whole-word match, allowing a plural "s" or "es". "chocolate" does not contain the word "cola",
     * "buttermilk" does not contain the word "butter", and "watermelon" does not contain "water".
     */
    static boolean containsWord(String text, String[] phrases) {
        if (text == null || text.isEmpty()) return false;
        for (String phrase : phrases) {
            Pattern p = WORD_CACHE.computeIfAbsent(phrase, key ->
                Pattern.compile("(?<![a-z0-9])" + Pattern.quote(key) + "(?:s|es)?(?![a-z0-9])"));
            if (p.matcher(text).find()) return true;
        }
        return false;
    }

    private static final java.util.Map<String, Pattern> WORD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace((char) 0xA0, ' ');
    }
}
