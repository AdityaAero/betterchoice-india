package com.betterchoice.android;

import java.net.URI;

final class AuthConfig {
    static boolean ready() { return valid(BuildConfig.AUTH_URL, BuildConfig.AUTH_PUBLIC_KEY); }
    static boolean valid(String url, String key) {
        try {
            URI uri = URI.create(url);
            // Hosted Supabase only. Custom auth hosts need explicit review.
            return "https".equals(uri.getScheme()) && uri.getHost() != null &&
                uri.getHost().matches("[a-z0-9-]+\\.supabase\\.co") && uri.getPort() == -1 &&
                uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null &&
                (uri.getPath().isEmpty() || uri.getPath().equals("/")) &&
                key.matches("sb_publishable_[A-Za-z0-9_-]+") && key.length() > 25;
        } catch (RuntimeException invalid) { return false; }
    }
    static String base() { return BuildConfig.AUTH_URL.replaceAll("/+$", ""); }
    private AuthConfig() {}
}
