package com.betterchoice.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * DEBUG BUILDS ONLY. Two testing aids that the release source set replaces with a stub:
 * 1. When no Supabase project is configured, sign-in runs against this local stand-in.
 * 2. "Skip sign-in" creates a local tester account even when Supabase IS configured, so the
 *    questions, trial and bubble can be tested without waiting for an email.
 * Everything stays on this phone: no request reaches Supabase while a bypass session is active.
 */
final class TestMode {
    static final String CODE = "123456";
    static final String BYPASS_LABEL = "Try as guest (test build only)";
    static final String TESTER = "tester@betterchoice.test";
    private static final String FILE = "test_mode_backend";
    private static final String BYPASS = "bypass_active";

    /** True when there is no Supabase configuration at all, so everything must run locally. */
    static boolean enabled() { return !AuthConfig.ready(); }

    /** The skip-sign-in button is offered in debug builds only. */
    static boolean canBypass(Context context) { return !bypassActive(context); }

    static boolean bypassActive(Context context) { return prefs(context).getBoolean(BYPASS, false); }

    static void startBypass(Context context) { prefs(context).edit().putBoolean(BYPASS, true).commit(); }

    static void endBypass(Context context) { prefs(context).edit().clear().commit(); }

    /** Routes to Supabase normally, and to the local stand-in when there is no project or a bypass is active. */
    static AuthApi.Transport transport(Context context) {
        Context app = context.getApplicationContext();
        AuthApi.Transport real = AuthApi.https();
        LocalBackend local = new LocalBackend(app);
        return (method, path, body, token) ->
            enabled() || bypassActive(app) ? local.request(method, path, body, token) : real.request(method, path, body, token);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // Synchronous commits keep the stand-in backend consistent across process restarts during testing.
    @android.annotation.SuppressLint("ApplySharedPref")
    private static final class LocalBackend implements AuthApi.Transport {
        private final SharedPreferences store;
        LocalBackend(Context context) { store = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

        @Override public synchronized String request(String method, String path, JSONObject body, String token) throws Exception {
            Thread.sleep(250); // Keep the loading state visible, like a real network call.
            if (path.startsWith("/auth/v1/otp")) return "{}";
            if (path.startsWith("/auth/v1/verify")) {
                if (!CODE.equals(body.optString("token"))) {
                    throw new AuthApi.Failure(400, "That code is invalid or expired. In test mode the code is " + CODE + ".");
                }
                String identity = body.has("phone") ? body.getString("phone") : body.getString("email");
                store.edit().putString("identity", identity).commit();
                return session(identity).toString();
            }
            if (path.startsWith("/auth/v1/token?grant_type=pkce")) throw new AuthApi.Failure(400, "Sign-in links are not available in test mode. Use code " + CODE + ".");
            if (path.startsWith("/auth/v1/token")) {
                String identity = store.getString("identity", null);
                if (identity == null) throw new AuthApi.Failure(401, "Please sign in again.");
                return session(identity).toString();
            }
            if (path.startsWith("/rest/v1/profiles") && "GET".equals(method)) {
                String row = store.getString("profile", null);
                return row == null ? "[]" : new JSONArray().put(new JSONObject(row)).toString();
            }
            if (path.startsWith("/rest/v1/profiles")) { store.edit().putString("profile", body.toString()).commit(); return ""; }
            if (path.startsWith("/rest/v1/rpc/match_product")) {
                String row = store.getString("product:" + ProductPage.normalize(body.optString("p_name")), null);
                return row == null ? "[]" : new JSONArray().put(new JSONObject(row)).toString();
            }
            if (path.startsWith("/rest/v1/product_contributions")) {
                store.edit().putString("product:" + ProductPage.normalize(body.optString("product_name")), body.toString()).commit();
                return "";
            }
            if (path.startsWith("/auth/v1/logout")) return "{}";
            if (path.startsWith("/functions/v1/delete-account")) { store.edit().clear().commit(); return ""; }
            throw new AuthApi.Failure(404, "Not available in test mode.");
        }

        private static JSONObject session(String identity) throws Exception {
            String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            JSONObject user = new JSONObject().put("id", id).put("email_confirmed_at", "2026-01-01T00:00:00Z");
            if (identity.startsWith("+")) user.put("phone", identity); else user.put("email", identity.toLowerCase(Locale.ROOT));
            return new JSONObject().put("access_token", "test-access-" + UUID.randomUUID()).put("refresh_token", "test-refresh")
                .put("expires_in", 86400).put("user", user).put("test_mode", true);
        }
    }

    private TestMode() {}
}
