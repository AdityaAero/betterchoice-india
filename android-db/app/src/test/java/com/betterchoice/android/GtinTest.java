package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class GtinTest {
    // Real Indian pack numbers (GS1 India prefix 890) and their printed check digits.
    private static final String MAGGI_EAN13 = "8901058000450";
    private static final String UPC12 = "036000291452";
    private static final String EAN8 = "96385074";

    @Test public void acceptsTheNumbersPrintedOnIndianPacks() {
        assertEquals("08901058000450", Gtin.normalize(MAGGI_EAN13));
        assertEquals("Every number is stored as one 14-digit key", 14, Gtin.normalize(MAGGI_EAN13).length());
    }

    @Test public void shorterBarcodesBecomeTheSameFourteenDigitKey() {
        assertEquals("00036000291452", Gtin.normalize(UPC12));
        assertEquals("00000096385074", Gtin.normalize(EAN8));
    }

    @Test public void spacesAndHyphensAroundTheNumberAreIgnored() {
        assertEquals(Gtin.normalize(MAGGI_EAN13), Gtin.normalize("890-1058 000450"));
        assertEquals(Gtin.normalize(MAGGI_EAN13), Gtin.normalize("  8901058000450  "));
    }

    @Test public void aMistypedOrMisreadNumberIsRejected() {
        assertNull("Wrong check digit", Gtin.normalize("8901058000451"));
        assertNull("Not a barcode length", Gtin.normalize("89010580004"));
        assertNull("Not a number", Gtin.normalize("best before 10/2026"));
        assertNull(Gtin.normalize(null));
        assertNull(Gtin.normalize(""));
    }

    /**
     * The check digit catches most single-digit errors, not all of them: dropping the last digit of
     * this EAN-13 leaves a 12-digit number whose own check digit passes. That is why a number is only
     * ever taken from the barcode scanner, which decodes a complete symbol, and never from OCR text.
     */
    @Test public void theCheckDigitAloneCannotCatchEveryMisread() {
        String truncated = MAGGI_EAN13.substring(0, 12);
        assertTrue("A dropped digit can still look valid", Gtin.valid(truncated));
        assertNotEquals("...but it resolves to a different pack, never silently to this one",
            Gtin.normalize(MAGGI_EAN13), Gtin.normalize(truncated));
    }

    @Test public void aBatchCodeOrPriceIsNotMistakenForABarcode() {
        assertFalse(Gtin.valid("20250918"));
        assertFalse(Gtin.valid("199"));
        assertFalse(Gtin.valid("1234567890123"));
    }

    // --- QR codes -------------------------------------------------------------------------------

    @Test public void aGs1DigitalLinkQrGivesUpTheProductNumber() {
        assertEquals("08901058000450", Gtin.fromDigitalLink("https://id.gs1.org/01/08901058000450"));
        assertEquals("08901058000450", Gtin.fromDigitalLink("https://brand.example/01/8901058000450"));
        assertEquals("Batch and serial after the GTIN are ignored",
            "08901058000450", Gtin.fromDigitalLink("https://brand.example/01/08901058000450/10/LOT42/21/7"));
        assertEquals("As a query parameter", "08901058000450", Gtin.fromDigitalLink("https://brand.example/p?01=08901058000450"));
        assertEquals("Case does not matter", "08901058000450", Gtin.fromDigitalLink("https://brand.example/GTIN/08901058000450"));
    }

    /** Most pack QR codes are marketing links. They hold no nutrition, and we never open them. */
    @Test public void aMarketingQrIsNotTreatedAsAProductNumber() {
        assertNull(Gtin.fromDigitalLink("https://www.instagram.com/beyondsnack"));
        assertNull(Gtin.fromDigitalLink("https://beyondsnack.in"));
        assertNull(Gtin.fromDigitalLink("upi://pay?pa=someone@bank&am=100"));
        assertNull("A number that is not a valid GTIN is still refused",
            Gtin.fromDigitalLink("https://brand.example/01/8901058000451"));
        assertNull(Gtin.fromDigitalLink(null));
        assertNull("Absurdly long input is refused outright", Gtin.fromDigitalLink("https://x/01/" + "9".repeat(600)));
    }

    @Test public void theCardShowsTheDigitsAsPrintedUnderTheBars() {
        assertEquals(MAGGI_EAN13, Gtin.display(Gtin.normalize(MAGGI_EAN13)));
        assertEquals(EAN8, Gtin.display(Gtin.normalize(EAN8)));
        assertEquals("", Gtin.display(null));
    }
}
