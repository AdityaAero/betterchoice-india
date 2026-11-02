package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks three better choices from the same category as the product on screen, from the curated catalogue, for this
 * shopper. One rule for every category and every shop; no rule names a product or a category.
 *
 * <p>A candidate is offered only when every one of these holds. Safety and the shopper's own limits are never
 * relaxed to fill three slots:
 * <ol>
 *   <li><b>Same category</b> (substitution group), placed by the same rule as the product on screen, and the same
 *       side of food/drink.</li>
 *   <li><b>Never a worse score</b>: its official Nutri-Score (2023) letter, graded by the app at runtime with the same
 *       table, is not worse than this product's at its most favourable reading (the curator's draft grade is never
 *       trusted on its own).</li>
 *   <li><b>A real difference</b> of at least 30%, the difference FSSAI requires of a comparative claim (Advertising and
 *       Claims Regulations, regulation 5(4)(a)): less of a nutrient this category is judged on and this product scores
 *       against (sugar for fizzy drinks, sodium for salty snacks…), or 30% more fibre and enough to be a "source of
 *       fibre" by Schedule I. With the same letter, the difference must be on this product's weakest point. For a
 *       shopper with a goal, the goal's own measure 30% better; failing that, a better letter with a real difference
 *       and the goal not made worse, said as such on the card.</li>
 *   <li><b>Nothing worse</b>: not higher in anything the category is judged on (label rounding allowed), and not
 *       "high" in anything this product is not. Swapping salty chips for a sugary bar is not an improvement.</li>
 *   <li><b>Safe for this shopper</b>, checked on the phone: allergies, diet (with positive evidence), pregnancy,
 *       breastfeeding, kidney and PKU rules, and every limit their conditions and goals set.</li>
 *   <li><b>Trusted data</b>: nutritionist-verified, or — in test builds only — a labelled draft.</li>
 * </ol>
 * Green comes first, then yellow, then red that is still a real improvement, each with its colour and letter said.
 * Three are picked from three brands where the category allows, else the next best of a brand already shown; fewer
 * than three only when no more products in the category pass, and the card says so.
 *
 * <p>Price and stock are not decided here. They belong to a shop, an address and a moment, and are checked on the
 * shop's own screen when the shopper taps a swap ({@link SwapCheck}).
 */
final class SwapEngine {
    static final int MAX_SWAPS = 3;

    static final class Swap {
        final SwapCatalogue.Product product;
        final NutriGrade.Band band;
        /** Its official five-letter Nutri-Score (2023), as the app graded it; '?' when not graded here. */
        final char letter;
        final String why;
        final double rank;
        /** Its colour and score beside this product's, in words: "Yellow, Nutri-Score C: better than this product, not yet green". */
        final String standing;
        /** False only when it is better overall but does not meet the shopper's goal; then {@link #goalNote} says so. */
        final boolean meetsGoal;
        /** Why it is offered to someone whose goal it does not meet; null otherwise. */
        final String goalNote;
        /** What its declared ingredient list says, good and not. Empty when no list is known. */
        final IngredientFacts ingredients;
        /** Every check it passed for this shopper, with its numbers: shown under "Why it suits you". */
        final List<String> checks;
        Swap(SwapCatalogue.Product product, NutriGrade.Band band, String why, double rank) {
            this(product, band, '?', why, rank, null, true, null, null, new ArrayList<>());
        }
        Swap(SwapCatalogue.Product product, NutriGrade.Band band, char letter, String why, double rank, String standing,
             boolean meetsGoal, String goalNote, IngredientFacts ingredients, List<String> checks) {
            this.product = product; this.band = band; this.letter = letter; this.why = why; this.rank = rank;
            this.standing = standing; this.meetsGoal = meetsGoal; this.goalNote = goalNote;
            this.ingredients = ingredients != null ? ingredients : IngredientFacts.of(null, false);
            this.checks = checks;
        }
        boolean draft() { return !product.verified(); }
        boolean green() { return band == NutriGrade.Band.A; }
    }

    static final class Outcome {
        final SwapCatalogue.Group group;
        final List<Swap> swaps;
        /** Why the list is empty, in the shopper's terms. Null when there are swaps. */
        final String emptyReason;
        /** Candidates passed over only because of this shopper's allergies or diet. */
        final int hiddenForSafety;
        /** Every better candidate left out, by reason: "your allergies", "pregnancy", "more carbohydrate"... */
        final Map<String, Integer> leftOut;
        /** Set when fewer than three could be offered: how many, and what was left out. Null otherwise. */
        final String shortfall;
        Outcome(SwapCatalogue.Group group, List<Swap> swaps, String emptyReason, int hiddenForSafety) {
            this(group, swaps, emptyReason, hiddenForSafety, new LinkedHashMap<>(), null);
        }
        Outcome(SwapCatalogue.Group group, List<Swap> swaps, String emptyReason, int hiddenForSafety,
                Map<String, Integer> leftOut, String shortfall) {
            this.group = group; this.swaps = swaps; this.emptyReason = emptyReason; this.hiddenForSafety = hiddenForSafety;
            this.leftOut = leftOut; this.shortfall = shortfall;
        }
        static Outcome none(SwapCatalogue.Group group, String reason) { return new Outcome(group, new ArrayList<>(), reason, 0); }
        boolean hasDrafts() { for (Swap s : swaps) if (s.draft()) return true; return false; }
        boolean allGreen() { for (Swap s : swaps) if (!s.green()) return false; return !swaps.isEmpty(); }
    }

    /**
     * @param productName  the name the shop shows
     * @param current      the values the rating was built on
     * @param currentBand  its band
     * @param drink        whether it was scored as a drink
     * @param safety       the shopper's allergies and diet, read on the phone
     * @param occasion     what they are buying for; orders the list, never filters it
     * @param allowDrafts  true only in test builds
     */
    static Outcome recommend(SwapCatalogue catalogue, String productName, Nutrition current, NutriGrade.Band currentBand,
                             boolean drink, SafetyProfile safety, Occasion occasion, boolean allowDrafts) {
        return recommend(catalogue, productName, current, currentBand, '?', drink, safety, occasion, allowDrafts);
    }

    /** @param currentLetter the official Nutri-Score letter shown for it, or '?' to grade it here */
    static Outcome recommend(SwapCatalogue catalogue, String productName, Nutrition current, NutriGrade.Band currentBand,
                             char currentLetter, boolean drink, SafetyProfile safety, Occasion occasion,
                             boolean allowDrafts) {
        return recommend(catalogue, productName, current, currentBand, currentLetter, drink, safety, occasion, allowDrafts, null);
    }

    static Outcome recommend(SwapCatalogue catalogue, String productName, Nutrition current, NutriGrade.Band currentBand,
                             char currentLetter, boolean drink, SafetyProfile safety, Occasion occasion,
                             boolean allowDrafts, Profile profile) {
        return recommend(catalogue, productName, null, current, currentBand, currentLetter, drink, safety, occasion, allowDrafts, profile);
    }

    /** @param productType what the shop's page says the product is ("Product Type: Soya Chunks"), or null */
    static Outcome recommend(SwapCatalogue catalogue, String productName, String productType, Nutrition current,
                             NutriGrade.Band currentBand, char currentLetter, boolean drink, SafetyProfile safety,
                             Occasion occasion, boolean allowDrafts, Profile profile) {
        if (catalogue == null || current == null || currentBand == null) return Outcome.none(null, null);
        RecommendationContext context = RecommendationContext.of(profile);
        SwapCatalogue.Group group = catalogue.placeFor(productName, productType, drink);
        if (group == null) return Outcome.none(null, "We haven't curated swaps for this kind of product yet.");
        if (!GoalComparisons.allowsGroup(profile, group.id)) return Outcome.none(group, "This food category is outside your selected preferences. Edit your form to include it.");
        if (currentBand == NutriGrade.Band.A && context.objective == RecommendationContext.Objective.AWARENESS)
            return Outcome.none(group, "Already one of the better choices here.");
        SafetyProfile who = safety == null ? SafetyProfile.unknown() : safety;
        char letter = letterOf(current, currentLetter, productName, drink, group);
        Pass pass = evaluate(catalogue, group, productName, current, letter, drink, who, occasion, allowDrafts, context);
        List<Swap> chosen = pick(pass.ranked);
        String name = group.name.toLowerCase(Locale.ROOT);
        String reason = null, shortfall = null;
        if (chosen.isEmpty()) {
            reason = pass.leftOut.isEmpty()
                ? "We know of no " + name + " that is better than this one" + (context.objective == RecommendationContext.Objective.AWARENESS ? "." : " for you.")
                : "None of the better " + name + " we know of suits you. " + leftOutLine(pass.leftOut);
        } else if (chosen.size() < MAX_SWAPS) {
            shortfall = "Only " + chosen.size() + " better " + name + " we know of " + (chosen.size() == 1 ? "suits" : "suit") + " you."
                + (pass.leftOut.isEmpty() ? "" : " " + leftOutLine(pass.leftOut));
        }
        return new Outcome(group, chosen, reason, pass.hiddenForSafety(), pass.leftOut, shortfall);
    }

    /**
     * Every candidate in a group that qualifies for this shopper against one product, best first, before the pick of
     * three. Used to measure how deep the catalogue is for each profile; the card always goes through {@link #recommend}.
     */
    static List<Swap> qualifying(SwapCatalogue catalogue, SwapCatalogue.Group group, String productName, Nutrition current,
                                 NutriGrade.Band currentBand, char currentLetter, boolean drink, Profile profile) {
        if (catalogue == null || group == null || current == null || currentBand == null) return new ArrayList<>();
        RecommendationContext context = RecommendationContext.of(profile);
        SafetyProfile who = profile == null ? SafetyProfile.unknown() : SafetyProfile.fromProfile(profile);
        Pass pass = evaluate(catalogue, group, productName, current, letterOf(current, currentLetter, productName, drink, group),
            drink, who, null, true, context);
        pass.ranked.sort(ORDER);
        return pass.ranked;
    }

    /** "Left out: 2 for your allergies, 1 for more carbohydrate." */
    static String leftOutLine(Map<String, Integer> leftOut) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : leftOut.entrySet()) parts.add(e.getValue() + " for " + e.getKey());
        return parts.isEmpty() ? "" : "Left out: " + String.join(", ", parts) + ".";
    }

    /** What meets the shopper's goal first, then green, yellow, red; within each, the bigger improvement. */
    private static final Comparator<Swap> ORDER = (a, b) -> a.meetsGoal != b.meetsGoal ? (a.meetsGoal ? -1 : 1)
        : a.band != b.band ? a.band.compareTo(b.band) : Double.compare(b.rank, a.rank);

    /**
     * Three distinct choices: one per brand first, so three swaps are three choices; when fewer brands qualify, the
     * next best of a brand already shown. The same product in another pack size is never shown twice.
     */
    private static List<Swap> pick(List<Swap> ranked) {
        ranked.sort(ORDER);
        List<Swap> chosen = new ArrayList<>();
        Set<String> brands = new HashSet<>(), names = new HashSet<>();
        for (Swap swap : ranked) {
            if (chosen.size() == MAX_SWAPS) break;
            if (brands.contains(brandKey(swap.product.brand)) || names.contains(nameKey(swap.product))) continue;
            brands.add(brandKey(swap.product.brand));
            names.add(nameKey(swap.product));
            chosen.add(swap);
        }
        for (Swap swap : ranked) {
            if (chosen.size() == MAX_SWAPS) break;
            if (chosen.contains(swap) || !names.add(nameKey(swap.product))) continue;
            chosen.add(swap);
        }
        chosen.sort(ORDER);
        return chosen;
    }

    /** One evaluation of the group for this shopper. */
    private static final class Pass {
        final List<Swap> ranked = new ArrayList<>();
        final Map<String, Integer> leftOut = new LinkedHashMap<>();
        int hiddenForSafety() {
            int n = 0;
            for (Map.Entry<String, Integer> e : leftOut.entrySet()) if (e.getKey().startsWith("your ")) n += e.getValue();
            return n;
        }
        void leave(String why) { leftOut.merge(why, 1, Integer::sum); }
    }

    /**
     * This product's letter at its most favourable reading, graded with the same table as the candidates, so a swap is
     * never called better than this product could be: a missing figure is taken at its least, and of the letter shown
     * and the one graded here, the better one is used.
     */
    static char letterOf(Nutrition current, char shown, String productName, boolean drink, SwapCatalogue.Group group) {
        Nutrition n = current.missingNegatives().isEmpty() ? current : NutriGrade.bestCase(current);
        char here = NutriGrade.grade(n, Beverage.classify(productName, null, drink),
            FoodCategory.kind(productName, group == null ? null : group.id)).letter();
        return shown >= 'A' && shown <= 'E' && shown < here ? shown : here;
    }

    private static Pass evaluate(SwapCatalogue catalogue, SwapCatalogue.Group group, String productName, Nutrition current,
                                 char currentLetter, boolean drink, SafetyProfile who, Occasion occasion,
                                 boolean allowDrafts, RecommendationContext context) {
        Pass pass = new Pass();
        RecommendationContext.Objective objective = context.objective;
        String focus = focusOf(objective);
        boolean gaining = objective == RecommendationContext.Objective.HIGHER_ENERGY;
        double threshold = NutritionCalculator.comparativeMin().doubleValue();
        Set<String> currentHigh = NutriGrade.highFlags(current, drink);
        String worst = worstFocus(group, current, drink);
        for (SwapCatalogue.Product candidate : catalogue.inGroup(group.id)) {
            if (!candidate.verified() && !allowDrafts) continue;
            if (candidate.beverage != drink && !"ice_cream".equals(group.id)) continue;
            if (sameProduct(candidate, productName)) continue;
            if (!sameCategory(catalogue, group, candidate)) continue;
            Nutrition n = candidate.per100;
            if (!n.basisStated || !n.missingNegatives().isEmpty()) continue;
            if (n.beverage != current.beverage) continue; // No g/ml equivalence without density.

            // Is it better at all? Graded by the same engine and table as this product.
            Beverage identity = Beverage.classify(candidate.displayName(), candidate.ingredients, n.beverage);
            if (candidate.sweetener) identity = identity.withSweetener();
            NutriGrade.Result grade = NutriGrade.grade(n, identity, FoodCategory.kind(candidate.displayName(), group.id));
            char letter = grade.letter();
            if (letter > currentLetter) continue;                         // never a worse score
            if (worseOnFocus(group, current, n, gaining)) continue;      // nothing the category is judged on gets worse
            Set<String> high = NutriGrade.highFlags(n, grade.scoredAsDrink());
            high.removeAll(currentHigh);
            if (gaining) high.remove("energy");
            Set<String> tradeOff = new HashSet<>(high);
            tradeOff.retainAll(group.allowNewHigh);
            high.removeAll(group.allowNewHigh);
            if (!high.isEmpty()) continue;                               // nothing new turns "high"
            List<String> differences = realDifferences(group, current, n);
            double gain = focus == null ? Double.NaN : GoalComparisons.improvement(focus, current, n);
            boolean meetsGoal;
            if (focus == null) {
                // A real difference, or it is not better; with the same letter, on this product's weakest point.
                if (differences.isEmpty() || !(letter < currentLetter || differences.contains(worst))) continue;
                meetsGoal = true;
            } else if (Double.isFinite(gain) && gain >= threshold) {
                meetsGoal = true;
            } else {
                // Better overall for someone with a goal: a better letter and a real difference, the goal no worse.
                if (letter >= currentLetter || differences.isEmpty() || goalWorse(objective, current, n)) continue;
                meetsGoal = false;
            }

            // Only now the shopper, so what is left out for them counts only what would otherwise have been offered.
            String veto = vetoFor(candidate, who, context);                                  // stage 1
            if (veto != null) { pass.leave(veto); continue; }
            String broken = brokenConstraint(context, current, n);                            // stage 4
            if (broken != null) { pass.leave(broken); continue; }

            IngredientFacts facts = IngredientFacts.of(candidate.ingredients, drink);
            double rank = (currentLetter - letter) * 30
                + Math.max(-10, Math.min(20, improvementShare(worst, current, n) * 20))
                + 4 * differences.size()
                + (candidate.verified() ? 15 : 0)
                + (occasion != null && group.occasions.contains(occasion) ? 5 : 0)
                + packCloseness(current.netQuantity, candidate.pack);
            String why = why(group, current, n, drink);
            if (meetsGoal && focus != null) {
                rank += Math.min(100, gain * 100);
                why = GoalComparisons.benefit(focus, current, n);
            }
            // Tie-breaks from the shopper's conditions, each said next to the benefit.
            for (RecommendationContext.TieBreak tie : context.tieBreaks) {
                switch (tie) {
                    case SODIUM_LOWER: {
                        java.math.BigDecimal cut = NutritionCalculator.reduction(current.sodiumMg, n.sodiumMg);
                        if (cut != null) {
                            rank += Math.max(-20, Math.min(20, cut.doubleValue() * 40));
                            if (cut.doubleValue() >= 0.1 && !why.contains("less sodium")) why += " · " + Math.round(cut.doubleValue() * 100) + "% less sodium";
                        }
                        break;
                    }
                    case FIBRE_HIGHER:
                        if (n.fibreG != null && current.fibreG != null && n.fibreG > current.fibreG) { rank += 5; if (!why.contains("fibre")) why += " · more fibre"; }
                        break;
                    case PROTEIN_HIGHER:
                        if (n.proteinG != null && current.proteinG != null && n.proteinG > current.proteinG) rank += 5;
                        break;
                    case NO_MILK:
                        if (candidate.ingredients != null && !candidate.ingredients.isBlank()
                                && !Allergen.scan(candidate.ingredients, null).containsAsIngredient(Allergen.MILK)) { rank += 8; why += " · no milk"; }
                        break;
                    default: break;
                }
            }
            // A trade-off the nutritionist allows is never hidden: it is said next to the benefit.
            for (String nutrient : tradeOff) why += " · higher in " + label(nutrient);
            // A list entered without its sweeteners cannot overrule a drink known to be sweetened.
            if (candidate.sweetener && facts.read && !facts.ultraMarkers.contains("sweeteners")) {
                facts.ultraMarkers.add("sweeteners");
                facts.strongMarker = true;
            }
            if (candidate.sweetener && facts.good.remove("No added sugar or sweeteners")) facts.good.add(0, "No added sugar");
            // Less processed first: the same priority the card uses.
            if (facts.read) rank += facts.ultraProcessed() ? -8 : 8;
            if (facts.hydrogenatedFat) rank -= 12;
            rank += 3 * facts.good.size() - 3 * facts.concerns.size();
            String goalNote = meetsGoal ? null : goalNote(objective, focus, gain, current, n);
            pass.ranked.add(new Swap(candidate, grade.band, letter, why, rank - 5 * tradeOff.size(),
                standing(grade.band, letter, currentLetter), meetsGoal, goalNote, facts,
                checks(context, who, group, current, candidate, grade, currentLetter, focus, meetsGoal, worst, differences, gaining)));
        }
        return pass;
    }

    /**
     * The same category, read three ways, all by general rules. By the rule that places the product on screen: if the
     * shopper opened the candidate's own page, the app would put it in this group. By Open Food Facts' own categories,
     * where they name one: they must not put it in another FSSAI food category (nesting within one, such as paneer
     * filed under cheese, is the same category; "Chocolate Crackers" filed as biscuits are not a chocolate). And a
     * candidate filed here only by its Open Food Facts category (its name fits no group, such as "Fuse") must not be
     * named as another FSSAI food category ("Choco Ragi Puffs" are a savoury, a "Moong" papad a pulse product).
     */
    static boolean sameCategory(SwapCatalogue catalogue, SwapCatalogue.Group group, SwapCatalogue.Product candidate) {
        FoodCategory.Fcs fcs = FoodCategory.fromSwapGroup(group.id);
        if (candidate.offGroup != null && FoodCategory.fromSwapGroup(candidate.offGroup) != fcs) return false;
        SwapCatalogue.Group placed = catalogue.groupFor(candidate.displayName(), group.drink);
        if (placed != null) return placed.id.equals(group.id);
        FoodCategory.Fcs named = FoodCategory.place(candidate.displayName(), false, null).category;
        return named == null || named == fcs;
    }

    /**
     * The nutrients on which the candidate makes a real difference, at FSSAI's 30%: less of each one the category is
     * judged on and this product scores against (Nutri-Score points above zero), or more fibre where fibre is the
     * category's point and the candidate is a source of fibre.
     */
    static List<String> realDifferences(SwapCatalogue.Group group, Nutrition current, Nutrition candidate) {
        List<String> out = new ArrayList<>();
        for (String nutrient : group.focus) {
            boolean counts = "fibre".equals(nutrient) || NutriGrade.foodPoints(nutrient, current) > 0;
            if (counts && improves(nutrient, current, candidate)) out.add(nutrient);
        }
        return out;
    }

    /** Its colour and letter beside this product's, in words, for the card. */
    static String standing(NutriGrade.Band band, char letter, char currentLetter) {
        String colour = band == NutriGrade.Band.A ? "Green" : band == NutriGrade.Band.B ? "Yellow" : "Still red";
        if (letter == currentLetter) return colour + ", the same Nutri-Score (" + letter + ") as this product";
        switch (band) {
            case A: return "Green, Nutri-Score " + letter;
            case B: return "Yellow, Nutri-Score " + letter + ": better than this product, not yet green";
            default: return "Still red, Nutri-Score " + letter + ": a step better than this product";
        }
    }

    /** True when the swap is worse on what the shopper's goal measures (unknown for this product never counts). */
    private static boolean goalWorse(RecommendationContext.Objective objective, Nutrition original, Nutrition candidate) {
        switch (objective) {
            case LOWER_ENERGY: return higher(original.energyKcal, candidate.energyKcal);
            case HIGHER_ENERGY: return lower(original.energyKcal, candidate.energyKcal);
            case MORE_PROTEIN: return lower(original.proteinG, candidate.proteinG);
            case PROTEIN_DENSITY: return lower(density(original), density(candidate));
            case LOWER_SUGAR: {
                boolean added = original.addedSugarsG != null && candidate.addedSugarsG != null;
                return higher(added ? original.addedSugarsG : original.sugarsG, added ? candidate.addedSugarsG : candidate.sugarsG);
            }
            default: return false;
        }
    }

    /** "Better overall, but only 12% less energy for your weight-loss goal (30% counts as a real difference)". */
    private static String goalNote(RecommendationContext.Objective objective, String focus, double gain, Nutrition current, Nutrition n) {
        String goal = goalName(objective).replaceFirst("^Your ", "your ");
        if (Double.isFinite(gain) && gain >= 0.01) {
            return "Better overall, but only " + Math.round(gain * 100) + "% " + goalWhat(focus, current, n) + " for " + goal
                + " (" + GoalComparisons.threshold() + " counts as a real difference)";
        }
        return "Better overall, but no help to " + goal;
    }

    /**
     * What a swap passed for this shopper, each said with its numbers, in the order the engine applies them: its score,
     * the goal (or the real differences), what did not get worse, each limit from their goals and conditions,
     * allergies, diet, and the pregnancy, breastfeeding and kidney rules. Only checks that apply to this shopper are listed.
     */
    static List<String> checks(RecommendationContext context, SafetyProfile who, SwapCatalogue.Group group, Nutrition current,
                               SwapCatalogue.Product candidate, NutriGrade.Result grade, char currentLetter, String focus,
                               boolean meetsGoal, String worst, List<String> differences, boolean gaining) {
        List<String> out = new ArrayList<>();
        Nutrition n = candidate.per100;
        String per = " per " + n.unit();
        char letter = grade.letter();
        if (grade.band == NutriGrade.Band.A) out.add("Green: Nutri-Score " + letter + " (2023), good in itself, not only better than this product");
        else if (letter < currentLetter) out.add("Nutri-Score " + letter + " (2023) against this product's " + currentLetter + ": a better score");
        else out.add("Nutri-Score " + letter + " (2023), the same as this product: chosen for the real differences below");
        if (focus != null && meetsGoal) {
            double share = GoalComparisons.improvement(focus, current, n);
            out.add(goalName(context.objective) + ": " + Math.round(share * 100) + "% " + goalWhat(focus, current, n)
                + " for the same portion (at least " + GoalComparisons.threshold() + ", FSSAI's bar for a real difference)");
        }
        // The worst point first, in the words the card has always used for it.
        List<String> order = new ArrayList<>(differences);
        if (worst != null && order.remove(worst)) order.add(0, worst);
        for (String nutrient : order) {
            Double before = value(nutrient, current), after = value(nutrient, n);
            if (before == null || after == null) continue;
            boolean fibre = "fibre".equals(nutrient);
            String share = before > 0 ? " (" + Math.round(Math.abs(after - before) / before * 100) + "% " + (fibre ? "more" : "energy".equals(nutrient) ? "fewer" : "less") + ")" : "";
            String change = fibre ? "More fibre" : less(nutrient);
            out.add(Character.toUpperCase(change.charAt(0)) + change.substring(1) + (nutrient.equals(worst) ? ", this product's weakest point: " : ": ")
                + amount(nutrient, before) + " → " + amount(nutrient, after) + per + share);
        }
        if (focus != null && !meetsGoal) {
            String line = goalLine(context.objective, current, n, per);
            if (line != null) out.add(line);
        }
        List<String> held = new ArrayList<>();
        for (String nutrient : group.focus) {
            if (differences.contains(nutrient) || "fibre".equals(nutrient) || (gaining && "energy".equals(nutrient))) continue;
            if (value(nutrient, current) != null && value(nutrient, n) != null) held.add(label(nutrient));
        }
        if (!held.isEmpty()) out.add("Not higher than this product in " + String.join(", ", held) + " (label rounding allowed)");
        for (RecommendationContext.Constraint c : context.constraints) {
            String line = constraintLine(c, current, n, per);
            if (line != null && !out.contains(line)) out.add(line);
        }
        if (who.hasAllergies()) {
            List<String> names = new ArrayList<>();
            for (Allergen a : who.allergens()) names.add(a.label.toLowerCase(Locale.ROOT));
            out.add("No " + String.join(", ", names) + " in its ingredients or allergen statement"
                + (who.tracesAreDangerous() ? ", nor as \"may contain\"" : ""));
        }
        String diet = dietLine(context.diet, candidate);
        if (diet != null) out.add(diet);
        if (context.trimester != 0) out.add("Pregnancy: none of the additives FSSAI's label warnings name (aspartame, acesulfame K, MSG, plant stanols)");
        if (context.lactation != 0) out.add("Breastfeeding: none of the additives FSSAI's label warnings name for lactating mothers");
        if (context.kidney) out.add("Kidney: no phosphate additive (KDIGO 2017) and no potassium salt substitute (WHO)");
        if (context.phenylketonuria) out.add("No aspartame (FSSAI's warning for phenylketonurics)");
        out.add("Adds no nutrient that is \"high\" which this product is not");
        return out;
    }

    /** "Your weight-loss goal, no worse: energy not higher: 450 kcal vs 460 kcal per 100 g" for a swap that does not meet it. */
    private static String goalLine(RecommendationContext.Objective objective, Nutrition original, Nutrition candidate, String per) {
        RecommendationContext.Constraint c;
        switch (objective) {
            case LOWER_ENERGY: c = RecommendationContext.Constraint.ENERGY_NOT_HIGHER; break;
            case HIGHER_ENERGY: c = RecommendationContext.Constraint.ENERGY_NOT_LOWER; break;
            case MORE_PROTEIN: c = RecommendationContext.Constraint.PROTEIN_NOT_LOWER; break;
            case PROTEIN_DENSITY: c = RecommendationContext.Constraint.PROTEIN_DENSITY_NOT_LOWER; break;
            case LOWER_SUGAR: c = RecommendationContext.Constraint.SUGAR_NOT_HIGHER; break;
            default: return null;
        }
        String line = constraintLine(c, original, candidate, per);
        return line == null ? null : goalName(objective) + ", no worse: " + Character.toLowerCase(line.charAt(0)) + line.substring(1);
    }

    private static String goalName(RecommendationContext.Objective objective) {
        switch (objective) {
            case LOWER_ENERGY: return "Your weight-loss goal";
            case HIGHER_ENERGY: return "Your weight-gain goal";
            case MORE_PROTEIN: return "Your muscle goal";
            case PROTEIN_DENSITY: return "Your weight-loss and muscle goals";
            case LOWER_SUGAR: return "Your sugar-control goal";
            default: return "Your goal";
        }
    }

    private static String goalWhat(String focus, Nutrition original, Nutrition candidate) {
        switch (focus) {
            case "protein_density": return "more protein per calorie";
            case "lower_sugar": return original.addedSugarsG != null && candidate.addedSugarsG != null ? "less added sugar" : "less sugar";
            case "protein": return "more protein";
            case "lower_energy": return "less energy";
            default: return "more energy";
        }
    }

    private static String amount(String nutrient, double v) {
        switch (nutrient) {
            case "sodium": return Math.round(v) + " mg";
            case "energy": return Math.round(v) + " kcal";
            default: return Nutrition.number(Math.round(v * 10) / 10.0) + " g";
        }
    }

    /** "Sodium not higher: 210 mg vs 480 mg per 100 g". Null when the original's figure is unknown (nothing to compare). */
    private static String constraintLine(RecommendationContext.Constraint c, Nutrition original, Nutrition candidate, String per) {
        String what, unit;
        Double a, b;
        switch (c) {
            case ENERGY_NOT_HIGHER: what = "Energy not higher"; unit = "kcal"; a = original.energyKcal; b = candidate.energyKcal; break;
            case ENERGY_NOT_LOWER: what = "Energy not lower"; unit = "kcal"; a = original.energyKcal; b = candidate.energyKcal; break;
            case PROTEIN_NOT_LOWER: what = "Protein not lower"; unit = "g"; a = original.proteinG; b = candidate.proteinG; break;
            case PROTEIN_NOT_HIGHER: what = "Protein not higher"; unit = "g"; a = original.proteinG; b = candidate.proteinG; break;
            case FIBRE_NOT_LOWER: what = "Fibre not lower"; unit = "g"; a = original.fibreG; b = candidate.fibreG; break;
            case SUGAR_NOT_HIGHER: what = "Sugar not higher"; unit = "g"; a = original.sugarsG; b = candidate.sugarsG; break;
            case CARBS_NOT_HIGHER: what = "Carbohydrate not higher"; unit = "g"; a = original.carbohydrateG; b = candidate.carbohydrateG; break;
            case SATFAT_NOT_HIGHER: what = "Saturated fat not higher"; unit = "g"; a = original.saturatedFatG; b = candidate.saturatedFatG; break;
            case TRANSFAT_NOT_HIGHER: what = "Trans fat not higher"; unit = "g"; a = original.transFatG; b = candidate.transFatG; break;
            case SODIUM_NOT_HIGHER: what = "Sodium not higher"; unit = "mg"; a = original.sodiumMg; b = candidate.sodiumMg; break;
            case PROTEIN_DENSITY_NOT_LOWER: {
                Double x = density(original), y = density(candidate);
                if (x == null || y == null) return null;
                return "Protein per calorie not lower: " + Nutrition.number(Math.round(y * 1000) / 10.0) + " g vs "
                    + Nutrition.number(Math.round(x * 1000) / 10.0) + " g per 100 kcal";
            }
            default: return null;
        }
        if (a == null || b == null) return null;
        String fa = "mg".equals(unit) || "kcal".equals(unit) ? String.valueOf(Math.round(a)) : Nutrition.number(Math.round(a * 10) / 10.0);
        String fb = "mg".equals(unit) || "kcal".equals(unit) ? String.valueOf(Math.round(b)) : Nutrition.number(Math.round(b * 10) / 10.0);
        return what + ": " + fb + " " + unit + " vs " + fa + " " + unit + per;
    }

    private static String dietLine(DietRules.Diet diet, SwapCatalogue.Product p) {
        switch (diet) {
            case VEGAN: return "Vegan: " + ("vegan".equals(p.veg) ? "marked vegan" : "no animal ingredient in its list");
            case VEGETARIAN: return "Vegetarian: " + (veg(p) ? "marked vegetarian" : "no meat, fish or egg in its list");
            case EGGETARIAN: return "Eggetarian: " + (veg(p) ? "marked vegetarian" : "no meat or fish in its list");
            case JAIN: return "Jain: no onion, garlic, root vegetables or honey in its list" + (veg(p) ? "; marked vegetarian" : "");
            case HALAL: return p.halal ? "Halal: halal mark recorded" : "Halal: no pork or alcohol in its list; only a halal mark on the pack confirms it";
            default: return null;
        }
    }

    private static boolean veg(SwapCatalogue.Product p) { return "vegetarian".equals(p.veg) || "vegan".equals(p.veg); }

    private static String focusOf(RecommendationContext.Objective objective) {
        switch (objective) {
            case LOWER_ENERGY: return "lower_energy";
            case HIGHER_ENERGY: return "higher_energy";
            case MORE_PROTEIN: return "protein";
            case PROTEIN_DENSITY: return "protein_density";
            case LOWER_SUGAR: return "lower_sugar";
            default: return null;
        }
    }

    /**
     * Stage 1 for a candidate: the shopper's allergies, diet (with positive evidence) and state or
     * condition. Returns why it is left out, or null when it may be offered.
     */
    static String vetoFor(SwapCatalogue.Product candidate, SafetyProfile who, RecommendationContext context) {
        String ingredients = candidate.ingredients;
        boolean listed = ingredients != null && !ingredients.isBlank();
        DietRules.Diet diet = context.diet;
        if (diet.vegetarianFamily() || diet == DietRules.Diet.HALAL) {
            if ("non-vegetarian".equals(candidate.veg) && diet.vegetarianFamily()) return "your diet";
            boolean marked = diet == DietRules.Diet.VEGAN ? "vegan".equals(candidate.veg)
                : diet.vegetarianFamily() && ("vegetarian".equals(candidate.veg) || "vegan".equals(candidate.veg));
            // A strict diet needs evidence: a veg or vegan mark, or an ingredient list to check.
            if (!marked && !listed) return "no ingredient list to check against your diet";
        }
        String declared = candidate.allergenText();
        if (listed || !declared.isEmpty() || who.hasAllergies() || who.wheatAllergy()) {
            SafetyGate.Result gate = SafetyGate.check(ingredients, declared.isEmpty() ? null : declared, who);
            if (gate.blocked()) {
                for (String code : gate.codes) if (code.startsWith("diet_")) return "your diet";
                return "your allergies";
            }
        }
        if (context.trimester != 0 && !ConditionRules.check(ingredients, null, context.trimester, false, false, false).fits()) return "pregnancy";
        if (context.lactation != 0 && !ConditionRules.check(ingredients, null, 0, true, false, false).fits()) return "breastfeeding";
        if (context.kidney && !ConditionRules.check(ingredients, null, 0, false, true, false).fits()) return "your kidney condition";
        if (context.phenylketonuria && !ConditionRules.check(ingredients, null, 0, false, false, true).fits()) return "phenylketonuria";
        return null;
    }

    /**
     * Stage 4: the first constraint a candidate breaks, compared at the same mass or volume, or null.
     * An unknown original figure cannot constrain; an unknown candidate figure cannot pass.
     */
    static String brokenConstraint(RecommendationContext context, Nutrition original, Nutrition candidate) {
        for (RecommendationContext.Constraint c : context.constraints) {
            switch (c) {
                case ENERGY_NOT_HIGHER: if (higher(original.energyKcal, candidate.energyKcal)) return label(c, candidate.energyKcal); break;
                case ENERGY_NOT_LOWER: if (lower(original.energyKcal, candidate.energyKcal)) return label(c, candidate.energyKcal); break;
                case PROTEIN_NOT_LOWER: if (lower(original.proteinG, candidate.proteinG)) return label(c, candidate.proteinG); break;
                case PROTEIN_NOT_HIGHER: if (higher(original.proteinG, candidate.proteinG)) return label(c, candidate.proteinG); break;
                case FIBRE_NOT_LOWER: if (lower(original.fibreG, candidate.fibreG)) return label(c, candidate.fibreG); break;
                case SUGAR_NOT_HIGHER: if (higher(original.sugarsG, candidate.sugarsG)) return label(c, candidate.sugarsG); break;
                case CARBS_NOT_HIGHER: if (higher(original.carbohydrateG, candidate.carbohydrateG)) return label(c, candidate.carbohydrateG); break;
                case SATFAT_NOT_HIGHER: if (higher(original.saturatedFatG, candidate.saturatedFatG)) return label(c, candidate.saturatedFatG); break;
                case TRANSFAT_NOT_HIGHER: if (higher(original.transFatG, candidate.transFatG)) return label(c, candidate.transFatG); break;
                case SODIUM_NOT_HIGHER: if (higher(original.sodiumMg, candidate.sodiumMg)) return label(c, candidate.sodiumMg); break;
                case PROTEIN_DENSITY_NOT_LOWER: {
                    Double a = density(original), b = density(candidate);
                    if (lower(a, b)) return b == null ? "protein per calorie not on file" : "less protein per calorie";
                    break;
                }
                default: break;
            }
        }
        return null;
    }

    private static Double density(Nutrition n) {
        return n.proteinG == null || n.energyKcal == null || n.energyKcal <= 0 ? null : n.proteinG / n.energyKcal;
    }

    /** True when the candidate is higher beyond label rounding, or unknown while the original is known. */
    private static boolean higher(Double original, Double candidate) {
        if (original == null || !Double.isFinite(original)) return false;
        if (candidate == null || !Double.isFinite(candidate)) return true;
        return candidate > original * (1 + TOLERANCE) + 0.05;
    }

    private static boolean lower(Double original, Double candidate) {
        if (original == null || !Double.isFinite(original)) return false;
        if (candidate == null || !Double.isFinite(candidate)) return true;
        return candidate < original * (1 - TOLERANCE) - 0.05;
    }

    private static String label(RecommendationContext.Constraint c, Double candidateValue) {
        String nutrient;
        boolean up;
        switch (c) {
            case ENERGY_NOT_HIGHER: nutrient = "energy"; up = true; break;
            case ENERGY_NOT_LOWER: nutrient = "energy"; up = false; break;
            case PROTEIN_NOT_LOWER: nutrient = "protein"; up = false; break;
            case PROTEIN_NOT_HIGHER: nutrient = "protein"; up = true; break;
            case FIBRE_NOT_LOWER: nutrient = "fibre"; up = false; break;
            case SUGAR_NOT_HIGHER: nutrient = "sugar"; up = true; break;
            case CARBS_NOT_HIGHER: nutrient = "carbohydrate"; up = true; break;
            case SATFAT_NOT_HIGHER: nutrient = "saturated fat"; up = true; break;
            case TRANSFAT_NOT_HIGHER: nutrient = "trans fat"; up = true; break;
            default: nutrient = "sodium"; up = true; break;
        }
        if (candidateValue == null || !Double.isFinite(candidateValue)) return nutrient + " not on file";
        return (up ? "more " : "less ") + nutrient;
    }

    /** The adverse focus nutrient the original scores worst on, or "fibre" when that is the group's point. */
    static String worstFocus(SwapCatalogue.Group group, Nutrition n, boolean drink) {
        String worst = null;
        int worstPoints = 0;
        for (String nutrient : group.focus) {
            if ("fibre".equals(nutrient)) continue;
            int points = NutriGrade.foodPoints(nutrient, n);
            if (points > worstPoints) { worst = nutrient; worstPoints = points; }
        }
        if (worst == null && group.focus.contains("fibre")) return "fibre";
        return worst;
    }

    static boolean improves(String nutrient, Nutrition original, Nutrition candidate) {
        if (nutrient == null) return true;          // nothing adverse to fix: the better band is enough
        Double before = value(nutrient, original), after = value(nutrient, candidate);
        if (before == null || after == null) return false;   // an undeclared figure proves nothing
        double min = NutritionCalculator.comparativeMin().doubleValue();
        // More fibre counts when it is a real difference (30%; from none, any amount) and enough to be a
        // source of fibre, so 0.2 g against 0.3 g is never offered as the point of a swap.
        if ("fibre".equals(nutrient)) return after >= before * (1 + min) && after > before && NutritionCalculator.fibreSource(candidate);
        if (before <= 0) return after <= 0;
        return after <= before * (1 - min);
    }

    /** Allowance for label rounding when comparing two products on the same nutrient. */
    static final double TOLERANCE = 0.05;

    /**
     * True when the swap has more of any adverse nutrient this group is judged on. Fixing the worst
     * problem is not enough if another one gets worse: baked chips with more sodium than the fried
     * ones are not the healthier salty snack.
     */
    static boolean worseOnFocus(SwapCatalogue.Group group, Nutrition original, Nutrition candidate) {
        return worseOnFocus(group, original, candidate, false);
    }
    private static boolean worseOnFocus(SwapCatalogue.Group group, Nutrition original, Nutrition candidate, boolean allowEnergyGain) {
        for (String nutrient : group.focus) {
            if ("fibre".equals(nutrient) || (allowEnergyGain && "energy".equals(nutrient))) continue;
            Double before = value(nutrient, original), after = value(nutrient, candidate);
            if (before == null || after == null) continue;
            if (after > before * (1 + TOLERANCE) + 0.05) return true;
        }
        return false;
    }

    private static double improvementShare(String nutrient, Nutrition original, Nutrition candidate) {
        if (nutrient == null) return 0;
        Double before = value(nutrient, original), after = value(nutrient, candidate);
        if (before == null || after == null || before <= 0) return 0;
        return "fibre".equals(nutrient) ? (after - before) / before : (before - after) / before;
    }

    /** One line saying what is better, with the biggest real change first. */
    static String why(SwapCatalogue.Group group, Nutrition original, Nutrition candidate, boolean drink) {
        String best = null;
        double bestShare = 0;
        for (String nutrient : group.focus) {
            double share = improvementShare(nutrient, original, candidate);
            if (share > bestShare) { bestShare = share; best = nutrient; }
        }
        if (best == null || bestShare < 0.1) return "Better overall balance";
        String label = label(best);
        if ("fibre".equals(best)) {
            double times = 1 + bestShare;
            return times >= 2 ? Nutrition.number(Math.round(times * 10) / 10.0) + "× the fibre" : Math.round(bestShare * 100) + "% more fibre";
        }
        if (value(best, candidate) != null && value(best, candidate) <= (best.equals("sugars") ? 0.5 : 0.0)) {
            return best.equals("sugars") ? "Practically no sugar" : "No " + label;
        }
        if (bestShare >= 0.5 && bestShare < 0.6) return "Half the " + label;
        return Math.round(bestShare * 100) + "% " + less(best);
    }

    /** "less sugar", but "fewer calories". */
    private static String less(String nutrient) {
        return "energy".equals(nutrient) ? "fewer calories" : "less " + label(nutrient);
    }

    private static String label(String nutrient) {
        switch (nutrient) {
            case "sugars": return "sugar";
            case "saturated_fat": return "saturated fat";
            case "sodium": return "sodium";
            case "energy": return "calories";
            default: return nutrient;
        }
    }

    private static Double value(String nutrient, Nutrition n) {
        switch (nutrient) {
            case "sugars": return n.sugarsG;
            case "saturated_fat": return n.saturatedFatG;
            case "sodium": return n.sodiumMg;
            case "energy": return n.energyKcal;
            case "fibre": return n.fibreG;
            default: return null;
        }
    }

    /** Checked on the phone. A block for this person — an allergen, or no ingredient data at all — drops it. */
    static boolean safeFor(SwapCatalogue.Product candidate, SafetyProfile who) {
        if (who.avoidsAnimalIngredients() && "non-vegetarian".equals(candidate.veg)) return false;
        String ingredients = candidate.ingredients;
        String declared = candidate.allergenText();
        if ((ingredients == null || ingredients.isEmpty()) && declared.isEmpty() && !who.hasAllergies()) return true;
        SafetyGate.Result result = SafetyGate.check(ingredients, declared.isEmpty() ? null : declared, who);
        return !result.blocked();
    }

    /** Prefers packs of a similar size, so a 1 kg tin never stands in for a 50 g snack. */
    private static double packCloseness(Double original, Double candidate) {
        if (original == null || candidate == null || original <= 0 || candidate <= 0) return 0;
        double ratio = candidate / original;
        return ratio >= 0.5 && ratio <= 2 ? 8 : -8;
    }

    private static boolean sameProduct(SwapCatalogue.Product candidate, String productName) {
        if (productName == null) return false;
        String shown = ProductPage.normalize(productName);
        String name = ProductPage.normalize(candidate.name);
        return !name.isEmpty() && shown.contains(name) && candidate.brand != null
            && shown.contains(ProductPage.normalize(candidate.brand).split(" ")[0]);
    }

    private static String brandKey(String brand) {
        String key = brand == null ? "" : ProductPage.normalize(brand).replace(" ", "");
        return key.length() > 6 ? key.substring(0, 6) : key;
    }

    /** The same product in another pack size counts once. */
    private static String nameKey(SwapCatalogue.Product product) {
        return ProductPage.normalize(product.displayName()).replaceAll("\\b\\d+(\\.\\d+)?\\s*(g|gm|kg|ml|l)\\b", "").replace(" ", "");
    }

    private SwapEngine() {}
}
