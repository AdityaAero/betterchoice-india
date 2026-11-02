package com.betterchoice.android;

/**
 * A product's number from its barcode (GTIN-8/12/13/14). The barcode identifies the pack; the
 * nutrition comes from a database. A prefix such as 890 shows the number was issued by GS1 India,
 * which is not a claim about where the food was made, so nothing here infers a country.
 */
final class Gtin {
    /** Normalised to 14 digits so EAN-13, UPC-12 and EAN-8 compare as the same key. */
    static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 8 && digits.length() != 12 && digits.length() != 13 && digits.length() != 14) return null;
        if (!checksumValid(digits)) return null;
        return "00000000000000".substring(0, 14 - digits.length()) + digits;
    }

    static boolean valid(String raw) { return normalize(raw) != null; }

    /** GS1 check digit: weights 3 and 1 from the right, total must be a multiple of ten. */
    static boolean checksumValid(String digits) {
        if (digits == null || digits.length() < 8) return false;
        int sum = 0;
        for (int i = 0; i < digits.length() - 1; i++) {
            int digit = digits.charAt(digits.length() - 2 - i) - '0';
            if (digit < 0 || digit > 9) return false;
            sum += i % 2 == 0 ? digit * 3 : digit;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == digits.charAt(digits.length() - 1) - '0';
    }

    /**
     * The product number carried inside a QR code, when it carries one at all.
     *
     * <p>A GS1 Digital Link writes the GTIN into the path as the application identifier `01`, as in
     * `https://id.gs1.org/01/08901058000450`, sometimes followed by a batch (`10`) or serial (`21`),
     * or passed as a query parameter. Those codes identify the exact pack and are worth following.
     *
     * <p>Most QR codes on Indian packs today are not Digital Links at all: they point at the brand's
     * Instagram or website. Those hold no nutrition data, so this returns null for them and the app
     * never fetches the address. A URL printed on packaging is untrusted input, and opening it would
     * be a way to send a shopper somewhere chosen by whoever printed the label.
     */
    static String fromDigitalLink(String url) {
        if (url == null) return null;
        String text = url.trim();
        if (text.length() > 512) return null;
        java.util.regex.Matcher path = java.util.regex.Pattern
            .compile("(?:^|/)(?:01|gtin)/([0-9]{8,14})(?![0-9])", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text);
        if (path.find()) return normalize(path.group(1));
        java.util.regex.Matcher query = java.util.regex.Pattern
            .compile("[?&](?:01|gtin)=([0-9]{8,14})(?![0-9])", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text);
        if (query.find()) return normalize(query.group(1));
        return null;
    }

    /** Human-readable form for the card: the digits as printed under the bars. */
    static String display(String normalized) {
        if (normalized == null) return "";
        String trimmed = normalized.replaceFirst("^0+", "");
        return trimmed.length() < 8 ? normalized : trimmed;
    }

    private Gtin() {}
}
