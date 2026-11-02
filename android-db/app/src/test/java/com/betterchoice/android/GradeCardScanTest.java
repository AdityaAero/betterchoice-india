package com.betterchoice.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
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
 * The Scan button appeared to do nothing: it closed the card, and the result that came back matched
 * the state already shown, so nothing reopened. These tests pin the two halves of the fix.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class GradeCardScanTest {
    private final List<View> attached = new ArrayList<>();
    private final int[] removals = {0};

    private GradeCard card(Context context) {
        WindowManager manager = (WindowManager) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class[]{WindowManager.class}, (p, method, args) -> {
                if (method.getName().equals("addView")) attached.add((View) args[0]);
                if (method.getName().equals("removeViewImmediate")) removals[0]++;
                return null;
            });
        return new GradeCard(context, manager);
    }

    private static ProductPage page() {
        return ProductPage.of("Lay's India's Magic Masala Potato Chips 50 g", 20.0, "com.grofers.customerapp");
    }

    /** Collects every view of the given type, so a button can be found by its label. */
    private static void collect(View view, List<View> into) {
        into.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), into);
        }
    }

    private Button button(String startsWith) {
        List<View> all = new ArrayList<>();
        collect(attached.get(attached.size() - 1), all);
        for (View view : all) {
            if (view instanceof Button && ((Button) view).getText().toString().startsWith(startsWith)) return (Button) view;
        }
        return null;
    }

    private String textOf() {
        List<View> all = new ArrayList<>();
        collect(attached.get(attached.size() - 1), all);
        StringBuilder text = new StringBuilder();
        for (View view : all) if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        return text.toString();
    }

    private static final class Recorder implements GradeCard.Actions {
        int scans;
        @Override public void skip(ProductPage page) { }
        @Override public void addDetails(ProductPage page) { }
        @Override public void cannotFind(ProductPage page) { }
        @Override public void scanNow(ProductPage page) { scans++; }
    }

    @Test public void tappingScanAsksForAScanAndLeavesTheCardForTheServiceToReplace() {
        Context context = RuntimeEnvironment.getApplication();
        GradeCard card = card(context);
        Recorder actions = new Recorder();
        card.show(GradeResult.needsLabel(page(), true), "com.grofers.customerapp", 0, 0, 40, actions);

        Button scan = button("Scan the pack");
        assertNotNull("The scan button must be on the card that asks for the pack", scan);
        int removalsBefore = removals[0];
        scan.performClick();

        assertEquals("The tap must reach the service", 1, actions.scans);
        assertEquals("The button must not close the card itself; the service swaps in the scanning state",
            removalsBefore, removals[0]);
    }

    @Test public void theScanningStateTellsTheUserSomethingIsHappening() {
        Context context = RuntimeEnvironment.getApplication();
        GradeCard card = card(context);
        card.show(GradeResult.scanning(page(), "08901058000450"), "com.grofers.customerapp", 0, 0, 40, new Recorder());
        String shown = textOf();
        assertTrue("Reading state must be visible: " + shown, shown.contains("Reading the pack"));
        assertTrue("A barcode already seen is worth showing", shown.contains("8901058000450"));
    }

    @Test public void aScanThatFoundNothingStillOffersTheNextStep() {
        Context context = RuntimeEnvironment.getApplication();
        GradeCard card = card(context);
        card.show(GradeResult.needsInput(page(), null), "com.grofers.customerapp", 0, 0, 40, new Recorder());
        assertNotNull("The user must be able to type the label", button("Add the label"));
        assertNotNull("...or try the scan again", button("Scan again"));
    }
}
