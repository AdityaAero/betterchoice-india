package com.betterchoice.android;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Places every product in the FSSAI food category system (Appendix A, categories 1.0 to 16.0; evidence
 * {@code fssai.fcs.N}), so no product is left without a route. Each category names what to compare,
 * and when the swap catalogue has nothing for it, the card shows "what to look for" computed from the
 * NIN 2024 thresholds (Table 15.1) instead of a blank.
 */
final class FoodCategory {
    enum Fcs {
        DAIRY(1, "Dairy products", "sugar (flavoured), saturated fat, sodium (cheese)"),
        FATS_OILS(2, "Fats and oils", "saturated fat and trans fat"),
        EDIBLE_ICES(3, "Edible ices", "sugar and saturated fat"),
        FRUIT_VEG_PULSES_NUTS(4, "Fruit, vegetables, pulses, nuts and seeds", "sodium (pickles), sugar (jams); nuts are not HFSS"),
        CONFECTIONERY(5, "Confectionery", "sugar and saturated fat"),
        CEREALS(6, "Cereals and cereal products", "fibre and whole grain, sugar, sodium"),
        BAKERY(7, "Bakery wares", "refined flour, sugar, saturated fat, sodium"),
        MEAT(8, "Meat and meat products", "sodium and saturated fat"),
        FISH(9, "Fish and fish products", "sodium"),
        EGGS(10, "Eggs and egg products", "sodium in prepared egg products"),
        SWEETENERS(11, "Sweeteners, including honey", "added sugar; sweeteners' ADI"),
        SALTS_SPICES_SAUCES(12, "Salts, spices, soups, sauces and protein products", "sodium and sugar per serving"),
        PARTICULAR_USES(13, "Foods for particular nutritional uses", "protein, added sugar, sweeteners"),
        BEVERAGES(14, "Beverages", "sugar, sweeteners, caffeine"),
        SAVOURIES(15, "Ready-to-eat savouries", "sodium, saturated fat, energy"),
        PREPARED(16, "Prepared foods", "sodium, saturated fat, energy per portion");

        final int number;
        final String name, compare;
        Fcs(int number, String name, String compare) { this.number = number; this.name = name; this.compare = compare; }
        String evidence() { return "fssai.fcs." + number; }
    }

    /** Where a product sits, and whether the adult rating applies to it at all. */
    static final class Placement {
        final Fcs category;          // null: no category could be read from the name
        final boolean infantFood, nutsOrSeeds;
        Placement(Fcs category, boolean infantFood, boolean nutsOrSeeds) {
            this.category = category; this.infantFood = infantFood; this.nutsOrSeeds = nutsOrSeeds;
        }
    }

    private static final String[][] WORDS = {
        // Checked in this order: specific before general ("peanut butter" before "butter", "ice cream" before "cream").
        {"PARTICULAR_USES", "infant", "baby food", "cerelac", "follow-up formula", "infant formula", "protein powder", "whey protein",
            "mass gainer", "meal replacement", "protein bar", "energy bar", "health drink", "horlicks", "bournvita", "complan", "pediasure", "ensure", "diabetic"},
        // "Ready to eat" makes a dish prepared food, whatever it is made of (rajma curry is not a pulse pack).
        {"PREPARED", "ready to eat", "ready meal", "instant meal", "heat and eat", "heat & eat"},
        {"EDIBLE_ICES", "ice cream", "icecream", "kulfi", "frozen dessert", "gelato", "sorbet", "popsicle"},
        {"FRUIT_VEG_PULSES_NUTS", "peanut butter", "almond butter", "nut butter", "jam", "pickle", "achar", "murabba", "raisins",
            "dates", "dry fruits", "almonds", "cashews", "walnuts", "pistachio", "seeds", "peanuts", "dal", "rajma", "chana dal",
            "moong", "masoor", "frozen peas", "sweet corn", "mushroom", "tomato puree"},
        {"SAVOURIES", "chips", "crisps", "namkeen", "bhujia", "mixture", "popcorn", "makhana", "puffs", "puff", "nachos", "khakhra",
            "chakli", "murukku", "mathri", "sev", "kurkure", "potato wafers", "roasted chana", "trail mix"},
        {"CONFECTIONERY", "chocolate", "chocolates", "candy", "candies", "toffee", "toffees", "eclairs", "chikki", "gajak", "mithai",
            "ladoo", "ladoos", "laddu", "laddoo", "laddoos", "barfi",
            "halwa", "gummies", "lollipop", "soan papdi", "rasgulla", "gulab jamun"},
        {"BAKERY", "bread", "breads", "bun", "buns", "pav", "biscuit", "biscuits", "cookie", "cookies", "rusk", "rusks", "cake", "cakes",
            "muffin", "muffins", "toast", "khari", "croissant", "wafer"},
        // Tea and coffee before "masala" and "milk": masala chai is a tea, not a spice mix. After bakery and
        // confectionery, so a tea-time biscuit or a coffee toffee stays where it is.
        {"BEVERAGES", "masala chai", "chai", "tea premix", "coffee premix", "tea bags", "green tea", "instant coffee", "filter coffee"},
        // Named oils before cereals: "Rice Bran Oil" is an oil, not rice.
        {"FATS_OILS", "ghee", "vanaspati", "margarine", "cooking oil", "mustard oil", "sunflower oil", "groundnut oil",
            "rice bran oil", "olive oil", "coconut oil", "butter"},
        {"CEREALS", "atta", "flour", "maida", "rice", "poha", "oats", "muesli", "granola", "cornflakes", "corn flakes", "millet",
            "ragi", "jowar", "bajra", "noodles", "pasta", "vermicelli", "suji", "rava", "daliya", "upma mix", "idli mix", "dosa mix", "cereal"},
        {"DAIRY", "milk", "curd", "dahi", "yoghurt", "yogurt", "paneer", "cheese", "cream", "lassi", "buttermilk", "chaas",
            "khoa", "khoya", "shrikhand"},
        {"MEAT", "chicken", "mutton", "sausage", "salami", "nugget", "kebab", "keema", "bacon", "ham", "meat"},
        {"FISH", "fish", "prawn", "shrimp", "tuna", "sardine", "surimi"},
        {"EGGS", "egg", "eggs"},
        {"SWEETENERS", "sugar", "jaggery", "gur", "honey", "sweetener", "stevia", "sugar free"},
        {"SALTS_SPICES_SAUCES", "salt", "masala", "spice", "ketchup", "sauce", "mayonnaise", "mayo", "chutney", "soup",
            "soya chunks", "vinegar", "seasoning", "dressing", "paste"},
        {"BEVERAGES", "juice", "soft drink", "cola", "soda", "tea", "coffee", "energy drink", "water", "squash", "sharbat",
            "kombucha", "coconut water", "nectar", "drink"},
        {"PREPARED", "ready to eat", "ready meal", "instant meal", "pizza", "samosa", "momos", "paratha", "biryani", "curry",
            "khichdi", "frozen snack", "sandwich", "wrap"},
        // Last resort, when nothing more specific named the product: an edible oil by any name ("Gingelly Oil", "Rice Bran
        // oil", "Refined Soyabean Oil") and a nut or seed in the singular ("Almond", "Raw peanut", "Walnut kernels"), each
        // then scored on Nutri-Score's fats, oils, nuts and seeds table. Anything with a product word above ("Peanut Chikki",
        // "Peanut Cookies", "Tuna in Oil") has already been placed by it.
        {"FATS_OILS", "oil", "oils"},
        {"FRUIT_VEG_PULSES_NUTS", "almond", "cashew", "walnut", "pistachios", "peanut", "seed", "nut", "nuts", "mixed nuts",
            "trail mix", "kernels"},
    };

    static Placement place(String productName, boolean drink, String swapGroup) {
        String name = productName == null ? "" : productName.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}&]+", " ");
        boolean infant = Beverage.containsWord(name, new String[]{"infant", "baby food", "cerelac", "infant formula", "follow-up formula"});
        Fcs fromGroup = fromSwapGroup(swapGroup);
        boolean nuts = "nuts_seeds".equals(swapGroup);
        if (fromGroup != null) return new Placement(fromGroup, infant, nuts);
        for (String[] row : WORDS) {
            String[] words = new String[row.length - 1];
            System.arraycopy(row, 1, words, 0, words.length);
            if (Beverage.containsWord(name, words)) {
                Fcs fcs = Fcs.valueOf(row[0]);
                boolean nutsOrSeeds = fcs == Fcs.FRUIT_VEG_PULSES_NUTS && Beverage.containsWord(name,
                    NUTS_AND_SEEDS);
                return new Placement(fcs, infant, nutsOrSeeds);
            }
        }
        return new Placement(drink ? Fcs.BEVERAGES : null, infant, false);
    }

    /** Nuts and seeds, in every form a pack names them. */
    private static final String[] NUTS_AND_SEEDS = {"almonds", "almond", "cashews", "cashew", "walnuts", "walnut", "pistachio",
        "pistachios", "seeds", "seed", "peanuts", "peanut", "nuts", "mixed nuts", "dry fruits", "trail mix"};
    private static final String[] NUT_BUTTERS = {"peanut butter", "almond butter", "cashew butter", "nut butter"};
    private static final String[] CREAMS = {"fresh cream", "cooking cream", "whipping cream", "malai"};
    private static final String[] CHEESES = {"cheese", "paneer"};
    /** Red meat as Nutri-Score 2023 defines it: beef, veal, pork, lamb, goat and game. Chicken is not. */
    private static final String[] RED_MEAT = {"mutton", "lamb", "beef", "veal", "pork", "goat", "buffalo meat", "carabeef",
        "ham", "bacon", "venison"};

    /**
     * Which Nutri-Score 2023 table a product is scored with. Fats and oils include cream, margarines and
     * butters; nuts, processed nuts (peanut butter) and seeds join them; cheese keeps its protein; red
     * meat counts at most two protein points (evidence ns2023.fats.category, ns2023.red.meat.protein.max).
     */
    static NutriGrade.FoodKind kind(String productName, String swapGroup) {
        String name = productName == null ? "" : productName.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}&]+", " ");
        if ("nuts_seeds".equals(swapGroup) || Beverage.containsWord(name, NUT_BUTTERS) || Beverage.containsWord(name, CREAMS))
            return NutriGrade.FoodKind.FATS_OILS_NUTS;
        Placement p = place(productName, false, swapGroup);
        if (p.category == Fcs.FATS_OILS || p.nutsOrSeeds) return NutriGrade.FoodKind.FATS_OILS_NUTS;
        if (Beverage.containsWord(name, CHEESES)) return NutriGrade.FoodKind.CHEESE;
        if (Beverage.containsWord(name, RED_MEAT)) return NutriGrade.FoodKind.RED_MEAT;
        return NutriGrade.FoodKind.GENERAL;
    }

    /** "What do you buy regularly?" answers, each mapped to the FSSAI categories it covers. */
    static java.util.List<Fcs> forBuyOption(String code) {
        switch (code == null ? "" : code) {
            case "snacks": return java.util.Arrays.asList(Fcs.SAVOURIES, Fcs.FRUIT_VEG_PULSES_NUTS);
            case "biscuits": return java.util.Collections.singletonList(Fcs.BAKERY);
            case "drinks": return java.util.Collections.singletonList(Fcs.BEVERAGES);
            case "dairy": return java.util.Arrays.asList(Fcs.DAIRY, Fcs.EDIBLE_ICES);
            case "bread": return java.util.Arrays.asList(Fcs.BAKERY, Fcs.FRUIT_VEG_PULSES_NUTS);
            case "noodles": case "breakfast": return java.util.Collections.singletonList(Fcs.CEREALS);
            case "chocolate": return java.util.Collections.singletonList(Fcs.CONFECTIONERY);
            case "readymade": return java.util.Collections.singletonList(Fcs.PREPARED);
            case "oils": return java.util.Collections.singletonList(Fcs.FATS_OILS);
            case "masala": return java.util.Collections.singletonList(Fcs.SALTS_SPICES_SAUCES);
            case "health": return java.util.Collections.singletonList(Fcs.PARTICULAR_USES);
            default: return java.util.Collections.emptyList();
        }
    }

    /** The swap groups each sit in one FSSAI category. */
    static Fcs fromSwapGroup(String group) {
        if (group == null) return null;
        switch (group) {
            case "salty_snacks": return Fcs.SAVOURIES;
            case "nuts_seeds": case "spreads": case "jams": case "pickles": case "dried_fruit": return Fcs.FRUIT_VEG_PULSES_NUTS;
            case "biscuits": case "bread": case "cakes": return Fcs.BAKERY;
            case "chocolate": case "sweets_mithai": return Fcs.CONFECTIONERY;
            case "flavoured_milk": case "curd_yoghurt": case "cheese": case "paneer_tofu": return Fcs.DAIRY;
            case "ice_cream": return Fcs.EDIBLE_ICES;
            case "breakfast_cereals": case "instant_noodles": case "flours_grains": case "pasta_vermicelli": return Fcs.CEREALS;
            case "soft_drinks": case "fruit_drinks": return Fcs.BEVERAGES;
            case "ready_meals": case "frozen_snacks": return Fcs.PREPARED;
            case "oils_fats": return Fcs.FATS_OILS;
            case "ketchup_sauces": case "mayo_dips": case "soups": case "masalas": return Fcs.SALTS_SPICES_SAUCES;
            case "health_drinks": case "bars": return Fcs.PARTICULAR_USES;
            default: return null;
        }
    }

    /**
     * "What to look for", computed from NIN 2024 Table 15.1 for a solid or a liquid, plus what matters in
     * this category. Shown when there are no curated swaps, so the card is never blank.
     */
    static List<String> guidance(Placement placement, boolean liquid) {
        List<String> lines = new ArrayList<>();
        String kind = liquid ? "liquid" : "solid", per = liquid ? "100 ml" : "100 g";
        BigDecimal kcal = Evidence.d("nin.hfss." + kind + ".kcal"), salt = Evidence.d("nin.hfss." + kind + ".salt");
        BigDecimal sugar = Evidence.d("nin.hfss." + kind + ".added.sugar");
        BigDecimal factor = NutritionCalculator.saltFromSodium(BigDecimal.ONE).value;   // mg salt per mg sodium
        BigDecimal sodium = salt.divide(factor, NutritionCalculator.MC);
        String category = placement.category == null ? "this product" : placement.category.name.toLowerCase(Locale.ROOT);
        lines.add("What to look for in " + category + " (NIN 2024 thresholds per " + per + "): energy under " + plain(kcal)
            + " kcal" + (placement.nutsOrSeeds ? " (nuts and seeds are exempt)" : "") + ", salt under " + plain(salt) + " mg (sodium "
            + plain(sodium.setScale(0, java.math.RoundingMode.HALF_UP)) + " mg), added sugar under " + plain(sugar) + " g.");
        if (placement.category != null) lines.add("In " + category + ", compare " + placement.category.compare + " for the same portion.");
        if (placement.category == Fcs.FATS_OILS) lines.add("NIN 2024 counts a portion of oil or ghee as " + plain(Evidence.d("nin.portion.oil.g"))
            + " g (one teaspoon).");
        if (placement.infantFood) lines.add("Food for infants and young children is held to different standards; the adult band is only a rough guide.");
        return lines;
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }

    private FoodCategory() {}
}
