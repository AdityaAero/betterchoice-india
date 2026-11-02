package com.betterchoice.android;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import org.json.JSONArray;

/** Provider validates OTPs. No generated/test code or privileged key exists in release builds. */
final class AuthApi {
    interface Transport { String request(String method, String path, JSONObject body, String token) throws Exception; }
    static final class Failure extends Exception {
        final int status;
        Failure(int status, String message) { super(message); this.status = status; }
    }
    private final Transport transport;
    AuthApi() { this(new HttpsTransport()); }
    /** The real Supabase transport, so debug builds can route around it while testing. */
    static Transport https() { return new HttpsTransport(); }
    AuthApi(Transport transport) { this.transport = transport; }
    /**
     * Requests a one-time code. For email, Supabase also sends a sign-in link; with a PKCE challenge the link
     * returns to this app with a code that only this phone can exchange.
     */
    void send(String identity, boolean phone, String captcha, String codeChallenge) throws Exception {
        JSONObject body = new JSONObject().put(phone ? "phone" : "email", identity).put("create_user", true);
        if (phone) body.put("channel", "sms");
        if (captcha != null && !captcha.isBlank()) body.put("gotrue_meta_security", new JSONObject().put("captcha_token", captcha));
        String path = "/auth/v1/otp";
        if (!phone && codeChallenge != null) {
            body.put("code_challenge", codeChallenge).put("code_challenge_method", "s256");
            path += "?redirect_to=" + java.net.URLEncoder.encode(EmailLink.redirectUrl(), "UTF-8");
        }
        transport.request("POST", path, body, "");
    }
    JSONObject exchangeEmailLink(String authCode, String verifier) throws Exception {
        JSONObject body = new JSONObject().put("auth_code", authCode).put("code_verifier", verifier);
        return new JSONObject(transport.request("POST", "/auth/v1/token?grant_type=pkce", body, ""));
    }
    JSONObject verify(String identity, boolean phone, String code) throws Exception {
        if (!AuthInput.otp(code)) throw new IllegalArgumentException("Enter the 6-digit code.");
        JSONObject body = new JSONObject().put(phone ? "phone" : "email", identity).put("token", code).put("type", phone ? "sms" : "email");
        return new JSONObject(transport.request("POST", "/auth/v1/verify", body, ""));
    }
    JSONObject refresh(String token) throws Exception {
        return new JSONObject(transport.request("POST", "/auth/v1/token?grant_type=refresh_token", new JSONObject().put("refresh_token", token), ""));
    }
    Profile loadProfile(String userId, String access) throws Exception {
        JSONArray rows = new JSONArray(transport.request("GET", profilePath(userId), null, access));
        return rows.length() == 0 ? new Profile() : Profile.fromJson(rows.getJSONObject(0));
    }
    void saveProfile(String userId, String access, Profile profile) throws Exception {
        JSONObject row = profile.syncJson().put("user_id", userId);
        transport.request("POST", "/rest/v1/profiles?on_conflict=user_id", row, access);
    }
    /** Anonymous read of verified products; sends only the on-screen product name, price and retailer. */
    /** Exact lookup by the pack's own number. An unknown or malformed number simply misses. */
    Nutrition matchByGtin(String gtin) throws Exception {
        String normalized = Gtin.normalize(gtin);
        if (normalized == null) return null;
        JSONObject body = new JSONObject().put("p_gtin", normalized);
        JSONArray rows = new JSONArray(transport.request("POST", "/rest/v1/rpc/match_by_gtin", body, ""));
        return rows.length() == 0 ? null : nutrition(rows.getJSONObject(0));
    }

    Nutrition matchProduct(ProductPage page) throws Exception {
        JSONObject body = new JSONObject().put("p_name", page.name).put("p_retailer", page.retailer)
            .put("p_price", page.price == null ? JSONObject.NULL : page.price);
        JSONArray rows = new JSONArray(transport.request("POST", "/rest/v1/rpc/match_product", body, ""));
        if (rows.length() == 0) return null;
        return nutrition(rows.getJSONObject(0));
    }

    private Nutrition nutrition(JSONObject row) {
        Nutrition n = new Nutrition();
        n.basisStated = true;   // stored per 100 g or per 100 ml by definition
        n.energyKcal = number(row, "energy_kcal");
        n.sugarsG = number(row, "sugars_g");
        n.saturatedFatG = number(row, "saturated_fat_g");
        n.sodiumMg = number(row, "sodium_mg");
        n.fibreG = number(row, "fibre_g");
        n.proteinG = number(row, "protein_g");
        n.beverage = row.optBoolean("is_beverage", false);
        return n;
    }
    /** Queues user-provided label values for review; nothing is published without verification. */
    void contribute(String access, ProductPage page, Nutrition n, String ingredients) throws Exception {
        contribute(access, page, n, ingredients, 100);
    }
    void contribute(String access, ProductPage page, Nutrition n, String ingredients, double labelBasis) throws Exception {
        contribute(access, page, n, ingredients, labelBasis, null);
    }

    /**
     * `labelBasis` records the basis the user read from the pack, before conversion to per 100.
     * `gtin` ties the submission to one exact pack, so the next shopper is matched by number.
     */
    void contribute(String access, ProductPage page, Nutrition n, String ingredients, double labelBasis, String gtin) throws Exception {
        JSONObject row = new JSONObject().put("product_name", page.name).put("retailer", page.retailer)
            .put("price_inr", page.price == null ? JSONObject.NULL : page.price)
            .put("energy_kcal", n.energyKcal).put("sugars_g", nullable(n.sugarsG)).put("saturated_fat_g", nullable(n.saturatedFatG))
            .put("sodium_mg", nullable(n.sodiumMg)).put("fibre_g", nullable(n.fibreG)).put("protein_g", nullable(n.proteinG))
            .put("is_beverage", n.beverage).put("ingredients", ingredients == null || ingredients.isBlank() ? JSONObject.NULL : ingredients)
            .put("net_quantity", nullable(n.netQuantity)).put("serving_size", nullable(n.servingSize))
            .put("servings_per_pack", nullable(n.servingsPerPack)).put("label_basis", labelBasis)
            .put("gtin", Gtin.normalize(gtin) == null ? JSONObject.NULL : Gtin.normalize(gtin));
        transport.request("POST", "/rest/v1/product_contributions", row, access);
    }
    void logout(String access) throws Exception { transport.request("POST", "/auth/v1/logout?scope=local", new JSONObject(), access); }
    void deleteAccount(String access) throws Exception { transport.request("POST", "/functions/v1/delete-account", new JSONObject(), access); }
    private static Double number(JSONObject row, String key) { return row.isNull(key) ? null : row.optDouble(key); }
    private static Object nullable(Double value) { return value == null ? JSONObject.NULL : value; }
    private static String profilePath(String userId) {
        if (!userId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid account");
        return "/rest/v1/profiles?user_id=eq." + userId +
            "&select=gender,age,weight_kg,height_cm,diet,goal,adult_confirmed,trial_started_at,onboarding_complete&limit=1";
    }
    private static final class HttpsTransport implements Transport {
        @Override public String request(String method, String path, JSONObject body, String token) throws Exception {
            if (!AuthConfig.ready()) throw new Failure(0, "Sign-in is not available in this build yet. Please try the configured beta.");
            HttpsURLConnection connection = (HttpsURLConnection) new URL(AuthConfig.base() + path).openConnection();
            try {
                connection.setConnectTimeout(path.contains("/rpc/") ? 5000 : 12000);
                connection.setReadTimeout(path.contains("/rpc/") ? 5000 : 12000);
                connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
                connection.setRequestMethod(method);
                connection.setRequestProperty("apikey", BuildConfig.AUTH_PUBLIC_KEY);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (!token.isBlank()) connection.setRequestProperty("Authorization", "Bearer " + token);
                if (path.startsWith("/rest/v1/profiles")) connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");
                else if (path.startsWith("/rest/v1/product_contributions")) connection.setRequestProperty("Prefer", "return=minimal");
                if (body != null) {
                    byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true); connection.setFixedLengthStreamingMode(data.length);
                    try (var output = connection.getOutputStream()) { output.write(data); }
                }
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    // Never display raw provider bodies: they may contain identifiers or diagnostics.
                    if (status == 429) throw new Failure(status, "Too many attempts. Please wait a little before trying again.");
                    if (path.contains("/verify") || path.contains("grant_type=pkce")) throw new Failure(status, "That code or link is invalid or expired. Check it or request a new one.");
                    if (status == 401) throw new Failure(status, "Your session has ended. Please sign in again.");
                    if (status == 403 && path.contains("/otp")) throw new Failure(status, "Verification could not be completed. Please try again.");
                    throw new Failure(status, "We could not complete that request. Please try again shortly.");
                }
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > 131072) throw new Failure(0, "The service returned an unexpected response.");
                        bytes.write(buffer, 0, count);
                    }
                    return bytes.toString(StandardCharsets.UTF_8.name());
                }
            } finally { connection.disconnect(); }
        }
    }
}
