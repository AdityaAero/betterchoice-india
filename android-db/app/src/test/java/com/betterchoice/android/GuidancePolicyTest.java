package com.betterchoice.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class GuidancePolicyTest {
    private static final String CHIPS = "lays magic masala 50 g|20|com.grofers.customerapp";
    private static final String OATS = "quaker oats 1 kg|199|com.zeptoconsumerapp";

    @Test public void staysQuietForTheFirstTwoSeconds() {
        GuidancePolicy policy = new GuidancePolicy();
        assertEquals(GuidancePolicy.Decision.WAIT, policy.decide(CHIPS, 1_000));
        assertEquals(GuidancePolicy.Decision.WAIT, policy.decide(CHIPS, 2_900));
        assertEquals(100, policy.waitRemainingMs(CHIPS, 2_900));
        assertEquals(GuidancePolicy.Decision.ANNOUNCE, policy.decide(CHIPS, 3_000));
    }

    @Test public void theCardOpensByItselfOncePerSessionNotPerItem() {
        GuidancePolicy policy = new GuidancePolicy();
        policy.decide(CHIPS, 0);
        assertEquals(GuidancePolicy.Decision.ANNOUNCE, policy.decide(CHIPS, 2_000));
        assertEquals("Same item again", GuidancePolicy.Decision.QUIET, policy.decide(CHIPS, 2_500));
        policy.decide(OATS, 10_000);
        assertEquals("A different item only shows the ? bubble",
            GuidancePolicy.Decision.QUIET, policy.decide(OATS, 12_000));
    }

    @Test public void aRatedProductIsNeverAskedAbout() {
        GuidancePolicy policy = new GuidancePolicy();
        policy.decide(CHIPS, 0);
        policy.rated(CHIPS);
        assertEquals(GuidancePolicy.Decision.QUIET, policy.decide(CHIPS, 10_000));
    }

    @Test public void aNewSessionMayShowTheHintOnceMore() {
        GuidancePolicy policy = new GuidancePolicy();
        policy.decide(CHIPS, 0);
        assertEquals(GuidancePolicy.Decision.ANNOUNCE, policy.decide(CHIPS, 2_000));
        policy.clear();
        assertEquals(GuidancePolicy.Decision.WAIT, policy.decide(CHIPS, 2_100));
        assertEquals(GuidancePolicy.Decision.ANNOUNCE, policy.decide(CHIPS, 4_200));
    }
}
