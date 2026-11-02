package com.betterchoice.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the shopper has told us that could make a food unsafe for them, as opposed to merely
 * unhealthy. Held apart from {@link Profile} on purpose: knowing someone's goal is weight loss is
 * ordinary product data, and knowing they are allergic to peanuts is not.
 *
 * <p><b>"No allergies recorded" is not the same as "this person has no allergies."</b> An empty list
 * is the commonest state in any consumer app — most people never open the settings screen — so
 * treating it as a clean bill of health would be the single most dangerous assumption this app could
 * make. {@link Allergies} keeps the four states apart, and only {@link Allergies#KNOWN} ever carries
 * a list.
 */
final class SafetyProfile {
    /** Whether the shopper has actually told us anything about allergies. */
    enum Allergies {
        /** They listed one or more. */
        KNOWN,
        /** They said they know of none. Still not a guarantee — it is a self-report. */
        NONE_KNOWN,
        /** They were asked and said they were not sure. */
        UNSURE,
        /** They were never asked, or skipped it. The default. */
        NOT_ANSWERED
    }

    /** How strictly to treat a possible trace, which is a different question from having it. */
    enum Severity {
        /** A reaction that could be dangerous: traces are treated as the real thing. */
        SEVERE,
        /** Uncomfortable rather than dangerous; traces are flagged, not blocked. */
        MILD,
        /** Not stated. Treated as severe, because guessing the other way is the unsafe guess. */
        UNKNOWN
    }

    final Allergies allergyState;
    private final List<Allergen> allergens;
    final Severity severity;
    /** Vegetarian, Vegan or Non-vegetarian, taken from the ordinary profile. */
    final String diet;

    private SafetyProfile(Allergies allergyState, List<Allergen> allergens, Severity severity, String diet) {
        this.allergyState = allergyState;
        this.allergens = Collections.unmodifiableList(new ArrayList<>(allergens));
        this.severity = severity;
        this.diet = diet == null ? "" : diet;
    }

    /** Nobody has been asked anything yet. */
    static SafetyProfile unknown() {
        return new SafetyProfile(Allergies.NOT_ANSWERED, Collections.emptyList(), Severity.UNKNOWN, "");
    }

    static SafetyProfile of(Allergies state, List<Allergen> allergens, Severity severity, String diet) {
        List<Allergen> list = state == Allergies.KNOWN && allergens != null ? allergens : Collections.emptyList();
        return new SafetyProfile(state, list, severity == null ? Severity.UNKNOWN : severity, diet);
    }

    /**
     * Reads what the shopper ticked on the health screen. An untouched screen stays
     * {@link Allergies#NOT_ANSWERED}; "None that I know of" becomes {@link Allergies#NONE_KNOWN},
     * which is their word and never a guarantee.
     */
    static SafetyProfile fromProfile(Profile profile) {
        if (profile == null) return unknown();
        String diet = profile.dietDetail();
        // "Should 'may contain' count as unsafe?" — unanswered stays UNKNOWN, which is treated as severe.
        String trace = profile.answers[Profile.TRACE_SEVERITY];
        Severity severity = "strict".equals(trace) ? Severity.SEVERE : "ingredients".equals(trace) ? Severity.MILD : Severity.UNKNOWN;
        java.util.List<String> codes = profile.healthCodes();
        if (codes.isEmpty()) {
            return new SafetyProfile(Allergies.NOT_ANSWERED, Collections.emptyList(), Severity.UNKNOWN, diet);
        }
        List<Allergen> allergens = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        for (String code : codes) {
            if ("none".equals(code)) continue;
            Allergen allergen = Allergen.byCode(code);
            if (allergen != null) allergens.add(allergen); else conditions.add(code);
        }
        Allergies state = allergens.isEmpty()
            ? (codes.contains("none") ? Allergies.NONE_KNOWN : Allergies.NOT_ANSWERED)
            : Allergies.KNOWN;
        SafetyProfile safety = new SafetyProfile(state, allergens, severity, diet);
        safety.conditions.addAll(conditions);
        return safety;
    }

    /** Condition codes such as `lactose`, `diabetes`, `kidney`. Never used to claim suitability. */
    final List<String> conditions = new ArrayList<>();

    boolean has(String condition) { return conditions.contains(condition); }

    List<Allergen> allergens() { return allergens; }

    boolean hasAllergies() { return allergyState == Allergies.KNOWN && !allergens.isEmpty(); }

    /** Traces block only when a reaction could be dangerous, or when we were not told. */
    boolean tracesAreDangerous() { return severity != Severity.MILD; }

    boolean avoidsAnimalIngredients() { return DietRules.Diet.of(diet).vegetarianFamily(); }

    /** Wheat allergy, as distinct from coeliac disease (every gluten cereal). */
    boolean wheatAllergy() { return conditions.contains("wheat"); }

    boolean isVegan() { return "Vegan".equals(diet); }
}
