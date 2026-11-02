package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

/** Worked examples use the published 2017 Nutri-Score thresholds, mapped A/B -> A, C -> B, D/E -> C. */
public class NutriGradeTest {
    static Nutrition food(double kcal, Double sugars, Double satFat, Double sodium, Double fibre, Double protein) {
        Nutrition n = new Nutrition();
        n.basisStated = true; // These fixtures explicitly represent per-100-g panels.
        n.energyKcal = kcal; n.sugarsG = sugars; n.saturatedFatG = satFat; n.sodiumMg = sodium; n.fibreG = fibre; n.proteinG = protein;
        return n;
    }

    @Test public void oatsAreDarkGreen() {
        // N = 4 energy + 0 sugar + 1 sat fat + 0 sodium = 5; P = 5 fibre + 5 protein; score -5 (Nutri-Score A).
        NutriGrade.Result r = NutriGrade.grade(food(379, 1.0, 1.2, 6.0, 10.0, 13.0));
        assertEquals(NutriGrade.Band.A, r.band);
        assertEquals(-5, r.score);
    }

    @Test public void plainCurdIsNutriScoreBAndThereforeGreen() {
        NutriGrade.Result r = NutriGrade.grade(food(60, 4.7, 2.5, 45.0, 0.0, 3.1));
        assertEquals(2, r.score);
        assertEquals(NutriGrade.Band.A, r.band);
    }

    @Test public void moderateSnackIsYellow() {
        // 2023: energy 1674 kJ 4 + sugars 10 g 2 + saturates 3 g 2 + salt 0.75 g 3 = 11, so protein is not
        // counted; fibre 2 g earns 0 (threshold 3.0); score 11, Nutri-Score D. Stricter than 2017 (9, C).
        NutriGrade.Result r = NutriGrade.grade(food(400, 10.0, 3.0, 300.0, 2.0, 12.0));
        assertEquals(11, r.score);
        assertEquals(NutriGrade.Band.C, r.band);
    }

    @Test public void saltyChipsAreRedWithReasons() {
        // 2023: energy 2243 kJ 6 + sugars 0 + saturates 3.1 g 3 + salt 1.75 g 8 = 17; fibre 4.4 g 2; score 15 (D).
        NutriGrade.Result r = NutriGrade.grade(NutritionParser.parse(NutritionParserTest.CHIPS_TABLE));
        assertEquals(15, r.score);
        assertEquals(NutriGrade.Band.C, r.band);
        assertTrue(r.reasons.toString(), r.reasons.stream().anyMatch(s -> s.startsWith("High sodium")));
    }

    @Test public void creamBiscuitIsRed() {
        // 2023: energy 2050 kJ 6 + sugars 35 g 10 + saturates 10 g 9 + salt 0.625 g 3 = 28; fibre 0; score 28 (E).
        NutriGrade.Result r = NutriGrade.grade(food(490, 35.0, 10.0, 250.0, 2.0, 5.0));
        assertEquals(28, r.score);
        assertEquals(NutriGrade.Band.C, r.band);
    }

    /**
     * A label stating values per 100 ml routes to the drinks branch even when nothing named the
     * product, so a cola cannot escape onto the food scale where its sugar barely registers.
     */
    @Test public void aPerMillilitreLabelRoutesToTheDrinksBranch() {
        Nutrition cola = food(42, 10.6, 0.0, 10.0, null, null); cola.beverage = true;
        assertEquals(NutriGrade.Band.C, NutriGrade.grade(cola).band);
        Nutrition barelySweet = food(10, 2.0, 0.0, 0.0, null, null); barelySweet.beverage = true;
        assertEquals("2 g of sugar in a glass is genuinely little", NutriGrade.Band.A, NutriGrade.grade(barelySweet).band);
        Nutrition unsweetened = food(0, 0.0, 0.0, 0.0, null, null); unsweetened.beverage = true;
        assertEquals(NutriGrade.Band.A, NutriGrade.grade(unsweetened).band);

        // The same numbers as a food score only 3 (2023: sugars 10.6 g, 3 points; Nutri-Score C, yellow),
        // while the drink scale puts the cola in red: which is why the branches differ.
        Nutrition asFood = food(42, 10.6, 0.0, 10.0, null, null);
        assertEquals(NutriGrade.Band.B, NutriGrade.grade(asFood).band);
    }

    @Test public void missingNutrientsAreDisclosed() {
        // A low-sugar, low-sodium food with no saturated-fat figure: that figure alone could make it red, so the
        // colour is held back and the card names what is missing (see MissingFigureTest for the whole class).
        GradeResult r = GradeResult.graded(food(120, 2.0, null, 50.0, null, null), GradeResult.Source.LABEL_TEXT, null);
        assertNotEquals(GradeResult.State.GRADED, r.state);
        assertTrue(r.note, r.note.contains("saturated fat"));
    }
}
