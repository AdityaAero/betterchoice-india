package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decides whether a food is safe to put in front of this particular shopper, separately from
 * whether it is nutritionally good.
 *
 * <p><b>This verdict is never averaged into a score.</b> That is the whole point of the class. A
 * chocolate bar that rates well and declares peanuts must not come out as a middling number, because
 * a middling number reads as "a bit of a compromise" when the truth is "this could put you in
 * hospital". Quality and safety are answers to different questions and are returned separately.
 *
 * <p>The order below is fixed, and deliberately not configurable. Ranking experiments, personal
 * goals and commercial pressure all operate <i>below</i> it:
 * <pre>
 *   allergens  →  diet  →  nutrition quality  →  goal  →  taste  →  price  →  availability
 * </pre>
 *
 * <p>This is general-wellness support, not clinical software. It has no model of diabetes, kidney
 * disease or pregnancy, and it must never be given one without clinical review: an app that tells
 * someone a food is fine for their condition has stopped being a shopping aid.
 */
final class SafetyGate {
    enum Verdict {
        /** Do not recommend, and say why. */
        BLOCK,
        /** Recommendable, but the shopper should see the reason first. */
        CAUTION,
        /** Worth stating on the card; changes nothing about the recommendation. */
        INFORM,
        /** Nothing to say. Note this is not a claim that the food is safe. */
        NOTHING_TO_FLAG
    }

    static final class Result {
        final Verdict verdict;
        /** Short machine-readable reasons, e.g. "allergen_milk", "no_allergen_information". */
        final List<String> codes;
        /** What to put on the card, in the shopper's words. */
        final List<String> messages;
        final Allergen.Scan scan;

        Result(Verdict verdict, List<String> codes, List<String> messages, Allergen.Scan scan) {
            this.verdict = verdict;
            this.codes = Collections.unmodifiableList(codes);
            this.messages = Collections.unmodifiableList(messages);
            this.scan = scan;
        }

        boolean blocked() { return verdict == Verdict.BLOCK; }
    }

    /**
     * @param ingredients   the ingredient statement, or null
     * @param allergenText  a separate "Contains / May contain" declaration, or null
     */
    static Result check(String ingredients, String allergenText, SafetyProfile profile) {
        Allergen.Scan scan = Allergen.scan(ingredients, allergenText);
        SafetyProfile who = profile == null ? SafetyProfile.unknown() : profile;
        List<String> codes = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        Verdict verdict = Verdict.NOTHING_TO_FLAG;

        // 1. Allergens, above everything else. A wheat allergy is wheat only; barley, rye and oats are
        // a coeliac concern (the separate gluten choice), so it is read from the wheat scan.
        if (who.wheatAllergy() && !who.allergens().contains(Allergen.GLUTEN)) {
            if (scan.wheatIngredient) {
                codes.add("allergen_wheat");
                messages.add("Contains wheat, which you asked us to avoid.");
                verdict = Verdict.BLOCK;
            } else if (scan.wheatTrace) {
                codes.add("allergen_traces_wheat");
                messages.add("May contain traces of wheat.");
                if (who.tracesAreDangerous()) verdict = Verdict.BLOCK;
                else if (verdict != Verdict.BLOCK) verdict = Verdict.CAUTION;
            } else if (scan.noInformation) {
                codes.add("no_allergen_information");
                messages.add("This label doesn't list its ingredients or allergens, so we can't check it against your allergies.");
                verdict = Verdict.BLOCK;
            }
        }
        if (who.hasAllergies()) {
            if (scan.noInformation) {
                // Refusing to answer is the safe answer. "No recommendation" is a success here.
                codes.add("no_allergen_information");
                messages.add("This label doesn't list its ingredients or allergens, so we can't check it against your allergies.");
                verdict = Verdict.BLOCK;
            }
            for (Allergen allergen : who.allergens()) {
                if (scan.containsAsIngredient(allergen)) {
                    codes.add("allergen_" + allergen.code);
                    messages.add("Contains " + allergen.label.toLowerCase(java.util.Locale.ROOT) + ", which you asked us to avoid.");
                    verdict = Verdict.BLOCK;
                } else if (scan.traces.contains(allergen)) {
                    if (who.tracesAreDangerous()) {
                        // A trace is treated as the real thing unless told it is only uncomfortable.
                        codes.add("allergen_traces_" + allergen.code);
                        messages.add("May contain traces of " + allergen.label.toLowerCase(java.util.Locale.ROOT) + ".");
                        verdict = Verdict.BLOCK;
                    } else if (verdict != Verdict.BLOCK) {
                        codes.add("allergen_traces_" + allergen.code);
                        messages.add("May contain traces of " + allergen.label.toLowerCase(java.util.Locale.ROOT) + ".");
                        verdict = Verdict.CAUTION;
                    }
                }
            }
        }

        // 2. Diet, which is a constraint the shopper set, not a preference to trade off. FSSAI's
        // definition: egg and any animal part are non-vegetarian; milk, honey and shellac are not.
        DietRules.Diet diet = DietRules.Diet.of(who.diet);
        DietRules.Verdict fit = DietRules.check(diet, ingredients, allergenText);
        for (String reason : fit.vetoes) {
            codes.add("diet_" + reason.replaceAll("[^a-z0-9]+", "_"));
            messages.add("Contains " + reason + ", so it doesn't match your " + diet.label.toLowerCase(java.util.Locale.ROOT) + " diet.");
            verdict = Verdict.BLOCK;
        }
        for (String reason : fit.cautions) {
            codes.add("diet_source_" + reason.replaceAll("[^a-z0-9]+", "_"));
            messages.add("Contains " + reason + (diet == DietRules.Diet.HALAL
                ? "; only a halal mark on the pack can confirm it." : "; the pack's veg mark is the manufacturer's word on it."));
            if (verdict == Verdict.NOTHING_TO_FLAG || verdict == Verdict.INFORM) verdict = Verdict.CAUTION;
        }

        // 2b. Lactose intolerance is not a milk allergy: it is uncomfortable, not dangerous, and how
        // much someone tolerates varies. So it warns, and never blocks the way an allergy does.
        if (who.has("lactose") && scan.containsAsIngredient(Allergen.MILK) && !who.allergens().contains(Allergen.MILK)) {
            codes.add("lactose_milk");
            messages.add("Contains milk. How much lactose you can handle is your call.");
            if (verdict == Verdict.NOTHING_TO_FLAG || verdict == Verdict.INFORM) verdict = Verdict.CAUTION;
        }

        // 2c. Conditions this app does not model. It says so plainly rather than staying silent,
        // because silence next to a rating reads as approval.
        for (String condition : new String[]{"diabetes", "hypertension", "kidney", "pregnancy", "breastfeeding", "cholesterol"}) {
            if (!who.has(condition)) continue;
            codes.add("condition_" + condition);
            messages.add("BetterChoice can read the label, but it won't judge whether this suits your "
                + label(condition) + ". Ask your doctor or dietitian.");
            if (verdict == Verdict.NOTHING_TO_FLAG) verdict = Verdict.INFORM;
        }

        // 3. What the label says. Stated for everyone and always, never turned into a safety claim.
        // It used to appear only when nothing else had been flagged, so a lactose warning hid the
        // fact that the same chocolate also declares soy and may contain nuts.
        if (!scan.all().isEmpty()) {
            if (verdict == Verdict.NOTHING_TO_FLAG) verdict = Verdict.INFORM;
            if (!scan.declared.isEmpty() || !scan.fromIngredients.isEmpty()) {
                messages.add("Contains: " + names(scan.declared, scan.fromIngredients));
            }
            if (!scan.traces.isEmpty()) messages.add("May contain: " + names(scan.traces, Collections.emptyList()));
            codes.add("allergens_listed");
        }

        // Never let silence read as a clean bill of health.
        if (!who.hasAllergies()) {
            switch (who.allergyState) {
                case NONE_KNOWN:
                    messages.add("You told us you know of no allergies. We can't confirm a food is safe for you.");
                    codes.add("no_known_allergies");
                    break;
                case UNSURE:
                case NOT_ANSWERED:
                    if (!scan.all().isEmpty()) {
                        messages.add("You haven't told BetterChoice about any allergies.");
                        codes.add("allergies_not_recorded");
                    }
                    break;
                default: break;
            }
            if (verdict == Verdict.NOTHING_TO_FLAG && !messages.isEmpty()) verdict = Verdict.INFORM;
        }

        return new Result(verdict, codes, messages, scan);
    }

    private static String label(String condition) {
        switch (condition) {
            case "diabetes": return "diabetes";
            case "hypertension": return "blood pressure";
            case "kidney": return "kidney condition";
            case "pregnancy": return "pregnancy";
            case "breastfeeding": return "breastfeeding";
            case "cholesterol": return "cholesterol or heart condition";
            default: return "condition";
        }
    }

    private static String names(List<Allergen> first, List<Allergen> second) {
        List<String> labels = new ArrayList<>();
        for (Allergen a : first) if (!labels.contains(a.label)) labels.add(a.label);
        for (Allergen a : second) if (!labels.contains(a.label)) labels.add(a.label);
        return String.join(", ", labels);
    }

    private SafetyGate() {}
}
