package com.betterchoice.android;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Caffeine and lactose are said as numbers, each from the evidence registry, and the arithmetic
 * matches the independent Python calculator (tools/calc_crosscheck.py, "products" block).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CaffeineLactoseTest {
    private static JSONObject products() throws Exception {
        for (String path : new String[]{"../security/evidence/calculator-crosscheck-v3.5.json", "security/evidence/calculator-crosscheck-v3.5.json"}) {
            File f = new File(path);
            if (f.exists()) return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getJSONObject("products");
        }
        throw new AssertionError("run python tools/calc_crosscheck.py first");
    }

    private static Nutrition drink(double sugars, Double added, Double pack) {
        Nutrition n = new Nutrition();
        n.beverage = true; n.basisStated = true; n.sugarsG = sugars; n.addedSugarsG = added;
        n.netQuantity = pack; n.netUnit = pack == null ? null : "ml";
        return n;
    }

    // ---------------------------------------------------------------- arithmetic against the Python cross-check

    @Test public void caffeineArithmeticMatchesTheIndependentCalculator() throws Exception {
        JSONObject caffeine = products().getJSONObject("caffeine");
        JSONArray declared = caffeine.getJSONArray("declared");
        for (int i = 0; i < declared.length(); i++) {
            JSONObject c = declared.getJSONObject(i);
            NutritionCalculator.Result mg = NutritionCalculator.portion(c.getDouble("per100"), c.getDouble("pack"), "mg");
            assertEquals(c.getString("name"), c.getDouble("packMg"), mg.d(), 0.01);
            assertEquals(c.getString("name"), c.getDouble("percentOfLimit"), NutritionCalculator.caffeineShare(mg).d(), 0.01);
        }
        JSONObject cups = caffeine.getJSONObject("cupsToLimit");
        for (String kind : new String[]{"instant", "brewed", "tea"}) {
            assertEquals(kind, cups.getJSONObject(kind).getDouble("fewest"),
                NutritionCalculator.portionsToCaffeineLimit(Evidence.d("nin.caffeine." + kind + ".high")).d(), 0.01);
            assertEquals(kind, cups.getJSONObject(kind).getDouble("most"),
                NutritionCalculator.portionsToCaffeineLimit(Evidence.d("nin.caffeine." + kind + ".low")).d(), 0.01);
        }
        NutritionCalculator.Result cola = new NutritionCalculator.Result(Evidence.d("who.anc.caffeine.soft.drink"), "mg", "WHO");
        assertEquals(caffeine.getDouble("colaPercentOfLimit"), NutritionCalculator.caffeineShare(cola).d(), 0.01);
    }

    @Test public void lactoseArithmeticMatchesTheIndependentCalculator() throws Exception {
        JSONArray cases = products().getJSONArray("lactose");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String name = c.getString("name");
            NutritionCalculator.Result per100 = NutritionCalculator.lactoseUpTo(c.getDouble("sugars"),
                c.isNull("added") ? null : c.getDouble("added"));
            assertEquals(name, c.getDouble("lactosePer100"), per100.d(), 0.01);
            if (c.has("lactosePerServing")) {
                assertEquals(name, c.getDouble("lactosePerServing"), NutritionCalculator.portion(per100.d(), c.getDouble("serving"), "g").d(), 0.01);
            }
            NutritionCalculator.Result amount = NutritionCalculator.amountHolding(per100, Evidence.d("lactose.tolerated.low"), "ml");
            if (c.isNull("amountFor12g")) assertNull(name, amount);
            else assertEquals(name, c.getDouble("amountFor12g"), amount.d(), 0.01);
        }
    }

    @Test public void fibreSwapsFollowFssaiComparativeAndSourceRules() throws Exception {
        JSONArray cases = products().getJSONArray("fibre");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            Nutrition before = new Nutrition(), after = new Nutrition();
            before.beverage = after.beverage = c.getBoolean("liquid");
            before.fibreG = c.getDouble("before"); after.fibreG = c.getDouble("after");
            before.energyKcal = after.energyKcal = c.getDouble("kcal");
            assertEquals(c.toString(), c.getBoolean("improves"), SwapEngine.improves("fibre", before, after));
        }
    }

    // ---------------------------------------------------------------- what the card says

    @Test public void aDeclaredAmountIsReadAndSetAgainstNinsLimit() {
        CaffeineLactose.Caffeine c = CaffeineLactose.caffeine("Red Bull Energy Drink 250 ml", "Nutrition per 100 ml ... Caffeine 32 mg/100 ml", true);
        assertEquals(CaffeineLactose.Source.DECLARED, c.source);
        assertEquals(0, new BigDecimal("32").compareTo(c.per100));
        String line = CaffeineLactose.caffeineLine(c, drink(11, null, 250.0), false);
        assertTrue(line, line.contains("32 mg per 100 ml") && line.contains("80 mg in this 250 ml pack") && line.contains("27%")
            && line.contains("300 mg a day NIN 2024"));
        assertEquals(0, new BigDecimal("32").compareTo(CaffeineLactose.caffeine("Energy drink", "caffeine content: 320 mg/l", true).per100));
        CaffeineLactose.Caffeine can = CaffeineLactose.caffeine("Energy drink", "Contains 80 mg caffeine per can", true);
        assertEquals(0, new BigDecimal("80").compareTo(can.perServing));
        assertTrue(CaffeineLactose.caffeineLine(can, null, false).contains("27%"));
    }

    @Test public void teaAndCoffeeUseNinsPerCupRanges() {
        assertEquals(CaffeineLactose.Source.INSTANT_COFFEE, CaffeineLactose.caffeine("Nescafe Classic Instant Coffee 100 g", "", false).source);
        assertEquals(CaffeineLactose.Source.BREWED_COFFEE, CaffeineLactose.caffeine("Bru Filter Coffee Powder 500 g", "", false).source);
        assertEquals(CaffeineLactose.Source.COFFEE, CaffeineLactose.caffeine("Continental Coffee 200 g", "", false).source);
        assertEquals(CaffeineLactose.Source.TEA, CaffeineLactose.caffeine("Tata Tea Gold 500 g", "", false).source);
        assertEquals(CaffeineLactose.Source.TEA, CaffeineLactose.caffeine("Wagh Bakri Masala Chai 250 g", "", false).source);
        assertEquals(CaffeineLactose.Source.TEA, CaffeineLactose.caffeine("Organic India Tulsi Green Tea 25 bags", "", false).source);
        assertNull("a herbal infusion has no tea leaf", CaffeineLactose.caffeine("Organic India Tulsi Ginger Tea 25 bags", "", false));
        assertNull("decaffeinated", CaffeineLactose.caffeine("Nescafe Decaf Instant Coffee", "", false));
        assertNull("a biscuit that names tea", CaffeineLactose.caffeine("Britannia Good Day Tea Time Biscuits", "", false));
        assertNull("a toffee that names coffee", CaffeineLactose.caffeine("Parle Coffee Bite Toffee", "", false));
        String tea = CaffeineLactose.caffeineLine(CaffeineLactose.caffeine("Tata Tea Gold 500 g", "", false), null, false);
        assertTrue(tea, tea.contains("30-65 mg a cup (150 ml) of tea") && tea.contains("4.6-10 cups"));
        String coffee = CaffeineLactose.caffeineLine(CaffeineLactose.caffeine("Nescafe Classic Instant Coffee 100 g", "", false), null, true);
        assertTrue(coffee, coffee.contains("50-65 mg") && coffee.contains("4.6-6 cups") && coffee.contains("WHO advises cutting down")
            && coffee.contains("more than 300 mg a day"));
    }

    @Test public void colasUseWhosCeilingAndUnreadAmountsAreSaidPlainly() {
        CaffeineLactose.Caffeine cola = CaffeineLactose.caffeine("Coca-Cola Original Taste Soft Drink 750 ml", "", true);
        assertEquals(CaffeineLactose.Source.COLA, cola.source);
        assertTrue(CaffeineLactose.caffeineLine(cola, null, false).contains("less than 50 mg per 250 ml (WHO 2016), under 17%"));
        CaffeineLactose.Caffeine unread = CaffeineLactose.caffeine("Monster Energy 350 ml", "CONTAINS CAFFEINE. Not recommended for children", true);
        assertEquals(CaffeineLactose.Source.UNDECLARED, unread.source);
        assertTrue(CaffeineLactose.caffeineLine(unread, null, false).contains("amount wasn't on the label"));
        assertNull("caffeine-free", CaffeineLactose.caffeine("Diet Coke Caffeine Free", "caffeine free", true));
        assertNull("no caffeine at all", CaffeineLactose.caffeine("Amul Taaza Toned Milk", "Milk", true));
    }

    @Test public void lactoseIsEstimatedForDairyOnly() {
        String milk = CaffeineLactose.lactoseLine("Amul Taaza Toned Milk 500 ml", drink(4.8, null, 500.0), "Toned milk", "", true);
        assertTrue(milk, milk.contains("up to 4.8 g per 100 ml") && milk.contains("12-15 g") && milk.contains("about 250 ml of this"));
        Nutrition flavoured = drink(10.2, 5.4, 180.0);
        flavoured.servingSize = 180.0;
        String shake = CaffeineLactose.lactoseLine("Amul Kool Kesar Flavoured Milk 180 ml", flavoured, "Milk, sugar, saffron", "", true);
        assertTrue(shake, shake.contains("up to 8.6 g in a 180 ml serving"));
        String unknown = CaffeineLactose.lactoseLine("Amul Kool Kesar Flavoured Milk 180 ml", drink(10.2, null, 180.0), "Milk, sugar, saffron", "", true);
        assertTrue(unknown, unknown.contains("can't be worked out"));
        assertTrue(CaffeineLactose.lactoseLine("Amul Lactose Free Milk 250 ml", drink(4.8, null, 250.0), "Milk", "", true)
            .contains("at most 0.1 g of lactose per 100 g"));
        assertNull("plant milk", CaffeineLactose.lactoseLine("Sofit Almond Milk 1 l", drink(2.0, null, 1000.0), "Water, almonds", "", true));
        assertNull("not dairy", CaffeineLactose.lactoseLine("Cadbury Dairy Milk Chocolate 50 g", drink(57, null, null), "Sugar, milk solids", "", false));
    }

    @Test public void theCardLinesFollowTheProfile() {
        Profile p = new Profile();
        p.adultConfirmed = true;
        p.answers[Profile.HEALTH] = "lactose";
        RecommendationContext lactose = RecommendationContext.of(p);
        java.util.List<String> lines = lactose.productLines("Amul Taaza Toned Milk 500 ml", drink(4.8, null, 500.0), "Ingredients: Toned milk", true);
        assertTrue(lines.toString(), lines.stream().anyMatch(l -> l.startsWith("Lactose: up to 4.8 g")));
        p.answers[Profile.HEALTH] = "lactose,milk";
        assertTrue("a milk allergy is a veto, not a lactose sum",
            RecommendationContext.of(p).productLines("Amul Taaza Toned Milk 500 ml", drink(4.8, null, 500.0), "Ingredients: Toned milk", true)
                .stream().noneMatch(l -> l.startsWith("Lactose")));
        p.answers[Profile.HEALTH] = "";
        assertTrue("caffeine is said for everyone", RecommendationContext.of(p)
            .productLines("Tata Tea Gold 500 g", null, "", false).stream().anyMatch(l -> l.startsWith("Caffeine: about 30-65 mg")));
    }
}
