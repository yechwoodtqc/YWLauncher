package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReorderAppsActivity extends Activity {
    private final ArrayList<ResolveInfo> apps = new ArrayList<>();
    private final ArrayList<String> order = new ArrayList<>();
    private final ArrayList<String> labels = new ArrayList<>();
    private ListView list;
    private BaseAdapter adapter;
    private int selected = 0;
    private android.content.SharedPreferences prefs;
    private Button upButton, downButton;

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
        Set<String> included = new HashSet<>();
        Set<String> savedIncluded = prefs.getStringSet("included", null);
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

    private GradientDrawable rowBackground(boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(7));
        if (active) {
            bg.setColor(ThemeUtils.accent(this));
            bg.setStroke(dp(2), ThemeUtils.isDark(this) ? Color.WHITE : 0xff174f9c);
        } else {
            bg.setColor(ThemeUtils.surface(this));
            bg.setStroke(dp(1), ThemeUtils.divider(this));
        }
        return bg;
    }

    private void buildPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.surface(this));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back_modern);
        back.setColorFilter(ThemeUtils.text(this));
        back.setContentDescription("Back");
        back.setBackground(ThemeUtils.focusOutlineBackground(this));
        back.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("Reorder apps");
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ThemeUtils.text(this));
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(toolbar);

        TextView help = new TextView(this);
        help.setText("Select an app, then use arrows or keypad 1 / 2. Save when finished.");
        help.setTextSize(14);
        help.setTextColor(ThemeUtils.secondaryText(this));
        help.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(help);

        list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int position) { return labels.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = convertView instanceof LinearLayout ? (LinearLayout) convertView : new LinearLayout(ReorderAppsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.removeAllViews();
                boolean active = position == selected;
                ImageView icon = new ImageView(ReorderAppsActivity.this);
                for (ResolveInfo app : apps) if (app.activityInfo.name.equals(order.get(position))) {
                    try { icon.setImageDrawable(app.loadIcon(getPackageManager())); }
                    catch (Exception ignored) { icon.setImageResource(android.R.drawable.sym_def_app_icon); }
                    break;
                }
                row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                TextView label = new TextView(ReorderAppsActivity.this);
                label.setText(labels.get(position));
                label.setTextSize(17);
                label.setSingleLine(false);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(dp(12), dp(4), dp(8), dp(4));
                label.setTextColor(active ? ThemeUtils.focusText(ReorderAppsActivity.this) : ThemeUtils.text(ReorderAppsActivity.this));
                row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                row.setPadding(dp(10), dp(4), dp(8), dp(4));
                row.setBackground(rowBackground(active));
                row.setFocusable(false);
                return row;
            }
        };
        list.setAdapter(adapter);
        if (!order.isEmpty()) list.setSelection(0);
        list.setOnItemClickListener((parent, view, position, id) -> selectRow(position));
        list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { selectRow(position); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(3), dp(5), dp(3), dp(8));
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout upGroup = arrowGroup("↑", "1", true);
        LinearLayout downGroup = arrowGroup("↓", "2", false);
        Button save = new Button(this); save.setText("Save"); ThemeUtils.tint(save, this);
        Button cancel = new Button(this); cancel.setText("Cancel"); ThemeUtils.tint(cancel, this);
        save.setTextColor(ThemeUtils.text(this));
        cancel.setTextColor(ThemeUtils.text(this));
        save.setBackground(ThemeUtils.focusOutlineBackground(this));
        cancel.setBackground(ThemeUtils.focusOutlineBackground(this));
        footer.addView(upGroup, new LinearLayout.LayoutParams(0, dp(52), 0.9f));
        footer.addView(downGroup, new LinearLayout.LayoutParams(0, dp(52), 0.9f));
        footer.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1.35f));
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1.35f));
        root.addView(footer);

        save.setOnClickListener(v -> {
            StringBuilder value = new StringBuilder();
            for (String name : order) { if (value.length() > 0) value.append('\n'); value.append(name); }
            prefs.edit().putString("ordered_apps", value.toString()).apply();
            Toast.makeText(this, "App order saved", Toast.LENGTH_SHORT).show();
            finish();
        });
        cancel.setOnClickListener(v -> finish());
        if (order.isEmpty()) { upButton.setEnabled(false); downButton.setEnabled(false); save.setEnabled(false); }
        setContentView(root);
    }

    private LinearLayout arrowGroup(String arrow, String number, boolean up) {
        LinearLayout group = new LinearLayout(this);
        group.setGravity(Gravity.CENTER);
        group.setOrientation(LinearLayout.HORIZONTAL);
        Button arrowButton = new Button(this);
        arrowButton.setText(arrow);
        arrowButton.setTextSize(27);
        arrowButton.setContentDescription(up ? "Move up" : "Move down");
        arrowButton.setTextColor(ThemeUtils.text(this));
        arrowButton.setBackground(ThemeUtils.focusOutlineBackground(this));
        arrowButton.setPadding(0, 0, 0, 0);
        group.addView(arrowButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView key = new TextView(this);
        key.setText(number);
        key.setTextSize(18);
        key.setTextColor(ThemeUtils.secondaryText(this));
        key.setGravity(Gravity.CENTER);
        group.addView(key, new LinearLayout.LayoutParams(dp(26), dp(52)));
        arrowButton.setOnClickListener(v -> move(up ? -1 : 1));
        if (up) upButton = arrowButton; else downButton = arrowButton;
        return group;
    }

    private void selectRow(int position) {
        if (position < 0 || position >= order.size()) return;
        selected = position;
        adapter.notifyDataSetChanged();
    }

    private void move(int direction) {
        if (selected < 0 || selected >= order.size()) return;
        int target = selected + direction;
        if (target < 0 || target >= order.size()) return;
        Collections.swap(order, selected, target);
        selected = target;
        refreshLabels();
        adapter.notifyDataSetChanged();
        list.setSelection(selected);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_1) { move(-1); return true; }
        if (keyCode == KeyEvent.KEYCODE_2) { move(1); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
