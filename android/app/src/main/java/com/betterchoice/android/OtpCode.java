package com.betterchoice.android;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a verification code in copied text, e.g. "Your code: 482913" or a bare "482913". */
final class OtpCode {
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])");

    /** Returns the code only when the text contains exactly one standalone 6-digit number. */
    static String find(CharSequence text) {
        if (text == null || text.length() == 0 || text.length() > 2000) return null;
        String normalized = text.toString().replaceAll("(?<=[0-9])[ \\u00a0-](?=[0-9])", "");
        Matcher matcher = SIX_DIGITS.matcher(normalized);
        if (!matcher.find()) return null;
        String code = matcher.group(1);
        return matcher.find() ? null : code;
    }

    private OtpCode() {}
}
