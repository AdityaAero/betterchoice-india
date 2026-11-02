package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Does a declared ingredient list fit the shopper's diet? Read from the ingredients, never the name.
 *
 * <p>Vegetarian follows FSSAI's definition (Labelling and Display Regulations 2(o), evidence
 * {@code fssai.nonveg.definition}): any part of an animal, bird, insect, fish or <b>egg</b> makes a
 * food non-vegetarian; milk, milk products, honey, beeswax, carnauba wax and shellac do not. Vegan
 * also excludes those. Jain and halal practice varies by household and certifier, so those two rule
 * sets are provisional and say so. Halal can be confirmed only by a halal mark on the pack: the app
 * flags what is plainly not halal and what has an undeclared source, never "halal" itself.
 */
final class DietRules {
    enum Diet {
        VEGETARIAN("Vegetarian"), EGGETARIAN("Eggetarian"), JAIN("Jain"), OMNIVORE("Non-vegetarian / both"),
        HALAL("Non-vegetarian (halal)"), VEGAN("Vegan"), NONE("");
        final String label;
        Diet(String label) { this.label = label; }

        static Diet of(String answer) {
            for (Diet d : values()) if (d != NONE && d.label.equals(answer)) return d;
            return NONE;
        }

        /** No meat, fish or animal-derived ingredient. */
        boolean vegetarianFamily() { return this == VEGETARIAN || this == EGGETARIAN || this == JAIN || this == VEGAN; }
        boolean provisional() { return this == JAIN || this == HALAL; }
    }

    /** What a list means for one diet: a veto (does not fit), or cautions (source not stated). */
    static final class Verdict {
        final List<String> vetoes = new ArrayList<>(), cautions = new ArrayList<>();
        boolean fits() { return vetoes.isEmpty(); }
    }

    /** Animal parts other than the FSSAI allergen groups (fish, crustacea and egg are read from Allergen). */
    static final String[] MEAT = {"chicken", "mutton", "lamb", "goat meat", "beef", "pork", "ham", "bacon", "meat",
        "gelatin", "gelatine", "lard", "tallow", "suet", "animal fat", "animal rennet", "bone broth", "bone marrow",
        "collagen", "squid", "octopus", "oyster", "mussel", "clam", "scallop", "cochineal", "carmine", "isinglass",
        "chicken extract", "meat extract", "fish sauce", "oyster sauce", "anchovy"};
    /** Extra for vegan: everything FSSAI still calls vegetarian but comes from an animal. */
    static final String[] VEGAN_EXTRA = {"honey", "beeswax", "bees wax", "shellac", "lanolin"};
    /** Additive codes that are animal-derived (carmine) or animal-sourced for vegans (beeswax, shellac). */
    static final String[] ANIMAL_CODES = {"120"};
    static final String[] VEGAN_CODES = {"901", "904"};
    /** Emulsifiers that may be animal- or plant-derived; the label rarely says which. */
    static final String[] SOURCE_NOT_STATED = {"471", "472e", "481", "482", "631", "627", "635"};
    /** Jain practice: no onion, garlic or root vegetables, no honey. Practice varies; provisional. */
    static final String[] JAIN_AVOID = {"onion", "garlic", "potato", "carrot", "radish", "beetroot", "beet root", "ginger",
        "turnip", "sweet potato", "yam", "honey", "mushroom"};
    /** Plainly not halal. Gelatin and rennet are not here: their source decides, so they are cautions. */
    static final String[] NOT_HALAL = {"pork", "ham", "bacon", "lard", "pepperoni", "wine", "rum", "beer", "brandy",
        "whisky", "liqueur", "alcohol", "ethanol"};
    static final String[] HALAL_SOURCE_NOT_STATED = {"gelatin", "gelatine", "rennet", "animal fat", "tallow", "carmine"};

    static Verdict check(Diet diet, String ingredients, String allergenText) {
        Verdict verdict = new Verdict();
        if (diet == null || diet == Diet.NONE || diet == Diet.OMNIVORE) return verdict;
        String text = lower(stripTraceStatements(ingredients));
        Allergen.Scan scan = Allergen.scan(ingredients, allergenText);
        if (diet.vegetarianFamily()) {
            for (Allergen animal : new Allergen[]{Allergen.FISH, Allergen.CRUSTACEA}) {
                if (scan.containsAsIngredient(animal)) verdict.vetoes.add(animal.label.toLowerCase(Locale.ROOT));
            }
            if (diet != Diet.EGGETARIAN && scan.containsAsIngredient(Allergen.EGG)) verdict.vetoes.add("egg");
            addWords(verdict.vetoes, text, MEAT);
            addCodes(verdict.vetoes, ingredients, ANIMAL_CODES, "carmine (INS 120)");
            addCodes(verdict.cautions, ingredients, SOURCE_NOT_STATED, "an emulsifier or flavour enhancer whose source isn't stated");
        }
        if (diet == Diet.VEGAN) {
            if (scan.containsAsIngredient(Allergen.MILK)) verdict.vetoes.add("milk");
            if (scan.containsAsIngredient(Allergen.EGG) && !verdict.vetoes.contains("egg")) verdict.vetoes.add("egg");
            addWords(verdict.vetoes, text, VEGAN_EXTRA);
            addCodes(verdict.vetoes, ingredients, VEGAN_CODES, "beeswax or shellac (INS 901/904)");
        }
        if (diet == Diet.JAIN) addWords(verdict.vetoes, text, JAIN_AVOID);
        if (diet == Diet.HALAL) {
            // Polyols are "sugar alcohols" on labels; they are not alcohol.
            addWords(verdict.vetoes, text.replaceAll("sugar\\s+alcohols?", " "), NOT_HALAL);
            addWords(verdict.cautions, text, HALAL_SOURCE_NOT_STATED);
            addCodes(verdict.cautions, ingredients, SOURCE_NOT_STATED, "an emulsifier whose animal or plant source isn't stated");
        }
        return verdict;
    }

    /** "May contain egg" is cross-contact, not an ingredient: it never makes a food non-vegetarian. */
    private static String stripTraceStatements(String ingredients) {
        return ingredients == null ? "" : ingredients.replaceAll("(?i)may\\s+contains?[^.\\n]*", " ");
    }

    private static void addWords(List<String> out, String text, String[] words) {
        for (String word : words) {
            if (Allergen.mentions(text, new String[]{word}, new String[]{}) && !out.contains(word)) out.add(word);
        }
    }

    private static void addCodes(List<String> out, String ingredients, String[] codes, String label) {
        if (ingredients == null) return;
        for (IngredientParser.Additive additive : IngredientParser.parse(ingredients).additives) {
            String code = IngredientParser.canonicalCode(additive.code);
            for (String wanted : codes) {
                if (code.equals(wanted) || code.replaceFirst("[a-z]+$", "").equals(wanted)) {
                    if (!out.contains(label)) out.add(label);
                    return;
                }
            }
        }
    }

    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }

    private DietRules() {}
}
