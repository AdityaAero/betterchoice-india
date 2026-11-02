package com.betterchoice.android;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Zepto product pages as the phone showed them on 27 September 2026 (Codex's audit screenshots
 * zepto-oats-*.png and zepto-icecream-*.png). Every row is Zepto's own "Highlights" layout: a label
 * line, then its value line, with no "per 100 g" anywhere.
 */
public class ZeptoRegressionTest {
    static final String ZEPTO = "com.zeptoconsumerapp";
    static final int HEIGHT = 2392;

    private static List<ScreenText> screen(Object... rows) {
        List<ScreenText> items = new ArrayList<>();
        for (int i = 0; i < rows.length; i += 2) items.add(new ScreenText((String) rows[i], (Integer) rows[i + 1], false));
        return items;
    }

    /** Quaker oats, details expanded, with the "Similar Products" rail below. */
    static List<ScreenText> oats() {
        return screen(
            "Quaker Rolled Instant Oats | High Protein Breakfast Cereal", 162, "₹85", 210, "₹86", 210,
            "No return or replacement", 473, "Fast Delivery", 473,
            "Highlights", 697, "Brand", 797, "Quaker", 800, "Product Type", 886, "Oats", 890,
            "Dietary Preference", 975, "Veg", 980, "Flavour", 1098, "Plain", 1103,
            "Key Features", 1187,
            "High in protein and dietary fiber, Cooks in just 3 minutes, Helps reduce cholesterol naturally", 1193,
            "Weight", 1383, "400 g", 1388, "Ingredients", 1473, "Jumbo Rolled Oats", 1477,
            "Unit", 1561, "1 pack (400 g)", 1566,
            "Allergen Information", 1651, "May contain: Barley and Wheat", 1655,
            "Fssai License", 1775, "10014064000435", 1779,
            "Nutrition Information", 1864,
            "Energy (kcal) 407.0, Protein (g) 11.8, Total Fat (g) 9.5, Carbohydrate (g) 68.5, Total Dietary "
                + "Fibre (g) 10.0, Iron (mg) 2.9, Magnesium (mg) 106.0, Sodium (mg) 9.5, Zinc (mg) 2.0", 1868,
            "Information", 2050,
            "Similar Products", 2150,
            "ADD", 2250, "ADD", 2250, "ADD", 2250, "Add to Cart", 2245,
            "₹186", 2280, "₹210", 2280, "₹24 OFF", 2300,
            "Quaker Rolled Instant Oats | High Protein Breakfast Cereal", 2330, "1 pack (1 kg)", 2380,
            "₹107", 2280, "₹200", 2280, "₹93 OFF", 2300,
            "Yoga Bar 26g High Protein Oats | Dark Chocolate | No Added Sugar", 2330, "1 pack (250 g)", 2380,
            "₹445", 2280, "Yoga Bar 23g Protein Oats | Almond & Dark Chocolate", 2330, "1 pack (1 kg)", 2380);
    }

    /** Amul Vanilla Magic, a 1 L tub, "View more" expanded. */
    static List<ScreenText> iceCream() {
        return screen(
            "Amul Vanilla Magic Ice Cream Tub | Creamy Delight", 162, "₹194", 210, "₹195", 210,
            "Flavour", 278, "Vanilla", 282, "Item Form", 367, "Cream", 371, "Weight", 455, "1 L", 459,
            "Item Included", 544, "1 x Ice cream tub", 548, "Unit", 633, "1 pack (1 L)", 637,
            "Key Features", 722,
            "Made with real milk, smooth velvety texture, classic vanilla flavour, perfect dessert for all occasions", 727,
            "Nutrition Information", 970,
            "Energy (kcal) 215.0 (RDA 6%), Total Fat (g) 13.0 (RDA 10%), Saturated Fat (g) 7.8 (RDA 19%), "
                + "Trans Fat (g) 0.5 (RDA 13%), Cholesterol (mg) 46.0, Carbohydrate (g) 20.8, Total Sugars (g) 20.8, "
                + "Added Sugars (g) 15.0 (RDA 16%), Protein (g) 3.7, Sodium (mg) 52.0 (RDA 1%)", 975,
            "Storage Instruction", 1485, "Always keep frozen at -18°C or below", 1490,
            "Packaging Type", 1628, "Tub", 1632, "Fssai License", 1717, "10012021000071", 1721,
            "Variant", 1806, "Vanilla", 1810, "View less", 1915, "Information", 2077);
    }

    @Test public void oatsReadOnlyTheTableNotKeyFeaturesOrTheRail() {
        ProductPage page = ProductPage.detect(oats(), HEIGHT, ZEPTO);
        assertNotNull(page);
        assertEquals("Quaker Rolled Instant Oats | High Protein Breakfast Cereal", page.name);
        assertEquals(85.0, page.price, 0.001);
        assertFalse("rail product leaked into the page text", page.text.contains("Yoga Bar"));
        Nutrition n = NutritionParser.parse(page.text);
        // Not usable: the page gives no sugar and no saturated fat. The old reading invented 1 g of
        // sugar from "No Added Sugar / 1 pack (250 g)" and 3 g of fibre from "Cooks in just 3 minutes".
        assertNull(n);
    }

    @Test public void oatsTableValuesAreTheTablesOwn() {
        String text = ProductPage.detect(oats(), HEIGHT, ZEPTO).text + "Total Sugars (g) 1.8\nSaturated Fat (g) 1.9\n";
        Nutrition n = NutritionParser.parse(text);
        assertNotNull(n);
        assertEquals(407, n.energyKcal, 0.001);
        assertEquals(10.0, n.fibreG, 0.001);
        assertEquals(11.8, n.proteinG, 0.001);
        assertEquals(9.5, n.sodiumMg, 0.001);
        assertEquals(9.5, n.totalFatG, 0.001);
        assertEquals(1.8, n.sugarsG, 0.001);
        assertNull("no added-sugar row on this page", n.addedSugarsG);
        assertEquals(400, n.netQuantity, 0.001);
        assertEquals("g", n.netUnit);
        assertFalse(n.basisStated);
    }

    @Test public void oatsAreNeverSaidToContainTreeNuts() {
        ProductPage page = ProductPage.detect(oats(), HEIGHT, ZEPTO);
        LabelEvidence label = LabelEvidence.of(page.text);
        assertEquals("Ingredients: Jumbo Rolled Oats", label.ingredients());
        SafetyGate.Result safety = SafetyGate.check(label.ingredients(), label.allergens(), SafetyProfile.unknown());
        assertFalse(safety.scan.mentions(Allergen.TREE_NUT));
        // Oats are one of the cereals FSSAI counts as containing gluten; barley and wheat are traces.
        assertTrue(safety.scan.containsAsIngredient(Allergen.GLUTEN));
    }

    @Test public void singleIngredientOatsAreRecognised() {
        String section = IngredientFacts.section(ProductPage.detect(oats(), HEIGHT, ZEPTO).text);
        assertEquals("Jumbo Rolled Oats", section);
        IngredientFacts facts = IngredientFacts.of(section, false);
        assertTrue(facts.good.toString(), facts.good.contains("Just one ingredient: jumbo rolled oats"));
        assertTrue(facts.good.contains("Oats are the main ingredient"));
    }

    @Test public void oatsWaitForAFullPanelInsteadOfGuessing() {
        GradeResult r = ProductAnalyzer.analyze(oats(), HEIGHT, ZEPTO, ProductAnalyzerTest.noPack(),
            ProductAnalyzerTest.noDatabase(), null);
        assertEquals(GradeResult.State.NEEDS_INPUT, r.state);
    }

    /** Founder's decision (27 Sep): an unstated basis is rated, the nutrients shown, and the card says so. */
    @Test public void iceCreamWithAnUnstatedBasisIsRatedAndSaysTheBasisIsUnknown() {
        GradeResult r = ProductAnalyzer.analyze(iceCream(), HEIGHT, ZEPTO, ProductAnalyzerTest.noPack(),
            ProductAnalyzerTest.noDatabase(), null);
        assertEquals(GradeResult.State.GRADED, r.state); assertNotNull(r.band);
        assertFalse(r.nutrition.basisStated);
        assertTrue(r.caveats.toString(), r.caveats.toString().contains("per 100 g or per 100 ml"));
        assertTrue("a 1 L tub with no weight has no whole-pack amount", r.caveats.toString().contains("whole-pack amounts are left out"));
        assertNull(DailyImpact.forPack(r.nutrition, DailyNeeds.generic()));
        Nutrition n = NutritionParser.parse(r.page.text);
        assertNotNull(n); assertFalse(n.basisStated);
        assertEquals(215, n.energyKcal, .001); assertEquals(20.8, n.sugarsG, .001);
        assertEquals(15, n.addedSugarsG, .001); assertEquals(7.8, n.saturatedFatG, .001);
        assertEquals(52, n.sodiumMg, .001); assertEquals(3.7, n.proteinG, .001);
        assertNull(n.packAmount()); assertNull(DailyImpact.forPack(n, DailyNeeds.generic()));
    }

    @Test public void aPackPhotoThatStatesItsBasisOutranksAnUnstatedPageTable() {
        String photo = "Nutrition Information Per 100 g\nEnergy 190 kcal\nTotal Sugars 18 g\nSaturated Fat 6 g\nSodium 60 mg";
        GradeResult r = ProductAnalyzer.analyze(iceCream(), HEIGHT, ZEPTO, page -> new PackScan(photo, null),
            ProductAnalyzerTest.noDatabase(), null);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals(GradeResult.Source.PACK_PHOTO, r.source);
        assertTrue(r.nutrition.basisStated);
        assertEquals(190, r.nutrition.energyKcal, 0.001);
        assertFalse(r.caveats.toString().contains("per 100 g or per 100 ml"));
    }

    @Test public void iceCreamWithWeightAndVolumeKeepsAWholePackInGrams() {
        Nutrition label = NutritionParser.parse("Energy (kcal) 215.0\nTotal Sugars (g) 20.8\nSaturated Fat (g) 7.8\n"
            + "Sodium (mg) 52.0\nNet Vol 1 L\nNet Wt 520 g");
        Nutrition settled = LabelBasis.settle(label, Beverage.classify("Vanilla Ice Cream Tub", "", false),
            "Net Vol 1 L\nNet Wt 520 g");
        assertEquals(520, settled.packAmount(), 0.001);
    }

    @Test public void statedPerHundredGramsStaysQuiet() {
        Nutrition label = NutritionParser.parse("Nutrition per 100 g\nEnergy 407 kcal\nSugars 1.8 g\n"
            + "Saturated fat 1.9 g\nSodium 9.5 mg\nNet weight 400 g");
        Nutrition settled = LabelBasis.settle(label, Beverage.NOT_A_DRINK, "");
        assertNull(settled.basisNote);
        assertEquals(400, settled.packAmount(), 0.001);
    }

    @Test public void unstatedBasisOnAFoodIsSaid() {
        Nutrition label = NutritionParser.parse("Energy (kcal) 407.0\nTotal Sugars (g) 1.8\nSaturated Fat (g) 1.9\nSodium (mg) 9.5");
        Nutrition settled = LabelBasis.settle(label, Beverage.NOT_A_DRINK, "");
        assertTrue(settled.basisNote, settled.basisNote.contains("couldn't find out whether these figures are per 100 g or per 100 ml"));
        assertTrue(settled.basisNote.contains("Rated as per 100 g"));
    }

    @Test public void aMissingNutrientThatCouldChangeTheBandIsDetected() {
        Nutrition oats = new Nutrition();
        oats.energyKcal = 407.0; oats.sugarsG = 1.8; oats.sodiumMg = 9.5; oats.fibreG = 10.0; oats.proteinG = 11.8;
        oats.totalFatG = 9.5;
        // Saturated fat not given: at zero it is A; at the 9.5 g the total fat allows it would not be.
        assertFalse(NutriGrade.bandSettled(oats, Beverage.NOT_A_DRINK));
        oats.totalFatG = 1.0;
        assertTrue(NutriGrade.bandSettled(oats, Beverage.NOT_A_DRINK));
        oats.saturatedFatG = 1.9;
        assertTrue(NutriGrade.bandSettled(oats, Beverage.NOT_A_DRINK));
    }

    @Test public void packSizeSeenUnderTheTitleIsKeptForTheTableBelow() {
        LabelEvidence facts = new LabelEvidence();
        facts.observe("Uncle Chipps Spicy Treat | Crunchy Potato Chips\nNet quantity: 1 pack (48.1 g)\n₹19");
        String tableScreen = "Nutrition Information\nEnergy (kcal) 544, Total Sugars (g) 2.1, Saturated Fat (g) 15.9, Sodium (mg) 718";
        Nutrition n = NutritionParser.parse(tableScreen + "\n" + facts.panel());
        assertEquals(48.1, n.packAmount(), 0.001);
    }

    @Test public void aFullScreenPhotoKeepsTheProductItBelongsTo() {
        ProductPage oats = ProductPage.detect(oats(), HEIGHT, ZEPTO);
        List<ScreenText> viewer = screen("1/6", 120, "Close", 140);
        ProductPage page = ProductPage.detect(viewer, HEIGHT, ZEPTO, null, null, oats);
        assertNotNull(page);
        assertEquals(oats.name, page.name);
        assertEquals(oats.price, page.price);
        // Without a product just left, the same screen identifies nothing.
        assertNull(ProductPage.detect(viewer, HEIGHT, ZEPTO, null, null, null));
    }

    @Test public void aRealPageIsNeverMistakenForAPhotoViewer() {
        ProductPage oats = ProductPage.detect(oats(), HEIGHT, ZEPTO);
        // A new product page after a window change is identified from its own text.
        ProductPage next = ProductPage.detect(iceCream(), HEIGHT, ZEPTO, null, null, oats);
        assertEquals("Amul Vanilla Magic Ice Cream Tub | Creamy Delight", next.name);
        // A short results screen with prices is not a photo viewer either.
        assertFalse(ProductPage.photoViewer(screen("Chips", 300, "₹20", 400, "ADD", 420)));
        assertFalse(ProductPage.photoViewer(screen("Search for chips", 100, "Close", 140)));
    }

    /**
     * Zepto's newer layout (Cadbury Bournville 70%, recorded 27 September): a SUMMARY line, a second
     * "View More" that opens a label/value table, and product cards with no rail heading.
     */
    static List<ScreenText> bournville() {
        List<ScreenText> items = screen(
            "Product Name is  Cadbury Bournville Rich Cocoa 70% Dark Chocolate Bar", 141,
            "Cadbury Bournville Rich Cocoa 70% Dark Chocolate Bar", 141, "₹125", 189, "₹135", 189,
            "This product has", 942, "598 calories", 942, "7.6g protein", 942, "40.7g carbs", 1020,
            "47.4g fats", 1020, "in every 100g serve.", 1020,
            "Calories", 1153, "598kcal", 1153, "Protein", 1239, "7.6g", 1239, "Carbohydrates", 1325, "40.7g", 1325,
            "Total Sugar", 1392, "24.4g", 1392, "Added Sugar", 1458, "23.3g", 1458, "Fats", 1543, "47.4g", 1543,
            "Saturated Fat", 1610, "32.1g", 1610, "Trans Fat", 1676, "0.1g", 1676, "Others", 1762,
            "Cholesterol", 1829, "4.4mg", 1829, "Sodium", 1895, "18mg", 1895);
        items.add(new ScreenText("View Less", 2011, true));
        items.add(new ScreenText("₹50, Yoga Bar Chocolate Chunk Multigrain Energy Bar, With Millets, Chocolate, "
            + "Almonds, High Dietary Fibre, 1 pc (35 g), ₹32", 2100, true));
        items.add(new ScreenText("Yoga Bar Chocolate Chunk Multigrain Energy Bar", 2250, false));
        items.add(new ScreenText("High Dietary Fibre 6 g", 2300, false));
        return items;
    }

    @Test public void zeptoLabelValueTableIsReadAndCardsAreLeftOut() {
        ProductPage page = ProductPage.detect(bournville(), HEIGHT, ZEPTO);
        assertNotNull(page);
        assertFalse(page.text.contains("Yoga Bar"));
        Nutrition n = NutritionParser.parse(page.text);
        assertNotNull(n);
        assertTrue("\"in every 100g serve\" states the basis", n.basisStated);
        assertEquals(598, n.energyKcal, 0.001);
        assertEquals(24.4, n.sugarsG, 0.001);
        assertEquals(23.3, n.addedSugarsG, 0.001);
        assertEquals(32.1, n.saturatedFatG, 0.001);
        assertEquals(47.4, n.totalFatG, 0.001);
        assertEquals(0.1, n.transFatG, 0.001);
        assertEquals(18, n.sodiumMg, 0.001);
        assertEquals(7.6, n.proteinG, 0.001);
        assertNull("no fibre row for this bar", n.fibreG);
    }

    // --- found in the 60-product Zepto audit, 27 September ---------------------------------------------

    /** Paper Boat Zero Prebiotic Soda: "5g Fibre" in the name, 1.7 g in the table, sweeteners by number. */
    static List<ScreenText> paperBoat() {
        return screen(
            "Product Name is  Paper Boat Zero Prebiotic Soda Cranberry Lime, 5g Fibre, Caffeinated Sugar Free Cold Drink", 141,
            "₹49", 189, "1 pc (300 ml)", 300, "3.82 calories", 540, "0g protein", 540, "0g fats", 618,
            "in every 100ml serve.", 618, "Ingredients", 700,
            "Carbonated Water, Sweeteners (968, 955), Fibre (Resistant Dextrin), Flavours (Natural and Nature-Identical Flavouring Substances)", 705,
            "Calories", 839, "3.82kcal", 839, "Protein", 925, "0g", 925, "Dietary Fiber", 1078, "1.7g", 1078,
            "Total Sugar", 1144, "0g", 1144, "Added Sugar", 1211, "0g", 1211, "Fats", 1296, "0g", 1296,
            "Sodium", 1449, "10mg", 1449);
    }

    @Test public void aTableRowOutranksANutrientClaimInTheName() {
        GradeResult r = ProductAnalyzer.analyze(paperBoat(), HEIGHT, ZEPTO, ProductAnalyzerTest.noPack(),
            ProductAnalyzerTest.noDatabase(), null);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertEquals(1.7, r.nutrition.fibreG, 0.001);
        assertEquals("100 ml", r.nutrition.unit());
        // A can of soda, not a dry powder: "5g Fibre" is a claim, not the pack size.
        assertEquals(Beverage.Kind.SWEETENER_ONLY, r.drinkKind);
    }

    @Test public void sizesThatAreNutrientClaimsAreNotPackSizes() {
        assertEquals("ml", Beverage.packUnit("Paper Boat Zero Prebiotic Soda Cranberry Lime, 5g Fibre", "1 pc (300 ml)"));
        assertNull(Beverage.packUnit("Yoga Bar 26g High Protein Oats", ""));
        assertEquals("g", Beverage.packUnit("Yoga Bar 26g High Protein Oats 400 g", ""));
    }

    @Test public void aTitleWithCommasIsStillTheTitle() {
        List<ScreenText> muesli = screen(
            "Yoga Bar Muesli Dark Chocolate + Cranberry, Omega - 3, Nuts & Seeds, Fibre Rich", 1188,
            "1 pack (700 g)", 1260, "₹347", 1320, "Highlights", 1500);
        ProductPage page = ProductPage.detect(muesli, HEIGHT, ZEPTO);
        assertEquals("Yoga Bar Muesli Dark Chocolate + Cranberry, Omega - 3, Nuts & Seeds, Fibre Rich", page.name);
        assertTrue(ProductPage.listLike("sugar, milk solids (25%), coffee (13%), hydrogenated vegetable oil"));
    }

    @Test public void transfatAsOneWordIsRead() {
        Nutrition n = NutritionParser.parse("Energy (kcal) 391, Total Sugar (g) 18.4, Saturated fatty acids (g) 1.5, "
            + "Transfat (g) 0.0, Sodium (mg) 110.5");
        assertEquals(0.0, n.transFatG, 0.001);
        assertEquals(1.5, n.saturatedFatG, 0.001);
    }

    @Test public void aTableSplitAcrossTwoScreensIsReadWhole() {
        LabelEvidence facts = new LabelEvidence();
        String upper = "in every 100ml serve.\nCalories\n40kcal\nProtein\n0g\nTotal Sugar\n10g";
        String lower = "Fats\n0g\nSodium\n1.1mg";
        facts.observe(upper);
        facts.observe(lower);
        Nutrition n = NutritionParser.parse(lower + "\n" + facts.panel());
        assertNotNull(n);
        assertEquals(40, n.energyKcal, 0.001);
        assertEquals(10, n.sugarsG, 0.001);
        assertEquals(1.1, n.sodiumMg, 0.001);
        assertTrue(n.basisStated);
        assertEquals("100 ml", n.unit());
    }

    @Test public void aShortNameIsTakenFromTheShopsOwnSummaryNotALink() {
        List<ScreenText> lassi = screen(
            "Amul Lassi, 1 pack (200 ml), MRP is  ₹20, Price with Zepto Now is  ₹19, Rating is  4.5", 1116,
            "Net quantity:", 1254, "1 pack (200 ml)", 1254, "View all Amul products", 1400, "Highlights", 1600);
        ProductPage first = ProductPage.detect(lassi, HEIGHT, ZEPTO);
        assertEquals("Amul Lassi", first.name);
        assertEquals(19.0, first.price, 0.001);
        // Scrolled on: the labelled title keeps the same product and the same key.
        List<ScreenText> next = screen("Product Name is  Amul Lassi", 141, "₹19", 189, "₹20", 189, "Ingredients", 900, "Milk", 905);
        ProductPage second = ProductPage.detect(next, HEIGHT, ZEPTO, null, first);
        assertEquals(first.key(), second.key());
        // Even a wrong earlier name gives way to the shop's labelled title.
        ProductPage wrong = ProductPage.of("View all Amul products", 19.0, ZEPTO);
        assertEquals("Amul Lassi", ProductPage.detect(next, HEIGHT, ZEPTO, null, wrong).name);
    }

    @Test public void aRememberedListEndsWhereItEnds() {
        LabelEvidence facts = new LabelEvidence();
        facts.observe("Ingredients\nGreen tea, natural flavouring substances\nHighlights");
        String later = facts.text() + "\nBack\nSearch\nShare\nProduct Name is  Lipton Lemongrass Ginger Green Tea\n"
            + "Total Sugar\n0g\nNutrition";
        assertEquals("Green tea, natural flavouring substances", IngredientFacts.section(later));
        assertTrue(Brew.plain(IngredientFacts.section(later)));
    }

    @Test public void teaWithSaltIsNotPlainTea() {
        assertFalse(Brew.plain("Green Tea, Jeera, Rock Salt, Lime"));
    }

    @Test public void theSellingPriceSplitInTwoIsTheOneRead() {
        List<ScreenText> top = screen("Farmley Prasadam Makhana - Fit For Fasting | Foxnuts", 1157, "1 pack (100 g)", 1283,
            "₹", 1367, "183", 1367, "₹299", 1437, "MRP", 1441, "Highlights", 1600);
        ProductPage first = ProductPage.detect(top, HEIGHT, ZEPTO);
        assertEquals(183.0, first.price, 0.001);
        // Scrolled to the rails: the sticky header still names it, and it is the same product and key.
        List<ScreenText> lower = new ArrayList<>(screen("Product Name is  Farmley Prasadam Makhana - Fit For Fasting | Foxnuts", 141,
            "₹183", 189, "₹299", 189));
        for (int i = 0; i < 10; i++) lower.add(new ScreenText("ADD", 900 + i * 100, false));
        ProductPage second = ProductPage.detect(lower, HEIGHT, ZEPTO, null, first);
        assertNotNull("a labelled product page is never a listing", second);
        assertEquals(first.key(), second.key());
    }

    // --- parser rules, one each --------------------------------------------------------------------

    @Test public void aNumberRunningIntoAWordIsNotAValue() {
        assertNull(NutritionParser.parse("Energy 450 kcal\nSaturated fat 2 g\nSodium 200 mg\nNo Added Sugar\n1 pack (250 g)").sugarsG);
    }

    @Test public void aSentenceIsNotATableCell() {
        Nutrition n = NutritionParser.parse("High in dietary fiber, cooks in just 3 g servings\n"
            + "Energy 400 kcal\nSugars 2 g\nSodium 10 mg\nDietary Fibre 10 g");
        assertEquals(10, n.fibreG, 0.001);
    }

    @Test public void labelledEnergyRowOutranksAMarketingCalorieCount() {
        Nutrition n = NutritionParser.parse("Only 99 kcal a serve!\nEnergy (kcal) 480\nSugars (g) 20\nSodium (mg) 300");
        assertEquals(480, n.energyKcal, 0.001);
    }

    @Test public void bracketUnitRowsAreReadAsOwnValues() {
        Nutrition n = NutritionParser.parse("Yoga Bar 20g Protein Bar\nEnergy (kcal) 380\nProtein (g) 33.3\n"
            + "Total Sugars (g) 5\nSodium (mg) 120");
        assertEquals(33.3, n.proteinG, 0.001);
    }

    // --- allergens -----------------------------------------------------------------------------------

    @Test public void anIngredientOutranksATraceOfTheSameAllergen() {
        Allergen.Scan scan = Allergen.scan("Ingredients: Wheat flour, sugar, palm oil", "May contain: barley");
        assertTrue(scan.containsAsIngredient(Allergen.GLUTEN));
        assertFalse(scan.traces.contains(Allergen.GLUTEN));
    }

    @Test public void repeatedTraceStatementsStayTraces() {
        Allergen.Scan scan = Allergen.scan("Ingredients: Sugar, cocoa butter, milk solids",
            "May contain: tree nuts\nAllergen Information\nMay contain: tree nuts");
        assertTrue(scan.traces.contains(Allergen.TREE_NUT));
        assertFalse(scan.containsAsIngredient(Allergen.TREE_NUT));
        assertTrue(scan.containsAsIngredient(Allergen.MILK));
    }

    @Test public void noListMeansNoInformationForSomeoneWithAllergies() {
        ProductPage page = ProductPage.detect(iceCream(), HEIGHT, ZEPTO);
        LabelEvidence label = LabelEvidence.of(page.text);
        SafetyProfile allergic = SafetyProfile.of(SafetyProfile.Allergies.KNOWN, List.of(Allergen.PEANUT),
            SafetyProfile.Severity.SEVERE, "");
        SafetyGate.Result safety = SafetyGate.check(label.ingredients(), label.allergens(), allergic);
        assertTrue(safety.codes.contains("no_allergen_information"));
        assertTrue(safety.blocked());
    }

    @Test public void allergenRowValueOnTheNextLineIsRead() {
        LabelEvidence label = LabelEvidence.of("Allergen Information\nMilk, Soy\nFssai License\n123");
        Allergen.Scan scan = Allergen.scan(label.ingredients(), label.allergens());
        assertTrue(scan.containsAsIngredient(Allergen.MILK));
        assertTrue(scan.containsAsIngredient(Allergen.SOY));
    }

    // --- tea, coffee, chocolate ----------------------------------------------------------------------

    @Test public void plainTeaDoesNotInventThePreparedCupsComposition() {
        List<ScreenText> tea = screen("Tata Tea Premium | Assam Tea Leaves", 162, "₹270", 210,
            "Highlights", 700, "Weight", 800, "500 g", 804, "Ingredients", 890, "Tea", 894,
            "Nutrition Information", 980, "Energy (kcal) 312.0, Protein (g) 20.0, Total Fat (g) 1.0", 985);
        GradeResult r = ProductAnalyzer.analyze(tea, HEIGHT, ZEPTO, ProductAnalyzerTest.noPack(),
            ProductAnalyzerTest.noDatabase(), null);
        assertEquals(GradeResult.State.NEEDS_INPUT, r.state);
        assertNull(r.band); assertNull(r.source);
        assertTrue(LabelGuide.category(r.page).contains("prepared drink"));
    }

    @Test public void coffeePremixNeedsLabelBasisAndFinalVolumeBeforeCupCalculation() {
        List<ScreenText> premix = screen("Nescafe 3 in 1 Instant Coffee Premix", 162, "₹120", 210,
            "Highlights", 700, "Weight", 800, "160 g", 804,
            "Ingredients", 890, "Sugar, Milk Solids (25%), Coffee (13%), Hydrogenated Vegetable Oil", 894,
            "Directions", 950, "Empty one sachet (16 g) into a cup and add 100 ml hot water", 954,
            "Nutrition Information", 1080,
            "Energy (kcal) 440, Total Sugars (g) 55, Saturated Fat (g) 9, Sodium (mg) 250, Protein (g) 7", 1085);
        GradeResult r = ProductAnalyzer.analyze(premix, HEIGHT, ZEPTO, ProductAnalyzerTest.noPack(),
            ProductAnalyzerTest.noDatabase(), null);
        // Rated as sold (the powder), with the basis flagged: no cup is inferred from the directions.
        assertEquals(GradeResult.State.GRADED, r.state);
        assertNotNull(r.band); assertNull(r.drinkKind);
        assertTrue(r.caveats.toString(), r.caveats.toString().contains("per 100 g or per 100 ml"));
        assertTrue(r.caveats.toString().contains("Rated as sold"));
        // 16 g powder plus 100 ml water does not prove 116 ml final prepared volume.
        assertTrue(LabelGuide.category(r.page).contains("no cup values are inferred"));
    }

    @Test public void premixWithoutAWaterInstructionIsRatedAsSold() {
        List<ScreenText> premix = screen("Instant Tea Premix Masala Chai", 162, "₹150", 210,
            "Highlights", 700, "Weight", 800, "200 g", 804,
            "Ingredients", 890, "Sugar, Milk Solids, Tea, Spices", 894,
            "Nutrition Information per 100 g", 1080,
            "Energy (kcal) 420, Total Sugars (g) 50, Saturated Fat (g) 6, Sodium (mg) 200", 1085);
        GradeResult r = ProductAnalyzer.analyze(premix, HEIGHT, ZEPTO, ProductAnalyzerTest.noPack(),
            ProductAnalyzerTest.noDatabase(), null);
        assertEquals(GradeResult.State.GRADED, r.state);
        assertNull(r.drinkKind);
        assertTrue(r.caveats.stream().anyMatch(c -> c.startsWith("Rated as sold")));
    }

    @Test public void readyToDrinkCoffeeAndTeaBiscuitsAreNotBrews() {
        assertFalse(Brew.isDryBrew("Starbucks Cold Coffee Frappuccino", "ml"));
        assertFalse(Brew.isDryBrew("Britannia Tea Time Biscuits", "g"));
        assertTrue(Brew.isDryBrew("Bru Instant Coffee Powder", "g"));
        assertTrue(Brew.isDryBrew("Lipton Green Tea Bags", null));
        assertFalse(Brew.plain("Ingredients: Tea"
            .replace("Tea", "Coffee, Sugar")));
        assertTrue(Brew.plain("Coffee (70%) & Chicory (30%)"));
    }

    @Test public void cocoaShareIsStatedWhenTheListGivesIt() {
        IngredientFacts facts = IngredientFacts.of(
            "Cocoa Solids (Cocoa Mass, Cocoa Butter) 70%, Sugar, Emulsifier (INS 322)", false);
        assertTrue(facts.information.toString(), facts.information.contains("Cocoa solids: 70%, as the label states"));
        assertNull(IngredientFacts.cocoaShare("Sugar, Cocoa Butter (20%), Milk Solids"));
    }
}
