package com.betterchoice.android;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Every number has a source on file, and the calculator types no number of its own. The quotes
 * themselves are checked against the documents by tools/evidence_check.py.
 */
public class EvidenceRegistryTest {
    private static File project(String relative) {
        File inApp = new File("../" + relative);
        return inApp.exists() ? inApp : new File(relative);
    }

    @Test public void everyEntryCitesAFileThatExists() {
        assertTrue(Evidence.all().size() >= 120);
        Set<String> ids = new HashSet<>();
        for (Evidence.Entry e : Evidence.all()) {
            assertTrue("duplicate " + e.id, ids.add(e.id));
            assertTrue(e.id + " cites a missing file " + e.file, project(e.file).exists());
            assertFalse(e.id + " has no quote", e.quote.isBlank());
            assertFalse(e.id + " has no locator", e.locator.isBlank());
            e.number();   // parses
            if (e.status == Evidence.Status.NEEDS_REVIEW) assertNotNull(e.id + " must say why it needs review", e.note);
        }
    }

    /** Only structural numbers may appear in the calculator: no hidden nutrition constant. */
    @Test public void theCalculatorTypesNoNutritionNumber() throws Exception {
        String source = new String(Files.readAllBytes(project("app/src/main/java/com/betterchoice/android/NutritionCalculator.java").toPath()),
            StandardCharsets.UTF_8);
        String code = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ").replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
        Set<String> allowed = Set.of("0", "1", "2", "3", "100", "1000");
        Matcher m = Pattern.compile("(?<![\\w.])\\d+(?:\\.\\d+)?(?![\\w.])").matcher(code);
        while (m.find()) assertTrue("unregistered number " + m.group() + " in NutritionCalculator", allowed.contains(m.group()));
    }

    /**
     * The four rules 3.5.0 showed as provisional now rest on a guideline or regulation: FSSAI's
     * comparative-claim rule (30%, which replaced Codex's 25% heuristic), ICMR's vegetarian protein,
     * ISSN's muscle range and KDIGO 4.1.8 on phosphate additives. The BMI-adjusted protein, which no
     * source on file supports, is gone.
     */
    @Test public void formerlyProvisionalRulesNowCiteAGuidelineOrRegulation() {
        for (String id : new String[]{"fssai.claims.comparative.min", "icmr.protein.vegetarian", "issn.protein.muscle.min",
                "kdigo.phosphate.additives"}) {
            Evidence.Entry e = Evidence.get(id);
            assertEquals(id, Evidence.Status.VERIFIED, e.status);
            assertTrue(id, e.file.startsWith("security/evidence/"));
        }
        assertEquals(0.30, Evidence.d("fssai.claims.comparative.min").doubleValue(), 0);
        for (String gone : new String[]{"goal.min.improvement", "nin.protein.reference.bmi", "kir.phosphate.additives.ckd"}) {
            try { Evidence.get(gone); fail(gone + " should have been removed"); } catch (IllegalArgumentException expected) { }
        }
    }

    /** An entry still awaiting review documents a conflict; the app never computes with it. */
    @Test public void entriesAwaitingReviewAreNotUsedByTheApp() throws Exception {
        File dir = project("app/src/main/java/com/betterchoice/android");
        StringBuilder code = new StringBuilder();
        for (File f : dir.listFiles((d, n) -> n.endsWith(".java") && !n.equals("Evidence.java"))) {
            code.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        }
        for (Evidence.Entry e : Evidence.all()) {
            if (e.status == Evidence.Status.NEEDS_REVIEW) assertFalse(e.id + " is used", code.toString().contains("\"" + e.id + "\""));
        }
    }
}
