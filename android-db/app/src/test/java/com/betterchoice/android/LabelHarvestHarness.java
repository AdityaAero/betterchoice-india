package com.betterchoice.android;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Colours for pack labels collected from a shop (phone_tests/harvest_product.py), by the app's own algorithm only.
 * NutritionParser reads the label text as it reads a pack photo on the phone; ProductAnalyzer.identity decides food or
 * drink and sweeteners as the app does; GradeResult/NutriGrade gives the colour (Nutri-Score 2023 in three bands, with
 * the app's FSSAI and ICMR-NIN rules). The shop's own "details" text is graded the same way, as a cross-check only.
 * Runs only when BC_HARVEST_IN names the harvest file; writes BC_HARVEST_OUT.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class LabelHarvestHarness {

    @Test public void grade() throws Exception {
        String in = System.getenv("BC_HARVEST_IN"), out = System.getenv("BC_HARVEST_OUT");
        if (in == null || in.isEmpty() || out == null || out.isEmpty()) return;
        try (PrintWriter w = new PrintWriter(new File(out), "UTF-8")) {
            for (String line : Files.readAllLines(new File(in).toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JSONObject h = new JSONObject(line);
                String name = h.optString("title", "");
                String gtin = h.isNull("gtin") ? null : h.optString("gtin", null);   // JSON null is no barcode, not "null"
                String ingredients = h.optString("ingredients_text", "");
                JSONObject row = new JSONObject().put("n", h.optInt("n")).put("title", name).put("gtin", gtin == null ? JSONObject.NULL : gtin);
                row.put("pack", grade(name, join(h.optString("label_text", ""), ingredients), GradeResult.Source.PACK_PHOTO, gtin));
                row.put("details", grade(name, join(h.optString("details_text", ""), ingredients), GradeResult.Source.LABEL_TEXT, gtin));
                w.println(row);
            }
        }
    }

    private static String join(String a, String b) {
        return b == null || b.isEmpty() ? a : a + "\n" + b;
    }

    static JSONObject grade(String name, String text, GradeResult.Source source, String gtin) throws Exception {
        JSONObject o = new JSONObject();
        if (text == null || text.isBlank()) return o.put("state", "no text");
        Nutrition n = NutritionParser.parse(text);
        if (n == null) return o.put("state", "no table");
        o.put("basis_stated", n.basisStated).put("unit", n.unit()).put("usable", n.usable());
        put(o, "energy_kcal", n.energyKcal); put(o, "sugars_g", n.sugarsG); put(o, "added_sugars_g", n.addedSugarsG);
        put(o, "saturated_fat_g", n.saturatedFatG); put(o, "total_fat_g", n.totalFatG); put(o, "trans_fat_g", n.transFatG);
        put(o, "sodium_mg", n.sodiumMg); put(o, "fibre_g", n.fibreG); put(o, "protein_g", n.proteinG);
        put(o, "carbohydrate_g", n.carbohydrateG);
        ProductPage page = ProductPage.of(name, null, "com.grofers.customerapp");
        Beverage drink = Beverage.classify(name, text, false);
        GradeResult r = GradeResult.graded(n, source, page, gtin, ProductAnalyzer.identity(page, text, n, drink), text);
        o.put("state", r.state.name());
        if (r.state == GradeResult.State.GRADED) {
            o.put("band", r.band.name()).put("letter", String.valueOf(r.letter))
                .put("colour", r.band == NutriGrade.Band.A ? "green" : r.band == NutriGrade.Band.B ? "yellow" : "red")
                .put("scored_as", r.drinkKind == null ? "food" : "drink: " + r.drinkKind.label);
        } else if (r.note != null) {
            o.put("note", r.note);
        }
        o.put("caveats", new JSONArray(r.caveats));
        return o;
    }

    private static void put(JSONObject o, String key, Double v) throws Exception {
        if (v != null) o.put(key, v);
    }
}
