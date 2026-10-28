package com.lge.ywlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class SettingsActivity extends Activity {
    private LinearLayout content;
    private SharedPreferences prefs;
    private static final int CREATE_LOG_FILE = 501;
    private static final int PICK_BACKGROUND_IMAGE = 502;
    private String pendingLogcat;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        prefs = getSharedPreferences("launcher", 0);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ThemeUtils.surface(this));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back_modern);
        back.setColorFilter(ThemeUtils.text(this));
        back.setContentDescription("Back");
        back.setBackground(ThemeUtils.focusOutlineBackground(this));
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextSize(19);
        title.setTextColor(ThemeUtils.text(this));
        title.setPadding(dp(4), 0, 0, 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(50)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(16));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);

        section("Notifications");
        addSwitchRow("Persistent launcher notification", "Keep a notification that opens the app.", "notify", true, this::refreshNotification);
        addSwitchRow("Show notification icon", "Display the notification icon in the status bar.", "status_icon", false, this::refreshNotification);

        section("Appearance");
        addSettingRow("Home layout", layoutSummary(), this::chooseLayout);
        addSettingRow("Grid rows", prefs.getInt("grid_rows", 3) + " rows per page", () -> chooseGridValue("grid_rows", "Grid rows", 2, 6));
        addSettingRow("Grid columns", prefs.getInt("grid_columns", 3) + " columns", () -> chooseGridValue("grid_columns", "Grid columns", 2, 6));
        addSettingRow("Icon size", iconSizeSummary(), this::chooseIconSize);
        addSettingRow("Background", backgroundSummary(), this::chooseBackground);
        addSettingRow("Theme", themeSummary(), this::chooseTheme);
        addSettingRow("Accent color", "Choose the highlight color.", this::chooseAccent);
        addSettingRow("Focus highlight", prefs.getBoolean("focus_outline_only", false) ? "Outline only" : "Filled highlight", this::chooseFocusStyle);

        section("Apps");
        addSettingRow("Choose apps", "Choose which installed apps appear in the app grid.", this::chooseApps);
        addSettingRow("Reorder apps", "Change the order of app icons.", this::reorderApps);

        section("Tools");
        addSettingRow("Widgets", "Date and weather widgets for the phone home screen.", () ->
            new AlertDialog.Builder(this)
                .setMessage("YW Launcher provides English/Hebrew Date and Weather widgets. To add one, open the phone's existing home-screen widget picker and choose “English and Hebrew Date” or “YW Weather”. These widgets appear on the phone's current home app; YW Launcher itself remains a regular app, not the default Home app.")
                .setPositiveButton("OK", null).show());
        addSettingRow("Export logcat", "Save recent Android log messages to a text file.", this::exportLogcat);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void section(String label) {
        TextView heading = new TextView(this);
        heading.setText(label.toUpperCase(Locale.US));
        heading.setTextColor(ThemeUtils.accent(this));
        heading.setTextSize(11);
        heading.setLetterSpacing(0.08f);
        heading.setPadding(dp(16), dp(18), dp(16), dp(8));
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addSeparator() {
        View separator = new View(this);
        separator.setBackgroundColor(ThemeUtils.divider(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        content.addView(separator, params);
    }

    private void addSettingRow(String titleText, String summaryText, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(11), dp(14), dp(11));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ThemeUtils.focusBackground(this));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(15);
        title.setTextColor(ThemeUtils.text(this));
        TextView summary = new TextView(this);
        summary.setText(summaryText);
        summary.setTextSize(12);
        summary.setTextColor(ThemeUtils.secondaryText(this));
        summary.setPadding(0, dp(3), 0, 0);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
        labels.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(25);
        chevron.setTextColor(ThemeUtils.secondaryText(this));
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), -1));

        row.setOnFocusChangeListener((view, focused) -> {
            int primary = focused ? ThemeUtils.focusText(this) : ThemeUtils.text(this);
            int secondary = focused ? ThemeUtils.focusText(this) : ThemeUtils.secondaryText(this);
            title.setTextColor(primary);
            summary.setTextColor(secondary);
            chevron.setTextColor(secondary);
        });
        row.setOnClickListener(v -> action.run());
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addSeparator();
    }

    private void addSwitchRow(String titleText, String summaryText, String key, boolean defaultValue, Runnable changed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(14), dp(10));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ThemeUtils.focusBackground(this));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(15);
        title.setTextColor(ThemeUtils.text(this));
        TextView summary = new TextView(this);
        summary.setText(summaryText);
        summary.setTextSize(12);
        summary.setTextColor(ThemeUtils.secondaryText(this));
        summary.setPadding(0, dp(3), 0, 0);
        labels.addView(title);
        labels.addView(summary);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{ThemeUtils.accent(this), ThemeUtils.isDark(this) ? Color.rgb(100, 110, 120) : Color.rgb(175, 185, 195)}));
        toggle.setTrackTintList(ColorStateList.valueOf(ThemeUtils.accent(this)));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            changed.run();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        row.setOnFocusChangeListener((view, focused) -> {
            int primary = focused ? ThemeUtils.focusText(this) : ThemeUtils.text(this);
            int secondary = focused ? ThemeUtils.focusText(this) : ThemeUtils.secondaryText(this);
            title.setTextColor(primary);
            summary.setTextColor(secondary);
        });
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addSeparator();
    }

    private String layoutSummary() {
        return "list".equals(prefs.getString("layout_mode", "grid")) ? "List" : "Grid";
    }

    private String iconSizeSummary() {
        int size = prefs.getInt("icon_size", 40);
        return size <= 32 ? "Small" : (size >= 48 ? "Large" : "Medium");
    }

    private String backgroundSummary() {
        int value = prefs.getInt("background", 0);
        return value == 1 ? "Blue" : (value == 2 ? "Black" : (value == 3 ? "Custom color" : (value == 4 ? "Custom image" : "System wallpaper")));
    }

    private String themeSummary() {
        String mode = prefs.getString("theme_mode", "system");
        return "dark".equals(mode) ? "Dark" : ("light".equals(mode) ? "Light" : "System");
    }

    private void chooseLayout() {
        String current = prefs.getString("layout_mode", "grid");
        int selected = "list".equals(current) ? 1 : 0;
        new AlertDialog.Builder(this).setTitle("Home layout")
            .setSingleChoiceItems(new String[]{"Grid", "List"}, selected, (dialog, which) -> {
                prefs.edit().putString("layout_mode", which == 0 ? "grid" : "list").apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseGridValue(String key, String title, int min, int max) {
        int current = prefs.getInt(key, 3);
        String[] choices = new String[max - min + 1];
        for (int i = min; i <= max; i++) choices[i - min] = String.valueOf(i);
        new AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(choices, Math.max(0, Math.min(max - min, current - min)), (dialog, which) -> {
                prefs.edit().putInt(key, min + which).apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseIconSize() {
        String[] labels = {"Small", "Medium", "Large"};
        int[] sizes = {32, 40, 48};
        int current = prefs.getInt("icon_size", 40);
        int selected = current <= 32 ? 0 : (current >= 48 ? 2 : 1);
        new AlertDialog.Builder(this).setTitle("Icon size")
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                prefs.edit().putInt("icon_size", sizes[which]).apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseBackground() {
        int selected = prefs.getInt("background", 0);
        new AlertDialog.Builder(this).setTitle("Background")
            .setSingleChoiceItems(new String[]{"System wallpaper", "Blue", "Black", "Custom color", "Custom image"}, Math.min(4, selected), (dialog, which) -> {
                dialog.dismiss();
                if (which == 3) {
                    prefs.edit().putInt("background", 3).apply();
                    choosePalette("Custom background color", "custom_background", Color.rgb(25, 35, 48));
                } else if (which == 4) {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    pick.setType("image/*");
                    startActivityForResult(pick, PICK_BACKGROUND_IMAGE);
                } else {
                    prefs.edit().putInt("background", which).apply();
                    recreate();
                }
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseTheme() {
        String mode = prefs.getString("theme_mode", "system");
        int selected = "dark".equals(mode) ? 2 : ("light".equals(mode) ? 1 : 0);
        new AlertDialog.Builder(this).setTitle("Theme")
            .setSingleChoiceItems(new String[]{"System", "Light", "Dark"}, selected, (dialog, which) -> {
                String[] modes = {"system", "light", "dark"};
                prefs.edit().putString("theme_mode", modes[which]).apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseAccent() {
        choosePalette("Accent color", "accent_color", Color.rgb(26, 115, 232));
    }

    private void chooseFocusStyle() {
        boolean outline = prefs.getBoolean("focus_outline_only", false);
        new AlertDialog.Builder(this).setTitle("Focus highlight")
            .setSingleChoiceItems(new String[]{"Filled highlight", "Outline only"}, outline ? 1 : 0, (dialog, which) -> {
                prefs.edit().putBoolean("focus_outline_only", which == 1).apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void choosePalette(String title, String key, int defaultColor) {
        String[] names = {"Blue", "Sky blue", "Indigo", "Deep purple", "Purple", "Pink", "Red", "Orange",
            "Amber", "Yellow", "Lime", "Green", "Teal", "Cyan", "Slate", "Black", "White"};
        int[] colors = {0xff1a73e8, 0xff039be5, 0xff3949ab, 0xff5e35b1, 0xff8e24aa, 0xffd81b60,
            0xffe53935, 0xfff4511e, 0xffffb300, 0xfffdd835, 0xff7cb342, 0xff43a047,
            0xff00897b, 0xff00acc1, 0xff607d8b, Color.BLACK, Color.WHITE};
        int current = prefs.getInt(key, defaultColor);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(choices);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(scroller)
            .setNegativeButton("Cancel", null).create();
        for (int i = 0; i < names.length; i++) {
            final int color = colors[i];
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(8), dp(18), dp(8));
            View swatch = new View(this);
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(color);
            shape.setCornerRadius(dp(5));
            shape.setStroke(dp(1), 0xff9aa3ad);
            swatch.setBackground(shape);
            row.addView(swatch, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView label = new TextView(this);
            label.setText(names[i] + (current == color ? "  ✓" : ""));
            label.setTextColor(ThemeUtils.text(this));
            label.setTextSize(15);
            label.setPadding(dp(14), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            row.setBackground(ThemeUtils.focusBackground(this));
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                prefs.edit().putInt(key, color).apply();
                dialog.dismiss();
                recreate();
            });
            choices.addView(row, new LinearLayout.LayoutParams(-1, -2));
            View line = new View(this);
            line.setBackgroundColor(ThemeUtils.divider(this));
            choices.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        dialog.show();
    }

    private ArrayList<ResolveInfo> queryApps() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<ResolveInfo> found = new ArrayList<>(getPackageManager().queryIntentActivities(query, 0));
        ArrayList<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo app : found) if (!MainActivity.class.getName().equals(app.activityInfo.name)) apps.add(app);
        Collections.sort(apps, (a,b) -> String.CASE_INSENSITIVE_ORDER.compare(
            a.loadLabel(getPackageManager()).toString(), b.loadLabel(getPackageManager()).toString()));
        return apps;
    }

    private Set<String> defaultIncludedActivities(List<ResolveInfo> apps) {
        Set<String> included = new LinkedHashSet<>();
        for (ResolveInfo app : apps) {
            boolean system = (app.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!system) included.add(app.activityInfo.name);
        }
        return included;
    }

    private Set<String> normalizeIncluded(Set<String> saved, List<ResolveInfo> apps) {
        if (saved == null) return defaultIncludedActivities(apps);
        Set<String> result = new LinkedHashSet<>();
        for (ResolveInfo app : apps) {
            if (saved.contains(app.activityInfo.name) || saved.contains(app.activityInfo.packageName)) result.add(app.activityInfo.name);
        }
        return result;
    }

    private void chooseApps() {
        ArrayList<ResolveInfo> apps = queryApps();
        Set<String> old = prefs.getStringSet("included", null);
        boolean[] checked = new boolean[apps.size()];
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo app = apps.get(i);
            labels[i] = app.loadLabel(getPackageManager()).toString();
            boolean system = (app.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            checked[i] = old == null ? !system :
                (old.contains(app.activityInfo.name) || old.contains(app.activityInfo.packageName));
        }
        new AlertDialog.Builder(this).setTitle("Apps to show")
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("Save", (dialog, which) -> {
                Set<String> included = new LinkedHashSet<>();
                for (int i = 0; i < apps.size(); i++) if (checked[i]) included.add(apps.get(i).activityInfo.name);
                ArrayList<String> order = new ArrayList<>();
                String oldOrder = prefs.getString("ordered_apps", "");
                if (oldOrder != null) {
                    for (String name : oldOrder.split("\\n")) {
                        for (ResolveInfo app : apps) {
                            if (app.activityInfo.name.equals(name) && included.contains(name) && !order.contains(name)) order.add(name);
                        }
                    }
                }
                for (ResolveInfo app : apps) if (included.contains(app.activityInfo.name) && !order.contains(app.activityInfo.name)) order.add(app.activityInfo.name);
                prefs.edit().putStringSet("included", included).putString("ordered_apps", joinLines(order)).apply();
                Toast.makeText(this, "App list saved", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", null).show();
    }

    private void reorderApps() {
        ArrayList<ResolveInfo> all = queryApps();
        Set<String> included = normalizeIncluded(prefs.getStringSet("included", null), all);
        Map<String, ResolveInfo> byName = new HashMap<>();
        for (ResolveInfo app : all) if (included.contains(app.activityInfo.name)) byName.put(app.activityInfo.name, app);
        ArrayList<String> order = new ArrayList<>();
        String raw = prefs.getString("ordered_apps", "");
        if (raw != null) for (String name : raw.split("\\n")) if (byName.containsKey(name) && !order.contains(name)) order.add(name);
        for (ResolveInfo app : all) if (byName.containsKey(app.activityInfo.name) && !order.contains(app.activityInfo.name)) order.add(app.activityInfo.name);
        if (order.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("No apps are selected. Use Choose apps first.")
                .setPositiveButton("OK", null).show();
            return;
        }

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView help = new TextView(this);
        help.setText("Select an app, then move it up or down.");
        help.setTextColor(ThemeUtils.text(this));
        dialogLayout.addView(help);

        ListView list = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1);
        for (String name : order) adapter.add(labelFor(byName.get(name)));
        list.setAdapter(adapter);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        LinearLayout controls = new LinearLayout(this);
        Button up = new Button(this); up.setText("↑  Move up"); ThemeUtils.tint(up, this); up.setHeight(dp(48)); up.setTextSize(14);
        Button down = new Button(this); down.setText("↓  Move down"); ThemeUtils.tint(down, this); down.setHeight(dp(48)); down.setTextSize(14);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(up, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(down, new LinearLayout.LayoutParams(0, dp(48), 1));
        dialogLayout.addView(controls);
        dialogLayout.addView(list, new LinearLayout.LayoutParams(-1, dp(100)));

        final int[] selected = {0};
        list.setOnItemClickListener((parent, view, position, id) -> selected[0] = position);
        up.setOnClickListener(v -> {
            int i = selected[0];
            if (i > 0) {
                Collections.swap(order, i, i - 1);
                selected[0] = i - 1;
                refreshOrderAdapter(adapter, order, byName);
                list.setItemChecked(selected[0], true);
            }
        });
        down.setOnClickListener(v -> {
            int i = selected[0];
            if (i < order.size() - 1) {
                Collections.swap(order, i, i + 1);
                selected[0] = i + 1;
                refreshOrderAdapter(adapter, order, byName);
                list.setItemChecked(selected[0], true);
            }
        });
        new AlertDialog.Builder(this).setTitle("App order").setView(dialogLayout)
            .setPositiveButton("Save", (dialog, which) -> prefs.edit().putString("ordered_apps", joinLines(order)).apply())
            .setNegativeButton("Cancel", null).show();
    }

    private String labelFor(ResolveInfo app) {
        return app == null ? "Unknown app" : app.loadLabel(getPackageManager()).toString();
    }

    private void refreshOrderAdapter(ArrayAdapter<String> adapter, ArrayList<String> order, Map<String, ResolveInfo> apps) {
        adapter.clear();
        for (String name : order) adapter.add(labelFor(apps.get(name)));
        adapter.notifyDataSetChanged();
    }

    private String joinLines(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(value);
        }
        return builder.toString();
    }

    private void refreshNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!prefs.getBoolean("notify", true)) {
            manager.cancel(42);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            new NotificationChannel("yw_launcher", "Launcher access", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = android.os.Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "yw_launcher") : new Notification.Builder(this);
        boolean showIcon = prefs.getBoolean("status_icon", false);
        notification.setSmallIcon(showIcon ? R.drawable.ic_launcher_notification : R.drawable.ic_notification_hidden)
            .setContentTitle("YW Launcher")
            .setContentText("Press to open your app launcher")
            .setContentIntent(pending)
            .setOngoing(true);
        if (!showIcon) notification.setVisibility(Notification.VISIBILITY_SECRET);
        manager.notify(42, notification.build());
    }

    private void exportLogcat() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t", "3000"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
            reader.close();
            pendingLogcat = output.toString();
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("text/plain");
            create.putExtra(Intent.EXTRA_TITLE, "ywlauncher-logcat.txt");
            startActivityForResult(create, CREATE_LOG_FILE);
        } catch (Exception e) {
            showError("Could not collect logcat: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKGROUND_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                prefs.edit().putString("custom_background_uri", uri.toString()).putInt("background", 4).apply();
                recreate();
            } catch (Exception e) {
                showError("Could not use that image: " + e.getMessage());
            }
            return;
        }
        if (requestCode == CREATE_LOG_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                OutputStream output = getContentResolver().openOutputStream(uri);
                if (output == null) throw new IOException("Could not open selected file");
                output.write((pendingLogcat == null ? "" : pendingLogcat).getBytes("UTF-8"));
                output.close();
                Toast.makeText(this, "Logcat file saved", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                showError("Could not save logcat: " + e.getMessage());
            }
        }
        pendingLogcat = null;
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }
}
