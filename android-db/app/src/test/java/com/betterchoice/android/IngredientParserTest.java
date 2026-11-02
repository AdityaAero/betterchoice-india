package com.betterchoice.android;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Ordered ingredients and additive codes are read only when the label actually declares them. */
public class IngredientParserTest {

    @Test public void readsExplicitAndClassScopedCodesInLabelOrder() {
        IngredientParser.Result parsed = IngredientParser.parse("Water, Mango Pulp (11.2%), Sugar, "
            + "Acidity Regulator (INS 330), Preservative (211), Sweeteners (951, 950), Colour (150d)");
        assertEquals(7, parsed.ingredients.size());
        assertEquals("Water", parsed.ingredients.get(0).text);
        assertEquals(1, parsed.ingredients.get(0).position);
        assertEquals(java.util.Arrays.asList("330", "211", "951", "950", "150d"), codes(parsed.additives));
        assertEquals("citric acid", parsed.additives.get(0).name);
        assertEquals("preservative", parsed.additives.get(1).function);
        assertEquals(4, parsed.additives.get(0).ingredientPosition);
    }

    @Test public void understandsIndianSubcodesAndKeepsUnknownCodesHonest() {
        IngredientParser.Result parsed = IngredientParser.parse("Flour, Raising Agents [503(ii), 500(ii)], "
            + "Emulsifier INS 472e, Flour Treatment Agent (1101(ii)), Salt");
        assertEquals(java.util.Arrays.asList("503ii", "500ii", "472e", "1101ii"), codes(parsed.additives));
        assertEquals("ammonium hydrogen carbonate", parsed.additives.get(0).name);
        assertEquals("INS 503(ii) ammonium hydrogen carbonate", parsed.additives.get(0).display());
        assertEquals("diacetyltartaric and fatty acid esters of glycerol", parsed.additives.get(2).name);
        assertNull("An unverified code is shown without a guessed name", parsed.additives.get(3).name);
        assertEquals("INS 1101(ii)", parsed.additives.get(3).display());
    }

    @Test public void doesNotTurnPercentagesOrNutritionNumbersIntoAdditives() {
        IngredientParser.Result parsed = IngredientParser.parse("Mango Pulp (100%), Water, Sugar 12.5 g, Salt 350 mg");
        assertTrue(parsed.additives.isEmpty());
    }

    @Test public void additiveSummaryIsInformationAndDoesNotChangeTheBand() {
        IngredientFacts facts = IngredientFacts.of("Water, Sugar, Acidity Regulator (INS 330), Preservative (INS 211)", true);
        assertEquals(2, facts.additives.size());
        assertTrue(facts.information.toString(), facts.information.get(0).contains("INS 330 citric acid"));
        assertFalse(facts.concerns.toString(), facts.concerns.toString().contains("sodium benzoate"));
    }

    private static List<String> codes(List<IngredientParser.Additive> additives) {
        List<String> codes = new java.util.ArrayList<>();
        for (IngredientParser.Additive additive : additives) codes.add(additive.code);
        return codes;
    }
}
