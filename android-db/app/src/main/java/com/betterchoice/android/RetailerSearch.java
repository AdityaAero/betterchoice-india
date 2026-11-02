package com.betterchoice.android;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the shop's own search for a swap, inside the shop's app, only when the shopper taps it.
 *
 * <p>Each shop's public web search link is tried in its own app (the link is pinned to that app's
 * package, so it never opens a browser or anything else). Shops change these links without notice,
 * so every shop has more than one form, tried in order. If none opens, the shop's app is brought to
 * the front and the search words are copied, so the shopper can paste them into the search box.
 *
 * <p>BetterChoice never types, taps, or adds anything to a cart inside the shop's app.
 */
final class RetailerSearch {
    /** The search links for one shop, most likely first. Empty when the shop sells no packaged food. */
    static List<String> links(String retailer, String query) {
        String q;
        // The Charset overload needs Android 13; the charset-name form works on every version we support.
        try { q = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8.name()); }
        catch (java.io.UnsupportedEncodingException impossible) { q = ""; }
        List<String> links = new ArrayList<>();
        if (retailer == null) return links;
        switch (retailer) {
            case "com.grofers.customerapp":
                links.add("https://blinkit.com/s/?q=" + q);
                break;
            case "com.zeptoconsumerapp":
                links.add("https://www.zeptonow.com/search?query=" + q);
                links.add("https://www.zepto.com/search?query=" + q);
                break;
            case "in.swiggy.android":
            case "in.swiggy.android.instamart":
                // Checked on a device (Swiggy app, 26 Sep 2026): the app does not accept
                // /instamart/search, but does accept instamart.in/search in its deep-link screen.
                links.add("https://www.instamart.in/search?query=" + q);
                break;
            case "com.flipkart.android":
                links.add("https://www.flipkart.com/search?q=" + q + "&marketplace=HYPERLOCAL");
                break;
            default:
                break;   // Zomato sells meals, not packaged groceries: no search to open.
        }
        return links;
    }

    static boolean supports(String retailer) { return !links(retailer, "x").isEmpty(); }

    /** True when the shop's search opened; false when the fallback (app + copied words) was used. */
    static boolean open(Context context, String retailer, String query) {
        for (String link : links(retailer, query)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .setPackage(retailer)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException notHandled) {
                DebugLog.diagnostic("search link not handled: " + link.substring(0, Math.min(40, link.length())));
            }
        }
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Search", query));
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(retailer);
        if (launch != null) {
            try { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (ActivityNotFoundException | SecurityException unavailable) { DebugLog.failure("Shop app unavailable"); }
        }
        return false;
    }

    private RetailerSearch() {}
}
