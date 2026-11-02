package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Food or drink, read in the right unit, and both sides of the story.
 *
 * <p>Pins down the failures reported on a real phone: a Cadbury Dairy Milk bar was scored as a
 * glass of milk "per 100 ml", ice cream labelled per 100 ml was treated as a drink, and a bag of
 * salted peanuts was shown only its sodium and saturated fat, never its protein, fibre or the
 * unsaturated kind of fat that makes up most of it.
 */
public class FoodFormTest {
    private static final String BLINKIT = "com.grofers.customerapp";
    private static final int HEIGHT = 2400;

    private static List<ScreenText> page(String title, String price, String... details) {
        List<ScreenText> screen = new ArrayList<>();
        screen.add(new ScreenText(title, 300, false));
        screen.add(new ScreenText(price, 340, false));
        screen.add(new ScreenText("Product Details", 600, true));
        int y = 640;
        for (String line : details) screen.add(new ScreenText(line, y += 20, false));
        screen.add(new ScreenText("ADD", 2200, true));
        return screen;
    }

    private static GradeResult analyze(List<ScreenText> screen) {
        return ProductAnalyzer.analyze(screen, HEIGHT, BLINKIT, p -> PackScan.EMPTY, null, null);
    }

    private static List<ScreenText> cadbury(String title) {
        return page(title, "₹20",
            "Net quantity: 1 pc (20.2 g)",
            "Nutrition Information (Per 100 g)",
            "Energy 531 kcal", "Protein 7.9 g", "Carbohydrate 59.6 g", "Total Sugars 57 g",
            "Added Sugars 44.8 g", "Total Fat 29.6 g", "Saturated Fat 19.6 g", "Trans Fat 0.1 g",
            "Sodium 104 mg",
            "Ingredients: Sugar, Milk Solids (24%), Cocoa Butter, Cocoa Solids, Emulsifiers (442, 476), Flavours",
            "Allergen information: Contains milk. May contain nuts, wheat and soy");
    }

    // --- Chocolate is a food, read per 100 g -------------------------------------------------------

    /** The reported bug, end to end: this used to come out "Milk, scored as a drink per 100 ml". */
    @Test public void aDairyMilkBarIsAFoodPer100Grams() {
        GradeResult result = analyze(cadbury("Cadbury Dairy Milk Chocolate Bar"));
        assertEquals(GradeResult.State.GRADED, result.state);
        assertNull("Scored as a drink: " + result.drinkKind, result.drinkKind);
        assertFalse(result.nutrition.beverage);
        assertEquals("100 g", result.nutrition.unit());
        assertEquals(20.2, result.nutrition.netQuantity, 0.001);
        assertEquals(NutriGrade.Band.C, result.band);
        assertTrue(result.concerns.toString(), result.concerns.contains("High sugar: 57 g per 100 g"));
        assertTrue(result.concerns.toString(), result.concerns.contains("High saturated fat: 19.6 g per 100 g"));
        for (String line : result.reasons) assertFalse("Wrong unit: " + line, line.contains("100 ml"));
        assertFalse("Its 7.9 g is only 6% of its energy, below any protein claim level: " + result.positives,
            result.positives.toString().contains("protein"));
    }

    /** Blinkit's screen-reader title starts "Product Name is"; the card printed it word for word. */
    @Test public void theSpokenTitlePrefixIsRemoved() {
        GradeResult result = analyze(cadbury("Product Name is Cadbury Dairy Milk Chocolate Bar"));
        assertEquals("Cadbury Dairy Milk Chocolate Bar", result.page.name);
        assertNull(result.drinkKind);
        assertEquals("Cadbury Dairy Milk", ProductPage.cleanTitle("Product name: Cadbury Dairy Milk"));
        assertEquals("Nameless Snacks Chips", ProductPage.cleanTitle("Nameless Snacks Chips"));
    }

    /** "chocolate" contains "cola"; "Dairy Milk" contains "milk". Neither makes it a drink. */
    @Test public void wordsInsideWordsNoLongerMakeADrink() {
        assertFalse(Beverage.classify("Cadbury Dairy Milk Chocolate 20 g", null, false).isDrink());
        assertFalse(Beverage.classify("Dairy Milk Silk Bubbly", null, false).isDrink());
        assertFalse(Beverage.classify("Dark Chocolate", "", true).isDrink());
        assertTrue(Beverage.classify("Coca-Cola Original 750 ml", null, false).isDrink());
        assertFalse("watermelon is not water", Beverage.classify("Watermelon Candy 50 g", null, false).isDrink());
    }

    /** A sweetener says nothing about being a drink: sugar-free chocolate is chocolate. */
    @Test public void sugarFreeChocolateIsStillAFood() {
        Beverage identity = Beverage.classify("Sugar Free Dark Chocolate 40 g",
            "Ingredients: cocoa solids, maltitol, sucralose (INS 955)", false);
        assertFalse(identity.isDrink());
        assertTrue(identity.definitelyFood);
    }

    /** Things with "milk" in the name that are eaten, not drunk. */
    @Test public void milkNamedFoodsAreFoods() {
        assertFalse(Beverage.classify("Britannia Milk Bikis 200 g", null, false).isDrink());
        assertFalse(Beverage.classify("Nestle Milkmaid Condensed Milk 400 g", null, false).isDrink());
        assertFalse("Cooking coconut milk is sold in ml and still eaten",
            Beverage.classify("Dabur Coconut Milk 200 ml", null, false).isDrink());
        assertFalse(Beverage.classify("Amul Butter 100 g", null, false).isDrink());
        assertFalse(Beverage.classify("Mother Dairy Milk Cake 250 g", null, false).isDrink());
    }

    /** And the ones that really are drinks, where the pack size breaks the tie. */
    @Test public void milkDrinksAreDrinks() {
        assertEquals(Beverage.Kind.SWEETENED, Beverage.classify("Hershey's Chocolate Milk 180 ml", null, false).kind);
        assertEquals(Beverage.Kind.SWEETENED, Beverage.classify("Amul Kool Chocolate Milkshake 200 ml", null, false).kind);
        assertTrue(Beverage.classify("Amul Masti Spiced Butter Milk 200 ml", null, false).isDrink());
        assertTrue(Beverage.classify("Bovonto Cream Soda 600 ml", null, false).isDrink());
        assertEquals(Beverage.Kind.MILK, Beverage.classify("Nandini Toned Milk 500 ml", null, false).kind);
        assertEquals("A pack sold in ml settles a bare name", Beverage.Kind.MILK,
            Beverage.classify("Heritage Milk", "Net Qty 500 ml", false).kind);
    }

    /** A drink noun on a pack sold by weight is a powder: scored as sold, and the card says so. */
    @Test public void aHealthDrinkPowderIsRatedAsTheDryProduct() {
        Beverage identity = Beverage.classify("Bournvita Chocolate Health Drink 500 g", null, false);
        assertFalse(identity.isDrink());
        assertTrue(identity.soldDry);
        Nutrition powder = new Nutrition();
        powder.energyKcal = 385.0; powder.sugarsG = 32.2; powder.saturatedFatG = 1.0; powder.sodiumMg = 150.0;
        powder.proteinG = 7.0; powder.basisStated = true;
        GradeResult result = GradeResult.graded(powder, GradeResult.Source.LABEL_TEXT, null, null, identity);
        assertNull(result.drinkKind);
        assertTrue(result.caveats.toString(), result.caveats.toString().contains("Rated as sold"));
    }

    // --- Ice cream is eaten, whatever unit the label uses ------------------------------------------

    private static final String[] ICE_CREAM_PANEL = {
        "Nutritional Information per 100 ml", "Energy 190 kcal", "Protein 3.5 g", "Total Sugars 20 g",
        "Total Fat 11 g", "Saturated Fat 7 g", "Sodium 60 mg"};

    @Test public void iceCreamPer100MlNeedsDensityBeforeFoodScoring() {
        List<String> lines = new ArrayList<>();
        lines.add("Net Qty 1 L");
        java.util.Collections.addAll(lines, ICE_CREAM_PANEL);
        GradeResult result = analyze(page("Amul Vanilla Magic Ice Cream Tub", "₹250", lines.toArray(new String[0])));
        assertEquals(GradeResult.State.NEEDS_INPUT, result.state);
        assertNull("Ice cream is not a drink", result.drinkKind);
        assertNull(result.band);
        assertTrue(result.note, result.note.contains("mass/volume"));
    }

    /** When the tub states both its weight and its volume, that ratio gives an honest per 100 g. */
    @Test public void iceCreamIsConvertedWhenThePackGivesWeightAndVolume() {
        List<String> lines = new ArrayList<>();
        lines.add("Net Vol 700 ml");
        lines.add("Net Wt 350 g");
        java.util.Collections.addAll(lines, ICE_CREAM_PANEL);
        GradeResult result = analyze(page("Kwality Wall's Frozen Dessert Chocolate Tub", "₹200", lines.toArray(new String[0])));
        assertNull(result.drinkKind);
        assertFalse(result.nutrition.beverage);
        assertEquals("100 g", result.nutrition.unit());
        assertEquals(380.0, result.nutrition.energyKcal, 0.01);
        assertEquals(40.0, result.nutrition.sugarsG, 0.01);
        assertEquals(350.0, result.nutrition.netQuantity, 0.01);
        assertTrue(result.caveats.toString(),
            result.caveats.toString().contains("using the pack's own 350 g in 700 ml"));
    }

    @Test public void conesKulfiAndSundaesAreFoods() {
        for (String name : new String[] {"Cornetto Double Chocolate Cone 105 ml", "Malai Kulfi 60 ml",
                "Havmor Butterscotch Ice Cream 700 ml", "Vadilal Frozen Dessert Mango 1 L"}) {
            Beverage identity = Beverage.classify(name, null, true);
            assertFalse(name, identity.isDrink());
            assertTrue(name, identity.definitelyFood);
        }
    }

    // --- Salted peanuts: the good side is said out loud ---------------------------------------------

    private static List<ScreenText> peanuts(String title) {
        return page(title, "₹99",
            "Net Qty 150 g",
            "Nutrition Information per 100 g",
            "Energy 590 kcal", "Protein 25.8 g", "Carbohydrate 16.1 g", "Total Sugars 4.2 g",
            "Dietary Fibre 8.4 g", "Total Fat 49.2 g", "Saturated Fat 7.4 g", "Trans Fat 0 g",
            "Sodium 540 mg",
            "Ingredients: Peanuts, Edible Vegetable Oil, Iodised Salt");
    }

    /**
     * Was red on the sodium and saturated fat alone. The published 2017 method credits nuts the way
     * it credits fruit and vegetables, and once a product is mostly nuts its protein counts too.
     */
    @Test public void saltedPeanutsShowTheirProteinFibreAndGoodFat() {
        GradeResult result = analyze(peanuts("Classic Roasted Salted Peanuts"));
        assertEquals(GradeResult.State.GRADED, result.state);
        assertEquals(NutriGrade.Band.B, result.band);
        // 540 mg is below the UK front-of-pack "high" (600 mg): it counts in the score, but the card
        // leads with what matters more for peanuts.
        assertFalse(result.concerns.toString(), result.concerns.toString().contains("sodium"));
        assertTrue(result.positives.toString(), result.positives.contains("Good protein: 25.8 g per 100 g"));
        assertTrue(result.positives.toString(), result.positives.contains("High fibre: 8.4 g per 100 g"));
        assertTrue(result.positives.toString(),
            result.positives.stream().anyMatch(s -> s.startsWith("Mostly unsaturated fat: 41.8 of 49.2 g")));
        // Nutri-Score 2023 scores nuts on the fats, oils, nuts and seeds table and no longer credits them
        // as fruit and vegetables: saturates 7.4 g x 37 = 274 kJ 2 + sugars 1 + saturates 15% of fat 1
        // + salt 1.35 g 6 = 10, fibre 8.4 g 5 -> 5, Nutri-Score C.
        assertFalse(result.positives.toString(), result.positives.toString().contains("nuts or pulses"));
        assertTrue(result.notes.toString(), result.notes.stream().anyMatch(s -> s.startsWith("Scored as a fat, oil, nut or seed")));
    }

    /** Masala peanuts are coated in batter: the name alone does not say how much is peanut. */
    @Test public void coatedPeanutsGetNoNutCreditButKeepTheirGoodSide() {
        GradeResult result = analyze(peanuts("Spicy Masala Coated Peanuts"));
        // 2023: processed peanuts sit in the nuts table too, and nuts earn no fruit-and-vegetable credit
        // either way; with the same label figures it scores as the plain peanuts (5, Nutri-Score C).
        assertEquals(NutriGrade.Band.B, result.band);
        assertFalse(result.positives.toString().contains("nuts or pulses"));
        assertTrue("The protein is still real: " + result.positives,
            result.positives.contains("Good protein: 25.8 g per 100 g"));
    }

    @Test public void aStatedNutShareIsBelievedAndAPartShareEarnsLess() {
        // Nutri-Score 2023 credits pulses, not nuts: a stated chana share counts, a stated peanut share does not.
        assertEquals(45, Beverage.classify("Chana Jor Garam 100 g", "Ingredients: Chana Dal (45%), Rice Bran Oil, Salt", false).fruitPercent);
        assertEquals(0, Beverage.classify("Peanut Chikki 100 g", "Ingredients: Jaggery, Peanuts (45%)", false).fruitPercent);
        assertEquals(1, NutriGrade.nutPoints(45));
        assertEquals(2, NutriGrade.nutPoints(65));
        assertEquals(5, NutriGrade.nutPoints(90));
        assertEquals(0, NutriGrade.nutPoints(40));
        assertEquals("Almonds earn no fruit-and-vegetable credit in 2023", 0,
            Beverage.classify("Fruit and Nut Chocolate 36 g", "Almonds (12%), raisins", false).fruitPercent);
    }

    // --- The good-side rules ---------------------------------------------------------------------

    private static Nutrition food(double kcal, Double protein, Double sugars, Double satFat, Double sodium) {
        Nutrition n = new Nutrition();
        n.energyKcal = kcal; n.proteinG = protein; n.sugarsG = sugars; n.saturatedFatG = satFat; n.sodiumMg = sodium;
        return n;
    }

    /** Protein must be both a real amount and a real share of the energy, as the claim rules require. */
    @Test public void proteinIsPraisedOnlyAtClaimLevels() {
        assertNull("Chocolate: 8 g but only 6% of energy", Highlights.protein(food(531, 7.9, 57.0, 19.6, 104.0), false));
        assertEquals("High protein", Highlights.protein(food(265, 18.3, 2.6, 13.0, 30.0), false));
        assertEquals("Good protein", Highlights.protein(food(590, 25.8, 4.2, 7.4, 540.0), false));
        assertNull("Too little to matter, however lean", Highlights.protein(food(20, 2.0, 0.0, 0.0, 5.0), false));
    }

    @Test public void nothingIsPraisedThatTheLabelDidNotDeclare() {
        Nutrition sparse = food(480, null, 30.0, 12.0, 600.0);
        List<String> good = Highlights.of(sparse, Beverage.NOT_A_DRINK, false);
        for (String line : good) {
            assertFalse(line, line.contains("protein") || line.contains("fibre") || line.contains("unsaturated"));
        }
        Nutrition noTotalFat = food(590, 25.8, 4.2, 7.4, 540.0);
        assertFalse(Highlights.of(noTotalFat, Beverage.NOT_A_DRINK, false).toString().contains("unsaturated"));
    }

    @Test public void theGoodSideNeverBuriesTheBand() {
        Nutrition everything = food(120, 12.0, 0.2, 0.3, 20.0);
        everything.fibreG = 7.0; everything.totalFatG = 5.0;
        assertTrue(Highlights.of(everything, Beverage.NOT_A_DRINK, false).size() <= Highlights.MAX_LINES);
    }

    // --- The label's fat lines ---------------------------------------------------------------------

    @Test public void totalAndTransFatAreReadWithoutConfusingSaturatedFat() {
        Nutrition n = NutritionParser.parse("Nutrition per 100 g\nEnergy 590 kcal\nTotal Fat 49.2 g\n"
            + "Saturated Fat 7.4 g\nTrans Fat 0 g\nTotal Sugars 4.2 g\nSodium 540 mg");
        assertNotNull(n);
        assertEquals(49.2, n.totalFatG, 0.001);
        assertEquals(7.4, n.saturatedFatG, 0.001);
        assertEquals(0.0, n.transFatG, 0.001);

        Nutrition plain = NutritionParser.parse("Per 100 g\nEnergy 531 kcal\nEnergy from fat 266 kcal\nFat 29.6 g\n"
            + "Saturated Fat 19.6 g\nSugars 57 g\nSodium 104 mg");
        assertNotNull(plain);
        assertEquals(29.6, plain.totalFatG, 0.001);

        Nutrition impossible = NutritionParser.parse("Per 100 g\nEnergy 531 kcal\nTotal Fat 5 g\n"
            + "Saturated Fat 19.6 g\nSugars 57 g\nSodium 104 mg");
        assertNull("Conflicting fat readings cannot establish which value is correct", impossible);
    }

    @Test public void theShopsPieceCountFormGivesTheWeight() {
        assertEquals(20.2, NutritionParser.netQuantity("Net quantity: 1 pc (20.2 g)"), 0.001);
        assertEquals("g", NutritionParser.netQuantityUnit("Net quantity: 1 pc (20.2 g)"));
        assertEquals(20.2, NutritionParser.netQuantity("Unit\n1 pc (20.2 g)"), 0.001);
        assertEquals(500.0, NutritionParser.netQuantity("Net Qty\n500 ml"), 0.001);
    }

    // --- Allergens are always listed ---------------------------------------------------------------

    /** A lactose warning used to hide the rest of the declaration. */
    @Test public void theDeclarationIsListedEvenWhenSomethingElseIsFlagged() {
        SafetyProfile lactose = SafetyProfile.of(SafetyProfile.Allergies.NONE_KNOWN, null, SafetyProfile.Severity.UNKNOWN, "");
        lactose.conditions.add("lactose");
        SafetyGate.Result result = SafetyGate.check("Sugar, milk solids, cocoa butter",
            "Contains milk. May contain nuts and soy", lactose);
        assertEquals(SafetyGate.Verdict.CAUTION, result.verdict);
        String said = String.join(" | ", result.messages);
        assertTrue(said, said.contains("lactose"));
        assertTrue(said, said.contains("May contain:"));
        assertTrue(said, said.contains("Contains:"));
    }

    // --- Units follow the label ---------------------------------------------------------------------

    @Test public void aDrinksServingIsPrintedInMillilitres() {
        Nutrition milk = new Nutrition();
        milk.energyKcal = 58.0; milk.sugarsG = 4.8; milk.saturatedFatG = 1.9; milk.sodiumMg = 45.0;
        milk.proteinG = 3.2; milk.beverage = true; milk.basisStated = true;
        DailyImpact serving = DailyImpact.of(milk, DailyNeeds.generic(), 200, false);
        assertTrue(serving.headline(), serving.headline().contains("200 ml"));
    }

    @Test public void aDrinkLabelledPer100GramsSaysSoInsteadOfPretending() {
        Nutrition juice = new Nutrition();
        juice.energyKcal = 45.0; juice.sugarsG = 9.0; juice.saturatedFatG = 0.0; juice.sodiumMg = 1.0;
        juice.beverage = false;
        juice.basisStated = true;   // the source says per 100 g, as Open Food Facts does
        GradeResult result = GradeResult.graded(juice, GradeResult.Source.OPEN_FOOD_FACTS, null, null,
            Beverage.classify("Tropicana 100% Orange Juice 1 L", null, false));
        assertEquals(GradeResult.State.NEEDS_INPUT, result.state);
        assertNull(result.band);
        assertTrue(result.note, result.note.contains("mass/volume"));
    }
}
