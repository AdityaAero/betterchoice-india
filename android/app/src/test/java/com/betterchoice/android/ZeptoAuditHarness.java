package com.betterchoice.android;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Accuracy audit on real product pages recorded from a phone (tools/zepto_audit/crawl.py). Runs only
 * when BC_AUDIT_DIR points at the recordings; otherwise it does nothing, so the normal suite is
 * unaffected.
 *
 * <p>Each recorded screen goes through the app's own pipeline in order, the way the bubble meets
 * them while the shopper scrolls: the page is identified, the label is read, the product is graded.
 * The result the bubble would end up showing is written out for comparison against an independent
 * reading of the same text (tools/zepto_audit/compare.py).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ZeptoAuditHarness {
    private static final String PKG = "com.zeptoconsumerapp";
    private static final int HEIGHT = 2392;

    @Test public void auditRecordedPages() throws Exception {
        String dir = System.getenv("BC_AUDIT_DIR");
        if (dir == null || dir.isEmpty()) return;
        File[] files = new File(dir).listFiles((d, n) -> n.endsWith(".json") && !n.startsWith("_"));
        if (files == null) return;
        java.util.Arrays.sort(files);
        try (PrintWriter out = new PrintWriter(new File(dir, "_app_results.jsonl"), "UTF-8")) {
            for (File file : files) {
                JSONObject fixture = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                JSONArray screens = fixture.getJSONArray("screens");
                ProductPage anchor = null;
                GradeResult best = null;
                int graded = 0;
                String lastState = null, lastNote = null;
                StringBuilder union = new StringBuilder();
                // The same per-product memory the watcher keeps: ingredient and allergen evidence and
                // per-100 rows seen on earlier screens of this product.
                java.util.Map<String, LabelEvidence> remembered = new java.util.HashMap<>();
                ProductAnalyzer.Memory memory = new ProductAnalyzer.Memory() {
                    @Override public boolean skipped(String key) { return false; }
                    @Override public Nutrition userDetails(String key) { return null; }
                    @Override public String evidence(String key, String text) {
                        LabelEvidence facts = remembered.computeIfAbsent(key, k -> new LabelEvidence());
                        facts.observe(text);
                        return facts.text() + "\n" + (text == null ? "" : text);
                    }
                    @Override public String nutritionText(String key, String text) {
                        LabelEvidence facts = remembered.get(key);
                        return text + "\n" + (facts == null ? "" : facts.panel());
                    }
                };
                for (int i = 0; i < screens.length(); i++) {
                    List<ScreenText> screen = new ArrayList<>();
                    JSONArray items = screens.getJSONArray(i);
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject it = items.getJSONObject(j);
                        screen.add(new ScreenText(it.getString("text"), it.getInt("top"), it.optInt("left", 0),
                            it.optBoolean("clickable")));
                        union.append(it.getString("text")).append('\n');
                    }
                    GradeResult r = ProductAnalyzer.analyze(screen, HEIGHT, fixture.optString("retailer", PKG),
                        p -> PackScan.EMPTY, null, memory, anchor);
                    if (r.page != null) anchor = r.page; else anchor = null;
                    if (r.state == GradeResult.State.GRADED) {
                        graded++;
                        best = r;   // the bubble keeps the latest graded reading of the page
                    } else if (r.page != null) {
                        lastState = r.state.name();
                        lastNote = r.note;
                    }
                }
                JSONObject row = new JSONObject();
                row.put("file", file.getName());
                row.put("last_ungraded_state", lastState == null ? JSONObject.NULL : lastState);
                row.put("last_ungraded_note", lastNote == null ? JSONObject.NULL : lastNote);
                row.put("query", fixture.optString("query"));
                row.put("tile", fixture.optString("tile"));
                row.put("screens", screens.length());
                row.put("graded_screens", graded);
                if (best != null) {
                    // What the card's allergen block is built from: the rating's own evidence plus
                    // anything remembered for this product since, exactly as the watcher merges it.
                    LabelEvidence facts = remembered.get(best.page.key());
                    String evidence = (best.labelText == null ? "" : best.labelText)
                        + (facts == null ? "" : "\n" + facts.text());
                    LabelEvidence label = LabelEvidence.of(evidence);
                    Allergen.Scan scan = Allergen.scan(label.ingredients(), label.allergens());
                    String ingredients = label.ingredients() == null ? null
                        : label.ingredients().replaceFirst("^Ingredients: ", "");
                    row.put("app_ingredients", ingredients == null ? JSONObject.NULL : ingredients);
                    row.put("app_contains", new JSONArray(labels(scan.declared, scan.fromIngredients)));
                    row.put("app_traces", new JSONArray(labels(scan.traces, java.util.Collections.emptyList())));
                    row.put("app_allergen_info", !scan.noInformation);
                    row.put("source", best.source.name());
                    row.put("band_settled", NutriGrade.bandSettled(best.nutrition,
                        best.drinkKind == null ? Beverage.NOT_A_DRINK : Beverage.of(best.drinkKind)));
                    row.put("missing", best.nutrition.missingNegatives());
                    row.put("pack", best.nutrition.packAmount() == null ? JSONObject.NULL : best.nutrition.packAmount());
                    Nutrition n = best.nutrition;
                    row.put("name", best.page.name);
                    row.put("price", best.page.price == null ? JSONObject.NULL : best.page.price);
                    row.put("band", best.band.name());
                    row.put("letter", String.valueOf(best.letter));
                    row.put("kind", best.drinkKind == null ? "food" : best.drinkKind.name());
                    row.put("unit", n.unit());
                    row.put("basis_stated", n.basisStated);
                    put(row, "energy_kcal", n.energyKcal); put(row, "sugars_g", n.sugarsG);
                    put(row, "added_sugars_g", n.addedSugarsG); put(row, "sat_fat_g", n.saturatedFatG);
                    put(row, "total_fat_g", n.totalFatG); put(row, "trans_fat_g", n.transFatG);
                    put(row, "sodium_mg", n.sodiumMg); put(row, "fibre_g", n.fibreG); put(row, "protein_g", n.proteinG);
                    put(row, "net_qty", n.netQuantity);
                    row.put("notes", new JSONArray(best.notes));
                    row.put("concerns", new JSONArray(best.concerns));
                    row.put("positives", new JSONArray(best.positives));
                    row.put("caveats", new JSONArray(best.caveats));
                    // The comparison card, exactly as GradeCard asks for it: debug build (drafts shown),
                    // no profile, no allergies recorded.
                    SwapEngine.Outcome swaps = SwapEngine.recommend(catalogue(), best.page.name, best.nutrition,
                        best.band, best.letter, best.drinkKind != null, SafetyProfile.unknown(), null, true, null);
                    row.put("swap_group", swaps.group == null ? JSONObject.NULL : swaps.group.id);
                    row.put("swap_count", swaps.swaps.size());
                    row.put("swap_empty_reason", swaps.emptyReason == null ? JSONObject.NULL : swaps.emptyReason);
                    JSONArray picked = new JSONArray();
                    for (SwapEngine.Swap s : swaps.swaps) picked.put(s.band.name() + " " + s.letter + " " + s.product.displayName() + " | " + s.why);
                    row.put("swaps", picked);
                    FoodCategory.Placement placed = FoodCategory.place(best.page.name, best.drinkKind != null,
                        swaps.group == null ? null : swaps.group.id);
                    row.put("fssai_category", placed.category == null ? JSONObject.NULL : placed.category.name());
                    row.put("food_kind", FoodCategory.kind(best.page.name, swaps.group == null ? null : swaps.group.id).name());
                    // Persona replay: the same card for each plan persona, with what was left out and why.
                    JSONObject personas = new JSONObject();
                    for (int pi = 0; pi < personas().size(); pi++) {
                        Profile persona = personas().get(pi);
                        SwapEngine.Outcome o = SwapEngine.recommend(catalogue(), best.page.name, best.nutrition, best.band,
                            best.letter, best.drinkKind != null, SafetyProfile.fromProfile(persona), null, true, persona);
                        JSONObject r = new JSONObject();
                        r.put("swaps", o.swaps.size());
                        r.put("left_out", new JSONObject(o.leftOut));
                        r.put("meets_goal", o.swaps.stream().filter(s -> s.meetsGoal).count());
                        r.put("shortfall", o.shortfall == null ? JSONObject.NULL : o.shortfall);
                        RecommendationContext c = RecommendationContext.of(persona);
                        r.put("not_for_you", new JSONArray(c.vetoes(IngredientFacts.section(best.labelText == null ? "" : best.labelText),
                            best.labelText, best.labelText)));
                        r.put("product_lines", new JSONArray(c.productLines(best.page.name, best.nutrition, best.labelText,
                            best.drinkKind != null)));
                        personas.put(PERSONA_NAMES.get(pi), r);
                    }
                    row.put("personas", personas);
                } else {
                    ProductPage page = ProductPage.detect(lastScreen(screens), HEIGHT, fixture.optString("retailer", PKG));
                    row.put("name", page == null ? JSONObject.NULL : page.name);
                    row.put("band", JSONObject.NULL);
                    String ingredients = IngredientFacts.section(union.toString());
                    row.put("app_ingredients", ingredients == null ? JSONObject.NULL : ingredients);
                }
                out.println(row.toString());
            }
        }
    }

    private static List<Profile> personas;
    private static final List<String> PERSONA_NAMES = new ArrayList<>();

    /** P1-P8 of the plan, from the calculator cross-check fixture. */
    private static List<Profile> personas() throws Exception {
        if (personas != null) return personas;
        personas = new ArrayList<>();
        JSONObject fixture = new JSONObject(new String(Files.readAllBytes(
            new File("../security/evidence/calculator-crosscheck-v3.5.json").toPath()), StandardCharsets.UTF_8));
        JSONArray rows = fixture.getJSONArray("personas");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (!row.getString("name").startsWith("P")) continue;
            JSONObject answers = row.getJSONObject("answers");
            Profile p = new Profile();
            p.adultConfirmed = true;
            for (int k = 0; k < Profile.KEYS.length; k++) p.answers[k] = answers.optString(Profile.KEYS[k], "");
            personas.add(p);
            PERSONA_NAMES.add(row.getString("name").substring(0, 2));
        }
        return personas;
    }

    private static SwapCatalogue catalogue;

    private static SwapCatalogue catalogue() throws Exception {
        if (catalogue == null) catalogue = SwapCatalogue.parse(new String(Files.readAllBytes(
            new File("src/main/assets/catalogue/swaps_v1.json").toPath()), StandardCharsets.UTF_8));
        return catalogue;
    }

    private static List<ScreenText> lastScreen(JSONArray screens) throws Exception {
        List<ScreenText> screen = new ArrayList<>();
        JSONArray items = screens.getJSONArray(0);
        for (int j = 0; j < items.length(); j++) {
            JSONObject it = items.getJSONObject(j);
            screen.add(new ScreenText(it.getString("text"), it.getInt("top"), it.optBoolean("clickable")));
        }
        return screen;
    }

    private static List<String> labels(List<Allergen> first, List<Allergen> second) {
        List<String> out = new ArrayList<>();
        for (Allergen a : first) if (!out.contains(a.label)) out.add(a.label);
        for (Allergen a : second) if (!out.contains(a.label)) out.add(a.label);
        return out;
    }

    private static void put(JSONObject row, String key, Double value) throws Exception {
        row.put(key, value == null ? JSONObject.NULL : Math.round(value * 1000) / 1000.0);
    }
}
