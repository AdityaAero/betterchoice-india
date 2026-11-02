package com.betterchoice.android;

import java.io.File;
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
 * The app's calculator against an independent Python implementation (tools/calc_crosscheck.py) for
 * 48 personas, including P1-P8 of the plan. Every reference must agree to within 0.01.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CalculatorCrossCheckTest {
    private static final String[] FIELDS = {"energyKcal", "maintenanceKcal", "stateExtraKcal", "totalFatLimitG",
        "saturatedFatLimitG", "transFatLimitG", "sugarLimitG", "sugarBetterG", "carbohydrateLowG", "carbohydrateHighG",
        "fibreG", "sodiumLimitMg", "weightKg", "proteinG", "muscleProteinLowG", "muscleProteinHighG"};

    private static JSONObject fixture() throws Exception {
        for (String path : new String[]{"../security/evidence/calculator-crosscheck-v3.5.json", "security/evidence/calculator-crosscheck-v3.5.json"}) {
            File f = new File(path);
            if (f.exists()) return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        }
        throw new AssertionError("run python tools/calc_crosscheck.py first");
    }

    private static double field(DailyNeeds n, String name) {
        switch (name) {
            case "energyKcal": return n.energyKcal;
            case "maintenanceKcal": return n.maintenanceKcal;
            case "stateExtraKcal": return n.stateExtraKcal;
            case "totalFatLimitG": return n.totalFatLimitG;
            case "saturatedFatLimitG": return n.saturatedFatLimitG;
            case "transFatLimitG": return n.transFatLimitG;
            case "sugarLimitG": return n.sugarLimitG;
            case "sugarBetterG": return n.sugarBetterG;
            case "carbohydrateLowG": return n.carbohydrateLowG;
            case "carbohydrateHighG": return n.carbohydrateHighG;
            case "fibreG": return n.fibreG;
            case "sodiumLimitMg": return n.sodiumLimitMg;
            case "weightKg": return n.weightKg;
            case "proteinG": return n.proteinG;
            case "muscleProteinLowG": return n.muscleProteinLowG;
            default: return n.muscleProteinHighG;
        }
    }

    @Test public void appMatchesTheIndependentCalculatorForEveryPersona() throws Exception {
        JSONArray personas = fixture().getJSONArray("personas");
        assertTrue(personas.length() >= 48);
        int compared = 0;
        for (int i = 0; i < personas.length(); i++) {
            JSONObject row = personas.getJSONObject(i);
            JSONObject answers = row.getJSONObject("answers"), expected = row.getJSONObject("expected");
            Profile p = new Profile();
            p.adultConfirmed = true;
            for (int k = 0; k < Profile.KEYS.length; k++) p.answers[k] = answers.optString(Profile.KEYS[k], "");
            DailyNeeds n = DailyNeeds.forProfile(p);
            String name = row.getString("name");
            assertEquals(name, expected.getString("basis"), n.basis.name());
            for (String f : FIELDS) {
                if (!expected.has(f)) continue;
                double actual = field(n, f);
                if (expected.isNull(f)) {
                    assertTrue(name + " " + f + " should be unknown, was " + actual, Double.isNaN(actual));
                } else {
                    assertEquals(name + " " + f, expected.getDouble(f), actual, 0.01);
                    compared++;
                }
            }
        }
        assertTrue("compared " + compared + " values", compared > 400);
    }
}
