package com.betterchoice.android;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class OnboardingRulesTest {
    @After public void reset() { AccountStore.replaceForTests(null); }


    @Test public void questionsFollowTheBriefAndCanBeSkipped() {
        assertArrayEquals(new String[]{"gender", "age", "weight_kg", "height_cm", "activity",
            "diet", "goal", "health_flags", "buys", "health_other",
            "diet_detail", "goals", "pregnancy_stage", "pre_pregnancy_weight", "waist_cm",
            "trace_severity", "diabetes_type", "kidney_stage"}, Profile.KEYS);
        assertArrayEquals("FSSAI: egg is non-vegetarian, so Eggetarian is its own choice",
            new String[]{"Vegetarian", "Eggetarian", "Jain", "Non-vegetarian / both", "Non-vegetarian (halal)", "Vegan"},
            Profile.CHOICES[Profile.DIET_DETAIL]);
        assertArrayEquals(new String[]{"Vegetarian", "Non-vegetarian", "Vegan"}, Profile.CHOICES[Profile.DIET]);
        assertArrayEquals(new String[]{"Weight loss", "Weight gain", "Muscle gain", "Improve nutritional awareness"}, Profile.CHOICES[Profile.GOAL]);
        assertArrayEquals("The three levels ICMR publishes energy figures for",
            new String[]{"Mostly sitting", "Moderately active", "Heavy physical work"}, Profile.CHOICES[Profile.ACTIVITY]);
        for (int step = 0; step < Profile.KEYS.length; step++) assertEquals("", Profile.validate(step, "  "));
    }

    /** Nine questions on six screens: related ones share a page, and there is no catch-all at the end. */
    @Test public void relatedQuestionsShareAScreen() {
        assertEquals(6, Profile.GROUPS.length);
        assertEquals(Profile.GROUPS.length, Profile.GROUP_TITLES.length);
        assertEquals(Profile.GROUPS.length, Profile.GROUP_REASONS.length);

        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int[] group : Profile.GROUPS) {
            for (int question : group) {
                assertTrue("Question " + question + " is on two screens", seen.add(question));
            }
        }
        assertFalse("The free-text box belongs to the health screen, not a screen of its own",
            seen.contains(Profile.HEALTH_OTHER));
        // The server's three-way diet and single goal are derived from the detailed answers; the
        // health follow-ups appear on the health screen only when their condition is ticked.
        java.util.Set<Integer> notOnAScreen = java.util.Set.of(Profile.HEALTH_OTHER, Profile.DIET, Profile.GOAL,
            Profile.PREGNANCY_STAGE, Profile.PRE_PREGNANCY_WEIGHT, Profile.TRACE_SEVERITY, Profile.DIABETES_TYPE, Profile.KIDNEY_STAGE);
        assertEquals("Every other question must appear exactly once", Profile.KEYS.length - notOnAScreen.size(), seen.size());
        for (int hidden : notOnAScreen) assertFalse(Profile.KEYS[hidden], seen.contains(hidden));
    }

    /** Nobody should be asked something the app already knows. */
    @Test public void screensAlreadyAnsweredAreNotShownAgain() {
        Profile blank = new Profile();
        assertEquals("A new profile asks everything", 6, blank.remainingGroups().size());

        Profile known = new Profile();
        known.answers[Profile.GENDER] = "Woman";
        known.answers[Profile.AGE] = "30";
        known.answers[Profile.HEIGHT] = "160";
        known.answers[Profile.WEIGHT] = "55";
        java.util.List<Integer> remaining = known.remainingGroups();
        assertEquals("Two screens are complete, four remain", 4, remaining.size());
        assertFalse("About you is done", remaining.contains(0));
        assertFalse("Your build is done", remaining.contains(1));
        assertTrue("Activity is still needed", remaining.contains(2));
    }

    /** A half-finished screen is still asked, or the missing half would never be collected. */
    @Test public void aPartlyAnsweredScreenIsStillShown() {
        Profile half = new Profile();
        half.answers[Profile.HEIGHT] = "170";      // weight still missing
        assertTrue(half.remainingGroups().contains(1));
    }

    @Test public void theShoppingQuestionKeepsOnlyKnownAisles() {
        assertEquals("snacks,drinks", Profile.validate(Profile.BUYS, "snacks, drinks"));
        assertEquals("Unknown aisles are dropped", "biscuits", Profile.validate(Profile.BUYS, "biscuits,spaceships"));
        Profile p = new Profile();
        p.answers[Profile.BUYS] = "snacks,dairy";
        assertEquals(java.util.List.of("snacks", "dairy"), p.buyCodes());
        assertEquals("Chips & namkeen", Profile.buyLabel("snacks"));
    }

    /** Every question says what its answer is used for, in one short line. */
    @Test public void everyQuestionCarriesAShortReason() {
        assertEquals(Profile.KEYS.length, Profile.TITLES.length);
        assertEquals(Profile.KEYS.length, Profile.REASONS.length);
        for (int i = 0; i < Profile.REASONS.length; i++) {
            String reason = Profile.REASONS[i];
            assertTrue("Reason " + i + " must be parenthesised: " + reason,
                reason.startsWith("(") && reason.endsWith(")"));
            assertTrue("Reason " + i + " is too long to sit under a question: " + reason, reason.length() <= 60);
            assertTrue("Question " + i + " is too long: " + Profile.TITLES[i], Profile.TITLES[i].length() <= 40);
        }
    }

    @Test public void theHealthAnswerKeepsOnlyKnownCodes() {
        assertEquals("milk,peanut", Profile.validate(Profile.HEALTH, "milk, peanut"));
        assertEquals("Unknown codes are dropped, not stored", "milk", Profile.validate(Profile.HEALTH, "milk,wizardry"));
        assertEquals("No duplicates", "soy", Profile.validate(Profile.HEALTH, "soy,soy"));
    }

    /** "None that I know of" cannot sit next to an actual allergy. */
    @Test public void noneKnownIsExclusive() {
        assertEquals("milk", Profile.validate(Profile.HEALTH, "none,milk"));
        assertEquals("none", Profile.validate(Profile.HEALTH, "none"));
    }

    /** Allergies and conditions are the one answer that should not leave the phone by default. */
    @Test public void healthAnswersAndActivityAreNeverSyncedToTheServer() {
        Profile p = new Profile();
        p.answers[Profile.GENDER] = "Woman";
        p.answers[Profile.ACTIVITY] = "Moderately active";
        p.answers[Profile.HEALTH] = "peanut,diabetes";
        org.json.JSONObject sync = p.syncJson();
        assertFalse("Health flags must not be sent", sync.has("health_flags"));
        assertFalse("No column for activity yet", sync.has("activity"));
        assertTrue("Ordinary answers still sync", sync.has("gender"));
        assertTrue("But they are kept on the phone", p.json().has("health_flags"));
        assertEquals(java.util.List.of("peanut", "diabetes"), p.healthCodes());
    }

    /**
     * The free-text box under the allergy list used to write into other_reason, which is synced, while
     * the line printed under it said "Never sent to our servers". It now has a field of its own that
     * is never sent, so that line is true.
     */
    @Test public void theAllergyNoteNeverLeavesThePhone() {
        Profile p = new Profile();
        p.answers[Profile.HEALTH_OTHER] = "Mild reaction to kiwi";
        org.json.JSONObject sync = p.syncJson();
        assertFalse(sync.has("health_other"));
        assertFalse("The old synced field must not come back", sync.has("other_reason"));
        java.util.Iterator<String> keys = sync.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            assertNotEquals("The note leaked into " + key, "Mild reaction to kiwi", sync.optString(key));
        }
        assertEquals("Kept on the phone", "Mild reaction to kiwi", p.json().optString("health_other"));
    }

    /** An optional note must never make the health screen count as unfinished. */
    @Test public void aBlankAllergyNoteDoesNotReopenTheHealthScreen() {
        Profile p = new Profile();
        p.answers[Profile.HEALTH] = "none";
        assertFalse("Health screen is done even with no note", p.remainingGroups().contains(4));
    }

    @Test public void theShoppingQuestionSaysHowOften() {
        assertEquals("What do you buy regularly?", Profile.GROUP_TITLES[5]);
        assertTrue(Profile.GROUP_REASONS[5], Profile.GROUP_REASONS[5].contains("weekly or more often"));
    }

    @Test public void activityLevelChangesTheDailyEnergyFigure() {
        Profile sitting = new Profile();
        sitting.answers[Profile.GENDER] = "Man"; sitting.answers[Profile.AGE] = "30";
        sitting.answers[Profile.WEIGHT] = "65"; sitting.answers[Profile.HEIGHT] = "170";
        sitting.answers[Profile.ACTIVITY] = "Mostly sitting";
        Profile heavy = sitting.copy();
        heavy.answers[Profile.ACTIVITY] = "Heavy physical work";

        double sedentary = DailyNeeds.forProfile(sitting).energyKcal;
        double hard = DailyNeeds.forProfile(heavy).energyKcal;
        // FAO/WHO/UNU 30-59 equation x 0.9 x ICMR-NIN 1.40; heavy work keeps ICMR's own ratio to it
        // (Table 1a, men: 3470 / 2110).
        assertEquals(2039.7, sedentary, 0.1);
        assertEquals(sedentary * 3470 / 2110, hard, 0.001);
    }

    /** The ticked allergens are what finally let the safety gate block anything. */
    @Test public void tickedAllergensReachTheSafetyGate() {
        Profile p = new Profile();
        p.answers[Profile.HEALTH] = "peanut";
        SafetyProfile safety = SafetyProfile.fromProfile(p);
        assertTrue(safety.hasAllergies());
        assertTrue(safety.allergens().contains(Allergen.PEANUT));
        assertTrue(SafetyGate.check("Ingredients: maida, groundnut oil.", null, safety).blocked());
    }

    @Test public void noneKnownIsRecordedButNeverTreatedAsAGuarantee() {
        Profile p = new Profile();
        p.answers[Profile.HEALTH] = "none";
        SafetyProfile safety = SafetyProfile.fromProfile(p);
        assertEquals(SafetyProfile.Allergies.NONE_KNOWN, safety.allergyState);
        assertFalse(safety.hasAllergies());
        SafetyGate.Result result = SafetyGate.check("Ingredients: wheat flour, milk solids.", null, safety);
        assertFalse(result.blocked());
        assertTrue(String.join(" ", result.messages).contains("can't confirm"));
    }

    /** Lactose intolerance warns; a milk allergy blocks. They are different conditions. */
    @Test public void lactoseIntoleranceWarnsWhereAMilkAllergyWouldBlock() {
        Profile intolerant = new Profile();
        intolerant.answers[Profile.HEALTH] = "lactose";
        SafetyGate.Result warned = SafetyGate.check("Ingredients: milk solids, sugar.", null,
            SafetyProfile.fromProfile(intolerant));
        assertFalse("Not dangerous, so not a block", warned.blocked());
        assertEquals(SafetyGate.Verdict.CAUTION, warned.verdict);

        Profile allergic = new Profile();
        allergic.answers[Profile.HEALTH] = "milk";
        assertTrue(SafetyGate.check("Ingredients: milk solids, sugar.", null,
            SafetyProfile.fromProfile(allergic)).blocked());
    }

    /** A condition this app does not model must be said out loud, not passed over in silence. */
    @Test public void unmodelledConditionsAreDeclaredRatherThanIgnored() {
        Profile p = new Profile();
        p.answers[Profile.HEALTH] = "kidney";
        SafetyGate.Result result = SafetyGate.check("Ingredients: potato, salt.", null, SafetyProfile.fromProfile(p));
        String said = String.join(" ", result.messages);
        assertTrue(said, said.contains("won't judge whether this suits your kidney condition"));
        assertFalse("Never a claim of suitability", said.contains("good for your kidney"));
    }

    @Test public void validationEnforcesAdultsAndRealisticValues() {
        assertEquals("34", Profile.validate(Profile.AGE, "34"));
        assertThrows(IllegalArgumentException.class, () -> Profile.validate(Profile.AGE, "16"));
        assertThrows(IllegalArgumentException.class, () -> Profile.validate(Profile.WEIGHT, "0.5"));
        assertEquals("72.5", Profile.validate(Profile.WEIGHT, "72.5"));
        assertThrows(IllegalArgumentException.class, () -> Profile.validate(Profile.DIET, "Keto"));
        assertThrows(IllegalArgumentException.class, () -> Profile.validate(Profile.HEALTH_OTHER, "x".repeat(301)));
    }

    @Test public void pageOrderIsAgeThenFormThenTrial() {
        Profile p = new Profile();
        assertEquals(OnboardingActivity.Page.AGE, OnboardingActivity.nextPage(p, false));
        p.adultConfirmed = true;
        assertEquals(OnboardingActivity.Page.INTRO, OnboardingActivity.nextPage(p, false));
        p.complete = true;
        assertEquals(OnboardingActivity.Page.TRIAL, OnboardingActivity.nextPage(p, false));
        p.trialStartedAt = "2026-09-17T10:00:00Z";
        assertEquals(OnboardingActivity.Page.INTRO, OnboardingActivity.nextPage(p, true));
    }

    @Test public void profileRoundTripKeepsConsentFlags() throws Exception {
        Profile p = new Profile();
        p.answers[Profile.GENDER] = "Woman"; p.adultConfirmed = true; p.complete = true; p.trialStartedAt = "2026-09-17T10:00:00Z";
        JSONObject json = p.json();
        assertTrue(json.isNull("goal"));
        Profile back = Profile.fromJson(new JSONObject(json.toString()));
        assertEquals("Woman", back.answers[Profile.GENDER]);
        assertTrue(back.adultConfirmed && back.complete && back.trialStarted());
    }

    @Test public void bubbleNeedsVerifiedAdultWithTrialAndSurvivesShortOfflinePeriods() throws Exception {
        assertTrue(TestAccounts.onboarded().canUseBubble());
        long now = System.currentTimeMillis();
        assertTrue("Within 72h offline allowance", TestAccounts.onboarded(now - 3_600_000).canUseBubble());
        assertFalse("Beyond offline allowance", TestAccounts.onboarded(now - AccountStore.OFFLINE_ALLOWANCE_MS - 60_000).canUseBubble());
        assertFalse(TestAccounts.signedOut().canUseBubble());
    }

    @Test public void signedOutLaunchStartsAtSignIn() {
        AccountStore.replaceForTests(TestAccounts.signedOut());
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.HIDE_OVERLAY_WINDOWS);
        try (var controller = Robolectric.buildActivity(OnboardingActivity.class).setup()) {
            assertEquals(OnboardingActivity.Page.SIGN_IN, controller.get().page());
        }
    }

    @Test public void iconLaunchShowsBrandSplashForTwoSecondsThenSignIn() {
        AccountStore.replaceForTests(TestAccounts.signedOut());
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.HIDE_OVERLAY_WINDOWS);
        android.content.Intent launcher = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        try (var controller = Robolectric.buildActivity(OnboardingActivity.class, launcher).setup()) {
            OnboardingActivity activity = controller.get();
            assertTrue(activity.splashShowing());
            assertEquals("Smarter choices, healthier India", Splash.TAGLINE);
            android.view.View splash = ((android.view.ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            assertEquals("BetterChoice India. Smarter choices, healthier India", splash.getContentDescription());
            assertEquals(2000, Splash.DURATION_MS);
            // Robolectric advances its clock while running the fade-in animation, so only check the end state.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(Splash.DURATION_MS + 100));
            assertFalse(activity.splashShowing());
            assertEquals(OnboardingActivity.Page.SIGN_IN, activity.page());
        }
    }

    @Test public void skipSignInCreatesALocalTesterAccountAndNeverLeavesThePhone() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        TestMode.endBypass(app);
        assertTrue("Debug builds offer the skip option", TestMode.canBypass(app));
        assertEquals("Try as guest (test build only)", TestMode.BYPASS_LABEL);

        AccountStore store = TestAccounts.signedOut();
        AuthController controller = new AuthController(app, store, new AuthApi(TestMode.transport(app)));
        String[] result = {"pending"};
        controller.startTestSession(error -> result[0] = error == null ? "ok" : error);
        long deadline = System.currentTimeMillis() + 10_000;
        while ("pending".equals(result[0]) && System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try { Thread.sleep(25); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
        assertEquals("ok", result[0]);
        assertTrue("Bypass is remembered so no request reaches Supabase", TestMode.bypassActive(app));
        assertTrue(store.signedIn());
        assertTrue("Marked as a test account", store.testMode());
        assertFalse("The questions and trial are still required", store.canUseBubble());

        controller.signOut();
        assertFalse("Signing out ends the test session", TestMode.bypassActive(app));
        assertFalse(store.hasSession());
    }

    @Test public void onlyTheCodeScreenBlocksScreenSharing() {
        AccountStore.replaceForTests(TestAccounts.signedOut());
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.HIDE_OVERLAY_WINDOWS);
        try (var controller = Robolectric.buildActivity(OnboardingActivity.class).setup()) {
            OnboardingActivity activity = controller.get();
            int secure = android.view.WindowManager.LayoutParams.FLAG_SECURE;
            assertEquals(OnboardingActivity.Page.SIGN_IN, activity.page());
            assertEquals("Sign-in screen can be shared", 0, activity.getWindow().getAttributes().flags & secure);
            activity.showCodePageForTests();
            assertEquals("One-time codes must never be captured", secure, activity.getWindow().getAttributes().flags & secure);
        }
    }

    @Test public void debugTestModeVerifiesOnlyTheTestCode() throws Exception {
        // Starting a test session is what routes requests to the local stand-in. Asserting instead
        // that this build has no Supabase configuration made the test pass only in an unconfigured
        // build, which is not the build anyone ships or tests on a phone.
        android.content.Context app = RuntimeEnvironment.getApplication();
        TestMode.startBypass(app);
        try {
            AuthApi api = new AuthApi(TestMode.transport(app));
            AuthApi.Failure wrong = assertThrows(AuthApi.Failure.class, () -> api.verify("+919876543210", true, "000000"));
            assertEquals(400, wrong.status);
            JSONObject session = api.verify("+919876543210", true, TestMode.CODE);
            assertTrue(session.getBoolean("test_mode"));
            AccountStore store = TestAccounts.signedOut();
            store.session(session);
            assertTrue(store.signedIn());
            assertTrue(store.testMode());
            assertFalse("Onboarding is still required", store.canUseBubble());
        } finally {
            TestMode.endBypass(app);
        }
    }

    /** A leftover test session must never swallow a real sign-in, or no code is ever sent. */
    @Test public void askingForARealCodeEndsAStaleTestSession() {
        android.content.Context app = RuntimeEnvironment.getApplication();
        // The controller is a singleton that outlives one test, so bind a fresh one to this app.
        AuthController.replaceForTests(null);
        try {
            TestMode.startBypass(app);
            assertTrue(TestMode.bypassActive(app));
            AuthController.get(app).send("someone@example.com", false, "", error -> { });
            assertFalse("A real identity must clear the local-only routing", TestMode.bypassActive(app));

            TestMode.startBypass(app);
            AuthController.get(app).send(TestMode.TESTER, false, "", error -> { });
            assertTrue("The tester's own sign-in keeps the local session", TestMode.bypassActive(app));
        } finally {
            TestMode.endBypass(app);
            AuthController.replaceForTests(null);
        }
    }
}
