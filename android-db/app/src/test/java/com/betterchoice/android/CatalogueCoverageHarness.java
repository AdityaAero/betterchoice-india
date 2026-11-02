package com.betterchoice.android;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * How deep the swap catalogue is, measured with the app's own engine: for every swap group and every single
 * profile choice (a diet, a goal, an allergy or a condition), how many catalogue products qualify as a swap for
 * the group's real red and amber products. Runs only when BC_COVERAGE_DIR holds products_2025.json (from
 * tools/off_india_catalogue.py); otherwise it does nothing, so the normal suite is unaffected.
 *
 * <p>Writes coverage.json, recommendations.csv (the best alternatives per group and profile, for the
 * nutritionist) and products_graded.jsonl (every product graded and placed as the app would).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CatalogueCoverageHarness {
    private static final int ORIGINALS_PER_GROUP = 12, LIST_PER_PROFILE = 50;

    /** One choice at a time, on an adult of ordinary size, so each column shows what that choice alone does. */
    static Map<String, String[][]> choices() {
        Map<String, String[][]> c = new LinkedHashMap<>();
        c.put("healthy", new String[0][]);
        String[][] diets = {{"vegetarian", "Vegetarian"}, {"eggetarian", "Eggetarian"}, {"jain", "Jain"},
            {"non_veg", "Non-vegetarian / both"}, {"halal", "Non-vegetarian (halal)"}, {"vegan", "Vegan"}};
        for (String[] d : diets) c.put("diet:" + d[0], new String[][]{{"diet_detail", d[1]}});
        for (String g : new String[]{"loss", "gain", "muscle", "sugar"}) c.put("goal:" + g, new String[][]{{"goals", g}});
        for (String a : new String[]{"milk", "egg", "fish", "crustacea", "peanut", "tree_nut", "soy", "wheat", "gluten",
                "sesame", "sulphite"}) c.put("allergy:" + a, new String[][]{{"health_flags", a}});
        c.put("condition:lactose", new String[][]{{"health_flags", "lactose"}});
        c.put("condition:diabetes", new String[][]{{"health_flags", "diabetes"}, {"diabetes_type", "type2"}});
        c.put("condition:hypertension", new String[][]{{"health_flags", "hypertension"}});
        c.put("condition:cholesterol", new String[][]{{"health_flags", "cholesterol"}});
        c.put("condition:kidney", new String[][]{{"health_flags", "kidney"}, {"kidney_stage", "no_dialysis"}});
        c.put("condition:dialysis", new String[][]{{"health_flags", "kidney"}, {"kidney_stage", "dialysis"}});
        c.put("condition:pregnancy", new String[][]{{"health_flags", "pregnancy"}, {"pregnancy_stage", "t2"},
            {"pre_pregnancy_weight", "60"}});
        c.put("condition:breastfeeding", new String[][]{{"health_flags", "breastfeeding"}, {"pregnancy_stage", "bf0"}});
        return c;
    }

    static Profile profile(String[][] answers) {
        Profile p = new Profile();
        p.adultConfirmed = true;
        java.util.Arrays.fill(p.answers, "");
        p.answers[Profile.AGE] = "30"; p.answers[Profile.WEIGHT] = "65"; p.answers[Profile.HEIGHT] = "170";
        p.answers[Profile.ACTIVITY] = "Mostly sitting"; p.answers[Profile.GOALS] = "aware";
        for (String[] a : answers) {
            for (int k = 0; k < Profile.KEYS.length; k++) if (Profile.KEYS[k].equals(a[0])) p.answers[k] = a[1];
        }
        p.syncDerived();
        return p;
    }

    private static final class Graded {
        final SwapCatalogue.Product product;
        final NutriGrade.Result grade;
        Graded(SwapCatalogue.Product product, NutriGrade.Result grade) { this.product = product; this.grade = grade; }
    }

    static NutriGrade.Result grade(SwapCatalogue.Product p) {
        Beverage identity = Beverage.classify(p.displayName(), p.ingredients, p.per100.beverage);
        if (p.sweetener) identity = identity.withSweetener();
        return NutriGrade.grade(p.per100, identity, FoodCategory.kind(p.displayName(), p.groupId));
    }

    @Test public void measureCatalogueDepth() throws Exception {
        String dir = System.getenv("BC_COVERAGE_DIR");
        if (dir == null || dir.isEmpty() || !new File(dir, "products_2025.json").exists()) return;
        String catalogueJson = new String(Files.readAllBytes(new File("src/main/assets/catalogue/swaps_v1.json").toPath()), StandardCharsets.UTF_8);
        SwapCatalogue catalogue = SwapCatalogue.parse(catalogueJson);
        JSONObject wrapper = new JSONObject();
        wrapper.put("groups", new JSONObject(catalogueJson).getJSONArray("groups"));
        wrapper.put("products", new JSONArray(new String(Files.readAllBytes(new File(dir, "products_2025.json").toPath()), StandardCharsets.UTF_8)));
        SwapCatalogue all = SwapCatalogue.parse(wrapper.toString());

        // Every product graded and placed as the app would, for the database and the nutritionist.
        Map<String, List<Graded>> byGroup = new LinkedHashMap<>();
        try (PrintWriter out = new PrintWriter(new File(dir, "products_graded.jsonl"), "UTF-8")) {
            for (SwapCatalogue.Group g : all.groups) byGroup.put(g.id, new ArrayList<>());
            for (SwapCatalogue.Product p : all.products) {
                NutriGrade.Result r = grade(p);
                String group = p.groupId == null || p.groupId.isEmpty() ? null : p.groupId;
                FoodCategory.Placement placed = FoodCategory.place(p.displayName(), p.beverage, group);
                JSONObject row = new JSONObject();
                row.put("gtin", p.gtin);
                row.put("band", r.band.name());
                row.put("letter", String.valueOf(r.letter()));
                row.put("score", r.score);
                row.put("food_kind", FoodCategory.kind(p.displayName(), group).name());
                row.put("fssai_category", placed.category == null ? JSONObject.NULL : placed.category.number);
                row.put("infant_food", placed.infantFood);
                out.println(row);
                if (group != null && byGroup.containsKey(group)) byGroup.get(group).add(new Graded(p, r));
            }
        }

        Map<String, String[][]> choices = choices();
        Map<String, Profile> profiles = new LinkedHashMap<>();
        for (Map.Entry<String, String[][]> e : choices.entrySet()) profiles.put(e.getKey(), profile(e.getValue()));
        JSONObject coverage = new JSONObject();
        try (PrintWriter csv = new PrintWriter(new File(dir, "recommendations.csv"), "UTF-8")) {
            csv.println("group,group_name,profile_choice,rank,qualifies_for,originals,gtin,brand,name,band,letter,energy_kcal,sugars_g,"
                + "added_sugars_g,saturated_fat_g,sodium_mg,protein_g,fibre_g,carbohydrate_g,veg,status,source_url,why,nutritionist_decision");
            for (SwapCatalogue.Group group : catalogue.groups) {
                List<Graded> originals = originals(byGroup.getOrDefault(group.id, Collections.emptyList()));
                JSONObject g = new JSONObject();
                g.put("name", group.name);
                g.put("candidates", catalogue.inGroup(group.id).size());
                g.put("originals", originals.size());
                JSONObject perProfile = new JSONObject();
                for (Map.Entry<String, Profile> e : profiles.entrySet()) {
                    Map<String, int[]> hits = new LinkedHashMap<>();          // gtin -> {originals qualified, rank sum}
                    Map<String, SwapEngine.Swap> example = new LinkedHashMap<>();
                    List<Integer> counts = new ArrayList<>();
                    for (Graded o : originals) {
                        List<SwapEngine.Swap> q = SwapEngine.qualifying(catalogue, group, o.product.displayName(), o.product.per100,
                            o.grade.band, o.grade.letter(), o.product.beverage, e.getValue());
                        counts.add(q.size());
                        for (SwapEngine.Swap s : q) {
                            int[] h = hits.computeIfAbsent(s.product.gtin, k -> new int[2]);
                            h[0]++; h[1] += (int) Math.round(s.rank);
                            example.putIfAbsent(s.product.gtin, s);
                        }
                    }
                    Collections.sort(counts);
                    JSONObject cell = new JSONObject();
                    cell.put("distinct", hits.size());
                    cell.put("median_per_original", counts.isEmpty() ? 0 : counts.get(counts.size() / 2));
                    cell.put("min_per_original", counts.isEmpty() ? 0 : counts.get(0));
                    perProfile.put(e.getKey(), cell);
                    List<String> order = new ArrayList<>(hits.keySet());
                    order.sort((a, b) -> hits.get(b)[0] != hits.get(a)[0] ? hits.get(b)[0] - hits.get(a)[0] : hits.get(b)[1] - hits.get(a)[1]);
                    for (int i = 0; i < Math.min(LIST_PER_PROFILE, order.size()); i++) {
                        SwapEngine.Swap s = example.get(order.get(i));
                        Nutrition n = s.product.per100;
                        csv.println(String.join(",", group.id, q(group.name), e.getKey(), String.valueOf(i + 1),
                            String.valueOf(hits.get(order.get(i))[0]), String.valueOf(originals.size()), q(s.product.gtin), q(s.product.brand),
                            q(s.product.name), s.band.name(), q(String.valueOf(grade(s.product).letter())), num(n.energyKcal), num(n.sugarsG),
                            num(n.addedSugarsG), num(n.saturatedFatG), num(n.sodiumMg), num(n.proteinG), num(n.fibreG), num(n.carbohydrateG),
                            q(s.product.veg), q(s.product.status), q(s.product.sourceUrl), q(s.why), ""));
                    }
                }
                g.put("profiles", perProfile);
                coverage.put(group.id, g);
            }
        }
        Files.write(new File(dir, "coverage.json").toPath(), coverage.toString(1).getBytes(StandardCharsets.UTF_8));
    }

    /** Up to twelve of the group's red and amber products, spread across their scores, to swap from. */
    private static List<Graded> originals(List<Graded> group) {
        List<Graded> pool = new ArrayList<>();
        for (Graded g : group) if (g.grade.band != NutriGrade.Band.A) pool.add(g);
        pool.sort((a, b) -> Integer.compare(a.grade.score, b.grade.score));
        if (pool.size() <= ORIGINALS_PER_GROUP) return pool;
        List<Graded> spread = new ArrayList<>();
        for (int i = 0; i < ORIGINALS_PER_GROUP; i++) spread.add(pool.get(i * (pool.size() - 1) / (ORIGINALS_PER_GROUP - 1)));
        return spread;
    }

    private static String num(Double v) { return v == null ? "" : String.format(Locale.ROOT, "%.2f", v); }

    private static String q(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"").replace('\n', ' ') + "\"";
    }
}
