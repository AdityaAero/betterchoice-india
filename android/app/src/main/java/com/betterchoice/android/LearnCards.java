package com.betterchoice.android;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Short, swipeable cards that teach label literacy: pack vs serving size, why per 100 g is the only
 * fair comparison, and what the ingredient order says. Facts and published rules only, never
 * medical or dietary advice, and never a claim about a particular brand.
 */
final class LearnCards {
    static final class Card {
        final String tag, title, body, takeaway;
        final int accent;
        Card(String tag, String title, String body, String takeaway, int accent) {
            this.tag = tag; this.title = title; this.body = body; this.takeaway = takeaway; this.accent = accent;
        }
    }

    static final Card[] CARDS = {
        new Card("SERVING SIZE", "A 150 g packet is rarely one serving",
            "A label may say 140 kcal, then add \"serving size 25 g\" in smaller type. The same packet holds 6 servings, " +
            "so finishing it is about 840 kcal.",
            "Check the serving size before you trust a calorie number.", BubbleStyle.GREEN),
        new Card("COMPARING", "Per 100 g is the only fair comparison",
            "One brand may declare per 20 g, another per 50 g. Those numbers cannot be compared. Indian labels (FSSAI) " +
            "must also give values per 100 g or 100 ml, which is the column BetterChoice always uses.",
            "Compare per 100 g; read per serving for what you'll actually eat.", BubbleStyle.NAVY),
        new Card("INGREDIENTS", "The order tells you the recipe",
            "Ingredients are listed in descending order by weight. If sugar or refined flour is second or third, " +
            "the product is largely made of it, whatever the front of the pack says.",
            "Read the first three ingredients before anything else.", BubbleStyle.GREEN),
        new Card("HIDDEN SUGAR", "Sugar has many names",
            "Liquid glucose, invert syrup, dextrose and honey contribute added sugars. Maltodextrin is a carbohydrate, not automatically a declared sugar. " +
            "Split across several names, each sits lower in the list even when the total is high.",
            "Add up the sugar-like names, not just the word \"sugar\".", BubbleStyle.RED),
        new Card("USEFUL NUTRIENTS", "Fibre and protein are worth looking for",
            "Whole grains, pulses, nuts, curd, eggs, fruit and vegetables bring fibre and protein, which help a food " +
            "satisfy you for longer. Scoring systems count them in a product's favour.",
            "More fibre and protein, less added sugar, saturated fat and sodium.", BubbleStyle.GREEN),
        new Card("SALT", "Sodium is not the same as salt",
            "Sodium in milligrams is about salt in grams divided by 2.5, times 1000. A snack at 700 mg sodium per 100 g " +
            "carries roughly 1.75 g of salt in that 100 g.",
            "Compare sodium per 100 g between similar products.", BubbleStyle.NAVY),
        new Card("ADDITIVES", "INS numbers are just identifiers",
            "INS 322 is lecithin, an emulsifier that keeps oil and water mixed. A number is not automatically harmful; " +
            "it names the additive and what it does.",
            "Look at what an additive does, not at how technical it sounds.", BubbleStyle.NAVY),
        new Card("VEG MARK", "Green circle, brown triangle",
            "Indian packs carry a green circle in a green square for vegetarian and a brown triangle in a brown square " +
            "for non-vegetarian. The mark is quicker to find than the ingredient list.",
            "Check the mark first, then the ingredients for what you avoid.", BubbleStyle.GREEN),
        new Card("BARCODE", "The barcode is only a name tag",
            "A barcode holds a GTIN, the product's number. It carries no calories, ingredients or price; those come from " +
            "a database. A number starting 890 was issued in India, which does not prove where the food was made.",
            "The barcode identifies the pack; the label still holds the facts.", BubbleStyle.NAVY)
    };

    /** A horizontally swipeable row of cards, sized for one-handed reading. */
    static java.util.List<Card> selected(Profile profile) {
        java.util.List<Card> result = new java.util.ArrayList<>();
        String goal = profile == null ? "" : profile.answers[Profile.GOAL];
        result.add("Muscle gain".equals(goal) ? CARDS[4] : CARDS[0]);
        result.add(CARDS[1]);
        java.util.List<String> buys = profile == null ? java.util.Collections.emptyList() : profile.buyCodes();
        result.add(buys.contains("drinks") || buys.contains("chocolate") || buys.contains("biscuits") ? CARDS[3]
            : buys.contains("snacks") || buys.contains("noodles") ? CARDS[5] : CARDS[2]);
        return result;
    }
    static View view(Context context) { return view(context, null); }
    static View view(Context context, Profile profile) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int gap = UiKit.dp(context, 12);
        row.setPadding(0, gap / 2, 0, gap / 2);
        int width = Math.min(UiKit.dp(context, 300), (int) (context.getResources().getDisplayMetrics().widthPixels * 0.82));
        for (Card card : selected(profile)) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, -2);
            params.rightMargin = gap;
            row.addView(card(context, card), params);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.addView(row);
        return scroller;
    }

    private static View card(Context context, Card card) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(context, 16);
        view.setPadding(pad, pad, pad, pad);
        view.setBackground(UiKit.shape(context, UiKit.surface(context), UiKit.line(context), 18));
        view.setContentDescription(card.title + ". " + card.body + " " + card.takeaway);

        TextView tag = UiKit.text(context, card.tag, 11, BubbleStyle.argb(0xFF, card.accent));
        tag.setTypeface(null, Typeface.BOLD); tag.setLetterSpacing(.12f);
        view.addView(tag);
        TextView title = UiKit.text(context, card.title, 19, UiKit.ink(context));
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setPadding(0, UiKit.dp(context, 8), 0, UiKit.dp(context, 8));
        view.addView(title);
        view.addView(UiKit.text(context, card.body, 14, UiKit.muted(context)));
        TextView takeaway = UiKit.text(context, card.takeaway, 13, BubbleStyle.argb(0xFF, card.accent));
        takeaway.setTypeface(null, Typeface.BOLD);
        takeaway.setPadding(0, UiKit.dp(context, 10), 0, 0);
        takeaway.setGravity(Gravity.START);
        view.addView(takeaway);
        return view;
    }

    private LearnCards() {}
}
