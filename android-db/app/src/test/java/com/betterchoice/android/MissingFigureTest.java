package com.betterchoice.android;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * One rule for every product, checked over a whole class of labels rather than one page: a missing sugar,
 * saturated-fat or sodium figure is never taken as zero. The colour is shown only when it is the same whatever the
 * missing figure is, the card names what is missing, and nothing missing is praised or cleared.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class MissingFigureTest {
    private static Nutrition label(double kcal, Double sugars, Double sat, Double sodium) {
        Nutrition n = new Nutrition();
        n.basisStated = true;
        n.energyKcal = kcal; n.sugarsG = sugars; n.saturatedFatG = sat; n.sodiumMg = sodium; n.proteinG = 6.0; n.fibreG = 2.0;
        return n;
    }

    /**
     * Over a grid of labels, with one negative figure removed at a time: whenever a colour is given, the full label
     * has that colour for every value the missing figure could take; whenever it is held back, some value would
     * change it.
     */
    @Test public void aColourIsShownOnlyWhenNoMissingFigureCouldChangeIt() {
        double[] kcals = {50, 150, 300, 450, 550};
        double[] sugars = {0, 4, 12, 25, 45};
        double[] sats = {0, 1.5, 4, 9, 16};
        double[] sodiums = {0, 150, 400, 800, 1500};
        double[] probe = {0, 50, 120, 300, 600, 1000, 2000, 5000};
        int shown = 0, held = 0;
        for (double k : kcals) for (double s : sugars) for (double f : sats) for (double na : sodiums) {
            for (int missing = 0; missing < 3; missing++) {
                Nutrition n = label(k, missing == 0 ? null : s, missing == 1 ? null : f, missing == 2 ? null : na);
                GradeResult r = GradeResult.graded(n, GradeResult.Source.LABEL_TEXT, null);
                java.util.Set<NutriGrade.Band> possible = java.util.EnumSet.noneOf(NutriGrade.Band.class);
                for (double v : probe) {
                    Nutrition full = n.copy();
                    if (missing == 0) full.sugarsG = Math.min(v / 50, 100);
                    if (missing == 1) full.saturatedFatG = Math.min(v / 50, 100);
                    if (missing == 2) full.sodiumMg = v;
                    possible.add(NutriGrade.grade(full, Beverage.NOT_A_DRINK).band);
                }
                if (r.state == GradeResult.State.GRADED) {
                    shown++;
                    assertEquals(n.missingNegatives() + " at " + k + "/" + s + "/" + f + "/" + na, java.util.EnumSet.of(r.band), possible);
                    assertTrue(r.caveats.toString(), r.caveats.get(0).startsWith("Not on the label: " + n.missingNegatives()));
                } else {
                    held++;
                    assertTrue("held back only when the colour could change", possible.size() > 1);
                }
            }
        }
        assertTrue("both outcomes occur in the grid: shown " + shown + ", held " + held, shown > 100 && held > 20);
    }

    /** The Zepto page shape: one sentence, no sodium, no "per 100 g". Plainly red whatever the sodium is: rated red. */
    @Test public void aSentenceLabelWithoutSodiumOrBasisIsRatedWhenItsColourIsSettled() {
        Nutrition n = NutritionParser.parse("Nutrition Information\nCarbohydrates 67 g, of which sugars 22 g, Protein 7 g, Fat 22 g, "
            + "Saturated Fatty acids 11 g, Trans fatty acids 0 g, Cholestrol 6.5 mg, Energy 494 kcal");
        assertNotNull(n);
        assertFalse(n.basisStated);
        GradeResult r = GradeResult.graded(n, GradeResult.Source.LABEL_TEXT, null);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals(NutriGrade.Band.C, r.band);
        assertTrue(r.caveats.toString(), r.caveats.get(0).startsWith("Not on the label: sodium. The colour is the same whatever the amount"));
        for (String s : r.positives) assertFalse("a missing figure is never praised: " + s, s.toLowerCase().contains("sodium") || s.toLowerCase().contains("salt"));
    }

    /** A low-everything label missing its sodium is held back: sodium alone could make it red. */
    @Test public void aBorderlineLabelMissingSodiumStillAsks() {
        GradeResult r = GradeResult.graded(label(120, 3.0, 0.5, null), GradeResult.Source.LABEL_TEXT, null);
        assertNotEquals(GradeResult.State.GRADED, r.state);
        assertTrue(r.note, r.note.contains("Missing: sodium, which could change the colour"));
    }

    /**
     * Total sugars are never below a declared added sugar. A label giving only "30 g of added sugar" is red whatever
     * its total; scoring the missing total from zero would have called it uncertain.
     */
    @Test public void aDeclaredAddedSugarBoundsTheMissingTotal() {
        Nutrition n = label(300, null, 2.0, 120.0);
        n.addedSugarsG = 30.0; n.carbohydrateG = 40.0; n.proteinG = 3.0; n.fibreG = 0.0;
        GradeResult r = GradeResult.graded(n, GradeResult.Source.LABEL_TEXT, null);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals(NutriGrade.Band.C, r.band);
        // The same label without the added-sugar figure could still be yellow, so it waits.
        Nutrition unknown = label(300, null, 2.0, 120.0);
        unknown.carbohydrateG = 40.0; unknown.proteinG = 3.0; unknown.fibreG = 0.0;
        assertNotEquals(GradeResult.State.GRADED, GradeResult.graded(unknown, GradeResult.Source.LABEL_TEXT, null).state);
    }

    /** Label phrasings read the same way wherever they appear: unit in brackets after the value, and "x g of y". */
    @Test public void commonPhrasingsAreRead() {
        // Energy, saturated fat and sodium make it a panel; total sugar is not declared, added sugar is, in passing.
        Nutrition yoghurt = NutritionParser.parse("Nutrition Information\nEnergy 136 (kcal), Protein 4.1 g, Carbohydrate 18.5 g "
            + "(including 12 g of added sugar), Fat 4.6 g, Saturated fat 2.9 g, Sodium 60 mg, Calcium 170 mg");
        assertNotNull(yoghurt);
        assertEquals(136, yoghurt.energyKcal, 0.001);
        assertEquals(4.1, yoghurt.proteinG, 0.001);
        assertEquals("never the fat figure that follows", 12, yoghurt.addedSugarsG, 0.001);
        assertEquals(4.6, yoghurt.totalFatG, 0.001);
        assertEquals(2.9, yoghurt.saturatedFatG, 0.001);
        assertNull("no total sugar was declared", yoghurt.sugarsG);
        Nutrition brackets = NutritionParser.parse("Energy 410 (kcal)\nProtein 9.5 (g)\nSodium 380 (mg)\nTotal Sugars 21 (g)\nSaturated Fat 7.2 (g)");
        assertEquals(410, brackets.energyKcal, 0.001);
        assertEquals(380, brackets.sodiumMg, 0.001);
        assertEquals(7.2, brackets.saturatedFatG, 0.001);
        Nutrition of = NutritionParser.parse("Energy 380 kcal per 100 g. Packed with 12 g of protein and 9 g of fibre, only 4 g of sugar, 1.2 g of saturated fat and 300 mg of sodium");
        assertEquals(12, of.proteinG, 0.001);
        assertEquals(9, of.fibreG, 0.001);
        assertEquals(4, of.sugarsG, 0.001);
        assertEquals(1.2, of.saturatedFatG, 0.001);
        assertEquals(300, of.sodiumMg, 0.001);
        // A run-on row never lends the total to the added sugar.
        Nutrition runOn = NutritionParser.parse("Energy 63 kcal\nTotal Sugars 15.6 g Added Sugars 14.2 g Sodium 26 mg");
        assertEquals(15.6, runOn.sugarsG, 0.001);
        assertEquals(14.2, runOn.addedSugarsG, 0.001);
    }

    /** "Nothing high in ..." names only the figures that were read. */
    @Test public void nothingMissingIsClearedAsNotHigh() {
        Nutrition n = label(60, 1.0, 0.2, null);
        n.proteinG = 3.0; n.fibreG = 0.0;
        for (String note : NutriGrade.grade(n).notes) assertFalse(note, note.startsWith("Nothing high") && note.contains("sodium"));
    }

    /** An ingredient list ends where the list ends, whatever screen controls follow it before a known end marker. */
    @Test public void anIngredientListStopsAtScreenControls() {
        String page = String.join("\n", "Ingredients",
            "Refined Wheat Flour (maida), Refined Palm Oil, Sugar, Invert Sugar Syrup, Raising Agents, Iodised Salt and Emulsifier",
            "Back", "Search", "Share", "Product Name is Britannia Pure Magic Choco Stars Chunky Choco Topped Cookie", "Allergen Information",
            "Contains wheat and milk");
        String list = IngredientFacts.section(page);
        assertNotNull(list);
        assertTrue(list, list.endsWith("Iodised Salt and Emulsifier"));
        // A list wrapped over lines (a pack photo) still joins, line by line, up to the marker.
        String wrapped = String.join("\n", "INGREDIENTS: Wheat Flour (Atta), Sugar, Edible",
            "Vegetable Oil (Palm), Milk Solids (5%), Salt,", "Raising Agents (500(ii), 503(ii)).", "Allergen Information: contains wheat, milk");
        String joined = IngredientFacts.section(wrapped);
        assertTrue(joined, joined.contains("Vegetable Oil (Palm)") && joined.contains("Raising Agents"));
    }
}
