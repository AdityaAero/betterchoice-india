package com.betterchoice.android;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Writes what the app itself concludes about every catalogue product — processing, fat, the capped
 * band — so the nutritionist's review sheet shows exactly what shoppers will see. Runs only when
 * BC_EXPORT_FACTS names an output file; otherwise it does nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CatalogueFactsExport {
    @Test public void exportFacts() throws Exception {
        String target = System.getenv("BC_EXPORT_FACTS");
        if (target == null || target.isEmpty()) return;
        File asset = new File("src/main/assets/" + SwapCatalogue.ASSET);
        SwapCatalogue catalogue = SwapCatalogue.parse(new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8));
        try (PrintWriter out = new PrintWriter(new File(target), "UTF-8")) {
            out.println("gtin\tapp_band\tnutri_score_letter\tultra_processed\tprocessing_markers\thydrogenated_fat\tgood\tconcerns");
            for (SwapCatalogue.Product p : catalogue.products) {
                Beverage identity = Beverage.classify(p.displayName(), p.ingredients, p.per100.beverage);
                if (p.sweetener) identity = identity.withSweetener();
                NutriGrade.Result grade = NutriGrade.grade(p.per100, identity);
                IngredientFacts facts = IngredientFacts.of(p.ingredients, grade.scoredAsDrink());
                NutriGrade.Band band = grade.band;
                out.println(String.join("\t", p.gtin == null ? "" : p.gtin, band.name(), String.valueOf(grade.letter()),
                    facts.read ? (facts.ultraProcessed() ? "yes" : "no") : "no list",
                    String.join("; ", facts.ultraMarkers), facts.hydrogenatedFat ? "yes" : "no",
                    String.join("; ", Priorities.positives(new java.util.ArrayList<>(facts.good))),
                    String.join("; ", Priorities.concerns(new java.util.ArrayList<>(facts.concerns)))));
            }
        }
    }
}
