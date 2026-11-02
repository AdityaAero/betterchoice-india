package com.betterchoice.android;

import java.util.Locale;
import java.util.regex.Pattern;

final class AuthInput {
    static String email(String raw) {
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !Pattern.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+", email))
            throw new IllegalArgumentException("Enter a valid email address.");
        return email;
    }
    static String phone(String dialCode, String raw) {
        String local = raw.replaceAll("[ ()-]", "");
        if (!dialCode.matches("[1-9][0-9]{0,3}") || !local.matches("[0-9]{5,14}"))
            throw new IllegalArgumentException("Enter your number without the country code.");
        // The national trunk prefix is not part of E.164 in most countries; do not
        // silently guess. The UI requests the number without a leading trunk zero.
        String value = "+" + dialCode + local;
        if (value.length() > 16 || (dialCode.equals("91") && !local.matches("[6-9][0-9]{9}")))
            throw new IllegalArgumentException(dialCode.equals("91") ? "Enter a 10-digit Indian mobile number." : "Check the number and country code.");
        return value;
    }
    static boolean otp(String value) { return value != null && value.matches("[0-9]{6}"); }
    static String mask(String value, boolean phone) {
        if (phone) return "•••• " + value.substring(Math.max(0, value.length() - 4));
        int at = value.indexOf('@');
        return at > 0 ? value.substring(0, 1) + "•••" + value.substring(at) : "your email";
    }
    private AuthInput() {}
}
