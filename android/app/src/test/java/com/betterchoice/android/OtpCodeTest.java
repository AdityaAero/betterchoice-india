package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class OtpCodeTest {
    @Test public void findsCodesInTypicalCopiedText() {
        assertEquals("482913", OtpCode.find("482913"));
        assertEquals("482913", OtpCode.find(" Your 6-digit code: 482913 "));
        assertEquals("482913", OtpCode.find("Your code is 482 913"));
        assertEquals("482913", OtpCode.find("482-913"));
    }

    @Test public void ignoresAmbiguousOrWrongText() {
        assertNull("Phone number", OtpCode.find("+91 9876543210"));
        assertNull("Too short", OtpCode.find("12345"));
        assertNull("Two codes", OtpCode.find("Old 111111, new 222222"));
        assertNull("No digits", OtpCode.find("hello"));
        assertNull(OtpCode.find(null));
        assertNull("Order id is longer", OtpCode.find("Order 1234567"));
    }
}
