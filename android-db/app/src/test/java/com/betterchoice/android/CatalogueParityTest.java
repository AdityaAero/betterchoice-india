package com.betterchoice.android;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Version 4 makes the same choices as version 3. For every catalogue product, the database's answer (built here exactly
 * as the seed and public.bc_lookup build it, and read back through the same JSON) gives the card the same three choices,
 * the same reasons and the same "left out" counts as version 3's engine over the whole catalogue, for shoppers with
 * different diets, allergies, conditions, body sizes and goals, in debug (drafts shown) and release (verified only).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CatalogueParityTest {
    private static SwapCatalogue catalogue;
    private static final Map<String, JSONObject> productDocs = new HashMap<>();
    private static final Map<String, JSONObject> groupDocs = new HashMap<>();
    private static final Occasion OCCASION = Occasion.fromClock(LocalDateTime.of(2026, 10, 15, 16, 30));

    @BeforeClass public static void load() throws Exception {
        String json = new String(Files.readAllBytes(new File("src/main/assets/" + SwapCatalogue.ASSET).toPath()), StandardCharsets.UTF_8);
        catalogue = SwapCatalogue.parse(json);
        JSONObject raw = new JSONObject(json);
        JSONArray products = raw.getJSONArray("products"), groups = raw.getJSONArray("groups");
        for (int i = 0; i < products.length(); i++) productDocs.put(products.getJSONObject(i).getString("gtin"), products.getJSONObject(i));
        for (int i = 0; i < groups.length(); i++) groupDocs.put(groups.getJSONObject(i).getString("id"), groups.getJSONObject(i));
    }

    /** Shoppers chosen to reach every veto, constraint, objective and override the engine has. */
    static List<Profile> shoppers() {
        List<Profile> out = new ArrayList<>();
        out.add(null);                                                     // no form filled in
        out.add(CardLayoutTest.profile("", "aware"));
        out.add(DecisionMatrixTest.profile("Woman", "Vegetarian", "loss", List.of("diabetes"), new String[]{"65", "170"}, ""));
        out.add(DecisionMatrixTest.profile("Man", "Vegan", "muscle", List.of("hypertension"), new String[]{"50", "175"}, "peanut,tree_nut,sesame"));
        out.add(DecisionMatrixTest.profile("Woman", "Jain", "", List.of("pregnancy"), new String[]{"65", ""}, "milk"));
        out.add(DecisionMatrixTest.profile("Man", "Non-vegetarian (halal)", "gain,muscle", List.of("kidney-nd"), new String[]{"95", "170"}, ""));
        out.add(DecisionMatrixTest.profile("Non-binary", "Eggetarian", "sugar", List.of("cholesterol", "lactose"), new String[]{"75", "170"}, ""));
        out.add(DecisionMatrixTest.profile("Woman", "", "loss,sugar", List.of("breastfeeding"), new String[]{"50", "175"}, ""));
        out.add(DecisionMatrixTest.profile("Man", "Non-vegetarian / both", "gain", List.of("kidney-d", "hypertension"), new String[]{"65", "170"}, "milk"));
        out.add(DecisionMatrixTest.profile("Woman", "Vegetarian", "muscle,sugar", List.of("diabetes", "cholesterol"), new String[]{"95", "170"}, "peanut,tree_nut,sesame"));
        return out;
    }

    /** public.bc_lookup's answer for an exact match: the record, its report, its category and its candidates in order. */
    static String answer(SwapCatalogue.Product p, List<String> alternatives) throws Exception {
        JSONArray docs = new JSONArray();
        for (String gtin : alternatives) docs.put(productDocs.get(gtin));
        JSONObject group = new JSONObject(groupDocs.get(p.groupId).toString());
        group.remove("allow_step_within_red");
        return new JSONObject().put("v", "parity")
            .put("match", new JSONObject().put("gtin", p.gtin).put("name", p.displayName()).put("score", 1).put("method", "exact"))
            .put("product", productDocs.get(p.gtin)).put("report", new JSONObject()).put("group", group)
            .put("alternatives", docs).toString();
    }

    private static String summary(SwapEngine.Outcome o) {
        List<String> picked = new ArrayList<>();
        for (SwapEngine.Swap s : o.swaps) picked.add(s.product.gtin + " " + s.band + " " + s.why);
        return (o.group == null ? "-" : o.group.id) + " " + picked + " empty=" + o.emptyReason + " short=" + o.shortfall
            + " left=" + o.leftOut + " hidden=" + o.hiddenForSafety;
    }

    /** The candidates the seed wrote (DbExportHarness, BC_DB_EXPORT_DIR), or null when no export is present. */
    private static Map<String, List<String>> exported() throws Exception {
        File export = new File("../../export/products_export.jsonl");
        if (!export.exists()) return null;
        Map<String, List<String>> out = new HashMap<>();
        for (String line : Files.readAllLines(export.toPath(), StandardCharsets.UTF_8)) {
            JSONObject row = new JSONObject(line);
            List<String> alternatives = new ArrayList<>();
            JSONArray a = row.getJSONArray("alternatives");
            for (int i = 0; i < a.length(); i++) alternatives.add(a.getString(i));
            out.put(row.getString("gtin"), alternatives);
        }
        return out;
    }

    /**
     * Every product, every shopper: the same choices, reasons and counts. Debug shows draft records as choices, release
     * only checked ones; every record is a draft today, so release is compared for one shopper (it offers none in both).
     * The seed's candidates are checked in the same pass: they must be the engine's current ones, or the seed is stale.
     */
    @Test public void theDatabaseAnswerGivesVersion3sChoicesForEveryProductAndShopper() throws Exception {
        List<Profile> goals = DbExportHarness.goalProfiles();
        List<Profile> shoppers = shoppers();
        Map<String, List<String>> seeded = exported();
        int compared = 0, withChoices = 0;
        List<String> differences = new ArrayList<>(), stale = new ArrayList<>();
        for (SwapCatalogue.Product p : catalogue.products) {
            GradeResult rated = CatalogueDb.grade(p, null);
            List<String> candidates = DbExportHarness.alternatives(catalogue, p, rated, goals);
            if (seeded != null && !candidates.equals(seeded.get(p.gtin))) stale.add(p.displayName());
            if (rated.state != GradeResult.State.GRADED) continue;
            SwapCatalogue.Group group = DbExportHarness.groupOf(catalogue, p);
            CatalogueDb.Match m = CatalogueDb.parse(answer(p, candidates)).match;
            assertNotNull(p.gtin, m);
            assertEquals(p.gtin, m.product.gtin);
            assertEquals("the record reads back unchanged", p.displayName(), m.product.displayName());
            String name = p.displayName();
            boolean drink = rated.drinkKind != null;
            for (int i = 0; i < shoppers.size(); i++) for (boolean drafts : i == 0 ? new boolean[]{true, false} : new boolean[]{true}) {
                Profile shopper = shoppers.get(i);
                SafetyProfile safety = SafetyProfile.fromProfile(shopper);
                SwapEngine.Outcome v3 = SwapEngine.recommendIn(catalogue, group, name, rated.nutrition, rated.band, rated.letter,
                    drink, safety, OCCASION, drafts, shopper);
                SwapEngine.Outcome v4 = SwapEngine.recommendIn(m.alternatives, m.group, name, rated.nutrition, rated.band, rated.letter,
                    drink, safety, OCCASION, drafts, shopper);
                compared++;
                if (!v3.swaps.isEmpty()) withChoices++;
                String a = summary(v3), b = summary(v4);
                if (!a.equals(b) && differences.size() < 10) differences.add(name + "\n  v3 " + a + "\n  v4 " + b);
            }
        }
        assertEquals("v4 must choose exactly as v3:\n" + String.join("\n", differences), Collections.emptyList(), differences);
        assertEquals("the seed is stale: re-run DbExportHarness and tools/v4_supabase_seed.py", Collections.emptyList(), stale);
        assertTrue("every graded product compared for every shopper: " + compared, compared > 900 * 11);
        assertTrue("the comparison covers real choices: " + withChoices + " of " + compared, withChoices > compared / 4);
        System.out.println("parity: " + compared + " comparisons, " + withChoices + " with choices, seed "
            + (seeded == null ? "not checked (no export)" : "current"));
    }

    /** The rating on the card comes from the record itself, identically however the shop words the product's name. */
    @Test public void theRatingIsTheRecordsWhateverTheShopCallsIt() throws Exception {
        int checked = 0;
        for (SwapCatalogue.Product p : catalogue.products) {
            CatalogueDb.Match m = CatalogueDb.parse(answer(p, Collections.emptyList())).match;
            GradeResult direct = CatalogueDb.grade(p, null);
            for (String title : Arrays.asList(p.displayName(), p.displayName().toUpperCase(java.util.Locale.ROOT) + " | New Pack")) {
                GradeResult shown = CatalogueDb.rate(ProductPage.of(title, 99.0, "com.zeptoconsumerapp"), m);
                assertEquals(title, direct.state, shown.state);
                assertEquals(title, direct.band, shown.band);
                assertEquals(title, direct.letter, shown.letter);
                assertSame(title, m, shown.db);
                assertEquals("the shop's page is the one shown", title, shown.page.name);
                checked++;
            }
        }
        assertEquals(catalogue.products.size() * 2, checked);
    }
}
