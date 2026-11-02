package com.betterchoice.android;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Version 4's database contents, computed by the app's own engine so the database and the phone can never disagree.
 * For every catalogue product: its report (colour, letter, the lines the card shows) and its better candidates, which
 * are the union, over the twelve possible goal sets, of what is better for that goal before anything personal is
 * applied. Conditions, allergies, diet and a goal's own limits only ever remove candidates (and are counted as "left
 * out"), so the phone, applying them privately to this union, reaches the same three choices and the same counts as
 * version 3 does from the whole catalogue.
 * Runs only when BC_DB_EXPORT_DIR is set; writes products_export.jsonl there.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class DbExportHarness {
    /** Never reached today: the largest category holds 102 products. */
    static final int MAX_ALTERNATIVES = 120;

    static SwapCatalogue catalogue() throws Exception {
        return SwapCatalogue.parse(new String(Files.readAllBytes(new File("src/main/assets/catalogue/swaps_v1.json").toPath()),
            StandardCharsets.UTF_8));
    }

    static SwapCatalogue.Group groupOf(SwapCatalogue c, SwapCatalogue.Product p) {
        for (SwapCatalogue.Group g : c.groups) if (g.id.equals(p.groupId)) return g;
        return null;
    }

    /**
     * The union of better candidates for one product: collected best first (no goal first, then each goal set in turn,
     * so a cap would keep the best), then put in catalogue order. The phone walks them in that order, as version 3 walks
     * the whole catalogue, so equal-ranked choices and the "left out" counts come out in the same order too.
     */
    static List<String> alternatives(SwapCatalogue c, SwapCatalogue.Product p, GradeResult rated, List<Profile> goalProfiles) {
        SwapCatalogue.Group group = groupOf(c, p);
        Map<String, Integer> order = new LinkedHashMap<>();
        if (group == null || rated.state != GradeResult.State.GRADED) return new ArrayList<>();
        for (Profile profile : goalProfiles) {
            List<SwapEngine.Swap> q = SwapEngine.betterBeforePersonal(c, group, p.displayName(), rated.nutrition, rated.band,
                rated.letter, rated.drinkKind != null, profile);
            for (SwapEngine.Swap s : q) if (!s.product.gtin.equals(p.gtin)) order.putIfAbsent(s.product.gtin, order.size());
        }
        List<String> out = new ArrayList<>(order.keySet());
        if (out.size() > MAX_ALTERNATIVES) out = new ArrayList<>(out.subList(0, MAX_ALTERNATIVES));
        Map<String, Integer> position = new java.util.HashMap<>();
        for (int i = 0; i < c.products.size(); i++) position.putIfAbsent(c.products.get(i).gtin, i);
        out.sort(java.util.Comparator.comparingInt(position::get));
        return out;
    }

    static List<Profile> goalProfiles() {
        List<Profile> out = new ArrayList<>();
        for (String goals : DecisionMatrixTest.goalSets()) out.add(CardLayoutTest.profile("", goals.isEmpty() ? "aware" : goals));
        return out;
    }

    @Test public void export() throws Exception {
        String dir = System.getenv("BC_DB_EXPORT_DIR");
        if (dir == null || dir.isEmpty()) return;
        SwapCatalogue c = catalogue();
        List<Profile> goals = goalProfiles();
        new File(dir).mkdirs();
        try (PrintWriter out = new PrintWriter(new File(dir, "products_export.jsonl"), "UTF-8")) {
            for (SwapCatalogue.Product p : c.products) {
                GradeResult r = CatalogueDb.grade(p, null);
                JSONObject row = new JSONObject().put("gtin", p.gtin).put("id", p.id);
                JSONObject report = new JSONObject().put("state", r.state.name());
                if (r.state == GradeResult.State.GRADED) {
                    CardFacts facts = CardFacts.of(r, RecommendationContext.of(null));
                    report.put("band", r.band.name()).put("letter", String.valueOf(r.letter))
                        .put("colour", r.band == NutriGrade.Band.A ? "green" : r.band == NutriGrade.Band.B ? "yellow" : "red")
                        .put("scored_as", r.drinkKind == null ? "food" : "drink: " + r.drinkKind.label)
                        .put("per", r.nutrition.unit())
                        .put("red", lines(facts.bad, facts.moreBad)).put("green", lines(facts.good, facts.moreGood))
                        .put("ingredient_good", new JSONArray(facts.ingredientGood))
                        .put("ingredient_concerns", new JSONArray(facts.ingredientConcerns))
                        .put("ingredient_information", new JSONArray(r.ingredients == null ? new ArrayList<>() : r.ingredients.information))
                        .put("notes", new JSONArray(r.notes)).put("caveats", new JSONArray(r.caveats));
                } else {
                    report.put("note", r.note == null ? JSONObject.NULL : r.note);
                }
                row.put("report", report);
                row.put("alternatives", new JSONArray(alternatives(c, p, r, goals)));
                out.println(row);
            }
        }
    }

    private static JSONArray lines(List<CardFacts.Line> face, List<CardFacts.Line> more) {
        JSONArray a = new JSONArray();
        for (CardFacts.Line l : face) a.put(l.text);
        for (CardFacts.Line l : more) a.put(l.text);
        return a;
    }
}
