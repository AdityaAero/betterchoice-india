package com.betterchoice.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import java.util.UUID;

/** Optional hosted Turnstile challenge. No JavaScript bridge or local-file access. */
final class CaptchaDialog {
    interface Result { void accept(String token); }
    @SuppressLint("SetJavaScriptEnabled")
    static void show(Activity activity, Result result) {
        Uri url = Uri.parse(BuildConfig.CAPTCHA_URL);
        // The operator pins this public page at build time, never through intents or user input.
        if (!"https".equals(url.getScheme()) || url.getHost() == null ||
            url.getUserInfo() != null || url.getPort() != -1 || url.getQuery() != null || url.getFragment() != null) { result.accept(null); return; }
        String state = UUID.randomUUID().toString();
        WebView web = new WebView(activity);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE); web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setSaveFormData(false); web.getSettings().setSafeBrowsingEnabled(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        boolean[] completed = {false};
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("A quick security check").setView(web).setNegativeButton("Cancel", null).create();
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (request.isForMainFrame() && "betterchoice-verify".equals(target.getScheme()) && "complete".equals(target.getHost()) && state.equals(target.getQueryParameter("state"))) {
                    String token = target.getQueryParameter("token");
                    if (!completed[0] && token != null && token.length() >= 10 && token.length() <= 4096) {
                        completed[0] = true; dialog.dismiss(); result.accept(token);
                    }
                    return true;
                }
                return request.isForMainFrame() && !("https".equals(target.getScheme()) && url.getHost().equals(target.getHost()) && url.getPath().equals(target.getPath()));
            }
        });
        dialog.setOnDismissListener(ignored -> { web.stopLoading(); web.destroy(); if (!completed[0]) result.accept(null); });
        dialog.show(); dialog.getWindow().setHideOverlayWindows(true);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        web.loadUrl(url.buildUpon().appendQueryParameter("state", state).build().toString());
    }
    private CaptchaDialog() {}
}
