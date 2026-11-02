package com.betterchoice.android;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * The card's face, as the founder asked for it (9 Oct 2026): no product name or price (the shop shows them), what counts
 * against the product in dark red with the shopper's own reasons first, what is good about it in dark green, the
 * ingredient evidence folded in its own menu, the figures and references behind the ⓘ, and few words.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class CardLayoutTest {
    private static final String NAME = "Fortune Soya Chunks", LABEL = "Nutrition Information\nEnergy (kcal) 345.0, Protein (g) 52.0, "
        + "Carbohydrates (g) 33.0, Total Sugars (g) 7.0, Dietary Fibre (g) 13.0, Total Fat (g) 0.5, Saturated Fat (g) 0.1, "
        + "Sodium (mg) 1300.0\nIngredients: Defatted Soya Flour, Iodised Salt\nAllergen Information: Contains: Soy";

    private static GradeResult soyaChunks() {
        Nutrition n = NutritionParser.parse(LABEL);
        assertNotNull(n);
        return GradeResult.graded(n, GradeResult.Source.LABEL_TEXT, ProductPage.of(NAME, 60.0, "com.zeptoconsumerapp"), null,
            Beverage.NOT_A_DRINK, LABEL);
    }

    static Profile profile(String health, String goals) {
        Profile p = new Profile();
        p.adultConfirmed = true;
        java.util.Arrays.fill(p.answers, "");
        p.answers[Profile.AGE] = "35"; p.answers[Profile.WEIGHT] = "70"; p.answers[Profile.HEIGHT] = "170";
        p.answers[Profile.ACTIVITY] = "Mostly sitting"; p.answers[Profile.GOALS] = goals; p.answers[Profile.HEALTH] = health;
        p.syncDerived();
        return p;
    }

    private static View card(Profile profile) {
        Context context = RuntimeEnvironment.getApplication();
        WindowManager manager = (WindowManager) Proxy.newProxyInstance(CardLayoutTest.class.getClassLoader(),
            new Class[]{WindowManager.class}, (p, method, args) -> null);
        return new GradeCard(context, manager).build(soyaChunks(), "com.zeptoconsumerapp", new GradeCard.Actions() {
            @Override public void skip(ProductPage page) { }
            @Override public void addDetails(ProductPage page) { }
            @Override public void cannotFind(ProductPage page) { }
            @Override public void scanNow(ProductPage page) { }
        }, profile);
    }

    /** Every TextView, with whether it shows on the card's face (no hidden container above it). */
    private static void collect(View view, boolean shown, List<TextView> all, List<TextView> visible) {
        boolean here = shown && view.getVisibility() == View.VISIBLE;
        if (view instanceof TextView) { all.add((TextView) view); if (here) visible.add((TextView) view); }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), here, all, visible);
        }
    }

    private static TextView startingWith(List<TextView> views, String prefix) {
        for (TextView v : views) if (v.getText().toString().startsWith(prefix)) return v;
        return null;
    }

    private static boolean bold(TextView v) {
        Typeface face = v.getTypeface();
        return (face != null && (face.isBold() || (face.getStyle() & Typeface.BOLD) != 0)) || v.getPaint().isFakeBoldText();
    }

    @Test public void theFaceShowsNoProductNameOrPriceAndFewWords() {
        List<TextView> all = new ArrayList<>(), visible = new ArrayList<>();
        collect(card(profile("", "aware")), true, all, visible);
        for (TextView v : all) {
            String text = v.getText().toString();
            assertFalse("the product's own name is not repeated: " + text, text.contains(NAME));
            assertFalse("nor its price: " + text, text.contains("₹60"));
        }
        assertTrue("few lines on the face: " + visible.size(), visible.size() <= 32);
        for (TextView v : visible) assertTrue("short lines: " + v.getText(), v.getText().length() <= 110);
    }

    @Test public void sodiumIsDarkRedAndPersonalForHighBloodPressureAndProteinIsDarkGreenForEveryone() {
        Context context = RuntimeEnvironment.getApplication();
        List<TextView> all = new ArrayList<>(), visible = new ArrayList<>();
        collect(card(profile("hypertension", "aware")), true, all, visible);
        TextView sodium = startingWith(visible, "•  High sodium");
        assertNotNull(sodium);
        assertTrue(sodium.getText().toString(), sodium.getText().toString().endsWith("your BP"));
        assertEquals(UiKit.bad(context), sodium.getCurrentTextColor());
        assertTrue("the shopper's own reason stands out", bold(sodium));
        TextView protein = startingWith(visible, "✓  High protein");
        assertNotNull(protein);
        assertEquals(UiKit.good(context), protein.getCurrentTextColor());
        assertNotNull(startingWith(visible, "✓  High fibre"));

        List<TextView> plainAll = new ArrayList<>(), plain = new ArrayList<>();
        collect(card(profile("", "aware")), true, plainAll, plain);
        TextView general = startingWith(plain, "•  High sodium");
        assertNotNull("still said for everyone, at the general 'high' level", general);
        assertFalse(general.getText().toString(), general.getText().toString().contains("your BP"));
        assertFalse("not the shopper's own reason", bold(general));
        assertNotNull("the good is said for everyone", startingWith(plain, "✓  High protein"));
    }

    @Test public void ingredientEvidenceAndTheDetailsAreOneTapAway() {
        List<TextView> all = new ArrayList<>(), visible = new ArrayList<>();
        collect(card(profile("", "aware")), true, all, visible);
        assertNotNull("the ingredients menu is offered", startingWith(visible, "Ingredients & allergens"));
        TextView list = startingWith(all, "Ingredients: Defatted Soya Flour");
        assertNotNull(list);
        assertFalse("the list itself is folded", visible.contains(list));
        TextView reference = startingWith(all, "Reference contribution");
        assertNotNull(reference);
        assertFalse("daily references wait behind the ⓘ", visible.contains(reference));
        TextView info = null;
        for (TextView v : visible) if ("ⓘ".contentEquals(v.getText())) info = v;
        assertNotNull("the details icon is on the face", info);
        info.performClick();
        List<TextView> after = new ArrayList<>(), shown = new ArrayList<>();
        collect(info.getRootView(), true, after, shown);
        assertTrue("one tap opens the details", shown.contains(reference));
    }
}
