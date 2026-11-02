package com.betterchoice.android;

import java.util.Collections;
import java.util.List;

/** What the bubble shows for the current screen. Kept in memory only; never written to disk. */
final class GradeResult {
    enum State {
        NOT_PRODUCT, NOT_FOOD,
        /** Food product page, nothing readable yet: the bubble asks the user to open the label. */
        NEEDS_LABEL,
        /** The user tapped Scan and the screen is being read right now. Shown so the tap has an effect. */
        SCANNING,
        NEEDS_INPUT, SKIPPED, GRADED,
        /** The shopper tapped a healthier swap and the shop's screen has answered: stock, price, budget. */
        SWAP_CHECKED
    }
    enum Source {
        LABEL_TEXT("the label on this page"),
        PACK_PHOTO("the pack photo, read on your phone"),
        BARCODE("this pack's barcode"),
        DATABASE("the BetterChoice database"),
        OPEN_FOOD_FACTS("Open Food Facts, by barcode"),
        USER("details you added"),
        /** No panel: plain water by its name. It used to claim "the label on this page". */
        PLAIN_WATER("its name: plain water, with nothing in it to measure", false),
        /** No panel needed: plain tea or coffee by its ingredient list, rated as the brewed cup. */
        AS_BREWED("typical values for plain tea or coffee, as its ingredient list shows", false);
        final String description;
        /** The figures were read for this product. False for typical values, which earn no "good" lines. */
        final boolean measured;
        Source(String description) { this(description, true); }
        Source(String description, boolean measured) { this.description = description; this.measured = measured; }
    }

    final State state;
    final NutriGrade.Band band;
    final Source source;
    final List<String> reasons;
    final ProductPage page;
    /** The values behind a rating, for the per-serving and whole-pack columns. */
    final Nutrition nutrition;
    /** The number read from the pack's barcode, when one was visible. Null otherwise. */
    final String gtin;
    /** True when the card should open by itself, without the user tapping the bubble. */
    boolean announce;

    private GradeResult(State state, NutriGrade.Band band, Source source, List<String> reasons,
                        ProductPage page, Nutrition nutrition, String gtin) {
        this.state = state; this.band = band; this.source = source; this.reasons = reasons;
        this.page = page; this.nutrition = nutrition; this.gtin = gtin;
    }

    static GradeResult neutral(State state, ProductPage page) {
        return new GradeResult(state, null, null, Collections.emptyList(), page, null, null);
    }

    /** Asks the user to open the label. `announce` is true the first time for a product. */
    static GradeResult needsLabel(ProductPage page, boolean announce) {
        GradeResult result = new GradeResult(State.NEEDS_LABEL, null, null, Collections.emptyList(), page, null, null);
        result.announce = announce;
        return result;
    }

    /** Why nothing was found, in the shopper's terms. Null when there is nothing useful to say. */
    String note;
    /**
     * The label text this rating was read from, kept so allergens can be checked against it.
     * Held in memory for the current screen only, exactly like the rest of this object.
     */
    String labelText;

    static GradeResult needsInput(ProductPage page, String gtin) { return needsInput(page, gtin, null); }

    static GradeResult needsInput(ProductPage page, String gtin, String note) {
        GradeResult result = new GradeResult(State.NEEDS_INPUT, null, null, Collections.emptyList(), page, null, gtin);
        result.note = note;
        return result;
    }

    /** The swap the shopper tapped, and what the shop's screen showed for it. Null otherwise. */
    SwapCheck.Pending check;
    SwapCheck.Result checkResult;

    /** The answer to a tapped swap, opened on the card without another tap. */
    static GradeResult swapChecked(SwapCheck.Pending pending, SwapCheck.Result found) {
        GradeResult result = new GradeResult(State.SWAP_CHECKED, null, null, Collections.emptyList(), null, null, null);
        result.check = pending;
        result.checkResult = found;
        result.announce = true;
        return result;
    }

    /** Shown the moment the user taps Scan, so the tap visibly does something while the read runs. */
    static GradeResult scanning(ProductPage page, String gtin) {
        return new GradeResult(State.SCANNING, null, null, Collections.emptyList(), page, null, gtin);
    }

    static GradeResult graded(Nutrition nutrition, Source source, ProductPage page) {
        return graded(nutrition, source, page, null);
    }

    static GradeResult graded(Nutrition nutrition, Source source, ProductPage page, String gtin) {
        return graded(nutrition, source, page, gtin, Beverage.NOT_A_DRINK);
    }

    /** The drink this was graded as, when it was a drink at all. Null for packaged food. */
    Beverage.Kind drinkKind;
    /** The rating's reasons, split so the card can show the good side in its own colour. */
    List<String> notes = Collections.emptyList();
    List<String> concerns = Collections.emptyList();
    List<String> positives = Collections.emptyList();
    List<String> caveats = Collections.emptyList();
    /** The official five-letter Nutri-Score behind the band, or '?' when not graded. */
    char letter = '?';
    /** What the ingredient list said, when one was read; used to rank swaps the same way. */
    IngredientFacts ingredients;

    /**
     * Grades the label as the product it is. The basis is settled first ({@link LabelBasis}): an ice
     * cream labelled per 100 ml is still scored as a food, and a drink sold as powder is scored as
     * the powder. The kind shown on the card is the kind actually scored, after the label's own
     * numbers have been checked, never the first guess from the name.
     */
    static GradeResult graded(Nutrition nutrition, Source source, ProductPage page, String gtin, Beverage drink) {
        return graded(nutrition, source, page, gtin, drink, null);
    }

    /** @param labelText the text the values were read from, kept for the allergen check */
    static GradeResult graded(Nutrition nutrition, Source source, ProductPage page, String gtin, Beverage drink,
                              String labelText) {
        Beverage identity = drink == null ? Beverage.NOT_A_DRINK : drink;
        String pageText = page == null ? null : page.text;
        // A pack's weight and volume are usually printed on the pack, not the shop page.
        String packText = labelText == null ? pageText : pageText == null ? labelText : labelText + "\n" + pageText;
        Nutrition settled = LabelBasis.settle(nutrition, identity, packText);
        if (settled == null) {
            GradeResult missing = needsInput(page, gtin, "No nutrition figures were read.");
            missing.labelText = packText;
            return missing;
        }
        // An unstated basis is rated as labels give it (food per 100 g, drink per 100 ml) and the card
        // says the basis could not be found; only a stated basis that disagrees with the product waits.
        if (settled.basisStated && ((!identity.isDrink() && !identity.soldDry && settled.beverage)
                || (identity.isDrink() && !settled.beverage && !identity.soldDry))) {
            GradeResult missing = needsInput(page, gtin, "The label uses a different mass/volume basis. A declared weight and volume are needed; we won't assume 1 ml equals 1 g.");
            missing.labelText = packText;
            return missing;
        }
        NutriGrade.FoodKind kind = FoodCategory.kind(page == null ? null : page.name, null);
        // Unknown amounts are not zero. A missing sugar, saturated-fat or sodium figure is scored at both ends of what
        // it could be (none, and the most the label allows); when the colour is the same either way, the colour is
        // shown and the card names what is missing. Only a missing figure that could change the colour holds it back.
        // This is the same reasoning for every product, never a rule for one.
        if (!settled.usable() || !NutriGrade.bandSettled(settled, identity, kind)) {
            GradeResult missing = needsInput(page, gtin, "Not enough information for a nutrient band. Missing: "
                + settled.missingNegatives() + ", which could change the colour. Unknown amounts are not zero.");
            missing.labelText = packText;
            return missing;
        }
        if (settled.ocrRepaired) {
            GradeResult uncertain = needsInput(page, gtin, "Some label characters or decimals needed repair. Open a clearer panel or confirm the amounts before a band is shown.");
            uncertain.labelText = packText;
            return uncertain;
        }
        // With a figure missing, reasons come from the label at the least it could be, and the letter at its worst
        // (the colour is the same either way).
        String missingFigures = settled.missingNegatives();
        NutriGrade.Result result = NutriGrade.grade(missingFigures.isEmpty() ? settled : NutriGrade.bestCase(settled), identity, kind);
        NutriGrade.Result worst = missingFigures.isEmpty() ? result : NutriGrade.grade(NutriGrade.worstCase(settled), identity, kind);
        // Ingredients explain the product separately. The A/B/C band remains the nutrient method's
        // result, so a processing heuristic can never silently recolour the bubble.
        IngredientFacts facts = IngredientFacts.of(IngredientFacts.section(packText), result.scoredAsDrink());
        java.util.List<String> notes = new java.util.ArrayList<>(result.notes);
        java.util.List<String> concerns = new java.util.ArrayList<>(result.concerns);
        concerns.addAll(facts.concerns);
        // Typical values are not this pack's figures, so they earn no "low sugar" or "low sodium" praise.
        java.util.List<String> positives = source.measured
            ? new java.util.ArrayList<>(result.positives) : new java.util.ArrayList<>();
        positives.addAll(facts.good);
        java.util.List<String> caveats = new java.util.ArrayList<>(result.caveats);
        if (!missingFigures.isEmpty()) {
            caveats.add(0, "Not on the label: " + missingFigures + ". The colour is the same whatever the amount"
                + (worst.letter() == result.letter() ? "." : " (Nutri-Score " + result.letter() + " to " + worst.letter() + ")."));
        }
        caveats.addAll(facts.information);
        concerns = Priorities.concerns(concerns);
        positives = Priorities.positives(positives);
        java.util.List<String> reasons = new java.util.ArrayList<>(notes);
        reasons.addAll(concerns); reasons.addAll(positives); reasons.addAll(caveats);
        GradeResult graded = new GradeResult(State.GRADED, result.band, source, Collections.unmodifiableList(reasons),
            page, settled.copy(), gtin);
        graded.drinkKind = result.identity == null ? null : result.identity.kind;
        graded.letter = worst.letter();
        graded.notes = Collections.unmodifiableList(notes);
        graded.concerns = Collections.unmodifiableList(concerns);
        graded.positives = Collections.unmodifiableList(positives);
        graded.caveats = Collections.unmodifiableList(caveats);
        graded.ingredients = facts;
        graded.labelText = labelText;
        return graded;
    }

    /** Carries a barcode found on this screen into a state built elsewhere, such as the 2-second hint. */
    GradeResult withGtin(String number) {
        if (number == null || number.equals(gtin)) return this;
        GradeResult copy = new GradeResult(state, band, source, reasons, page, nutrition, number);
        copy.announce = announce;
        copy.drinkKind = drinkKind; copy.note = note; copy.labelText = labelText;
        copy.notes = notes; copy.concerns = concerns; copy.positives = positives; copy.caveats = caveats;
        copy.letter = letter; copy.ingredients = ingredients;
        return copy;
    }

    /**
     * Same answer, so the bubble need not redraw. The reasons count too: when the ingredient list
     * scrolls into view after the first rating, the band may not move but what the card says does,
     * and an out-of-date card had been left saying "no added sugar" about a sugared drink.
     */
    boolean sameAs(GradeResult other) {
        return other != null && state == other.state && band == other.band && source == other.source &&
            (page == null ? other.page == null : other.page != null && page.key().equals(other.page.key()))
            && reasons.equals(other.reasons) && concerns.equals(other.concerns) && positives.equals(other.positives)
            && java.util.Objects.equals(note, other.note) && java.util.Objects.equals(labelText, other.labelText);
    }
}
