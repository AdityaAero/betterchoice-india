package com.betterchoice.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * "Add details": the user types values from the product's nutrition label (per 100 g or 100 ml).
 * The rating updates immediately on this phone; the values are sent for review, not published directly.
 * Not exported: only BetterChoice's own bubble can open it.
 */
public final class ContributeActivity extends Activity {
    private static final String NAME = "name", PRICE = "price", RETAILER = "retailer", GTIN = "gtin";
    private final SafeTouches.GestureGuard guard = new SafeTouches.GestureGuard();
    private ProductPage page;
    /** Barcode read from the pack, if the bubble saw one. Submitted with the label, never shown as editable. */
    private String gtin;
    private EditText energy, sugars, satFat, sodium, fibre, protein, ingredients, netQuantity, servingSize, servingsPerPack, basis;
    private CheckBox beverage;
    private TextView preview, message;
    private Button submit;

    static Intent intent(Context context, ProductPage page) { return intent(context, page, null); }

    static Intent intent(Context context, ProductPage page, String gtin) {
        Intent intent = new Intent(context, ContributeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(NAME, page.name).putExtra(RETAILER, page.retailer);
        if (page.price != null) intent.putExtra(PRICE, page.price.doubleValue());
        String number = Gtin.normalize(gtin);
        if (number != null) intent.putExtra(GTIN, number);
        return intent;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setHideOverlayWindows(true);
        String name = getIntent().getStringExtra(NAME);
        String retailer = getIntent().getStringExtra(RETAILER);
        gtin = Gtin.normalize(getIntent().getStringExtra(GTIN));
        if (name == null || name.isBlank() || retailer == null || !ForegroundPolicy.SUPPORTED.contains(retailer)) { finish(); return; }
        page = ProductPage.of(name.length() > 140 ? name.substring(0, 140) : name,
            getIntent().hasExtra(PRICE) ? getIntent().getDoubleExtra(PRICE, 0) : null, retailer);

        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 22); body.setPadding(pad, pad, pad, pad); body.setBackgroundColor(UiKit.paper(this));
        body.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom); return insets;
        });
        TextView tag = UiKit.text(this, "HELP INDIA EAT HEALTHIER", 12, UiKit.green(this)); tag.setTypeface(null, Typeface.BOLD); tag.setLetterSpacing(.12f);
        body.addView(tag);
        TextView title = UiKit.text(this, "Add label details", 28, UiKit.ink(this)); title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        body.addView(title);
        body.addView(UiKit.text(this, page.name + (page.price == null ? "" : "  ·  ₹" + Nutrition.number(page.price)), 14, UiKit.muted(this)));
        UiKit.gap(body, 10);
        body.addView(UiKit.text(this, "Type the numbers exactly as printed. If the pack lists them per serving or per pack, just say which — we do the maths.", 14, UiKit.ink(this)));
        UiKit.gap(body, 8);
        beverage = new CheckBox(this); beverage.setText("This is a drink (values per 100 ml)"); beverage.setTextColor(UiKit.ink(this));
        body.addView(beverage);
        body.addView(UiKit.text(this, "Pack", 15, UiKit.muted(this)));
        netQuantity = field(body, "Net quantity on the pack (g or ml)");
        servingSize = field(body, "Serving size (g or ml)");
        servingsPerPack = field(body, "Servings per pack");
        body.addView(UiKit.text(this, "The numbers on the pack", 15, UiKit.muted(this)));
        basis = field(body, "Values are per how many g / ml? (100, 30, 45…)");
        basis.setText("100");
        energy = field(body, "Energy (kcal) *");
        sugars = field(body, "Total sugars (g)");
        satFat = field(body, "Saturated fat (g)");
        sodium = field(body, "Sodium (mg)");
        fibre = field(body, "Dietary fibre (g)");
        protein = field(body, "Protein (g)");
        ingredients = UiKit.input(this, "Ingredients (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ingredients.setSingleLine(false); ingredients.setMinLines(3); ingredients.setGravity(Gravity.TOP);
        ingredients.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1500)});
        ingredients.setHint("Ingredients, in the order printed on the pack");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = UiKit.dp(this, 10);
        body.addView(ingredients, lp);
        preview = UiKit.text(this, "", 16, UiKit.ink(this)); preview.setTypeface(null, Typeface.BOLD);
        preview.setPadding(0, UiKit.dp(this, 14), 0, UiKit.dp(this, 4));
        body.addView(preview);
        submit = UiKit.button(this, "Rate & share for review", true);
        submit.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.topMargin = UiKit.dp(this, 12);
        body.addView(submit, sp);
        Button cancel = UiKit.button(this, "Cancel", false);
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = UiKit.dp(this, 10);
        body.addView(cancel, cp);
        message = UiKit.text(this, "", 13, UiKit.muted(this)); message.setPadding(0, UiKit.dp(this, 12), 0, 0);
        body.addView(message);
        body.addView(UiKit.text(this, "Shared for review: product name, price, app, these values and your account ID.", 12, UiKit.muted(this)));
        beverage.setOnCheckedChangeListener((b, checked) -> updatePreview());
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(UiKit.paper(this)); scroll.addView(body);
        setContentView(scroll);
        updatePreview();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return guard.reject(event.getActionMasked(), event.getFlags()) || super.dispatchTouchEvent(event);
    }

    private EditText field(LinearLayout body, String hint) {
        EditText input = UiKit.input(this, hint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        input.setContentDescription(hint);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { updatePreview(); }
            public void afterTextChanged(Editable e) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = UiKit.dp(this, 8);
        body.addView(input, lp);
        return input;
    }

    /** The basis the user says the printed numbers use; 100 unless they change it. */
    double basisValue() {
        Double entered = value(basis, 2000);
        return entered == null || entered <= 0 ? 100 : entered;
    }

    /** Values as printed, converted to per 100 g / 100 ml, or null if they cannot be graded yet. */
    Nutrition entered() {
        Nutrition n = new Nutrition();
        n.basisStated = true;   // the form converts whatever basis was typed to per 100
        n.beverage = beverage.isChecked();
        n.energyKcal = value(energy, 950);
        n.sugarsG = value(sugars, 100);
        n.saturatedFatG = value(satFat, 100);
        n.sodiumMg = value(sodium, 40000);
        n.fibreG = value(fibre, 100);
        n.proteinG = value(protein, 100);
        n.netQuantity = value(netQuantity, 20000);
        n.servingSize = value(servingSize, 1000);
        n.servingsPerPack = value(servingsPerPack, 200);
        Nutrition per100 = PackMath.toPer100(n, basisValue());
        return per100.usable() ? per100 : null;
    }

    private static Double value(EditText input, double max) {
        String raw = input.getText().toString().trim().replace(',', '.');
        if (raw.isEmpty()) return null;
        try {
            double v = Double.parseDouble(raw);
            return v < 0 || v > max ? null : v;
        } catch (NumberFormatException invalid) { return null; }
    }

    private void updatePreview() {
        if (preview == null) return;
        Nutrition n = entered();
        if (n == null) {
            preview.setText("Add energy and two of sugars, saturated fat or sodium to see the rating.");
            preview.setTextColor(UiKit.muted(this));
            submit.setEnabled(false);
            return;
        }
        NutriGrade.Result result = NutriGrade.grade(n);
        String pack = "";
        if (basisValue() != 100) {
            pack = "\nPer 100 " + (n.beverage ? "ml" : "g") + ": " + Math.round(n.energyKcal) + " kcal"
                + (n.sugarsG == null ? "" : ", sugars " + Nutrition.number(n.sugarsG) + " g");
        }
        if (n.netQuantity != null) {
            pack += "\nWhole pack: " + Math.round(n.energyKcal * n.netQuantity / 100) + " kcal";
        }
        preview.setText("Rating " + result.band.name() + " · " + BubbleStyle.meaning(result.band) + pack);
        preview.setTextColor(BubbleStyle.argb(0xFF, result.band == NutriGrade.Band.B ? 0x8A6D00 : BubbleStyle.bandColor(result.band)));
        submit.setEnabled(true);
    }

    private void submit() {
        Nutrition n = entered();
        if (n == null) return;
        // The bubble uses these values right away for this item during this session.
        BubbleService.userDetailsAdded(page, n);
        submit.setEnabled(false);
        if (!AuthController.available() || !AccountStore.get(this).sessionUsable()) {
            message.setText("Rated on this phone. Sign in and go online to share these details for review.");
            return;
        }
        message.setText("Sending for review…");
        AuthController.get(this).contribute(page, n, ingredients.getText().toString(), basisValue(), gtin, error -> {
            if (isDestroyed() || isFinishing()) return;
            if (error == null) {
                message.setTextColor(Color.rgb(27, 127, 59));
                message.setText("Thank you! Your details were sent for review. The bubble now shows this rating.");
                submit.postDelayed(this::finish, 1600);
            } else {
                message.setTextColor(Color.rgb(158, 49, 37));
                message.setText("Rated on this phone, but sharing failed: " + error);
                submit.setEnabled(true);
            }
        });
    }
}
