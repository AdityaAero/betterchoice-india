package com.betterchoice.android;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/**
 * Version 4's way to the BetterChoice database, built for the lowest latency a shopper can feel:
 * <ul>
 *   <li>one request per product (the database answers with the record, its category and its better candidates);</li>
 *   <li>a memory cache (most recent {@value #MEMORY} names) and a disk cache that survives restarts: a product seen
 *       again costs nothing, and a name the database does not list is remembered for {@value #MISS_HOURS} hours;</li>
 *   <li>two screens asking for the same name while the first request is in flight share it;</li>
 *   <li>a hard {@value #TIMEOUT_MS} ms limit on every wait (the request itself runs on its own thread, and an answer
 *       that arrives later is still cached for the next look), and after a failure a pause of {@value #BACKOFF_MS} ms
 *       for that name, so a slow network never holds the bubble or hammers the server;</li>
 *   <li>the whole cache is dropped when the database's catalogue version changes.</li>
 * </ul>
 * The disk cache lives in the app's private cache folder. Answers older than {@value #HIT_HOURS} hours are deleted the
 * first time the cache is used after a start, and all of it when the shopper withdraws consent.
 * Only the product's name, the shop's own product type and the shop are sent: never the shopper's identity or health.
 */
final class CatalogueClient {
    interface Transport { String lookup(JSONObject body, int timeoutMs) throws Exception; }
    interface Clock { long now(); }

    static final int MEMORY = 256;
    static final int TIMEOUT_MS = 2500;
    static final long HIT_HOURS = 24, MISS_HOURS = 6, BACKOFF_MS = 20_000;
    private static final long HOUR_MS = 3_600_000L;

    private static final class Cached {
        final long at;
        final String body;
        final CatalogueDb.Lookup lookup;
        Cached(long at, String body, CatalogueDb.Lookup lookup) { this.at = at; this.body = body; this.lookup = lookup; }
    }

    private final Transport transport;
    private final File dir;
    private final Clock clock;
    private final Map<String, Cached> memory = new LinkedHashMap<String, Cached>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) { return size() > MEMORY; }
    };
    private final ConcurrentHashMap<String, FutureTask<CatalogueDb.Lookup>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService network = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "catalogue-net");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    /** The catalogue version the cached answers belong to; a different one in a fresh answer clears them. */
    private volatile String version;
    /** Network requests made, for tests and the debug log. */
    volatile int requests;
    private boolean opened;

    /** Touches no file: the folder is opened on the first lookup, which never runs on the main thread. */
    CatalogueClient(Transport transport, File dir, Clock clock) {
        this.transport = transport;
        this.dir = dir;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Once per start: the folder, the catalogue version the saved answers belong to, and old answers deleted. */
    private synchronized void open() {
        if (opened || dir == null) { opened = true; return; }
        opened = true;
        if (!dir.isDirectory() && !dir.mkdirs()) DebugLog.failure("Catalogue cache folder unavailable");
        File stamp = new File(dir, "version.txt");
        try { if (stamp.exists()) version = new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8).trim(); }
        catch (Exception unreadable) { version = null; }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json") || n.endsWith(".tmp"));
        if (files != null) for (File f : files) {
            if (f.getName().endsWith(".tmp") || clock.now() - f.lastModified() > HIT_HOURS * HOUR_MS) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    /** Consent withdrawn: nothing the database answered stays on the phone. */
    synchronized void forget() {
        synchronized (memory) { memory.clear(); }
        failedAt.clear();
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        version = null;
        opened = false;
    }

    /** The database's answer for the product on screen: from memory, disk or the network, in that order. Never null. */
    CatalogueDb.Lookup lookup(String name, String productType, String retailer) {
        return lookup(name, productType, retailer, false);
    }

    /** @param userAsked the shopper tapped "Try again": the pause after a failure is skipped */
    CatalogueDb.Lookup lookup(String name, String productType, String retailer, boolean userAsked) {
        String key = key(name, productType);
        if (key.isEmpty()) return CatalogueDb.Lookup.failed();
        open();
        Cached cached = cached(key);
        if (cached != null) return cached.lookup.cached();
        Long failed = failedAt.get(key);
        if (!userAsked && failed != null && clock.now() - failed < BACKOFF_MS) return CatalogueDb.Lookup.failed();
        FutureTask<CatalogueDb.Lookup> task = new FutureTask<>(() -> fetch(key, name, productType, retailer));
        FutureTask<CatalogueDb.Lookup> running = inFlight.putIfAbsent(key, task);
        if (running == null) {
            running = task;
            network.execute(() -> { try { task.run(); } finally { inFlight.remove(key, task); } });
        }
        try { return running.get(TIMEOUT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return CatalogueDb.Lookup.failed(); }
        catch (Exception slow) { return CatalogueDb.Lookup.failed(); }
    }

    private CatalogueDb.Lookup fetch(String key, String name, String productType, String retailer) {
        try {
            JSONObject body = new JSONObject().put("p_name", name)
                .put("p_type", productType == null ? JSONObject.NULL : productType)
                .put("p_retailer", retailer == null ? JSONObject.NULL : retailer);
            requests++;
            String answer = transport.lookup(body, TIMEOUT_MS);
            CatalogueDb.Lookup lookup = CatalogueDb.parse(answer);
            store(key, answer, lookup);
            failedAt.remove(key);
            return lookup;
        } catch (Exception unavailable) {
            failedAt.put(key, clock.now());
            DebugLog.failure("Catalogue lookup unavailable");
            return CatalogueDb.Lookup.failed();
        }
    }

    private Cached cached(String key) {
        Cached e;
        synchronized (memory) { e = memory.get(key); }
        if (e == null) e = fromDisk(key);
        if (e == null) return null;
        long ttl = (e.lookup.listed ? HIT_HOURS : MISS_HOURS) * HOUR_MS;
        if (clock.now() - e.at > ttl) {
            synchronized (memory) { memory.remove(key); }
            if (dir != null) //noinspection ResultOfMethodCallIgnored
                file(key).delete();
            return null;
        }
        synchronized (memory) { memory.put(key, e); }
        return e;
    }

    private void store(String key, String body, CatalogueDb.Lookup lookup) {
        if (lookup.version != null && !lookup.version.equals(version)) clear(lookup.version);
        Cached e = new Cached(clock.now(), body, lookup);
        synchronized (memory) { memory.put(key, e); }
        if (dir == null) return;
        try {
            JSONObject saved = new JSONObject().put("at", e.at).put("body", body);
            File target = file(key), temp = new File(dir, target.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(saved.toString().getBytes(StandardCharsets.UTF_8)); }
            if (!temp.renameTo(target)) { //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        } catch (Exception unwritable) {
            DebugLog.failure("Catalogue cache unwritable");
        }
    }

    private Cached fromDisk(String key) {
        if (dir == null) return null;
        File f = file(key);
        if (!f.exists()) return null;
        try {
            JSONObject saved = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            String body = saved.getString("body");
            CatalogueDb.Lookup lookup = CatalogueDb.parse(body);
            if (version != null && lookup.version != null && !version.equals(lookup.version)) return null;
            return new Cached(saved.getLong("at"), body, lookup);
        } catch (Exception unreadable) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return null;
        }
    }

    /** A new catalogue version: every cached answer belongs to the old one. */
    private synchronized void clear(String newVersion) {
        synchronized (memory) { memory.clear(); }
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
                f.delete();
            try { Files.write(new File(dir, "version.txt").toPath(), newVersion.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception unwritable) { DebugLog.failure("Catalogue cache unwritable"); }
        }
        version = newVersion;
    }

    private File file(String key) { return new File(dir, sha256(key) + ".json"); }

    /** The cache key: the name and the shop's type, lower-case, punctuation and spacing evened out. */
    static String key(String name, String productType) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (n.isEmpty()) return "";
        String t = productType == null ? "" : productType.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return n + "|" + t;
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format(Locale.ROOT, "%02x", d[i]));
            return hex.toString();
        } catch (Exception impossible) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /**
     * The live transport: one POST to the database's lookup function with the public (publishable) key. The connection
     * is left open for reuse (keep-alive), and the answer arrives gzip-compressed.
     */
    static Transport https() {
        return (body, timeoutMs) -> {
            if (!AuthConfig.ready()) throw new IllegalStateException("The BetterChoice database is not configured in this build.");
            HttpsURLConnection c = (HttpsURLConnection) new URL(AuthConfig.base() + "/rest/v1/rpc/bc_lookup").openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            c.setRequestMethod("POST");
            c.setRequestProperty("apikey", BuildConfig.AUTH_PUBLIC_KEY);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(data.length);
            try (var out = c.getOutputStream()) { out.write(data); }
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) {
                c.disconnect();
                throw new IllegalStateException("Catalogue lookup failed: " + status);
            }
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (bytes.size() + n > 1_048_576) throw new IllegalStateException("Catalogue answer too large");
                    bytes.write(buffer, 0, n);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        };
    }
}
