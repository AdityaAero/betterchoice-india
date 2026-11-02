package com.betterchoice.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

final class CountryPicker {
    interface Selected { void accept(String name, String code); }
    static void show(Activity activity, Selected selected) {
        ArrayList<String[]> countries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activity.getAssets().open("country_codes.csv"), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) { String[] row = line.split(","); countries.add(new String[]{new Locale("", row[0]).getDisplayCountry(Locale.ENGLISH), row[1]}); }
        } catch (Exception unavailable) { return; }
        countries.sort(Comparator.comparing(row -> row[0]));
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(24, 16, 24, 16);
        EditText search = UiKit.input(activity, "Country or calling code", android.text.InputType.TYPE_CLASS_TEXT);
        body.addView(search); ListView list = new ListView(activity); body.addView(list, new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 340)));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        ArrayList<String[]> visible = new ArrayList<>(); list.setAdapter(adapter);
        Runnable filter = () -> {
            visible.clear(); adapter.clear(); String query = search.getText().toString().trim().toLowerCase(Locale.ROOT).replace("+", "");
            for (String[] country : countries) if (country[0].toLowerCase(Locale.ROOT).contains(query) || country[1].startsWith(query)) {
                visible.add(country); adapter.add(country[0] + "  +" + country[1]);
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            public void afterTextChanged(Editable e) {}
        });
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Choose your country").setView(body).setNegativeButton("Cancel", null).create();
        list.setFilterTouchesWhenObscured(true);
        list.setOnTouchListener((view, event) -> SafeTouches.isObscured(event.getFlags()));
        list.setOnItemClickListener((parent, view, position, id) -> { String[] row = visible.get(position); selected.accept(row[0], row[1]); dialog.dismiss(); });
        filter.run(); dialog.show(); dialog.getWindow().setHideOverlayWindows(true);
    }
    private CountryPicker() {}
}
