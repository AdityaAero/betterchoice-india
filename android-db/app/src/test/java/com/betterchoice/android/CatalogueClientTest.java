package com.betterchoice.android;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * The cache in front of the BetterChoice database: what is answered once is not asked again, a slow or failing network
 * never holds the bubble, and nothing stale or withdrawn stays on the phone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CatalogueClientTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static final long HOUR = 3_600_000L;
    private final long[] now = {System.currentTimeMillis()};
    private final List<String> asked = new ArrayList<>();
    private String version = "v4-a";
    private boolean down;
    private File dir;

    /** A database with one listed product (the catalogue's first, in its own category); names with "unlisted" are not listed. */
    private String hit() throws Exception {
        JSONObject raw = new JSONObject(new String(Files.readAllBytes(new File("src/main/assets/" + SwapCatalogue.ASSET).toPath()),
            StandardCharsets.UTF_8));
        JSONObject product = raw.getJSONArray("products").getJSONObject(0);
        JSONArray groups = raw.getJSONArray("groups");
        JSONObject group = null;
        for (int i = 0; i < groups.length(); i++) if (groups.getJSONObject(i).getString("id").equals(product.getString("group"))) group = groups.getJSONObject(i);
        return new JSONObject().put("v", version)
            .put("match", new JSONObject().put("gtin", product.getString("gtin")).put("name", product.getString("name")).put("score", 1).put("method", "exact"))
            .put("product", product).put("report", new JSONObject()).put("group", group).put("alternatives", new JSONArray()).toString();
    }

    private String miss() throws Exception {
        return new JSONObject().put("v", version).put("match", JSONObject.NULL)
            .put("nearest", new JSONArray().put(new JSONObject().put("name", "Something close").put("score", 0.4))).toString();
    }

    private CatalogueClient.Transport database() {
        return (body, timeoutMs) -> {
            asked.add(body.getString("p_name"));
            if (down) throw new java.io.IOException("offline");
            return body.getString("p_name").toLowerCase(java.util.Locale.ROOT).contains("unlisted") ? miss() : hit();
        };
    }

    private CatalogueClient client() { return new CatalogueClient(database(), dir, () -> now[0]); }

    @Before public void setUp() throws Exception { dir = folder.newFolder("catalogue-v4"); }

    @Test public void aProductSeenAgainIsAnsweredFromMemoryWithoutAsking() {
        CatalogueClient c = client();
        CatalogueDb.Lookup first = c.lookup("Britannia Good Day Cashew Cookies", "Cookies", "com.zeptoconsumerapp");
        assertTrue(first.listed);
        assertFalse(first.fromCache);
        CatalogueDb.Lookup again = c.lookup("britannia good-day  cashew COOKIES", "cookies", "in.swiggy.android");
        assertTrue("spacing, case and punctuation do not make a new question", again.fromCache);
        assertSame(first.match.product.gtin, again.match.product.gtin);
        assertEquals(1, asked.size());
        assertEquals(1, c.requests);
    }

    @Test public void theShopsProductTypeIsPartOfTheQuestion() {
        CatalogueClient c = client();
        c.lookup("Soya Chunks", "Soya Chunks", "com.zeptoconsumerapp");
        c.lookup("Soya Chunks", "Frozen Snacks", "com.zeptoconsumerapp");
        assertEquals(2, asked.size());
    }

    @Test public void answersSurviveARestartOnDisk() {
        client().lookup("Britannia Good Day Cashew Cookies", null, null);
        CatalogueDb.Lookup restarted = client().lookup("Britannia Good Day Cashew Cookies", null, null);
        assertTrue(restarted.listed);
        assertTrue(restarted.fromCache);
        assertEquals(1, asked.size());
    }

    @Test public void aListedAnswerLastsADayAndANotListedOneSixHours() {
        CatalogueClient c = client();
        c.lookup("Britannia Good Day Cashew Cookies", null, null);
        assertFalse(c.lookup("Unlisted Local Namkeen", null, null).listed);
        now[0] += 5 * HOUR;
        c.lookup("Britannia Good Day Cashew Cookies", null, null);
        c.lookup("Unlisted Local Namkeen", null, null);
        assertEquals("both still cached at 5 h", 2, asked.size());
        now[0] += 2 * HOUR;
        assertFalse(c.lookup("Unlisted Local Namkeen", null, null).fromCache);
        assertEquals("the miss is asked again after 6 h", 3, asked.size());
        c.lookup("Britannia Good Day Cashew Cookies", null, null);
        assertEquals("the hit is still cached at 7 h", 3, asked.size());
        now[0] += 18 * HOUR;
        assertFalse(c.lookup("Britannia Good Day Cashew Cookies", null, null).fromCache);
        assertEquals("the hit is asked again after 24 h", 4, asked.size());
    }

    @Test public void aNewCatalogueVersionDropsEveryOlderAnswer() throws Exception {
        CatalogueClient c = client();
        c.lookup("Britannia Good Day Cashew Cookies", null, null);
        c.lookup("Unlisted Local Namkeen", null, null);
        assertEquals(2, dir.listFiles((d, n) -> n.endsWith(".json")).length);
        version = "v4-b";
        now[0] += 7 * HOUR;                                  // the miss expires and is asked again: the answer is v4-b
        c.lookup("Unlisted Local Namkeen", null, null);
        assertEquals("only the new version's answer is kept", 1, dir.listFiles((d, n) -> n.endsWith(".json")).length);
        assertEquals("v4-b", new String(Files.readAllBytes(new File(dir, "version.txt").toPath()), StandardCharsets.UTF_8));
        c.lookup("Britannia Good Day Cashew Cookies", null, null);
        assertEquals("the old version's hit is asked again", 4, asked.size());
    }

    @Test public void aFailureIsNotCachedAndPausesOnlyThatProduct() {
        CatalogueClient c = client();
        down = true;
        assertTrue(c.lookup("Britannia Good Day Cashew Cookies", null, null).failed);
        assertTrue(c.lookup("Britannia Good Day Cashew Cookies", null, null).failed);
        assertEquals("a failing product is not asked again straight away", 1, asked.size());
        down = false;
        assertFalse("another product is asked", c.lookup("Unlisted Local Namkeen", null, null).fromCache);
        assertEquals(2, asked.size());
        assertTrue("the shopper's Try again skips the pause", c.lookup("Britannia Good Day Cashew Cookies", null, null, true).listed);
        assertEquals(3, asked.size());
        assertEquals("nothing was written for the failure", 2, dir.listFiles((d, n) -> n.endsWith(".json")).length);
    }

    @Test public void thePauseAfterAFailureEnds() {
        CatalogueClient c = client();
        down = true;
        c.lookup("Britannia Good Day Cashew Cookies", null, null);
        down = false;
        now[0] += CatalogueClient.BACKOFF_MS + 1;
        assertTrue(c.lookup("Britannia Good Day Cashew Cookies", null, null).listed);
        assertEquals(2, asked.size());
    }

    @Test public void aMalformedAnswerIsAFailureAndIsNeverCached() {
        CatalogueClient c = new CatalogueClient((body, timeout) -> { asked.add("x"); return "<html>gateway</html>"; }, dir, () -> now[0]);
        assertTrue(c.lookup("Britannia Good Day Cashew Cookies", null, null).failed);
        assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".json")).length);
    }

    @Test public void twoScreensAskingTogetherShareOneRequest() throws Exception {
        CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        String answer = hit();
        CatalogueClient c = new CatalogueClient((body, timeout) -> {
            calls.incrementAndGet();
            inside.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return answer;
        }, null, () -> now[0]);
        CatalogueDb.Lookup[] results = new CatalogueDb.Lookup[2];
        Thread first = new Thread(() -> results[0] = c.lookup("Britannia Good Day Cashew Cookies", null, null));
        first.start();
        assertTrue(inside.await(5, TimeUnit.SECONDS));
        Thread second = new Thread(() -> results[1] = c.lookup("Britannia Good Day Cashew Cookies", null, null));
        second.start();
        Thread.sleep(100);
        release.countDown();
        first.join(5000);
        second.join(5000);
        assertEquals(1, calls.get());
        assertTrue(results[0].listed);
        assertTrue(results[1].listed);
    }

    @Test public void aSlowDatabaseNeverHoldsTheBubbleBeyondTheLimitAndItsLateAnswerIsKept() throws Exception {
        String answer = hit();
        CountDownLatch answered = new CountDownLatch(1);
        CatalogueClient c = new CatalogueClient((body, timeout) -> {
            asked.add(body.getString("p_name"));
            Thread.sleep(CatalogueClient.TIMEOUT_MS + 600L);
            answered.countDown();
            return answer;
        }, dir, () -> now[0]);
        long started = System.nanoTime();
        CatalogueDb.Lookup slow = c.lookup("Britannia Good Day Cashew Cookies", null, null);
        long waited = (System.nanoTime() - started) / 1_000_000;
        assertTrue(slow.failed);
        assertTrue("waited " + waited + " ms", waited >= CatalogueClient.TIMEOUT_MS - 50 && waited <= CatalogueClient.TIMEOUT_MS + 400);
        assertTrue(answered.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        CatalogueDb.Lookup next = c.lookup("Britannia Good Day Cashew Cookies", null, null);
        assertTrue("the late answer was cached", next.listed && next.fromCache);
        assertEquals(1, asked.size());
    }

    @Test public void memoryHoldsTheMostRecentNamesOnly() {
        CatalogueClient c = new CatalogueClient(database(), null, () -> now[0]);
        for (int i = 0; i <= CatalogueClient.MEMORY; i++) c.lookup("Unlisted product " + i, null, null);
        c.lookup("Unlisted product " + CatalogueClient.MEMORY, null, null);
        assertEquals("the latest is remembered", CatalogueClient.MEMORY + 1, asked.size());
        c.lookup("Unlisted product 0", null, null);
        assertEquals("the oldest was let go", CatalogueClient.MEMORY + 2, asked.size());
    }

    @Test public void oldAnswersAreDeletedOnStartAndAllOfThemWhenConsentIsWithdrawn() throws Exception {
        client().lookup("Britannia Good Day Cashew Cookies", null, null);
        File[] saved = dir.listFiles((d, n) -> n.endsWith(".json"));
        assertEquals(1, saved.length);
        assertTrue(saved[0].setLastModified(now[0] - 25 * HOUR));
        File leftover = new File(dir, "abc.json.tmp");
        assertTrue(leftover.createNewFile());
        CatalogueClient restarted = client();
        restarted.lookup("Unlisted Local Namkeen", null, null);
        assertFalse("a day-old answer is gone", saved[0].exists());
        assertFalse("a half-written file is gone", leftover.exists());
        restarted.forget();
        assertEquals("nothing stays after consent is withdrawn", 0, dir.listFiles().length);
        restarted.lookup("Britannia Good Day Cashew Cookies", null, null);
        assertEquals("and the next lookup asks the database", 3, asked.size());
    }

    @Test public void onlyTheNameTypeAndShopAreSent() throws Exception {
        JSONObject[] sent = new JSONObject[1];
        String answer = hit();
        new CatalogueClient((body, timeout) -> { sent[0] = body; return answer; }, null, () -> now[0])
            .lookup("Britannia Good Day Cashew Cookies", "Cookies", "com.zeptoconsumerapp");
        List<String> keys = new ArrayList<>();
        sent[0].keys().forEachRemaining(keys::add);
        java.util.Collections.sort(keys);
        assertEquals(List.of("p_name", "p_retailer", "p_type"), keys);
    }

    @Test public void anEmptyNameIsNeverSent() {
        CatalogueClient c = client();
        assertTrue(c.lookup("  ", null, null).failed);
        assertTrue(c.lookup(null, "Cookies", null).failed);
        assertEquals(0, asked.size());
    }
}
