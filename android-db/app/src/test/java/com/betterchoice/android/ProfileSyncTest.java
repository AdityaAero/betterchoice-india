package com.betterchoice.android;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * The server's profiles table accepts only Vegetarian / Non-vegetarian / Vegan and one goal (migration
 * 20260917170000). The detailed answers stay on the phone; the server gets the nearest allowed value.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class ProfileSyncTest {
    @Test public void detailedAnswersAreProjectedOntoTheServersValues() throws Exception {
        Profile p = new Profile();
        p.answers[Profile.DIET_DETAIL] = "Jain";
        p.answers[Profile.GOALS] = "sugar,muscle";
        JSONObject sent = p.syncJson();
        assertEquals("Vegetarian", sent.getString("diet"));
        assertEquals("Muscle gain", sent.getString("goal"));
        assertFalse("device-only answers never leave the phone", sent.has("diet_detail") || sent.has("goals") || sent.has("pregnancy_stage"));
        assertEquals("Non-vegetarian", Profile.dietForServer("Non-vegetarian (halal)"));
        assertEquals("Vegetarian", Profile.dietForServer("Eggetarian"));
        assertEquals("Improve nutritional awareness", Profile.goalForServer(java.util.Collections.singletonList("sugar")));
    }

    @Test public void olderAnswersStillWork() {
        Profile p = new Profile();
        p.answers[Profile.DIET] = "Vegan"; p.answers[Profile.GOAL] = "Weight loss";
        assertEquals("Vegan", p.dietDetail());
        assertEquals(java.util.Collections.singletonList("loss"), p.goalCodes());
    }

    @Test public void aServerChangeFromAnotherDeviceWinsOverAContradictingDetail() {
        Profile local = new Profile();
        local.answers[Profile.DIET_DETAIL] = "Jain"; local.answers[Profile.GOALS] = "loss";
        Profile server = new Profile();
        server.answers[Profile.DIET] = "Vegetarian"; server.answers[Profile.GOAL] = "Weight loss";
        Profile merged = server.withLocalAnswers(local);
        assertEquals("consistent detail is kept", "Jain", merged.answers[Profile.DIET_DETAIL]);
        server.answers[Profile.DIET] = "Vegan"; server.answers[Profile.GOAL] = "Muscle gain";
        merged = server.withLocalAnswers(local);
        assertEquals("Vegan", merged.dietDetail());
        assertEquals(java.util.Collections.singletonList("muscle"), merged.goalCodes());
    }

    @Test public void weightLossAndGainCannotBothBeChosenAndStaleFollowUpsAreCleared() {
        try { Profile.validate(Profile.GOALS, "loss,gain"); fail("loss and gain together"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("not both")); }
        assertEquals("loss,sugar", Profile.validate(Profile.GOALS, "loss, sugar, nonsense"));
        Profile p = new Profile();
        p.answers[Profile.HEALTH] = "diabetes"; p.answers[Profile.PREGNANCY_STAGE] = "t3"; p.answers[Profile.KIDNEY_STAGE] = "dialysis";
        p.answers[Profile.DIABETES_TYPE] = "type1"; p.answers[Profile.TRACE_SEVERITY] = "strict";
        p.clearUnusedFollowUps();
        assertEquals("", p.answers[Profile.PREGNANCY_STAGE]);
        assertEquals("", p.answers[Profile.KIDNEY_STAGE]);
        assertEquals("", p.answers[Profile.TRACE_SEVERITY]);
        assertEquals("type1", p.answers[Profile.DIABETES_TYPE]);
    }
}
