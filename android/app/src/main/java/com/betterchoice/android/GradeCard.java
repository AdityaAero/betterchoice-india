package com.betterchoice.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small card shown only after the user taps the bubble. On its face, as few words as the facts allow: the colour, what
 * counts against the product for this shopper (dark red) and what is good about it (dark green), whether they can eat
 * it, and three better choices. The product's own name and price are not repeated: the shop's page shows them. The
 * ingredient evidence is one tap away in its own menu, and the figures, daily references and sources behind the ⓘ.
 */
final class GradeCard {
    interface Actions {
        void skip(ProductPage page);
        void addDetails(ProductPage page);
        /** User says the label is nowhere on this page: re-read once more, then offer the form. */
        void cannotFind(ProductPage page);
        /** User has the pack or its barcode on screen right now: read it immediately. */
        void scanNow(ProductPage page);
        /** User tapped a healthier swap: open the shop's own search for it and check it there. */
        default void checkSwap(ProductPage page, GradeResult rated, SwapEngine.Swap swap) {}
    }

    private final Context context;
    private final WindowManager manager;
    private View view;

    GradeCard(Context context, WindowManager manager) {
        this.context = context;
        this.manager = manager;
    }

    boolean isShowing() { return view != null; }

    // Absolute coordinates match the bubble's own placement.
    @android.annotation.SuppressLint("RtlHardcoded")
    void show(GradeResult result, String retailerPackage, int bubbleX, int bubbleY, int bubbleSize, Actions actions) {
        hide();
        int dp8 = UiKit.dp(context, 8);
        LinearLayout card = build(result, retailerPackage, actions, AccountStore.get(context).profile());

        // Leave room for OEM edge panels. Their overlap can legitimately mark every
        // card touch as partially obscured; retain touch protection and avoid the edge.
        int edge = UiKit.dp(context, 32);
        int width = Math.min(UiKit.dp(context, 300), context.getResources().getDisplayMetrics().widthPixels - edge * 2);
        // A long card scrolls rather than running off the screen.
        int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.72);
        ScrollView scroller = new ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroller.setClipToPadding(false);
        scroller.setElevation(dp8);
        scroller.addView(card);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable while it is open, so Back closes the card rather than leaving the shop's page underneath;
            // touches outside the card still reach the shop.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        scroller.setFocusableInTouchMode(true);
        scroller.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != android.view.KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) hide();
            return true;
        });
        params.gravity = Gravity.TOP | Gravity.LEFT;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        params.x = Math.max(edge, Math.min(bubbleX, screenWidth - width - edge));
        params.y = bubbleY + bubbleSize + dp8;
        if (params.y > screenHeight * 0.55) params.y = Math.max(dp8, bubbleY - UiKit.dp(context, 320));
        if (params.y + maxHeight > screenHeight) params.y = Math.max(dp8, screenHeight - maxHeight - dp8);
        try {
            manager.addView(scroller, params);
            view = scroller;
            scroller.requestFocus();
            // Android 13 and later deliver Back (and the back gesture) through the window's dispatcher.
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                android.window.OnBackInvokedDispatcher dispatcher = scroller.findOnBackInvokedDispatcher();
                if (dispatcher != null) {
                    dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, this::hide);
                }
            }
        } catch (RuntimeException unavailable) {
            view = null;
            DebugLog.failure("Unable to attach grade card");
        }
    }

    /** The card's content, without its window, for this shopper's profile: built apart so a test can read it. */
    LinearLayout build(GradeResult result, String retailerPackage, Actions actions, Profile profile) {
        int dp8 = UiKit.dp(context, 8);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp8 * 2, dp8 * 2, dp8 * 2, dp8 * 2);
        card.setBackground(UiKit.shape(context, UiKit.surface(context), UiKit.line(context), 18));
        card.setElevation(dp8);

        if (result.state == GradeResult.State.GRADED) {
            graded(card, result, retailerPackage, actions, profile);
            return card;
        }
        if (result.state == GradeResult.State.NEEDS_LABEL) {
            title(card, "Check the label details");
            card.addView(UiKit.text(context, LabelGuide.details(result.page), 14, UiKit.ink(context)));
            muted(card, LabelGuide.category(result.page));
            if (result.note != null) muted(card, result.note);
            evidenceWithoutGrade(card, result, profile);
            barcode(card, result.gtin);
            muted(card, "Read on your phone. The picture is never saved or sent.");
            // No hide() here: the service replaces this card with the scanning state straight away,
            // so the tap visibly does something instead of just closing the card.
            button(card, "Scan the pack  ⟳", true, () -> actions.scanNow(result.page));
            button(card, "Not on this page", false, () -> { hide(); actions.cannotFind(result.page); });
            button(card, "Skip", false, () -> { hide(); actions.skip(result.page); });
        } else if (result.state == GradeResult.State.SCANNING) {
            title(card, "Reading the pack…");
            card.addView(UiKit.text(context, "Hold still for a moment.", 14, UiKit.ink(context)));
            barcode(card, result.gtin);
            muted(card, "Looking for the nutrition panel and the barcode.");
        } else if (result.state == GradeResult.State.NEEDS_INPUT) {
            title(card, "Insufficient nutrition information");
            // Say what actually happened; "not found" on its own tells the shopper nothing.
            card.addView(UiKit.text(context, result.note != null ? result.note
                : "Not on the page, in the photo, or in our database.", 14, UiKit.ink(context)));
            muted(card, LabelGuide.details(result.page));
            muted(card, LabelGuide.category(result.page));
            evidenceWithoutGrade(card, result, profile);
            barcode(card, result.gtin);
            button(card, "Add the label  →", true, () -> { hide(); actions.addDetails(result.page); });
            button(card, "Scan again  ⟳", false, () -> actions.scanNow(result.page));
            button(card, "Skip", false, () -> { hide(); actions.skip(result.page); });
        } else if (result.state == GradeResult.State.SWAP_CHECKED) {
            title(card, "Checked on " + shopName(retailerPackage));
            checked(card, result.check, result.checkResult, retailerPackage, true);
        } else if (result.state == GradeResult.State.SKIPPED) {
            title(card, "Skipped");
            muted(card, "Not rating this one again today.");
        } else if (result.state == GradeResult.State.NOT_FOOD) {
            title(card, "Not food");
            muted(card, "BetterChoice rates packaged food and drinks.");
        } else {
            title(card, "Open any food item");
            muted(card, "The bubble turns into its rating:");
            for (NutriGrade.Band band : NutriGrade.Band.values()) {
                TextView row = UiKit.text(context, "●  " + band.name() + " · " + BubbleStyle.meaning(band), 13, UiKit.ink(context));
                row.setTextColor(BubbleStyle.argb(0xFF, band == NutriGrade.Band.B ? 0x8A6D00 : BubbleStyle.bandColor(band)));
                card.addView(row);
            }
        }
        // Closing is at the top, so a long card never has to be scrolled to the end to be dismissed.
        TextView close = UiKit.text(context, "Close  ✕", 13, UiKit.ink(context));
        close.setGravity(Gravity.END);
        close.setPadding(0, 0, 0, UiKit.dp(context, 4));
        close.setContentDescription("Close the BetterChoice card");
        close.setOnClickListener(v -> hide());
        card.addView(close, 0);
        return card;
    }

    void hide() {
        if (view == null) return;
        try { manager.removeViewImmediate(view); }
        catch (RuntimeException detached) { DebugLog.failure("Grade card removal unavailable"); }
        finally { view = null; }
    }

    /**
     * A rated product. The face: colour and two words, "not for you" when it is not, what counts against it for this
     * shopper in dark red and what is good about it in dark green, the ingredients menu, and three better choices.
     * Behind the ⓘ: the figures, the shopper's daily references, the rating's caveats and its sources.
     */
    private void graded(LinearLayout card, GradeResult result, String retailerPackage, Actions actions, Profile profile) {
        RecommendationContext person = RecommendationContext.of(profile);
        LinearLayout summary = column(), details = column();
        details.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(chip(result.band, 18), new LinearLayout.LayoutParams(UiKit.dp(context, 38), UiKit.dp(context, 38)));
        TextView verdict = UiKit.text(context, "  " + BubbleStyle.shortMeaning(result.band), 15, UiKit.ink(context));
        verdict.setTypeface(null, Typeface.BOLD);
        header.addView(verdict, new LinearLayout.LayoutParams(0, -2, 1));
        TextView info = icon("ⓘ", "Details: the figures, your daily needs and the sources");
        info.setOnClickListener(v -> {
            boolean open = details.getVisibility() != View.VISIBLE;
            details.setVisibility(open ? View.VISIBLE : View.GONE);
            summary.setVisibility(open ? View.GONE : View.VISIBLE);
            info.setText(open ? "‹" : "ⓘ");
            info.setContentDescription(open ? "Back to the summary" : "Details: the figures, your daily needs and the sources");
        });
        header.addView(info);
        TextView close = icon("✕", "Close the BetterChoice card");
        close.setOnClickListener(v -> hide());
        header.addView(close);
        card.addView(header);
        card.addView(summary);
        card.addView(details);

        // Can you eat it: the shopper's allergies, diet and state, before anything else.
        String labelText = result.labelText == null ? "" : result.labelText;
        LabelEvidence label = LabelEvidence.of(result.labelText);
        SafetyGate.Result safety = SafetyGate.check(label.ingredients(), label.allergens(), SafetyProfile.fromProfile(profile));
        List<String> stop = new ArrayList<>(person.vetoes(IngredientFacts.section(labelText), labelText, labelText));
        if (safety.blocked()) for (String message : safety.messages) if (!contains(stop, message)) stop.add(message);
        if (!stop.isEmpty()) {
            TextView banner = UiKit.text(context, "⛔  Not for you: " + String.join(" ", stop), 14, UiKit.bad(context));
            banner.setTypeface(null, Typeface.BOLD);
            banner.setPadding(0, UiKit.dp(context, 8), 0, UiKit.dp(context, 2));
            summary.addView(banner);
        }
        if (result.nutrition != null && !result.nutrition.basisStated) {
            line(summary, "⚠  Basis not on the label: read per " + (result.drinkKind != null ? "100 ml" : "100 g"),
                UiKit.ink(context), false, 13);
        }

        CardFacts facts = CardFacts.of(result, person);
        for (CardFacts.Line l : facts.bad) line(summary, "•  " + l.text, UiKit.bad(context), l.personal, 14);
        for (CardFacts.Line l : facts.good) line(summary, "✓  " + l.text, UiKit.good(context), l.personal, 14);
        for (String l : person.productLines(result.page == null ? null : result.page.name, result.nutrition, labelText,
                result.drinkKind != null)) {
            line(summary, l, UiKit.ink(context), true, 13);
        }
        if (result.nutrition != null && !(facts.bad.isEmpty() && facts.good.isEmpty())) {
            TextView per = UiKit.text(context, "Amounts per " + result.nutrition.unit(), 11, UiKit.muted(context));
            per.setPadding(0, UiKit.dp(context, 2), 0, 0);
            summary.addView(per);
        }
        ingredients(summary, result, facts, safety, profile);
        if (result.checkResult != null) checked(summary, result.check, result.checkResult, retailerPackage, false);
        SwapEngine.Outcome outcome = swaps(summary, result, retailerPackage, actions, profile);
        detailsPanel(details, result, facts, person, profile, outcome, actions);
    }

    /**
     * Three better choices from the same category, green first, each a tile: its colour, its name and pack, and the one
     * thing that is better about it. A tap checks price and stock on the shop's own screen; why it suits this shopper
     * is one more tap away.
     */
    private SwapEngine.Outcome swaps(LinearLayout card, GradeResult result, String retailerPackage, Actions actions, Profile profile) {
        if (result.page == null || result.nutrition == null || result.band == null) return null;
        SwapEngine.Outcome outcome = SwapEngine.recommend(SwapCatalogue.get(context), result.page.name, result.page.productType(),
            result.nutrition, result.band, result.letter, result.drinkKind != null, SafetyProfile.fromProfile(profile),
            Occasion.fromClock(LocalDateTime.now()), BuildConfig.DEBUG, profile);
        if (outcome.group == null) return outcome;     // no curated category: what to look for sits behind the ⓘ
        if (!GoalComparisons.allowsGroup(profile, outcome.group.id)) {
            String option = GoalComparisons.buyCode(outcome.group.id);
            muted(card, "Choices for " + outcome.group.name.toLowerCase(Locale.ROOT) + " are off: tick \""
                + Profile.buyLabel(option) + "\" in your form to see them.");
            return outcome;
        }
        // Always the same category as this product; "Green" only when every choice shown is green.
        TextView heading = UiKit.text(context, (outcome.allGreen() ? "Green" : "Better") + " choices in "
            + outcome.group.name.toLowerCase(Locale.ROOT), 14, UiKit.ink(context));
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, UiKit.dp(context, 14), 0, UiKit.dp(context, 2));
        card.addView(heading);
        if (outcome.swaps.isEmpty()) {
            muted(card, outcome.emptyReason);
            return outcome;
        }
        boolean canCheck = RetailerSearch.supports(retailerPackage);
        for (SwapEngine.Swap swap : outcome.swaps) {
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            int pad = UiKit.dp(context, 8);
            tile.setPadding(pad, pad, pad, pad);
            tile.setBackground(UiKit.shape(context, UiKit.surface(context), UiKit.line(context), 12));
            tile.addView(chip(swap.band, 13), new LinearLayout.LayoutParams(UiKit.dp(context, 26), UiKit.dp(context, 26)));
            LinearLayout text = column();
            text.setPadding(pad, 0, pad, 0);
            String pack = swap.product.packText();
            TextView name = UiKit.text(context, swap.product.displayName() + (pack.isEmpty() ? "" : " · " + pack), 13, UiKit.ink(context));
            name.setTypeface(null, Typeface.BOLD);
            name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(name);
            TextView why = UiKit.text(context, swap.why, 12, UiKit.good(context));
            why.setMaxLines(2); why.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(why);
            tile.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            if (canCheck) {
                TextView go = UiKit.text(context, "›", 20, UiKit.muted(context));
                tile.addView(go);
                tile.setClickable(true);
                tile.setContentDescription(swap.product.displayName() + ". " + swap.why + ". Tap to check price and stock on "
                    + shopName(retailerPackage) + ".");
                tile.setOnClickListener(v -> { hide(); actions.checkSwap(result.page, result, swap); });
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = UiKit.dp(context, 6);
            card.addView(tile, params);
            swapDetails(card, result.nutrition, swap);
        }
        if (outcome.shortfall != null) muted(card, outcome.shortfall);
        return outcome;
    }

    /**
     * The recommended product itself, one tap away: its colour in words, its label figures beside this product's, every
     * check it passed for this shopper, its ingredients and allergen statement, and where the data comes from.
     */
    private void swapDetails(LinearLayout card, Nutrition current, SwapEngine.Swap swap) {
        LinearLayout details = column();
        details.setPadding(UiKit.dp(context, 8), 0, 0, UiKit.dp(context, 6));
        details.setVisibility(View.GONE);
        TextView toggle = UiKit.text(context, "Why it suits you  ▾", 11, UiKit.muted(context));
        toggle.setPadding(UiKit.dp(context, 8), UiKit.dp(context, 2), 0, UiKit.dp(context, 2));
        toggle.setOnClickListener(v -> {
            boolean open = details.getVisibility() != View.VISIBLE;
            details.setVisibility(open ? View.VISIBLE : View.GONE);
            toggle.setText(open ? "Hide  ▴" : "Why it suits you  ▾");
        });
        SwapCatalogue.Product p = swap.product;
        Nutrition n = p.per100;
        if (swap.standing != null) details.addView(UiKit.text(context, swap.standing, 12, UiKit.ink(context)));
        if (swap.goalNote != null) details.addView(UiKit.text(context, swap.goalNote, 12, UiKit.muted(context)));
        details.addView(UiKit.text(context, "Per " + n.unit() + ": this product → " + (p.brand == null ? "the swap" : p.brand),
            12, UiKit.ink(context)));
        String[][] rows = {
            {"Energy", fmt(current.energyKcal, "kcal"), fmt(n.energyKcal, "kcal")},
            {"Sugars", fmt(current.sugarsG, "g"), fmt(n.sugarsG, "g")},
            {"Added sugars", fmt(current.addedSugarsG, "g"), fmt(n.addedSugarsG, "g")},
            {"Saturated fat", fmt(current.saturatedFatG, "g"), fmt(n.saturatedFatG, "g")},
            {"Salt", salt(current.sodiumMg), salt(n.sodiumMg)},
            {"Protein", fmt(current.proteinG, "g"), fmt(n.proteinG, "g")},
            {"Fibre", fmt(current.fibreG, "g"), fmt(n.fibreG, "g")}};
        for (String[] r : rows) {
            if (r[1].equals(NOT_SHOWN) && r[2].equals(NOT_SHOWN)) continue;
            details.addView(UiKit.text(context, "  " + r[0] + ": " + r[1] + " → " + r[2], 12, UiKit.ink(context)));
        }
        for (String check : swap.checks) details.addView(UiKit.text(context, "✓ " + check, 12, UiKit.good(context)));
        for (String good : swap.ingredients.good) details.addView(UiKit.text(context, "✓ " + good, 12, UiKit.good(context)));
        for (String concern : swap.ingredients.concerns) details.addView(UiKit.text(context, "• " + concern, 12, UiKit.bad(context)));
        if (p.ingredients != null && !p.ingredients.isBlank()) {
            String list = p.ingredients.length() > 260 ? p.ingredients.substring(0, 257) + "…" : p.ingredients;
            details.addView(UiKit.text(context, "Ingredients: " + list, 11, UiKit.ink(context)));
        } else {
            details.addView(UiKit.text(context, "Ingredients: not on record, so it is never offered to someone with an allergy or a strict diet.",
                11, UiKit.ink(context)));
        }
        if (!p.contains.isEmpty()) details.addView(UiKit.text(context, "Contains: " + String.join(", ", p.contains), 11, UiKit.ink(context)));
        if (!p.mayContain.isEmpty()) details.addView(UiKit.text(context, "May contain: " + String.join(", ", p.mayContain), 11, UiKit.ink(context)));
        details.addView(UiKit.text(context, "Source: Open Food Facts (ODbL)" + (p.labelDate == null ? "" : ", label from " + p.labelDate)
            + (p.gtin == null ? "" : ", barcode " + p.gtin)
            + (swap.draft() ? ". Draft until our nutritionist checks the pack." : ". Checked by our nutritionist."), 11, UiKit.muted(context)));
        card.addView(toggle);
        card.addView(details);
    }

    /**
     * What the declared ingredient list says, apart from the nutrients and folded under one tap: its good points and
     * concerns, the additives it names, the allergen statement, and the list itself.
     */
    private void ingredients(LinearLayout card, GradeResult result, CardFacts facts, SafetyGate.Result safety, Profile profile) {
        LinearLayout menu = column();
        for (String good : facts.ingredientGood) line(menu, "✓  " + good, UiKit.good(context), false, 13);
        for (String concern : facts.ingredientConcerns) line(menu, "•  " + concern, UiKit.bad(context), false, 13);
        IngredientFacts declared = result.ingredients;
        if (declared != null) for (String information : declared.information) muted(menu, information);
        additiveLimits(menu, declared, profile);
        // Allergen lines that do not stop this shopper: what the label declares, said once.
        if (!safety.blocked() && safety.verdict != SafetyGate.Verdict.NOTHING_TO_FLAG) {
            for (String message : safety.messages) line(menu, message, UiKit.ink(context), false, 13);
        }
        String list = IngredientFacts.section(result.labelText);
        if (list != null) muted(menu, "Ingredients: " + (list.length() > 400 ? list.substring(0, 397) + "…" : list));
        if (menu.getChildCount() == 0) return;
        muted(menu, "Additive names alone do not establish exposure or safety.");
        boolean concern = !facts.ingredientConcerns.isEmpty();
        TextView toggle = UiKit.text(context, "Ingredients & allergens  ▸", 13, concern ? UiKit.bad(context) : UiKit.ink(context));
        toggle.setTypeface(null, Typeface.BOLD);
        toggle.setPadding(0, UiKit.dp(context, 10), 0, UiKit.dp(context, 2));
        menu.setVisibility(View.GONE);
        toggle.setOnClickListener(v -> {
            boolean open = menu.getVisibility() != View.VISIBLE;
            menu.setVisibility(open ? View.VISIBLE : View.GONE);
            toggle.setText("Ingredients & allergens  " + (open ? "▾" : "▸"));
        });
        card.addView(toggle);
        card.addView(menu);
    }

    /**
     * Behind the ⓘ: what is the same on every product or needed only on a closer look. Lines beyond the face's three,
     * the rating's caveats, the figures per 100 g, serving and pack, this shopper's daily references and how they were
     * worked out, what to look for in a category with no curated choices, and where every figure comes from.
     */
    private void detailsPanel(LinearLayout panel, GradeResult result, CardFacts facts, RecommendationContext person,
                              Profile profile, SwapEngine.Outcome outcome, Actions actions) {
        title(panel, "Details");
        for (CardFacts.Line l : facts.moreBad) line(panel, "•  " + l.text, UiKit.bad(context), l.personal, 13);
        for (CardFacts.Line l : facts.moreGood) line(panel, "✓  " + l.text, UiKit.good(context), l.personal, 13);
        for (String note : result.notes) muted(panel, note);
        for (String caveat : result.caveats) muted(panel, caveat);
        String advice = LabelGuide.categoryAdvice(result.page);
        if (advice != null) muted(panel, advice);
        ninClass(panel, result);
        if (outcome != null && outcome.group == null && result.page != null) {
            if (outcome.emptyReason != null) muted(panel, "Choices: " + outcome.emptyReason);
            FoodCategory.Placement placement = FoodCategory.place(result.page.name, result.drinkKind != null, null);
            for (String line : FoodCategory.guidance(placement, result.drinkKind != null)) muted(panel, line);
        }
        if (outcome != null && !outcome.leftOut.isEmpty() && outcome.shortfall == null) muted(panel, SwapEngine.leftOutLine(outcome.leftOut));
        if (outcome != null && outcome.hasDrafts()) {
            muted(panel, "Draft picks use Open Food Facts data (ODbL) until our nutritionist checks each pack.");
        }
        packTable(panel, result.nutrition);
        dailyImpact(panel, result.nutrition, profile);
        DailyNeeds needs = DailyNeeds.of(person);
        LinearLayout yours = folded(panel, "Your goal, conditions and daily needs");
        for (String line : GoalComparisons.explain(profile, result.nutrition)) muted(yours, line);
        muted(yours, needs.limits());
        workings(yours, needs);
        // Say which branch produced this, because the two use different methods and thresholds,
        // and state the basis the label actually used rather than the one the method prefers.
        String basis = result.nutrition == null ? ""
            : result.nutrition.basisStated ? " per " + result.nutrition.unit()
            : " (label basis not stated; read as per " + (result.drinkKind != null ? "100 ml" : "100 g") + ")";
        muted(panel, result.drinkKind != null
            ? "From " + result.source.description + " · " + result.drinkKind.label
                + ", scored as a drink" + basis + ". Water is the reference."
            : "From " + result.source.description + " · scored as a food" + basis + " (Nutri-Score 2023 in 3 bands).");
        muted(panel, "The colour is a provisional nutrient band: it does not establish allergy safety or personal suitability. "
            + "A guide, not medical advice.");
        // ODbL requires the source to be credited wherever its data is shown.
        if (result.source == GradeResult.Source.OPEN_FOOD_FACTS) muted(panel, "Product data © Open Food Facts contributors, ODbL.");
        barcode(panel, result.gtin);
        if (result.source != GradeResult.Source.USER) button(panel, "Looks wrong? Fix it", false, () -> { hide(); actions.addDetails(result.page); });
    }

    /** What the shop's own screen showed for a tapped swap: stock, price, and the 2x budget. */
    private void checked(LinearLayout card, SwapCheck.Pending check, SwapCheck.Result found, String retailerPackage,
                         boolean full) {
        if (check == null || found == null) return;
        SwapCatalogue.Product product = check.swap.product;
        if (full) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, UiKit.dp(context, 6), 0, 0);
            row.addView(chip(check.swap.band, 13), new LinearLayout.LayoutParams(UiKit.dp(context, 26), UiKit.dp(context, 26)));
            TextView name = UiKit.text(context, "  " + product.displayName(), 14, UiKit.ink(context));
            name.setTypeface(null, Typeface.BOLD);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(row);
            card.addView(UiKit.text(context, check.swap.why, 12, UiKit.good(context)));
        } else {
            TextView heading = UiKit.text(context, "Your swap, checked", 14, UiKit.ink(context));
            heading.setTypeface(null, Typeface.BOLD);
            heading.setPadding(0, UiKit.dp(context, 10), 0, 0);
            card.addView(heading);
        }
        int stateColour = found.state == SwapCheck.State.IN_STOCK ? UiKit.good(context)
            : found.state == SwapCheck.State.OUT_OF_STOCK ? UiKit.warning(context) : UiKit.ink(context);
        TextView state = UiKit.text(context, (found.state == SwapCheck.State.IN_STOCK ? "✓  " : "") + found.state.label, 13, stateColour);
        state.setPadding(0, UiKit.dp(context, 6), 0, 0);
        card.addView(state);
        if (found.state == SwapCheck.State.NOT_FOUND) {
            card.addView(UiKit.text(context, "Scroll the results, or search " + shopName(retailerPackage) + " for \"" + SwapCheck.query(product) + "\".",
                12, UiKit.ink(context)));
            muted(card, "Your delivery address may not stock it right now.");
            return;
        }
        if (found.price != null) {
            String unit = product.beverage ? "ml" : "g";
            StringBuilder line = new StringBuilder("₹" + Nutrition.number(found.price));
            if (found.pack != null) line.append(" for ").append(Nutrition.number(found.pack)).append(' ').append(unit);
            if (found.per100 != null) line.append("  (₹").append(Nutrition.number(found.per100)).append(" per 100 ").append(unit).append(')');
            card.addView(UiKit.text(context, line.toString(), 13, UiKit.ink(context)));
            if (found.packs > 1 && found.combinedCost != null) {
                card.addView(UiKit.text(context, found.packs + " packs to replace yours: ₹" + Nutrition.number(found.combinedCost), 12, UiKit.ink(context)));
            }
            if (found.withinBudget != null && check.originalPrice != null) {
                String limit = "₹" + Nutrition.number(found.ceiling) + ", twice your ₹" + Nutrition.number(check.originalPrice);
                card.addView(UiKit.text(context, found.withinBudget ? "Within your limit (" + limit + ")" : "Over your limit (" + limit + ")",
                    12, found.withinBudget ? UiKit.good(context) : UiKit.warning(context)));
            }
            if (found.pack != null && !found.samePack && product.pack != null) {
                muted(card, "A different pack size from the one we rated; the rating per 100 " + unit + " is the same.");
            }
        } else {
            muted(card, "No price was shown next to it.");
        }
        muted(card, "Read from " + shopName(retailerPackage) + "'s screen for your delivery address, just now. Not stored."
            + " Delivery and handling charges are separate.");
    }

    static String shopName(String retailerPackage) {
        if (retailerPackage == null) return "the shop";
        switch (retailerPackage) {
            case "com.grofers.customerapp": return "Blinkit";
            case "com.zeptoconsumerapp": return "Zepto";
            case "in.swiggy.android": case "in.swiggy.android.instamart": return "Instamart";
            case "com.flipkart.android": return "Flipkart";
            case "com.application.zomato": return "Zomato";
            default: return "the shop";
        }
    }

    /** Per 100 g for comparison, per label serving, and the whole pack: what the label hides. */
    private void packTable(LinearLayout card, Nutrition nutrition) {
        if (nutrition == null) return;
        java.util.List<PackMath.Row> rows = PackMath.table(nutrition);
        if (rows.isEmpty()) return;
        String summary = PackMath.packSummary(nutrition);
        if (!summary.isEmpty()) muted(card, summary);
        boolean serving = PackMath.servingSize(nutrition) != null;
        boolean pack = nutrition.packAmount() != null;
        if (!serving && !pack) return;
        LinearLayout header = row(card);
        cell(header, "Per " + nutrition.unit(), true, 1.3f);
        if (serving) cell(header, "Per serving", true, 1f);
        if (pack) cell(header, "Whole pack", true, 1f);
        for (PackMath.Row value : rows) {
            LinearLayout line = row(card);
            cell(line, value.nutrient + "  " + value.per100Text(), false, 1.3f);
            if (serving) cell(line, value.perServingText(), false, 1f);
            if (pack) cell(line, value.perPackText(), false, 1f);
        }
        if (pack && PackMath.singleSittingPack(nutrition)) {
            muted(card, serving ? "Compare the serving and whole-pack columns with the amount you plan to eat."
                : "Compare the whole-pack column with the amount you plan to eat.");
        }
    }

    private LinearLayout row(LinearLayout card) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = UiKit.dp(context, 4);
        card.addView(line, params);
        return line;
    }

    private void cell(LinearLayout line, String text, boolean heading, float weight) {
        TextView view = UiKit.text(context, text, heading ? 11 : 12, heading ? UiKit.muted(context) : UiKit.ink(context));
        if (heading) view.setTypeface(null, Typeface.BOLD);
        line.addView(view, new LinearLayout.LayoutParams(0, -2, weight));
    }

    /**
     * Allergens and diet, in their own block with their own heading, for a product not yet rated.
     *
     * <p>Kept visually separate from the A/B/C band on purpose. The band answers "is this a good
     * food"; this answers "can you eat it", and folding the second into the first would let a
     * decent score read as reassurance about an allergen.
     */
    private void safety(LinearLayout card, GradeResult result, Profile profile) {
        if (result.page == null) return;
        // Only the declared ingredient list and allergen statement are read: the rest of a shop
        // page names other products ("Yoga Bar … Almond") and marketing words, and scanning it gave
        // Quaker oats "Contains: Tree nuts". It also hid a missing list from someone with allergies,
        // because a whole page is never too short to count as "information".
        LabelEvidence label = LabelEvidence.of(result.labelText);
        SafetyGate.Result safety = SafetyGate.check(label.ingredients(), label.allergens(), SafetyProfile.fromProfile(profile));
        if (safety.verdict == SafetyGate.Verdict.NOTHING_TO_FLAG || safety.messages.isEmpty()) return;

        boolean stop = safety.blocked();
        TextView heading = UiKit.text(context, stop ? "⛔  Not for you" : "Allergens", 14,
            stop ? UiKit.warning(context) : UiKit.ink(context));
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, UiKit.dp(context, 12), 0, 0);
        card.addView(heading);
        for (String message : safety.messages) {
            card.addView(UiKit.text(context, message, 13, stop ? UiKit.ink(context) : UiKit.muted(context)));
        }
    }

    /**
     * What this costs the shopper's own day. The whole pack is shown when the pack size is known,
     * because an 80 g snack bag is eaten in one go far more often than in four measured servings.
     */
    private void dailyImpact(LinearLayout card, Nutrition nutrition, Profile profile) {
        if (nutrition == null) return;
        DailyNeeds needs = DailyNeeds.forProfile(profile);
        title(card, "Reference contribution");
        LinearLayout content = column();
        LinearLayout choices = row(card);
        Button hundred = UiKit.button(context, "100 " + nutrition.measure(), false);
        choices.addView(hundred, new LinearLayout.LayoutParams(0, -2, 1));
        hundred.setOnClickListener(v -> referenceRows(content, DailyImpact.of(nutrition, needs, 100, false)));
        DailyImpact serving = DailyImpact.forServing(nutrition, needs);
        if (serving != null) {
            Button one = UiKit.button(context, "Serving", false);
            choices.addView(one, new LinearLayout.LayoutParams(0, -2, 1));
            one.setOnClickListener(v -> referenceRows(content, serving));
        }
        DailyImpact pack = DailyImpact.forPack(nutrition, needs);
        if (pack != null) {
            Button whole = UiKit.button(context, "Pack", false);
            choices.addView(whole, new LinearLayout.LayoutParams(0, -2, 1));
            whole.setOnClickListener(v -> referenceRows(content, pack));
        }
        card.addView(content);
        referenceRows(content, serving == null ? DailyImpact.of(nutrition, needs, 100, false) : serving);
        if (!nutrition.basisStated) muted(card, "These amounts assume the label is per " + nutrition.unit()
            + "; the label did not say.");
        muted(card, "Total sugar is shown in grams only. Added and free sugars are different; an undeclared quantity remains unknown. Unlisted vitamins/minerals are not zero.");
    }

    private void evidenceWithoutGrade(LinearLayout card, GradeResult result, Profile profile) {
        String ingredients = IngredientFacts.section(result.labelText);
        if (ingredients != null) {
            title(card, "Ingredient evidence");
            muted(card, ingredients.length() > 900 ? ingredients.substring(0, 900) + "…" : ingredients);
            IngredientFacts facts = IngredientFacts.of(ingredients, false);
            for (String line : facts.information) muted(card, line);
            additiveLimits(card, facts, profile);
            muted(card, "Additive quantities and total exposure are unknown. No safe-dose claim is made.");
        }
        safety(card, result, profile);
    }

    /** Declared additives with an ADI or a regulatory note, scaled to the shopper's weight when known. */
    private void additiveLimits(LinearLayout card, IngredientFacts facts, Profile profile) {
        if (facts == null) return;
        Double kg = DailyNeeds.statedWeight(profile);
        for (String line : AdditiveLimits.lines(facts.additives, kg)) muted(card, line);
    }

    private void referenceRows(LinearLayout content, DailyImpact impact) {
        content.removeAllViews();
        if (impact == null) return;
        muted(content, impact.headline());
        for (DailyImpact.Line line : impact.lines) {
            muted(content, line.nutrient + "  " + line.amount + "  ·  " + line.percent + "% of " + line.reference);
        }
        muted(content, impact.confidence());
    }

    /** The number under the bars. Shown so the user can see we matched *this* pack, not a lookalike. */
    private void barcode(LinearLayout card, String gtin) {
        if (gtin == null) return;
        muted(card, "▌▌▌  " + Gtin.display(gtin));
    }

    private void title(LinearLayout card, String text) {
        TextView t = UiKit.text(context, text, 16, UiKit.ink(context)); t.setTypeface(null, Typeface.BOLD); card.addView(t);
    }

    private void muted(LinearLayout card, String text) {
        TextView t = UiKit.text(context, text, 12, UiKit.muted(context)); t.setPadding(0, UiKit.dp(context, 6), 0, 0); card.addView(t);
    }

    /** One line of the face: the shopper's own reasons in bold, everything at a readable size. */
    private void line(LinearLayout card, String text, int colour, boolean bold, int size) {
        TextView t = UiKit.text(context, text, size, colour);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, UiKit.dp(context, 4), 0, 0);
        card.addView(t);
    }

    /** The colour itself, with its letter, as on the bubble. */
    private TextView chip(NutriGrade.Band band, int size) {
        TextView chip = UiKit.text(context, band.name(), size, band == NutriGrade.Band.B ? UiKit.ON_BAND_B : Color.WHITE);
        chip.setGravity(Gravity.CENTER);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setBackground(UiKit.shape(context, BubbleStyle.argb(0xFF, BubbleStyle.bandColor(band)), 0, 20));
        return chip;
    }

    /** A small tappable symbol in the header, with a spoken label for screen readers. */
    private TextView icon(String symbol, String description) {
        TextView t = UiKit.text(context, symbol, 18, UiKit.ink(context));
        t.setGravity(Gravity.CENTER);
        int size = UiKit.dp(context, 40);
        t.setMinWidth(size);
        t.setMinHeight(size);
        t.setContentDescription(description);
        return t;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    /** A section folded under one tap, for what is the same on every product. Returns the container to fill. */
    private LinearLayout folded(LinearLayout card, String label) {
        TextView toggle = UiKit.text(context, label + "  ▾", 13, UiKit.ink(context));
        toggle.setTypeface(null, Typeface.BOLD);
        toggle.setPadding(0, UiKit.dp(context, 10), 0, UiKit.dp(context, 2));
        LinearLayout content = column();
        content.setVisibility(View.GONE);
        toggle.setOnClickListener(v -> {
            boolean open = content.getVisibility() != View.VISIBLE;
            content.setVisibility(open ? View.VISIBLE : View.GONE);
            toggle.setText(label + (open ? "  ▴" : "  ▾"));
        });
        card.addView(toggle);
        card.addView(content);
        return content;
    }

    /** "How we calculated this": every personal number with its formula and inputs, folded until asked for. */
    private void workings(LinearLayout card, DailyNeeds needs) {
        LinearLayout box = column();
        box.setVisibility(View.GONE);
        for (String line : needs.workings) muted(box, line);
        muted(box, "Sources: FAO/WHO/UNU 2004, ICMR-NIN 2020 and 2024, WHO 2012-2023, FSSAI labelling regulations; "
            + "evidence " + Evidence.VERSION + ". Each number is quoted from its source; the judgement calls are listed for a dietitian's review.");
        Button toggle = UiKit.button(context, "How we calculated this", false);
        toggle.setTextSize(13);
        toggle.setOnClickListener(v -> box.setVisibility(box.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        card.addView(toggle);
        card.addView(box);
    }

    /** NIN 2024 Table 15.1 reading of this label: the Indian per-100 thresholds, computed. */
    private void ninClass(LinearLayout card, GradeResult result) {
        if (result.nutrition == null || result.page == null) return;
        FoodCategory.Placement placement = FoodCategory.place(result.page.name, result.drinkKind != null, null);
        NutritionCalculator.NinClass nin = NutritionCalculator.ninClass(result.nutrition, result.drinkKind != null, placement.nutsOrSeeds);
        String per = result.drinkKind != null ? "100 ml" : "100 g";
        if (nin.energyClass > 0 && !placement.nutsOrSeeds) muted(card, "NIN 2024 energy class " + nin.energyClass
            + " of 3 (per " + per + ")" + (nin.hfss() ? ": high in fat, sugar or salt by NIN's thresholds." : "."));
        for (String reason : nin.reasons) muted(card, "•  " + reason + ".");
    }

    private void button(LinearLayout card, String label, boolean primary, Runnable click) {
        Button b = UiKit.button(context, label, primary);
        b.setTextSize(14); b.setMinHeight(UiKit.dp(context, 44));
        b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = UiKit.dp(context, 8);
        card.addView(b, params);
    }

    private static final String NOT_SHOWN = "not on label";

    private static String fmt(Double v, String unit) {
        if (v == null || !Double.isFinite(v)) return NOT_SHOWN;
        return ("kcal".equals(unit) ? String.valueOf(Math.round(v)) : Nutrition.number(Math.round(v * 10) / 10.0)) + " " + unit;
    }

    /** Salt from sodium by WHO's pairing of the two limits (NutritionCalculator), in grams. */
    private static String salt(Double sodiumMg) {
        if (sodiumMg == null || !Double.isFinite(sodiumMg)) return NOT_SHOWN;
        double grams = NutritionCalculator.saltFromSodium(java.math.BigDecimal.valueOf(sodiumMg)).d() / 1000;
        return Nutrition.number(Math.round(grams * 100) / 100.0) + " g";
    }

    private static boolean contains(List<String> lines, String message) {
        for (String line : lines) if (line.equalsIgnoreCase(message)) return true;
        return false;
    }
}
