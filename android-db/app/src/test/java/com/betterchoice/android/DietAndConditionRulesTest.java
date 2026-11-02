package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

/** The diet and condition lexicons, case by case, each tied to the rule it implements. */
public class DietAndConditionRulesTest {
    private static boolean fits(DietRules.Diet diet, String ingredients) {
        return DietRules.check(diet, ingredients, null).fits();
    }

    @Test public void fssaiVegetarianExcludesEggButNotMilkHoneyOrShellac() {
        assertFalse("FSSAI 2(o): egg is non-vegetarian", fits(DietRules.Diet.VEGETARIAN, "Wheat flour, sugar, egg, butter"));
        assertTrue("an eggetarian eats egg", fits(DietRules.Diet.EGGETARIAN, "Wheat flour, sugar, egg, butter"));
        assertTrue("milk is vegetarian", fits(DietRules.Diet.VEGETARIAN, "Milk solids, sugar, cocoa butter"));
        assertTrue("honey is vegetarian under FSSAI", fits(DietRules.Diet.VEGETARIAN, "Oats, honey, almonds"));
        assertTrue("shellac is vegetarian under FSSAI", fits(DietRules.Diet.VEGETARIAN, "Sugar, glazing agent (shellac)"));
        assertFalse(fits(DietRules.Diet.VEGETARIAN, "Sugar, gelatin, citric acid"));
        assertFalse(fits(DietRules.Diet.VEGETARIAN, "Chicken (40%), salt, spices"));
        assertFalse("carmine is an insect", fits(DietRules.Diet.VEGETARIAN, "Sugar, colour (120)"));
        assertTrue("may contain egg is cross-contact, not an ingredient", fits(DietRules.Diet.VEGETARIAN, "Wheat flour, sugar. May contain egg."));
    }

    @Test public void veganAlsoExcludesMilkHoneyAndBeeProductsButNotCocoaButter() {
        assertTrue("cocoa butter is a plant fat", fits(DietRules.Diet.VEGAN, "Cocoa mass, sugar, cocoa butter"));
        assertTrue("coconut milk is not dairy", fits(DietRules.Diet.VEGAN, "Coconut milk, water, guar gum"));
        assertFalse(fits(DietRules.Diet.VEGAN, "Oats, honey"));
        assertFalse(fits(DietRules.Diet.VEGAN, "Sugar, milk solids"));
        assertFalse(fits(DietRules.Diet.VEGAN, "Sugar, glazing agent (904)"));
    }

    @Test public void jainAndHalalRulesAreReadFromTheList() {
        assertFalse(fits(DietRules.Diet.JAIN, "Potato, edible oil, salt, onion powder"));
        assertFalse(fits(DietRules.Diet.JAIN, "Rice flour, garlic, salt"));
        assertTrue(fits(DietRules.Diet.JAIN, "Rice flour, gram flour, salt, chilli"));
        assertFalse(fits(DietRules.Diet.HALAL, "Pork, salt, spices"));
        assertTrue("polyols are sugar alcohols, not alcohol", fits(DietRules.Diet.HALAL, "Sugar alcohols (maltitol), cocoa"));
        DietRules.Verdict gelatin = DietRules.check(DietRules.Diet.HALAL, "Sugar, gelatin", null);
        assertTrue("gelatin's source decides: a caution, not a veto", gelatin.fits());
        assertFalse(gelatin.cautions.isEmpty());
    }

    @Test public void cocoaButterIsNotMilkForAMilkAllergy() {
        Allergen.Scan scan = Allergen.scan("Cocoa mass, sugar, cocoa butter, soy lecithin", null);
        assertFalse(scan.containsAsIngredient(Allergen.MILK));
        assertTrue(scan.containsAsIngredient(Allergen.SOY));
        assertTrue(Allergen.scan("Sugar, milk solids, cocoa butter", null).containsAsIngredient(Allergen.MILK));
    }

    @Test public void wheatAllergyIsWheatOnlyButCoeliacIsEveryGlutenCereal() {
        Allergen.Scan oats = Allergen.scan("Rolled oats, barley flakes", null);
        assertTrue("coeliac: oats and barley count", oats.containsAsIngredient(Allergen.GLUTEN));
        assertFalse("wheat allergy: oats and barley do not", oats.wheatIngredient);
        Allergen.Scan atta = Allergen.scan("Whole wheat flour (atta), salt", null);
        assertTrue(atta.wheatIngredient);
        Profile p = new Profile(); p.answers[Profile.HEALTH] = "wheat"; p.answers[Profile.TRACE_SEVERITY] = "strict";
        SafetyProfile wheat = SafetyProfile.fromProfile(p);
        assertTrue(SafetyGate.check("Whole wheat flour (atta), salt", null, wheat).blocked());
        assertFalse(SafetyGate.check("Rolled oats, barley flakes", null, wheat).blocked());
    }

    @Test public void pregnancyRulesUseFssaiLabelWarnings() {
        String diet = "Carbonated water, acidity regulator (338), sweeteners (951, 950)";
        assertFalse("aspartame and acesulfame K carry FSSAI pregnancy warnings", ConditionRules.check(diet, null, 2, false, false, false).fits());
        assertFalse(ConditionRules.check(diet, null, 0, true, false, false).fits());
        assertTrue(ConditionRules.check(diet, null, 0, false, false, false).fits());
        String msg = "Noodles, salt, flavour enhancer (621)";
        assertFalse("MSG: FSSAI warns pregnant women", ConditionRules.check(msg, null, 1, false, false, false).fits());
        assertTrue("MSG warning names pregnant women, not lactating mothers", ConditionRules.check(msg, null, 0, true, false, false).fits());
        ConditionRules.Verdict coffee = ConditionRules.check("Coffee, sugar", "CONTAINS CAFFEINE", 2, false, false, false);
        assertTrue("caffeine is never a veto: WHO advises cutting down above 300 mg a day, not avoiding it", coffee.fits());
        assertTrue("caffeine is said with its amount by CaffeineLactose, not as a bare caution", coffee.cautions.isEmpty());
        assertFalse(ConditionRules.check("Juice", "Not recommended for children, pregnant or lactating women", 3, false, false, false).fits());
    }

    @Test public void kidneyAndPkuRules() {
        assertFalse("WHO excludes kidney impairment from potassium salt substitutes",
            ConditionRules.check("Iodised salt, potassium chloride", null, 0, false, true, false).fits());
        ConditionRules.Verdict cheese = ConditionRules.check("Cheese, emulsifying salts (339, 452)", null, 0, false, true, false);
        assertFalse("phosphate additive: KDIGO 2017 4.1.8", cheese.fits());
        assertTrue(cheese.vetoes.get(0), cheese.vetoes.get(0).contains("KDIGO") && !cheese.vetoes.get(0).contains("provisional"));
        assertTrue(ConditionRules.check("Cheese, emulsifying salts (339, 452)", null, 0, false, false, false).fits());
        assertFalse("aspartame: FSSAI warns phenylketonurics", ConditionRules.check("Sweetener (951)", null, 0, false, false, true).fits());
    }
}
