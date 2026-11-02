package com.betterchoice.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads an ordered declared ingredient list and the additive codes actually printed in it. */
final class IngredientParser {
    static final class Ingredient {
        /** One-based position in the label's descending-order list. */
        final int position;
        final String text;

        Ingredient(int position, String text) { this.position = position; this.text = text; }
    }

    static final class Additive {
        final String code;
        final String name;
        final String function;
        final int ingredientPosition;

        Additive(String code, String name, String function, int ingredientPosition) {
            this.code = code;
            this.name = name;
            this.function = function;
            this.ingredientPosition = ingredientPosition;
        }

        String display() {
            return name == null ? "INS " + printedCode(code) : "INS " + printedCode(code) + " " + name;
        }
    }

    static final class Result {
        final List<Ingredient> ingredients;
        final List<Additive> additives;

        Result(List<Ingredient> ingredients, List<Additive> additives) {
            this.ingredients = ingredients;
            this.additives = additives;
        }

        List<String> rawItems() {
            List<String> items = new ArrayList<>();
            for (Ingredient ingredient : ingredients) items.add(ingredient.text);
            return items;
        }
    }

    private static final String[] ADDITIVE_CLASSES = {
        "acidity regulator", "anticaking", "anti caking", "antioxidant", "colour", "color",
        "emulsifier", "emulsifying salt", "flavour enhancer", "flavor enhancer", "flour treatment", "gelling agent",
        "glazing agent", "humectant", "preservative", "raising agent", "stabiliser", "stabilizer",
        "sweetener", "thickener"
    };
    private static final Pattern EXPLICIT = Pattern.compile(
        "(?i)\\b(?:ins|e)\\s*[-:]?\\s*(\\d{3,4})\\s*(?:\\(\\s*([ivx]{1,4}|[a-z])\\s*\\)|([ivx]{1,4}|[a-z])\\b)?");
    private static final Pattern CONTEXT_CODE = Pattern.compile(
        "(?i)(?<![\\d.])(\\d{3,4})\\s*(?:\\(\\s*([ivx]{1,4}|[a-z])\\s*\\)|([ivx]{1,4}|[a-z])\\b)?(?![\\d.%])");

    static Result parse(String declaredList) {
        List<String> raw = IngredientFacts.parts(declaredList);
        List<Ingredient> ingredients = new ArrayList<>();
        Map<String, Additive> additives = new LinkedHashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            String item = raw.get(i);
            int position = i + 1;
            ingredients.add(new Ingredient(position, item));
            collect(EXPLICIT.matcher(item), position, additives);
            if (additiveContext(item)) collect(CONTEXT_CODE.matcher(item), position, additives);
        }
        return new Result(ingredients, new ArrayList<>(additives.values()));
    }

    private static void collect(Matcher matcher, int position, Map<String, Additive> found) {
        while (matcher.find()) {
            String code = canonicalCode(matcher.group(1) + suffix(matcher));
            int number;
            try { number = Integer.parseInt(code.replaceFirst("[a-z]+$", "")); }
            catch (NumberFormatException invalid) { continue; }
            if (number < 100 || number > 1521 || found.containsKey(code)) continue;
            InsAdditives.Entry entry = InsAdditives.find(code);
            found.put(code, new Additive(code, entry == null ? null : entry.name,
                entry == null ? null : entry.function, position));
        }
    }

    private static String suffix(Matcher matcher) {
        String bracketed = matcher.group(2);
        String plain = matcher.group(3);
        return bracketed != null ? bracketed : plain == null ? "" : plain;
    }

    private static boolean additiveContext(String item) {
        String lower = item.toLowerCase(Locale.ROOT);
        for (String marker : ADDITIVE_CLASSES) if (lower.contains(marker)) return true;
        return false;
    }

    static String canonicalCode(String code) {
        if (code == null) return "";
        return code.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z]", "");
    }

    static String printedCode(String code) {
        Matcher matcher = Pattern.compile("^(\\d+)([a-z]+)?$").matcher(code);
        if (!matcher.matches() || matcher.group(2) == null) return code;
        String suffix = matcher.group(2);
        return suffix.matches("i{1,3}|iv")
            ? matcher.group(1) + "(" + suffix + ")"
            : matcher.group(1) + suffix;
    }

    private IngredientParser() {}
}
