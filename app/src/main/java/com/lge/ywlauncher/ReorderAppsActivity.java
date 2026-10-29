package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReorderAppsActivity extends Activity {
    private final ArrayList<ResolveInfo> apps = new ArrayList<>();
    private final ArrayList<String> order = new ArrayList<>();
    private final ArrayList<String> labels = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView list;
    private int selected = 0;
    private android.content.SharedPreferences prefs;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        prefs = getSharedPreferences("launcher", 0);
        loadOrder();
        buildPage();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void loadOrder() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(intent, 0);
        Set<String> savedIncluded = prefs.getStringSet("included", null);
        Set<String> included = new HashSet<>();
        for (ResolveInfo app : found) {
            if (MainActivity.class.getName().equals(app.activityInfo.name)) continue;
            boolean system = (app.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (savedIncluded == null ? !system : savedIncluded.contains(app.activityInfo.name) || savedIncluded.contains(app.activityInfo.packageName)) {
                included.add(app.activityInfo.name);
                apps.add(app);
            }
        }
        Collections.sort(apps, (a,b) -> String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(), b.loadLabel(getPackageManager()).toString()));
        String raw = prefs.getString("ordered_apps", "");
        if (raw != null) for (String name : raw.split("\\n")) if (included.contains(name) && !order.contains(name)) order.add(name);
        for (ResolveInfo app : apps) if (!order.contains(app.activityInfo.name)) order.add(app.activityInfo.name);
        refreshLabels();
    }

    private void refreshLabels() {
        labels.clear();
        for (String name : order) {
            String label = name;
            for (ResolveInfo app : apps) if (app.activityInfo.name.equals(name)) { label = app.loadLabel(getPackageManager()).toString(); break; }
            labels.add(label);
        }
    }

    private void buildPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.surface(this));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("‹ Back");
        ThemeUtils.tint(back, this);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(48)));
        TextView title = new TextView(this);
        title.setText("Reorder apps");
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ThemeUtils.text(this));
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(toolbar);

        TextView help = new TextView(this);
        help.setText("Select an app, then move it up or down. Save when finished.");
        help.setTextSize(13);
        help.setTextColor(ThemeUtils.secondaryText(this));
        help.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(help);

        list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_activated_1, labels);
        list.setAdapter(adapter);
        if (!order.isEmpty()) list.setItemChecked(0, true);
        list.setOnItemClickListener((parent, view, position, id) -> selected = position);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setPadding(dp(8), dp(6), dp(8), dp(6));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Button up = new Button(this); up.setText("↑  Move up"); ThemeUtils.tint(up, this);
        Button down = new Button(this); down.setText("↓  Move down"); ThemeUtils.tint(down, this);
        up.setBackground(ThemeUtils.focusBackground(this));
        down.setBackground(ThemeUtils.focusBackground(this));
        up.setOnFocusChangeListener((v,f)->up.setBackground(f?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
        down.setOnFocusChangeListener((v,f)->down.setBackground(f?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
        controls.addView(up, new LinearLayout.LayoutParams(0, dp(50), 1));
        controls.addView(down, new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(controls);

        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(8), dp(4), dp(8), dp(10));
        Button save = new Button(this); save.setText("Save order"); ThemeUtils.tint(save, this);
        Button cancel = new Button(this); cancel.setText("Cancel"); ThemeUtils.tint(cancel, this);
        footer.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(footer);

        up.setOnClickListener(v -> move(-1));
        down.setOnClickListener(v -> move(1));
        save.setOnClickListener(v -> {
            StringBuilder value = new StringBuilder();
            for (String name : order) { if (value.length() > 0) value.append('\\n'); value.append(name); }
            prefs.edit().putString("ordered_apps", value.toString()).apply();
            Toast.makeText(this, "App order saved", Toast.LENGTH_SHORT).show();
            finish();
        });
        cancel.setOnClickListener(v -> finish());
        if (order.isEmpty()) { up.setEnabled(false); down.setEnabled(false); save.setEnabled(false); }
        setContentView(root);
    }

    private void move(int direction) {
        int target = selected + direction;
        if (target < 0 || target >= order.size()) return;
        Collections.swap(order, selected, target);
        selected = target;
        refreshLabels();
        adapter.notifyDataSetChanged();
        list.setItemChecked(selected, true);
        list.setSelection(selected);
        list.requestFocus();
    }
}