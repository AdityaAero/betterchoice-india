package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the card says about a product for this shopper, in as few words as the facts allow: what counts against it,
 * in dark red, with the shopper's own reasons first ("High sodium · 1300 mg · your BP"), and what is good about it, in
 * dark green ("High protein · 52 g"). The nutrient lines are the rating's own (Nutri-Score's "high" levels, FSSAI's
 * claim levels); ingredient evidence is kept apart for its own menu. Nothing here changes the colour.
 *
 * <p>A shopper's condition makes a nutrient count from the level a food stops being "low" in it (FSSAI's claim levels,
 * the same {@link Highlights} uses): sodium for high blood pressure or the kidneys, sugar for diabetes or a sugar goal,
 * saturated fat for the heart. For everyone else only the general "high" levels are flagged, and the good is said too.
 */
final class CardFacts {
    /** At most this many lines of each kind on the card's face; the rest wait behind its details icon. */
    static final int MAX_EACH = 3;

    static final class Line {
        final String text;
        /** True when the line is there, or first, because of this shopper's own condition or goal. */
        final boolean personal;
        Line(String text, boolean personal) { this.text = text; this.personal = personal; }
        @Override public String toString() { return text; }
    }

    final List<Line> bad = new ArrayList<>(), good = new ArrayList<>();
    /** Lines beyond {@link #MAX_EACH}, for the details panel. */
    final List<Line> moreBad = new ArrayList<>(), moreGood = new ArrayList<>();
    /** What the declared ingredient list says: for the ingredients menu, never the face of the card. */
    final List<String> ingredientGood = new ArrayList<>(), ingredientConcerns = new ArrayList<>();

    static CardFacts of(GradeResult result, RecommendationContext person) {
        CardFacts out = new CardFacts();
        if (result == null) return out;
        IngredientFacts facts = result.ingredients;
        List<String> fromIngredients = new ArrayList<>();
        if (facts != null) { fromIngredients.addAll(facts.concerns); fromIngredients.addAll(facts.good); }
        boolean drink = result.drinkKind != null;
        Nutrition n = result.nutrition;

        List<Line> bad = new ArrayList<>();
        for (String concern : result.concerns) {
            if (fromIngredients.contains(concern)) { out.ingredientConcerns.add(concern); continue; }
            String tag = tag(nutrientOf(concern), person, true);
            bad.add(new Line(compact(concern) + (tag == null ? "" : " · " + tag), tag != null));
        }
        // A condition makes a nutrient count from where a food stops being "low" in it, not only from "high".
        if (n != null && person != null) {
            if ((person.hypertension || person.kidney) && n.sodiumMg != null && n.sodiumMg > Highlights.LOW_SODIUM_MG
                    && !mentions(bad, "sodium")) {
                bad.add(new Line("Sodium · " + Nutrition.number(Math.round(n.sodiumMg)) + " mg, not low · "
                    + tag("sodium", person, true), true));
            }
            double lowSugar = drink ? Highlights.LOW_SUGAR_DRINK_G : Highlights.LOW_SUGAR_FOOD_G;
            if ((person.diabetes || person.goals.contains(RecommendationContext.Goal.SUGAR)) && n.sugarsG != null
                    && n.sugarsG > lowSugar && !mentions(bad, "sugar")) {
                bad.add(new Line("Sugar · " + Nutrition.number(n.sugarsG) + " g, not low · " + tag("sugar", person, true), true));
            }
            double lowSatFat = drink ? Highlights.LOW_SAT_FAT_DRINK_G : Highlights.LOW_SAT_FAT_FOOD_G;
            if (person.cholesterol && n.saturatedFatG != null && n.saturatedFatG > lowSatFat && !mentions(bad, "saturated")) {
                bad.add(new Line("Saturated fat · " + Nutrition.number(n.saturatedFatG) + " g, not low · "
                    + tag("saturated", person, true), true));
            }
        }
        List<Line> good = new ArrayList<>();
        for (String positive : result.positives) {
            if (fromIngredients.contains(positive)) { out.ingredientGood.add(positive); continue; }
            String tag = tag(nutrientOf(positive), person, false);
            good.add(new Line(compact(positive) + (tag == null ? "" : " · " + tag), tag != null));
        }
        // The shopper's own reasons first, each list otherwise in the rating's order.
        split(personalFirst(bad), out.bad, out.moreBad);
        split(personalFirst(good), out.good, out.moreGood);
        return out;
    }

    /** "High protein: 52 g per 100 g" -> "High protein · 52 g"; the basis is said once, under the lines. */
    static String compact(String line) {
        String s = line.replaceAll("\\s+per\\s+100\\s*(?:g|ml)\\b", "")
            .replace(" — the kind to choose", "").replace(" (best kept near zero)", "")
            .replace(", as the label declares", "").replace(", which counts in its favour", "")
            .replace(" of its ", " of ");
        int colon = s.indexOf(": ");
        if (colon > 0) s = s.substring(0, colon) + " · " + s.substring(colon + 2);
        return s.trim();
    }

    /** Which nutrient a rating line is about, by its words; null for anything else. */
    static String nutrientOf(String line) {
        String s = line.toLowerCase(Locale.ROOT);
        if (s.contains("sodium") || s.contains("salt")) return "sodium";
        if (s.contains("saturated") || s.contains("trans fat")) return "saturated";
        if (s.contains("sugar")) return "sugar";
        if (s.contains("protein")) return "protein";
        if (s.contains("fibre") || s.contains("fiber")) return "fibre";
        if (s.contains("energy") || s.contains("calorie")) return "energy";
        return null;
    }

    /**
     * The shopper's own reason for a nutrient, said in two or three words, or null when it is not theirs. For a concern
     * ({@code bad}): their condition or goal makes it matter. For a good line: their condition or goal is helped by it.
     */
    static String tag(String nutrient, RecommendationContext person, boolean bad) {
        if (nutrient == null || person == null) return null;
        switch (nutrient) {
            case "sodium":
                if (person.hypertension) return "your BP";
                if (person.kidney) return "your kidneys";
                return null;
            case "sugar":
                if (person.diabetes) return "your diabetes";
                if (person.goals.contains(RecommendationContext.Goal.SUGAR)) return "your sugar goal";
                return null;
            case "saturated":
                return person.cholesterol ? "your heart" : null;
            case "energy":
                if (person.goals.contains(RecommendationContext.Goal.LOSS)) return "your weight goal";
                return null;
            case "protein":
                // Kidney care keeps protein moderate (not on dialysis): never praised to that shopper as "for you".
                if (bad || (person.kidney && !person.dialysis)) return null;
                return person.goals.contains(RecommendationContext.Goal.MUSCLE) ? "your muscle goal" : null;
            case "fibre":
                if (bad) return null;
                if (person.diabetes) return "your diabetes";
                if (person.goals.contains(RecommendationContext.Goal.LOSS)) return "your weight goal";
                return null;
            default:
                return null;
        }
    }

    private static boolean mentions(List<Line> lines, String word) {
        for (Line line : lines) if (line.text.toLowerCase(Locale.ROOT).contains(word)) return true;
        return false;
    }

    private static List<Line> personalFirst(List<Line> lines) {
        List<Line> out = new ArrayList<>();
        for (Line line : lines) if (line.personal) out.add(line);
        for (Line line : lines) if (!line.personal) out.add(line);
        return out;
    }

    private static void split(List<Line> lines, List<Line> face, List<Line> more) {
        for (Line line : lines) (face.size() < MAX_EACH ? face : more).add(line);
    }

    private CardFacts() {}
}
