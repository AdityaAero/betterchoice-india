package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The grader's 2023 tables against the report's own rows (Evidence ns2023.*, each verified in the PDF by
 * tools/evidence_check.py), plus worked examples of each rule.
 */
public class NutriScore2023Test {
    private static List<Double> numbers(String quote) {
        List<Double> out = new ArrayList<>();
        Matcher m = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(quote);
        while (m.find()) out.add(Double.parseDouble(m.group()));
        return out;
    }

    @Test public void generalTableMatchesTheReport() {
        for (int i = 1; i <= 20; i++) {
            List<Double> row = numbers(Evidence.get("ns2023.general.a." + i).quote);
            assertEquals(i, row.get(0), 0);
            int col = 1;
            if (i <= 10) { assertEquals("energy row " + i, NutriGrade.ENERGY_KJ[i - 1], row.get(col++), 0); }
            if (i <= 15) { assertEquals("sugars row " + i, NutriGrade.SUGARS[i - 1], row.get(col++), 0); }
            if (i <= 10) { assertEquals("saturates row " + i, NutriGrade.SAT_FAT[i - 1], row.get(col++), 0); }
            assertEquals("salt row " + i, NutriGrade.SALT_G[i - 1], row.get(col), 0);
        }
        for (int i = 1; i <= 7; i++) {
            List<Double> row = numbers(Evidence.get("ns2023.c." + i).quote);
            assertEquals("protein row " + i, NutriGrade.PROTEIN[i - 1], row.get(1), 0);
            if (i <= 5) assertEquals("fibre row " + i, NutriGrade.FIBRE[i - 1], row.get(2), 0);
        }
    }

    @Test public void fatsTableMatchesTheReport() {
        for (int i = 0; i <= 10; i++) {
            List<Double> row = numbers(Evidence.get("ns2023.fats.a." + i).quote);
            if (i >= 1) assertEquals("energy from saturates row " + i, NutriGrade.SAT_ENERGY_KJ[i - 1], row.get(1), 0);
            // Ratio column: "< 16" on row 1 is the bound of the next point; "≥ 64" on row 10 is the last threshold.
            assertEquals("ratio row " + i, NutriGrade.SAT_RATIO[Math.min(i, 9)], row.get(3), 0);
        }
    }

    private static Nutrition n(double kcal, double sugar, double sat, double fat, double sodium, double protein, double fibre) {
        Nutrition n = new Nutrition();
        n.energyKcal = kcal; n.sugarsG = sugar; n.saturatedFatG = sat; n.totalFatG = fat; n.sodiumMg = sodium;
        n.proteinG = protein; n.fibreG = fibre; n.basisStated = true;
        return n;
    }

    @Test public void workedExamples() {
        // Potato chips, 553 kcal, 0.6 g sugar, 15.8 g sat, 500 mg sodium (1.25 g salt), 6.7 g protein, 3 g fibre.
        // Energy 2314 kJ: 6; sugars 0; saturates 10; salt 1.25 g: 6 -> A = 22 (>= 11, protein off); fibre 0 -> 22: E.
        NutriGrade.Result chips = NutriGrade.grade(n(553, 0.6, 15.8, 35.1, 500, 6.7, 3.0), Beverage.NOT_A_DRINK);
        assertEquals(22, chips.score);
        assertEquals('E', chips.letter());
        assertEquals(NutriGrade.Band.C, chips.band);

        // Mustard oil: scored as a fat by its saturates share (about 12% of 100 g fat), not by its 900 kcal.
        NutriGrade.Result oil = NutriGrade.grade(n(900, 0, 12, 100, 0, 0, 0), Beverage.NOT_A_DRINK, NutriGrade.FoodKind.FATS_OILS_NUTS);
        NutriGrade.Result asGeneral = NutriGrade.grade(n(900, 0, 12, 100, 0, 0, 0), Beverage.NOT_A_DRINK);
        assertTrue("the fats table is kinder to a low-saturate oil", oil.score < asGeneral.score);
        // Energy from saturates 12 x 37 = 444 kJ: 3 points; ratio 12%: 1 point -> A = 4, C = 0 -> 4: C.
        assertEquals(4, oil.score);
        assertEquals('C', oil.letter());

        // Cheese keeps its protein even at 11 A points or more.
        Nutrition cheddar = n(400, 0.5, 20, 33, 700, 25, 0);
        NutriGrade.Result cheese = NutriGrade.grade(cheddar, Beverage.NOT_A_DRINK, NutriGrade.FoodKind.CHEESE);
        NutriGrade.Result general = NutriGrade.grade(cheddar, Beverage.NOT_A_DRINK);
        assertEquals(general.score - 7, cheese.score);

        // Red meat: at most 2 protein points.
        Nutrition mince = n(200, 0, 3, 10, 70, 20, 0);
        assertEquals(NutriGrade.grade(mince, Beverage.NOT_A_DRINK).score + 5,
            NutriGrade.grade(mince, Beverage.NOT_A_DRINK, NutriGrade.FoodKind.RED_MEAT).score);
    }

    @Test public void productsAreScoredWithTheRightTable() {
        assertEquals(NutriGrade.FoodKind.FATS_OILS_NUTS, FoodCategory.kind("Fortune Kachi Ghani Mustard Oil", null));
        assertEquals(NutriGrade.FoodKind.FATS_OILS_NUTS, FoodCategory.kind("Amul Pure Ghee", null));
        assertEquals(NutriGrade.FoodKind.FATS_OILS_NUTS, FoodCategory.kind("Pintola Peanut Butter Crunchy", null));
        assertEquals(NutriGrade.FoodKind.FATS_OILS_NUTS, FoodCategory.kind("Happilo California Almonds", null));
        assertEquals(NutriGrade.FoodKind.CHEESE, FoodCategory.kind("Amul Cheese Slices", null));
        assertEquals(NutriGrade.FoodKind.RED_MEAT, FoodCategory.kind("Licious Mutton Curry Cut", null));
        assertEquals(NutriGrade.FoodKind.GENERAL, FoodCategory.kind("Licious Chicken Breast", null));
        assertEquals(NutriGrade.FoodKind.GENERAL, FoodCategory.kind("Britannia Good Day Butter Cookies", null));
    }
}
