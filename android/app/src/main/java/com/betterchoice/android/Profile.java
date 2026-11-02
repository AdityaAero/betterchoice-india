package com.betterchoice.android;

import org.json.JSONObject;
import org.json.JSONException;

/** Optional answers only; no inference, score, diet advice or demographic targeting. */
final class Profile {
    static final String[] KEYS = {"gender", "age", "weight_kg", "height_cm", "activity",
        "diet", "goal", "health_flags", "buys", "health_other",
        "diet_detail", "goals", "pregnancy_stage", "pre_pregnancy_weight", "waist_cm",
        "trace_severity", "diabetes_type", "kidney_stage"};
    static final int GENDER = 0, AGE = 1, WEIGHT = 2, HEIGHT = 3, ACTIVITY = 4,
        DIET = 5, GOAL = 6, HEALTH = 7, BUYS = 8,
        /**
         * Free text under the allergy checklist, for anything the list does not cover. Not a screen
         * of its own: it belongs to the health screen, so it is not in {@link #GROUPS}, and being
         * optional it must never make that screen count as unfinished.
         */
        HEALTH_OTHER = 9,
        /** The diet as chosen (Jain, Eggetarian, halal...). DIET keeps the server's three values. */
        DIET_DETAIL = 10,
        /** Goal codes, several allowed. GOAL keeps the server's single value. */
        GOALS = 11,
        /** Follow-ups on the health screen, asked only when the condition is ticked. */
        PREGNANCY_STAGE = 12, PRE_PREGNANCY_WEIGHT = 13, WAIST = 14, TRACE_SEVERITY = 15,
        DIABETES_TYPE = 16, KIDNEY_STAGE = 17;

    /** Answers that never make a screen count as unfinished. */
    static final java.util.Set<Integer> OPTIONAL = java.util.Set.of(HEALTH_OTHER, WAIST, PREGNANCY_STAGE,
        PRE_PREGNANCY_WEIGHT, TRACE_SEVERITY, DIABETES_TYPE, KIDNEY_STAGE);

    /**
     * Questions that belong on one screen. Asking height and weight on two separate screens is two
     * taps and two page loads for one thought; a person answering "about me" is already holding both
     * numbers in their head. Six screens for nine questions, and a screen where everything is
     * already known is skipped rather than shown and dismissed.
     */
    static final int[][] GROUPS = {
        {GENDER, AGE},
        {HEIGHT, WEIGHT, WAIST},
        {ACTIVITY},
        {DIET_DETAIL, GOALS},
        {HEALTH},
        {BUYS}};

    /** The heading for each screen, which is the thought being asked about, not the field name. */
    static final String[] GROUP_TITLES = {
        "About you", "Your build", "How active is your week?", "How you eat",
        "Anything we should avoid?", "What do you buy regularly?"};
    static final String[] GROUP_REASONS = {
        "(Woman or Man selects the energy equations; any other answer averages them)",
        "(weight sets your energy, fat, sugar and protein references)",
        "(sets your daily energy estimate, as ICMR-NIN does)",
        "(what we filter out, and what we aim for)",
        "(flags declared restrictions; cannot guarantee safety)",
        "(weekly or more often — so we lead with these)"};

    /** True when every question on this screen already has an answer. */
    boolean groupAnswered(int group) {
        for (int question : GROUPS[group]) if (!OPTIONAL.contains(question) && answers[question].isBlank()) return false;
        return true;
    }

    /** Screens still worth showing, in order. Anything already answered is not asked again. */
    java.util.List<Integer> remainingGroups() {
        java.util.List<Integer> remaining = new java.util.ArrayList<>();
        for (int g = 0; g < GROUPS.length; g++) if (!groupAnswered(g)) remaining.add(g);
        return remaining;
    }

    /**
     * The columns the Supabase `profiles` table actually has. Activity and the health answers stay
     * on the phone: there is no column for them yet, and allergies and conditions are the one kind
     * of answer that should not be synced to a server by default.
     */
    private static final java.util.Set<String> SYNCED = java.util.Set.of(
        "gender", "age", "weight_kg", "height_cm", "diet", "goal");

    static final String[] TITLES = {
        "Your gender?", "Your age?", "Your weight?", "Your height?", "How active is your week?",
        "How do you eat?", "What matters most right now?", "Anything we should avoid?",
        "What do you buy regularly?", "Other allergy or condition?",
        "How do you eat?", "What are your goals?", "How far along, or baby's age?", "Weight before pregnancy?",
        "Your waist?", "Avoid 'may contain' too?", "Which diabetes?", "Are you on dialysis?"};

    /** One short line under each question saying what the answer is actually used for. */
    static final String[] REASONS = {
        "(Woman/Man selects energy equations; others use an average)",
        "(needs change with life stage)",
        "(sets your protein, energy and daily limits)",
        "(saved for future reviewed personalisation)",
        "(sets your energy estimate: ICMR-NIN activity levels)",
        "(we filter out what you don't eat)",
          "(prioritises measurable comparisons for the same portion)",
          "(flags declared allergens; cannot guarantee safety)",
        "(weekly or more often — so we lead with these)",
        "(optional)",
        "(we filter out what you don't eat)",
        "(choose any; weight loss and gain can't both apply)",
        "(changes your energy and protein needs)",
        "(BMI in pregnancy uses this weight)",
        "(optional; NIN cut-offs 90 cm men, 80 cm women)",
        "(traces matter for severe allergies)",
        "(type 1 adds carbohydrate grams per portion)",
        "(dialysis changes protein needs)"};

    static final String[][] CHOICES = {
        {"Woman", "Man", "Non-binary", "Self-describe", "Prefer not to say"},
        null, null, null,
        // Matches the three activity levels ICMR-NIN publishes energy figures for.
        {"Mostly sitting", "Moderately active", "Heavy physical work"},
        {"Vegetarian", "Non-vegetarian", "Vegan"},
        {"Weight loss", "Weight gain", "Muscle gain", "Improve nutritional awareness"},
        null, null, null,
        // FSSAI: egg is non-vegetarian; milk, honey and shellac are not (Labelling Regulations 2(o)).
        {"Vegetarian", "Eggetarian", "Jain", "Non-vegetarian / both", "Non-vegetarian (halal)", "Vegan"},
        null, null, null, null, null, null, null};

    /** Goal codes; weight loss and weight gain exclude each other. */
    static final String[][] GOAL_OPTIONS = {
        {"loss", "Weight loss"}, {"gain", "Weight gain"}, {"muscle", "Muscle gain"},
        {"sugar", "Sugar control"}, {"aware", "Improve nutritional awareness"}};
    static final String[][] PREGNANCY_OPTIONS = {
        {"t1", "First trimester"}, {"t2", "Second trimester"}, {"t3", "Third trimester"}};
    static final String[][] BREASTFEEDING_OPTIONS = {{"bf0", "Baby 0–6 months"}, {"bf7", "Baby 7–12 months"}};
    static final String[][] SEVERITY_OPTIONS = {{"strict", "Yes, avoid 'may contain' too"}, {"ingredients", "No, only actual ingredients"}};
    static final String[][] DIABETES_OPTIONS = {{"type1", "Type 1"}, {"type2", "Type 2"}, {"unsure", "Not sure"}};
    static final String[][] KIDNEY_OPTIONS = {{"no_dialysis", "Not on dialysis"}, {"dialysis", "On dialysis"}, {"unsure", "Don't know"}};

    /**
     * The allergens FSSAI requires a pack to declare, plus the conditions that change what is safe.
     * Stored as stable codes so the wording on screen can change without losing anyone's answers.
     * `none` is exclusive: choosing it clears the rest, and choosing any other clears it.
     */
    static final String[][] HEALTH_OPTIONS = {
        {"none", "None that I know of"},
        {"milk", "Milk"}, {"egg", "Egg"}, {"fish", "Fish"}, {"crustacea", "Shellfish"},
        {"peanut", "Peanuts"}, {"tree_nut", "Tree nuts"}, {"soy", "Soy"},
        {"wheat", "Wheat allergy"}, {"gluten", "Coeliac / gluten-free diet"},
        {"sesame", "Sesame"}, {"sulphite", "Sulphites"},
        {"lactose", "Lactose intolerance"}, {"diabetes", "Diabetes"},
        {"hypertension", "High blood pressure"}, {"cholesterol", "High cholesterol or heart disease"},
        {"kidney", "Kidney condition"}, {"pregnancy", "Pregnant"}, {"breastfeeding", "Breastfeeding"}};

    /**
     * The aisles people actually shop in. Knowing these lets the app say something useful before a
     * shopper has scanned anything, and lets the flash cards lead with what they buy every week.
     */
    static final String[][] BUY_OPTIONS = {
        {"snacks", "Chips & namkeen"}, {"biscuits", "Biscuits & cookies"},
        {"drinks", "Soft drinks & juices"}, {"dairy", "Milk, curd & paneer"},
        {"bread", "Bread & bakery"}, {"noodles", "Noodles & pasta"},
        {"breakfast", "Cereal & breakfast"}, {"chocolate", "Chocolate & sweets"},
        {"readymade", "Ready-to-eat meals"}, {"oils", "Cooking oil & ghee"},
        {"masala", "Masala & sauces"}, {"health", "Protein & health foods"}};

    static String buyLabel(String code) {
        for (String[] option : BUY_OPTIONS) if (option[0].equals(code)) return option[1];
        return code;
    }

    java.util.List<String> buyCodes() { return codes(answers[BUYS]); }

    /** Goal codes, read from the older single goal when the newer list was never filled. */
    java.util.List<String> goalCodes() {
        java.util.List<String> list = codes(answers[GOALS]);
        if (!list.isEmpty()) return list;
        String code = goalCode(answers[GOAL]);
        if (code != null) list.add(code);
        return list;
    }

    /** The diet as chosen, read from the older three-way answer when the newer one was never filled. */
    String dietDetail() {
        if (!answers[DIET_DETAIL].isBlank()) return answers[DIET_DETAIL];
        switch (answers[DIET]) {
            case "Vegetarian": return "Vegetarian";
            case "Non-vegetarian": return "Non-vegetarian / both";
            case "Vegan": return "Vegan";
            default: return "";
        }
    }

    /** The server accepts only Vegetarian, Non-vegetarian or Vegan. */
    static String dietForServer(String detail) {
        switch (detail == null ? "" : detail) {
            case "Vegetarian": case "Eggetarian": case "Jain": return "Vegetarian";
            case "Non-vegetarian / both": case "Non-vegetarian (halal)": return "Non-vegetarian";
            case "Vegan": return "Vegan";
            default: return "";
        }
    }

    /** The server accepts one goal: the body goal if any, else nutritional awareness. */
    static String goalForServer(java.util.List<String> goals) {
        if (goals.contains("loss")) return "Weight loss";
        if (goals.contains("gain")) return "Weight gain";
        if (goals.contains("muscle")) return "Muscle gain";
        return goals.isEmpty() ? "" : "Improve nutritional awareness";
    }

    static String goalCode(String goal) {
        switch (goal == null ? "" : goal) {
            case "Weight loss": return "loss";
            case "Weight gain": return "gain";
            case "Muscle gain": return "muscle";
            case "Improve nutritional awareness": case "Nutritional awareness": return "aware";
            default: return null;
        }
    }

    /** A follow-up whose condition was unticked is cleared, so a stale stage can never apply. */
    void clearUnusedFollowUps() {
        java.util.List<String> health = healthCodes();
        boolean allergy = false;
        for (String code : health) if (Allergen.byCode(code) != null || "wheat".equals(code)) allergy = true;
        if (!allergy) answers[TRACE_SEVERITY] = "";
        boolean pregnant = health.contains("pregnancy"), feeding = health.contains("breastfeeding");
        String stage = answers[PREGNANCY_STAGE];
        if (pregnant ? !stage.startsWith("t") : feeding ? !stage.startsWith("bf") : !stage.isEmpty()) answers[PREGNANCY_STAGE] = "";
        if (!pregnant) answers[PRE_PREGNANCY_WEIGHT] = "";
        if (!health.contains("diabetes")) answers[DIABETES_TYPE] = "";
        if (!health.contains("kidney")) answers[KIDNEY_STAGE] = "";
    }

    /** Keeps the server's three-way diet and single goal in step with the detailed answers. */
    void syncDerived() {
        String diet = dietForServer(answers[DIET_DETAIL]);
        if (!diet.isEmpty()) answers[DIET] = diet;
        String goal = goalForServer(codes(answers[GOALS]));
        if (!goal.isEmpty()) answers[GOAL] = goal;
    }

    private static java.util.List<String> codes(String raw) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return list;
        for (String part : raw.split(",")) {
            String code = part.trim();
            if (!code.isEmpty() && !list.contains(code)) list.add(code);
        }
        return list;
    }

    /** Where the allergens end and the conditions begin, for the two headings on that screen. */
    static final int FIRST_CONDITION = 12;

    static String healthLabel(String code) {
        for (String[] option : HEALTH_OPTIONS) if (option[0].equals(code)) return option[1];
        return code;
    }

    /** The codes ticked on the health screen, in registry order. */
    java.util.List<String> healthCodes() { return codes(answers[HEALTH]); }
    final String[] answers = new String[KEYS.length];
    boolean complete;
    boolean adultConfirmed;
    String trialStartedAt = "";

    Profile() { java.util.Arrays.fill(answers, ""); }

    boolean trialStarted() { return !trialStartedAt.isEmpty(); }

    static Profile fromJson(JSONObject json) {
        Profile p = new Profile();
        if (json == null) return p;
        for (int i = 0; i < KEYS.length; i++) p.answers[i] = json.isNull(KEYS[i]) ? "" : json.optString(KEYS[i], "");
        p.complete = json.optBoolean("onboarding_complete", false);
        p.adultConfirmed = json.optBoolean("adult_confirmed", false);
        p.trialStartedAt = json.isNull("trial_started_at") ? "" : json.optString("trial_started_at", "");
        return p;
    }

    Profile copy() { return fromJson(json()); }

    /** Reloading a server profile must preserve this account's deliberately device-only answers. */
    Profile withLocalAnswers(Profile local) {
        Profile merged = copy();
        if (local != null) for (int i = 0; i < KEYS.length; i++)
            if (!SYNCED.contains(KEYS[i])) merged.answers[i] = local.answers[i];
        // Changed on another device: the server's simpler answer wins over a detail it contradicts.
        if (!merged.answers[DIET].isBlank() && !merged.answers[DIET].equals(dietForServer(merged.answers[DIET_DETAIL])))
            merged.answers[DIET_DETAIL] = "";
        if (!merged.answers[GOAL].isBlank() && !merged.answers[GOAL].equals(goalForServer(codes(merged.answers[GOALS]))))
            merged.answers[GOALS] = "";
        return merged;
    }

    JSONObject json() {
        JSONObject obj = new JSONObject();
        try {
            for (int i = 0; i < KEYS.length; i++) obj.put(KEYS[i], answers[i].isBlank() ? JSONObject.NULL : answers[i]);
            obj.put("onboarding_complete", complete);
            obj.put("adult_confirmed", adultConfirmed);
            obj.put("trial_started_at", trialStartedAt.isEmpty() ? JSONObject.NULL : trialStartedAt);
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return obj;
    }

    /** Only the columns Supabase has. Health answers and activity never leave the phone. */
    JSONObject syncJson() {
        JSONObject obj = new JSONObject();
        String[] sent = answers.clone();
        // The server holds the three-way diet and one goal; the detail stays on this phone.
        String diet = dietForServer(answers[DIET_DETAIL]), goal = goalForServer(codes(answers[GOALS]));
        if (!diet.isEmpty()) sent[DIET] = diet;
        if (!goal.isEmpty()) sent[GOAL] = goal;
        try {
            for (int i = 0; i < KEYS.length; i++) {
                if (!SYNCED.contains(KEYS[i])) continue;
                obj.put(KEYS[i], sent[i].isBlank() ? JSONObject.NULL : sent[i]);
            }
            obj.put("onboarding_complete", complete);
            obj.put("adult_confirmed", adultConfirmed);
            obj.put("trial_started_at", trialStartedAt.isEmpty() ? JSONObject.NULL : trialStartedAt);
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return obj;
    }

    static String validate(int step, String raw) {
        String value = raw.trim();
        if (value.isEmpty()) return "";
        if (step == BUYS) {
            java.util.List<String> kept = new java.util.ArrayList<>();
            for (String part : value.split(",")) {
                String code = part.trim();
                for (String[] option : BUY_OPTIONS) {
                    if (option[0].equals(code) && !kept.contains(code)) { kept.add(code); break; }
                }
            }
            return String.join(",", kept);
        }
        if (step == HEALTH) {
            // A list of known codes; anything else is dropped rather than stored.
            java.util.List<String> kept = new java.util.ArrayList<>();
            for (String part : value.split(",")) {
                String code = part.trim();
                for (String[] option : HEALTH_OPTIONS) {
                    if (option[0].equals(code) && !kept.contains(code)) { kept.add(code); break; }
                }
            }
            // "None that I know of" cannot sit alongside an actual allergy.
            if (kept.contains("none") && kept.size() > 1) kept.remove("none");
            return String.join(",", kept);
        }
        if (step == GOALS) {
            java.util.List<String> kept = new java.util.ArrayList<>();
            for (String part : value.split(",")) {
                String code = part.trim();
                for (String[] option : GOAL_OPTIONS) if (option[0].equals(code) && !kept.contains(code)) { kept.add(code); break; }
            }
            if (kept.contains("loss") && kept.contains("gain")) throw new IllegalArgumentException("Choose weight loss or weight gain, not both.");
            return String.join(",", kept);
        }
        if (step == PREGNANCY_STAGE || step == TRACE_SEVERITY || step == DIABETES_TYPE || step == KIDNEY_STAGE) {
            String[][] options = step == PREGNANCY_STAGE ? null : step == TRACE_SEVERITY ? SEVERITY_OPTIONS
                : step == DIABETES_TYPE ? DIABETES_OPTIONS : KIDNEY_OPTIONS;
            java.util.List<String[]> all = new java.util.ArrayList<>();
            if (options == null) { all.addAll(java.util.Arrays.asList(PREGNANCY_OPTIONS)); all.addAll(java.util.Arrays.asList(BREASTFEEDING_OPTIONS)); }
            else all.addAll(java.util.Arrays.asList(options));
            for (String[] option : all) if (option[0].equals(value)) return value;
            throw new IllegalArgumentException("Choose an option or skip this question.");
        }
        if (step == ACTIVITY || step == DIET || step == GOAL || step == DIET_DETAIL) {
            if (!java.util.Arrays.asList(CHOICES[step]).contains(value)) throw new IllegalArgumentException("Choose an option or skip this question.");
        } else if (step == GENDER) {
            if (value.length() > 60) throw new IllegalArgumentException("Keep this under 60 characters.");
        } else if (step == AGE || step == WEIGHT || step == HEIGHT || step == PRE_PREGNANCY_WEIGHT || step == WAIST) {
            double n;
            try {
                if (!value.matches(step == AGE ? "[0-9]{1,3}" : "[0-9]{1,3}(\\.[0-9])?")) throw new NumberFormatException();
                n = Double.parseDouble(value);
            } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Check the value, or skip this question."); }
            if (step == AGE && n < 18) throw new IllegalArgumentException("BetterChoice is currently for adults 18 and over.");
            int min = step == AGE ? 18 : step == HEIGHT || step == WAIST ? 40 : 10;
            int max = step == AGE ? 120 : step == HEIGHT ? 260 : step == WAIST ? 200 : 500;
            if (n < min || n > max) throw new IllegalArgumentException("Check the value, or skip this question.");
        } else if (value.length() > 300) throw new IllegalArgumentException("Keep your note under 300 characters.");
        return value;
    }
}
