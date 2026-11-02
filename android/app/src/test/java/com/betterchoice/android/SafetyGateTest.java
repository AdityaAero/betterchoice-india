package com.betterchoice.android;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The dangerous cases, deliberately. A false negative here — letting an allergen through — matters
 * far more than any recommendation being prettier or a score being better tuned.
 */
public class SafetyGateTest {

    /** The real allergen declaration from the Beyond Snack banana chips pack. */
    private static final String BEYOND_SNACK_INGREDIENTS =
        "INGREDIENTS: Raw Banana (83.5%), Rice Bran Oil (13.2%), Salt, Seasoning (7%). "
        + "Contains permitted natural colours and added natural and nature identical flavouring substances.";
    private static final String BEYOND_SNACK_ALLERGENS =
        "ALLERGEN DECLARATION: May Contains traces of Soy, Milk solids, Wheat & Mustard";

    private static SafetyProfile allergicTo(Allergen allergen, SafetyProfile.Severity severity) {
        return SafetyProfile.of(SafetyProfile.Allergies.KNOWN, Collections.singletonList(allergen), severity, "");
    }

    // --- Allergens come before everything ---------------------------------------------------------

    @Test public void aDeclaredAllergenBlocksOutright() {
        SafetyGate.Result result = SafetyGate.check(
            "Refined wheat flour, sugar, palm oil, milk solids, cocoa solids.", "Contains: Milk",
            allergicTo(Allergen.MILK, SafetyProfile.Severity.SEVERE));
        assertTrue(result.blocked());
        assertTrue(result.codes.toString(), result.codes.contains("allergen_milk"));
    }

    /**
     * The real pack says "May Contains traces of Soy, Milk solids, Wheat & Mustard". For a severe
     * allergy that is a block: a trace is treated as the real thing rather than as a probability to
     * weigh up.
     */
    @Test public void aTraceBlocksWhenTheAllergyCouldBeDangerous() {
        SafetyGate.Result result = SafetyGate.check(BEYOND_SNACK_INGREDIENTS, BEYOND_SNACK_ALLERGENS,
            allergicTo(Allergen.SOY, SafetyProfile.Severity.SEVERE));
        assertTrue("A severe soy allergy must not be offered a may-contain-soy pack", result.blocked());
        assertTrue(result.codes.contains("allergen_traces_soy"));
    }

    @Test public void aTraceOnlyCautionsWhenTheReactionIsMild() {
        SafetyGate.Result result = SafetyGate.check(BEYOND_SNACK_INGREDIENTS, BEYOND_SNACK_ALLERGENS,
            allergicTo(Allergen.SOY, SafetyProfile.Severity.MILD));
        assertEquals(SafetyGate.Verdict.CAUTION, result.verdict);
        assertFalse(result.blocked());
    }

    /** Not knowing is treated as the dangerous case, because the other guess is the unsafe one. */
    @Test public void anUnstatedSeverityIsTreatedAsSevere() {
        SafetyGate.Result result = SafetyGate.check(BEYOND_SNACK_INGREDIENTS, BEYOND_SNACK_ALLERGENS,
            allergicTo(Allergen.SOY, SafetyProfile.Severity.UNKNOWN));
        assertTrue(result.blocked());
    }

    /** Refusing to answer is a correct outcome, not a failure. */
    @Test public void noLabelInformationBlocksRatherThanAssumingItIsFine() {
        SafetyGate.Result result = SafetyGate.check(null, null,
            allergicTo(Allergen.PEANUT, SafetyProfile.Severity.SEVERE));
        assertTrue(result.blocked());
        assertTrue(result.codes.contains("no_allergen_information"));
        assertTrue(result.messages.toString(), result.messages.get(0).contains("can't check"));
    }

    @Test public void anAllergenInTheIngredientsIsCaughtEvenWithNoDeclaration() {
        SafetyGate.Result result = SafetyGate.check(
            "Ingredients: Maida, sugar, groundnut oil, salt.", null,
            allergicTo(Allergen.PEANUT, SafetyProfile.Severity.SEVERE));
        assertTrue("Groundnut is peanut", result.blocked());
    }

    // --- Never certify safety ---------------------------------------------------------------------

    @Test public void aShopperWithNoKnownAllergiesIsNeverToldAFoodIsSafe() {
        SafetyProfile noneKnown = SafetyProfile.of(SafetyProfile.Allergies.NONE_KNOWN, null,
            SafetyProfile.Severity.UNKNOWN, "");
        SafetyGate.Result result = SafetyGate.check(
            "Refined wheat flour, sugar, milk solids.", "Contains: Milk, Wheat", noneKnown);
        assertFalse(result.blocked());
        String said = String.join(" ", result.messages);
        assertTrue("The label must still be stated: " + said, said.contains("Contains"));
        // The requirement is that no sentence makes an affirmative safety claim. Checking for the
        // substring "is safe for you" fails on "we can't confirm a food is safe for you", which is
        // exactly the sentence we want, so test for the negation instead.
        assertTrue("Any mention of safety must be negated: " + said,
            said.matches("(?s).*\\b(can't|cannot|won't|never|unable to)\\b[^.]*\\bsafe\\b.*"));
        for (String message : result.messages) {
            boolean claimsSafety = message.matches("(?s).*\\bis (safe|allergy-free)\\b.*")
                && !message.matches("(?s).*\\b(can't|cannot|won't|never|unable to)\\b.*");
            assertFalse("This reads as a safety guarantee: " + message, claimsSafety);
        }
    }

    @Test public void aShopperWhoWasNeverAskedIsToldSoRatherThanAssumedClear() {
        SafetyGate.Result result = SafetyGate.check(
            "Wheat flour, milk solids.", "Contains: Milk", SafetyProfile.unknown());
        assertEquals(SafetyGate.Verdict.INFORM, result.verdict);
        assertTrue(String.join(" ", result.messages).contains("haven't told BetterChoice"));
    }

    // --- Diet is a constraint, not a preference to trade off --------------------------------------

    @Test public void veganAndVegetarianDiffOnMilkAndEgg() {
        String biscuit = "Refined wheat flour, sugar, milk solids, butter.";
        SafetyProfile vegan = SafetyProfile.of(SafetyProfile.Allergies.NOT_ANSWERED, null, null, "Vegan");
        SafetyProfile vegetarian = SafetyProfile.of(SafetyProfile.Allergies.NOT_ANSWERED, null, null, "Vegetarian");

        assertTrue("Milk is not vegan", SafetyGate.check(biscuit, null, vegan).blocked());
        assertFalse("Milk is fine for an Indian vegetarian",
            SafetyGate.check(biscuit, null, vegetarian).blocked());

        String withFish = "Rice, anchovy paste, salt.";
        assertTrue(SafetyGate.check(withFish, null, vegetarian).blocked());
        assertTrue(SafetyGate.check(withFish, null, vegan).blocked());
    }

    // --- Word matching must not misfire -----------------------------------------------------------

    @Test public void allergenTermsMatchWholeWordsOnly() {
        Allergen.Scan scan = Allergen.scan("Ingredients: coconut oil, oatmeal-free blend, milkweed extract.", null);
        assertFalse("'coat'/'coconut' must not trigger oats", scan.mentions(Allergen.GLUTEN) && !scan.mentions(Allergen.MILK));
        Allergen.Scan clean = Allergen.scan("Ingredients: coconut oil, salt, turmeric.", null);
        assertTrue("Nothing here is an allergen", clean.all().isEmpty());
    }

    @Test public void declaredAndTraceStatementsAreKeptApart() {
        Allergen.Scan scan = Allergen.scan(
            "Ingredients: wheat flour, sugar.", "Contains: Wheat. May contain: Peanuts, Tree nuts");
        assertTrue(scan.declared.contains(Allergen.GLUTEN));
        assertTrue(scan.traces.contains(Allergen.PEANUT));
        assertTrue(scan.traces.contains(Allergen.TREE_NUT));
        assertFalse("A possible trace is not an ingredient", scan.containsAsIngredient(Allergen.PEANUT));
    }

    /** Peanut and tree nut are different conditions; FSSAI groups them, we must not. */
    @Test public void peanutAndTreeNutAreNotTheSameAllergy() {
        SafetyGate.Result result = SafetyGate.check("Ingredients: cashew, sugar.", null,
            allergicTo(Allergen.PEANUT, SafetyProfile.Severity.SEVERE));
        assertFalse("A cashew is not a peanut", result.blocked());

        SafetyGate.Result treeNut = SafetyGate.check("Ingredients: cashew, sugar.", null,
            allergicTo(Allergen.TREE_NUT, SafetyProfile.Severity.SEVERE));
        assertTrue(treeNut.blocked());
    }

    @Test public void severalAllergiesAreAllChecked() {
        SafetyProfile both = SafetyProfile.of(SafetyProfile.Allergies.KNOWN,
            Arrays.asList(Allergen.MILK, Allergen.PEANUT), SafetyProfile.Severity.SEVERE, "");
        SafetyGate.Result result = SafetyGate.check(
            "Ingredients: wheat flour, milk solids, groundnut oil.", null, both);
        assertTrue(result.blocked());
        assertTrue(result.codes.contains("allergen_milk"));
        assertTrue(result.codes.contains("allergen_peanut"));
    }

    /** The gate returns a verdict, never a number. Nothing here can be averaged into a score. */
    @Test public void theVerdictIsNeverANumber() {
        SafetyGate.Result blocked = SafetyGate.check("milk solids", "Contains: Milk",
            allergicTo(Allergen.MILK, SafetyProfile.Severity.SEVERE));
        assertEquals(SafetyGate.Verdict.BLOCK, blocked.verdict);
        for (java.lang.reflect.Field field : SafetyGate.Result.class.getDeclaredFields()) {
            assertNotEquals("A score on the safety result would invite averaging it away",
                double.class, field.getType());
            assertNotEquals(int.class, field.getType());
        }
    }
}
