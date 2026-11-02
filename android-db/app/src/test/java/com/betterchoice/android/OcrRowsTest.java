package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class OcrRowsTest {
    @Test public void lostDecimalCannotTurnCornFlakesIntoConflictingFatRating() {
        assertNull(NutritionParser.parse("Per 100 g\nEnergy 378 kcal\nTotal Fat 1.0 g\n"
            + "Saturated fatty acids 6 g\nTotal Sugars 9.2 g\nDietary Fibre 25 g\nProtein 6.7 g\nSodium 490 mg"));
    }
    @Test public void realButterOcrDoesNotReadUnsaltedShelfLifeAsSalt() {
        String text = "Nutritional Information\n10 servings per container\nServing size 10 g\n"
            + "Amount per 100 g* %RDA#\nEnergy (kcal) 740 4%\nTotal Fat (g) 82.0 12%\n"
            + "Saturated Fat (g)49.0 22%\nTrans Fat (g) 0.0 0%\nCholesterol (mg)225\n"
            + "Carbohydrate (g) 0.0\nTotal Sugars (g) 0.0\nAdded Sugars (g) 0.0 0%\n"
            + "Protein (g) 0.6\nSodium (mg) 0%\nRDA stands for Recommended\n"
            + "Dietary Allowance per serving\nAmul Unsalted Butter\nFlavour Shelf Life\nUnsalted 180 days";
        Nutrition n = NutritionParser.parse(text);
        assertNotNull(n);
        assertEquals(740, n.energyKcal, .001);
        assertEquals(49, n.saturatedFatG, .001);
        assertEquals(0, n.sugarsG, .001);
        assertNull("OCR lost the sodium amount; a percentage is not its value", n.sodiumMg);
        assertEquals(.6, n.proteinG, .001);
    }

    @Test public void columnMajorButterPanelBecomesReadableRowsWithoutRdaAsAmounts() {
        String[] labels = {"Energy (kcal)", "Total Fat (g)", "Saturated Fat (g)", "Total Sugars (g)", "Protein (g)", "Sodium (mg)"};
        String[] values = {"740", "82.0", "49.0", "0.0", "0.6", "0"};
        List<OcrRows.Fragment> fragments = new ArrayList<>();
        fragments.add(new OcrRows.Fragment("Amount per 100 g", 0, 0, 200, 18));
        for (int i = 0; i < labels.length; i++) fragments.add(new OcrRows.Fragment(labels[i], 0, 30 + i * 25, 180, 48 + i * 25));
        for (int i = 0; i < values.length; i++) fragments.add(new OcrRows.Fragment(values[i], 200, 31 + i * 25, 240, 49 + i * 25));
        fragments.add(new OcrRows.Fragment("22%", 270, 81, 310, 99));
        Nutrition n = NutritionParser.parse(OcrRows.assemble(fragments));
        assertNotNull(n);
        assertEquals(740, n.energyKcal, .001);
        assertEquals(49, n.saturatedFatG, .001);
        assertEquals(0, n.sugarsG, .001);
        assertEquals(.6, n.proteinG, .001);
        assertEquals(0, n.sodiumMg, .001);
    }

    @Test public void verticallyDifferentAndOverlappingLinesStaySeparate() {
        String text = OcrRows.assemble(List.of(
            new OcrRows.Fragment("Ingredients:", 0, 0, 100, 20),
            new OcrRows.Fragment("Milk, Soy", 0, 22, 100, 42),
            new OcrRows.Fragment("49", 200, 55, 220, 75)));
        assertFalse(text.contains("Ingredients: Milk"));
        assertFalse(text.contains("Milk, Soy 49"));
    }
}
