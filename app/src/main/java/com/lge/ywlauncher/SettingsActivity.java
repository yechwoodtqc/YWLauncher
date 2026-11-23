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
    private ScrollView settingsScroll;
    private SharedPreferences prefs;
    private static final int CREATE_LOG_FILE = 501;
    private static final int PICK_BACKGROUND_IMAGE = 502;
    private static final int PICK_BACKGROUND_CHOOSER = 508;
    private static final int PICK_KEPT_WALLPAPER = 503;
    private static final int CREATE_DICTIONARY_FILE = 504;
    private static final int PICK_DICTIONARY_FILE = 505;
    private static final int PICK_LOCAL_FILE = 506;
    private static final int PICK_LOCAL_FOLDER = 507;
    private String pendingFileAction;
    private int pendingBrowserRequest;
    private String pendingLogcat;
    private String pendingDictionaryBackup;
    private String pendingExportContent;
    private String pendingExportName;

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
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(50)));

        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(16));
        settingsScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        settingsScroll.post(() -> { settingsScroll.scrollTo(0, 0); content.requestFocus(); });

        section("Notifications");
        String notificationHelp = isLgFlipModel()
            ? "Keep quick access to YW Launcher in the notification shade on this LG flip phone."
            : "Keep a notification that opens the app.";
        addSwitchRow("Persistent launcher notification", notificationHelp, "notify", true, this::refreshNotification);
        addSwitchRow("Show notification icon", "Display the notification icon in the status bar.", "status_icon", false, this::refreshNotification);
        if (isLgFlipModel()) {
            addSettingRow("Add YW Launcher contact", "Save a separate contact that opens YW Launcher.", () -> addLauncherContact("launcher.vcf"));
            addSettingRow("Add YW Home contact", "Save a separate contact that opens the main launcher screen.", () -> addLauncherContact("home.vcf"));
        }
        addSwitchRow("Make YW Home a camera option", "Adds YW Home to apps that can handle the standard camera capture intent. Android 6 cannot reliably limit this to calls; the phone's dedicated camera key may still open its normal camera directly.", "camera_home_enabled", false, this::updateCameraHomeHandler);

        section("Appearance");
        addSettingRow("Home layout", layoutSummary(), this::chooseLayout);
        addSettingRow("Grid rows", prefs.getInt("grid_rows", defaultGridRows()) + " rows per page", () -> chooseGridValue("grid_rows", "Grid rows", 2, 6));
        addSettingRow("Grid columns", prefs.getInt("grid_columns", 3) + " columns", () -> chooseGridValue("grid_columns", "Grid columns", 2, 6));
        addSettingRow("Icon size", iconSizeSummary(), this::chooseIconSize);
        addSettingRow("Font size", fontSizeSummary(), this::chooseFontSize);
        addSettingRow("Background", backgroundSummary(), this::chooseBackground);
        addSettingRow("Theme", themeSummary(), this::chooseTheme);
        addSettingRow("Accent color", "Choose the highlight color.", this::chooseAccent);
        addSettingRow("Focus highlight", prefs.getBoolean("focus_outline_only", true) ? "Outline only" : "Filled highlight", this::chooseFocusStyle);

        section("Apps");
        addSettingRow("Choose apps", "Choose apps with launcher icons and check marks.", () -> startActivity(new Intent(this, ChooseAppsActivity.class)));
        addSettingRow("Reorder apps", "Change the order of app icons.", () -> startActivity(new Intent(this, ReorderAppsActivity.class)));

        section("Tweaks");
        addSwitchRow("Keep wallpaper", "Choose an image to restore after restart if another app replaces it.", "keep_wallpaper", false, this::updateKeptWallpaper);
        addSettingRow("Customize kept wallpaper", keptWallpaperSummary(), this::customizeKeptWallpaper);
        addSettingRow("LG Keyboard settings", "General > Word Suggestions > Emoticons. Turn it on to add emoji suggestions to T9.", this::openLgKeyboardSettings);

        section("Tools");
        addSettingRow("Export logcat", "Save recent Android log messages to Downloads.", this::exportLogcat);
    }

    @Override protected void onPause() { super.onPause(); }

    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_SOFT_LEFT) { finish(); return true; }
        if (keyCode == android.view.KeyEvent.KEYCODE_SOFT_RIGHT) {
            View focused = getCurrentFocus();
            if (focused != null && focused.isEnabled() && focused.isClickable()) {
                focused.performClick();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private String keptWallpaperSummary() {
        File image = new File(getFilesDir(), "kept_wallpaper.png");
        return image.isFile() && image.length() > 0
            ? "Preview, crop, and adjust zoom for the saved image."
            : "Choose an image first, then adjust its preview and zoom.";
    }

    private void customizeKeptWallpaper() {
        File image = new File(getFilesDir(), "kept_wallpaper.png");
        if (image.isFile() && image.length() > 0) {
            showKeptWallpaperPreview(Uri.fromFile(image), true);
        } else {
            prefs.edit().putBoolean("keep_wallpaper", true).apply();
            launchKeptWallpaperBrowser();
        }
    }

    private void launchKeptWallpaperBrowser() {
        // Use the same Android-6-compatible picker flow as the working custom
        // launcher background picker, rather than the old internal browser.
        Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*"});
        try {
            startActivityForResult(Intent.createChooser(picker, "Choose wallpaper image"),
                PICK_KEPT_WALLPAPER);
        } catch (Exception e) {
            showError("No file picker is available on this device.");
        }
    }

    private void updateKeptWallpaper() {
        // Turning the switch off only disables restoration. Turning it on asks
        // for an image; the preference remains enabled only after a successful copy.
        if (!prefs.getBoolean("keep_wallpaper", false)) return;
        launchKeptWallpaperBrowser();
    }

    private int dialogTheme() {
        return ThemeUtils.isDark(this) ? R.style.FullScreenDialogDarkTheme : R.style.FullScreenDialogTheme;
    }

    private final class FullScreenDialogBuilder extends AlertDialog.Builder {
        FullScreenDialogBuilder(Activity activity) { super(activity, ThemeUtils.isDark(activity) ? R.style.FullScreenDialogDarkTheme : R.style.FullScreenDialogTheme); }
        @Override public AlertDialog show() {
            AlertDialog dialog = super.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(ThemeUtils.surface(SettingsActivity.this)));
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                dialog.getWindow().setDimAmount(1.0f);
                dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
            return dialog;
        }
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
        title.setTextSize(15 * fontScale());
        title.setTextColor(ThemeUtils.text(this));
        TextView summary = new TextView(this);
        summary.setText(summaryText);
        summary.setTextSize(12 * fontScale());
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
        String mode = prefs.getString("layout_mode", "grid");
        return "list".equals(mode) ? "List" : ("single_grid".equals(mode) ? "Single-screen grid" : "Paged grid");
    }

    private int defaultGridRows() { String model=(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL).toLowerCase(Locale.US); return (model.contains("exalt")||model.contains("vn220")||model.contains("lg-ax"))?4:3; }

    private int defaultIconSize() {
        String model = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).toLowerCase(Locale.US);
        if (model.contains("exalt") || model.contains("vn220") || model.contains("lg-ax")) return 48;
        return 40;
    }

    private float fontScale() { int v=prefs.getInt("font_size_percent",100); if(v<=85)return .85f; if(v<=100)return 1f; if(v<=115)return 1.15f; if(v<=130)return 1.3f; return 1.45f; }

    private String fontSizeSummary() { int v=prefs.getInt("font_size_percent",100); if(v<=85)return "Smaller"; if(v<=100)return "Small"; if(v<=115)return "Large"; if(v<=130)return "Larger"; return "Biggest"; }

    private void chooseFontSize() {
        String[] labels = {"Smaller", "Small", "Large", "Larger", "Biggest"};
        int[] values = {85, 100, 115, 130, 145};
        int current = prefs.getInt("font_size_percent", 100), selected = 1;
        for (int i=0;i<values.length;i++) if(values[i]==current) selected=i;
        new FullScreenDialogBuilder(this).setTitle("Launcher font size")
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                prefs.edit().putInt("font_size_percent", values[which]).apply();
                dialog.dismiss(); recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private String iconSizeSummary() {
        int size = prefs.getInt("icon_size", defaultIconSize());
        return size <= 32 ? "Small" : (size >= 48 ? "Large" : "Medium");
    }

    private int defaultBackgroundForDevice() {
        String model = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).toLowerCase(Locale.US);
        if (model.contains("l125dl") || model.contains("l125") || model.contains("classic")) return 6;
        if (model.contains("vn220") || model.contains("exalt") || model.contains("lg-ax")) return 5;
        return 0;
    }

    private String backgroundSummary() {
        int value = prefs.getInt("background", defaultBackgroundForDevice());
        if (value == 0) value = defaultBackgroundForDevice();
        if (value == 6) return "LG Classic menu";
        if (value == 5) return "Exalt gradient";
        if (value == 7) return "System wallpaper";
        return value == 1 ? "Blue" : (value == 2 ? "Black" : (value == 3 ? "Custom color" : (value == 4 ? "Custom image" : "System wallpaper")));
    }

    private String themeSummary() {
        String mode = prefs.getString("theme_mode", "system");
        return "dark".equals(mode) ? "Dark" : ("light".equals(mode) ? "Light" : "System");
    }

    private void chooseLayout() {
        String current = prefs.getString("layout_mode", "grid");
        int selected = "list".equals(current) ? 1 : ("single_grid".equals(current) ? 2 : 0);
        new FullScreenDialogBuilder(this).setTitle("Home layout")
            .setSingleChoiceItems(new String[]{"Paged grid", "List", "Single-screen grid"}, selected, (dialog, which) -> {
                prefs.edit().putString("layout_mode", which == 1 ? "list" : (which == 2 ? "single_grid" : "grid")).apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseGridValue(String key, String title, int min, int max) {
        int current = prefs.getInt(key, 3);
        String[] choices = new String[max - min + 1];
        for (int i = min; i <= max; i++) choices[i - min] = String.valueOf(i);
        new FullScreenDialogBuilder(this).setTitle(title)
            .setSingleChoiceItems(choices, Math.max(0, Math.min(max - min, current - min)), (dialog, which) -> {
                prefs.edit().putInt(key, min + which).apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseIconSize() {
        String[] labels = {"Small", "Medium", "Large"};
        int[] sizes = {32, 40, 48};
        int current = prefs.getInt("icon_size", defaultIconSize());
        int selected = current <= 32 ? 0 : (current >= 48 ? 2 : 1);
        new FullScreenDialogBuilder(this).setTitle("Icon size")
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                prefs.edit().putInt("icon_size", sizes[which]).apply();
                dialog.dismiss();
                recreate();
            }).setNegativeButton("Cancel", null).show();
    }

    private void chooseBackground() {
        startActivityForResult(new Intent(this, BackgroundPickerActivity.class), PICK_BACKGROUND_CHOOSER);
    }

    private void chooseCustomBackgroundSource() {
        // Match YW App Installer: ACTION_GET_CONTENT + a wildcard primary type.
        // Several Android 6 OEM file managers do not register image/* directly.
        Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*"});
        try {
            startActivityForResult(Intent.createChooser(picker, "Choose background image"),
                PICK_BACKGROUND_IMAGE);
        } catch (Exception e) {
            showError("No file picker is available on this device.");
        }
    }

    private void chooseTheme() {
        String mode = prefs.getString("theme_mode", "system");
        int selected = "dark".equals(mode) ? 2 : ("light".equals(mode) ? 1 : 0);
        new FullScreenDialogBuilder(this).setTitle("Theme")
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
        boolean outline = prefs.getBoolean("focus_outline_only", true);
        new FullScreenDialogBuilder(this).setTitle("Focus highlight")
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
        AlertDialog dialog = new FullScreenDialogBuilder(this).setTitle(title).setView(scroller)
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
        new FullScreenDialogBuilder(this).setTitle("Apps to show")
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
            new FullScreenDialogBuilder(this).setMessage("No apps are selected. Use Choose apps first.")
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
        AlertDialog reorderDialog = new FullScreenDialogBuilder(this).setTitle("App order")
            .setView(dialogLayout)
            .setPositiveButton("Save", (dialog, which) -> prefs.edit().putString("ordered_apps", joinLines(order)).apply())
            .setNegativeButton("Cancel", null).create();
        reorderDialog.setOnShowListener(dialog -> {
            if (reorderDialog.getWindow() != null) {
                reorderDialog.getWindow().setLayout(-1, -1);
                reorderDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
        up.setBackground(ThemeUtils.focusBackground(this));
        down.setBackground(ThemeUtils.focusBackground(this));
        up.setOnFocusChangeListener((v, focused) -> up.setBackground(focused ? ThemeUtils.focusOutlineBackground(this) : ThemeUtils.focusBackground(this)));
        down.setOnFocusChangeListener((v, focused) -> down.setBackground(focused ? ThemeUtils.focusOutlineBackground(this) : ThemeUtils.focusBackground(this)));
        reorderDialog.show();
        if (list.getCount() > 0) { list.setItemChecked(0, true); list.requestFocus(); }
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
            .setLargeIcon(NotificationIconUtils.createLargeIcon(this, R.drawable.ic_launcher))
            .setContentTitle("YW Launcher")
            .setContentText("Press to open your app launcher")
            .setContentIntent(pending)
            .setOngoing(true);
        manager.notify(42, notification.build());
    }


    private void openLgKeyboardSettings() {
        new FullScreenDialogBuilder(this)
            .setTitle("LG Keyboard · Emoticon suggestions")
            .setMessage("In LG Keyboard settings, open General > Word Suggestions > Emoticons and turn it on. When you type a word with a matching emoji, the emoji can then appear as a T9 suggestion.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open keyboard settings", (dialog, which) -> {
                try {
                    Intent keyboard = new Intent();
                    keyboard.setComponent(new ComponentName("com.lge.ime", "com.lge.ime.setting.MainSettingsActivity"));
                    keyboard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(keyboard);
                } catch (Exception e) {
                    showError("LG Keyboard settings are not available on this device.");
                }
            }).show();
    }

    private boolean isLgFlipModel() {
        String model = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).toLowerCase(Locale.US);
        return model.contains("l125") || model.contains("classic") || model.contains("vn220")
            || model.contains("exalt") || model.contains("lg-ax");
    }

    private void addLauncherContact(String card) {
        Uri uri = Uri.parse("content://com.lge.ywlauncher.vcard/" + card);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "text/x-vcard");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(intent); }
        catch (Exception e) {
            showError("No contact importer on this phone accepts VCF cards. Open Contacts and import the bundled YWLauncher.vcf or YWHome.vcf file instead.");
        }
    }

    private void updateCameraHomeHandler() {
        int state = prefs.getBoolean("camera_home_enabled", false)
            ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(
            new android.content.ComponentName(this, CameraHomeActivity.class), state,
            android.content.pm.PackageManager.DONT_KILL_APP);
    }

    private boolean hasUserDictionaryProvider() {
        android.content.pm.ProviderInfo provider =
            getPackageManager().resolveContentProvider("user_dictionary", 0);
        if (provider == null || !"com.android.providers.userdictionary".equals(provider.packageName)) {
            showError("The Android User Dictionary provider (com.android.providers.userdictionary) is not available on this phone.");
            return false;
        }
        return true;
    }

    private void backupUserDictionary() {
        if (!hasUserDictionaryProvider()) return;
        new Thread(() -> {
            try {
                org.json.JSONArray entries = new org.json.JSONArray();
                String[] columns = { android.provider.UserDictionary.Words.WORD,
                    android.provider.UserDictionary.Words.FREQUENCY, android.provider.UserDictionary.Words.LOCALE,
                    android.provider.UserDictionary.Words.APP_ID, android.provider.UserDictionary.Words.SHORTCUT };
                android.database.Cursor cursor = getContentResolver().query(
                    android.provider.UserDictionary.Words.CONTENT_URI, columns, null, null, null);
                if (cursor == null) throw new IOException("The Android User Dictionary provider returned no data.");
                try {
                    while (cursor.moveToNext()) {
                        org.json.JSONObject entry = new org.json.JSONObject();
                        for (int i=0;i<columns.length;i++) {
                            if(cursor.isNull(i)) entry.put(columns[i],org.json.JSONObject.NULL);
                            else if(i==1||i==3) entry.put(columns[i],cursor.getInt(i));
                            else entry.put(columns[i],cursor.getString(i));
                        }
                        entries.put(entry);
                    }
                } finally { cursor.close(); }
                // Do not silently create a backup that contains no words.
                // Android exposes the User Dictionary provider here; some LG
                // keyboard dictionaries are private to the keyboard and are
                // not exposed through this provider.
                if (entries.length() == 0) {
                    runOnUiThread(() -> showError("No words were found in Android's User Dictionary, so no empty backup was created. The LG keyboard's private predictive dictionary is not exposed by Android's public User Dictionary provider."));
                    return;
                }
                org.json.JSONObject backup=new org.json.JSONObject();
                backup.put("format","ywlauncher-user-dictionary-v1");
                backup.put("source_package","com.android.providers.userdictionary");
                backup.put("created_at",System.currentTimeMillis()); backup.put("entries",entries);
                pendingDictionaryBackup=backup.toString(2);
                runOnUiThread(() -> saveExportToDownloads("ywlauncher-t9-dictionary.json", pendingDictionaryBackup, CREATE_DICTIONARY_FILE));
            } catch(Exception e) { runOnUiThread(() -> showError("Could not back up the T9 dictionary: "+e.getMessage())); }
        },"YWDictionaryBackup").start();
    }

    private void restoreUserDictionary() {
        if (!hasUserDictionaryProvider()) return;
        launchFileBrowser("open", "json", PICK_DICTIONARY_FILE);
    }

    private void writeDictionaryBackup(Uri destination) {
        try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
            if (output == null) throw new IOException("The selected destination could not be opened.");
            output.write((pendingDictionaryBackup == null ? "" : pendingDictionaryBackup).getBytes("UTF-8"));
            output.flush();
            Toast.makeText(this, "T9 dictionary backup saved.", Toast.LENGTH_LONG).show();
        } catch(Exception e) { showError("Could not save the dictionary backup: "+e.getMessage()); }
        finally { pendingDictionaryBackup=null; }
    }

    private void readDictionaryBackup(Uri source) {
        new Thread(() -> {
            try {
                StringBuilder json=new StringBuilder();
                try(InputStream input=getContentResolver().openInputStream(source)) {
                    if(input==null) throw new IOException("The selected backup could not be opened.");
                    BufferedReader reader=new BufferedReader(new InputStreamReader(input,"UTF-8"));
                    String line; while((line=reader.readLine())!=null) json.append(line);
                }
                org.json.JSONObject object=new org.json.JSONObject(json.toString());
                if(!"ywlauncher-user-dictionary-v1".equals(object.optString("format")))
                    throw new IOException("This is not a YW Launcher T9 dictionary backup.");
                org.json.JSONArray entries=object.getJSONArray("entries"); int inserted=0,skipped=0;
                java.util.HashSet<String> existing=new java.util.HashSet<>();
                String[] projection={android.provider.UserDictionary.Words.WORD,
                    android.provider.UserDictionary.Words.LOCALE,
                    android.provider.UserDictionary.Words.SHORTCUT};
                android.database.Cursor existingCursor=getContentResolver().query(
                    android.provider.UserDictionary.Words.CONTENT_URI,projection,null,null,null);
                if(existingCursor==null) throw new IOException("The Android User Dictionary provider returned no data.");
                try(android.database.Cursor cursor=existingCursor) {
                    while(cursor.moveToNext()) {
                        String w=cursor.getString(0);
                        String l=cursor.isNull(1)?"":cursor.getString(1);
                        String sh=cursor.isNull(2)?"":cursor.getString(2);
                        existing.add((w==null?"":w).trim().toLowerCase(Locale.US)+"|"+l+"|"+sh);
                    }
                }
                for(int i=0;i<entries.length();i++) {
                    org.json.JSONObject entry=entries.getJSONObject(i);
                    String word=entry.optString(android.provider.UserDictionary.Words.WORD,"").trim();
                    if(word.isEmpty()){skipped++;continue;}
                    android.content.ContentValues values=new android.content.ContentValues();
                    values.put(android.provider.UserDictionary.Words.WORD,word);
                    values.put(android.provider.UserDictionary.Words.FREQUENCY,entry.optInt(android.provider.UserDictionary.Words.FREQUENCY,250));
                    String locale=entry.optString(android.provider.UserDictionary.Words.LOCALE,"");
                    if(locale.isEmpty()) values.putNull(android.provider.UserDictionary.Words.LOCALE); else values.put(android.provider.UserDictionary.Words.LOCALE,locale);
                    values.put(android.provider.UserDictionary.Words.APP_ID,0);
                    String shortcut=entry.optString(android.provider.UserDictionary.Words.SHORTCUT,"");
                    if(shortcut.isEmpty()) values.putNull(android.provider.UserDictionary.Words.SHORTCUT); else values.put(android.provider.UserDictionary.Words.SHORTCUT,shortcut);
                    String identity=word.trim().toLowerCase(Locale.US)+"|"+locale+"|"+shortcut;
                    if(existing.contains(identity)){skipped++;continue;}
                    Uri created=getContentResolver().insert(android.provider.UserDictionary.Words.CONTENT_URI,values);
                    if(created!=null){inserted++;existing.add(identity);}else skipped++;
                }
                final int restored=inserted,ignored=skipped;
                runOnUiThread(() -> Toast.makeText(this,"Restored "+restored+" words; skipped "+ignored+".",Toast.LENGTH_LONG).show());
            } catch(SecurityException e) { runOnUiThread(() -> showError("Android denied access to com.android.providers.userdictionary. On this phone that provider may be restricted to the system keyboard.")); }
            catch(Exception e) { runOnUiThread(() -> showError("Could not restore the T9 dictionary: "+e.getMessage())); }
        },"YWDictionaryRestore").start();
    }

    private void exportLogcat() {
        new Thread(() -> {
            Process process=null;
            try {
                process=new ProcessBuilder("logcat","-d","-t","3000").redirectErrorStream(true).start();
                StringBuilder output=new StringBuilder();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))) {
                    String line;
                    while((line=reader.readLine())!=null) output.append(line).append('\n');
                }
                int exitCode=process.waitFor();
                if(exitCode!=0 && output.length()==0)
                    throw new IOException("logcat exited with code "+exitCode);
                pendingLogcat=output.toString();
                runOnUiThread(() -> saveExportToDownloads("ywlauncher-logcat.txt", pendingLogcat, CREATE_LOG_FILE));
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> showError("Logcat export was interrupted."));
            } catch(Exception e) {
                final String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                runOnUiThread(() -> showError("Could not collect logcat: "+message));
            } finally {
                if(process!=null) process.destroy();
            }
        },"YWLogcatExport").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == PICK_BACKGROUND_CHOOSER) {
            if (resultCode == RESULT_OK) recreate();
            return;
        }
        if(requestCode==CREATE_DICTIONARY_FILE || requestCode==CREATE_LOG_FILE ||
           requestCode==PICK_DICTIONARY_FILE || requestCode==PICK_KEPT_WALLPAPER ||
           requestCode==PICK_BACKGROUND_IMAGE) {
            Uri uri = null;
            if (resultCode == RESULT_OK && data != null) {
                String selectedPath = data.getStringExtra(FileBrowserActivity.EXTRA_PATH);
                if (requestCode == PICK_KEPT_WALLPAPER && selectedPath != null) uri = Uri.fromFile(new File(selectedPath));
                else uri = data.getData();
            }
            if(uri!=null && android.os.Build.VERSION.SDK_INT>=19 &&
                requestCode!=CREATE_DICTIONARY_FILE && requestCode!=CREATE_LOG_FILE) {
                // The restore and image pickers only need read access. Keep the
                // permission flag explicit so Android lint can verify its value.
                if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) { }
                }
            }
            if(requestCode==CREATE_DICTIONARY_FILE) {
                if(uri!=null) writeDictionaryBackup(uri); else pendingDictionaryBackup=null;
                return;
            }
            if(requestCode==CREATE_LOG_FILE) {
                try {
                    if(uri==null) return;
                    try(OutputStream out=getContentResolver().openOutputStream(uri, "wt")) {
                        if(out==null) throw new IOException("The selected destination could not be opened.");
                        out.write((pendingLogcat==null?"":pendingLogcat).getBytes("UTF-8"));
                    }
                    Toast.makeText(this,"Logcat exported.",Toast.LENGTH_LONG).show();
                } catch(Exception e) { showError("Could not save logcat: "+e.getMessage()); }
                finally { pendingLogcat=null; }
                return;
            }
            if(uri==null) {
                if(requestCode==PICK_KEPT_WALLPAPER) prefs.edit().putBoolean("keep_wallpaper",false).apply();
                return;
            }
            if(requestCode==PICK_DICTIONARY_FILE) { readDictionaryBackup(uri); return; }
            if(requestCode==PICK_KEPT_WALLPAPER) {
                showKeptWallpaperPreview(uri);
                return;
            }
            if(requestCode==PICK_BACKGROUND_IMAGE) {
                showWallpaperPreview(uri); return;
            }
        }
    }

    private void saveExportToDownloads(String fileName, String contents, int requestCode) {
        if (contents == null) {
            showError("There is no export data to save.");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingExportName = fileName;
            pendingExportContent = contents;
            pendingBrowserRequest = requestCode;
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 700);
            return;
        }
        writeExportToDownloads(fileName, contents, requestCode);
    }

    private void writeExportToDownloads(String fileName, String contents, int requestCode) {
        try {
            File downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (!downloads.exists() && !downloads.mkdirs()) throw new IOException("Could not create the Downloads folder.");
            File destination = new File(downloads, fileName);
            try (OutputStream output = new FileOutputStream(destination)) {
                output.write(contents.getBytes("UTF-8"));
                output.flush();
            }
            android.media.MediaScannerConnection.scanFile(this, new String[]{destination.getAbsolutePath()}, null, null);
            Toast.makeText(this, "Saved to Downloads/" + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showError("Could not save " + fileName + ": " + e.getMessage());
        } finally {
            if (requestCode == CREATE_LOG_FILE) pendingLogcat = null;
            if (requestCode == CREATE_DICTIONARY_FILE) pendingDictionaryBackup = null;
        }
    }

    private void launchFileBrowser(String mode, String filter, int requestCode) {
        // ACTION_OPEN_DOCUMENT grants a persistable read permission. This is
        // important for custom backgrounds, whose URI is reopened on later
        // launcher starts rather than copied into private storage.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        // Older file managers often do not advertise JSON MIME support. Let the
        // user browse files and validate the selected backup after opening it.
        intent.setType("image".equals(filter) ? "image/*" : "*/*");
        try { startActivityForResult(Intent.createChooser(intent, "Choose file"), requestCode); }
        catch (Exception e) { showError("No file browser is available on this phone."); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 701) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) launchKeptWallpaperBrowser();
            else {
                prefs.edit().putBoolean("keep_wallpaper", false).apply();
                showError("Storage access is needed to browse and preview an image on this phone.");
            }
            return;
        }
        if (requestCode == 700) {
            int action = pendingBrowserRequest;
            pendingBrowserRequest = 0;
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingExportContent != null && pendingExportName != null) {
                String name = pendingExportName;
                String contents = pendingExportContent;
                pendingExportName = null;
                pendingExportContent = null;
                writeExportToDownloads(name, contents, action);
            } else {
                pendingExportName = null;
                pendingExportContent = null;
                pendingLogcat = null;
                pendingDictionaryBackup = null;
                showError(granted ? "Nothing was waiting to be exported." : "Storage permission is required to save exports to Downloads.");
            }
        }
    }

    private void copyFile(File source, File destination) throws IOException {
        try(InputStream in=new FileInputStream(source); OutputStream out=new FileOutputStream(destination)) {
            byte[] buffer=new byte[8192]; int n; while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
        }
    }

    private void showKeptWallpaperPreview(Uri imageUri) {
        showKeptWallpaperPreview(imageUri, false);
    }

    private void showKeptWallpaperPreview(Uri imageUri, boolean editingSavedImage) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(imageUri)) {
            if (in == null) throw new IOException("Could not open the selected image.");
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception e) { showError("Could not inspect this image: " + e.getMessage()); return; }
        int targetW = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int targetH = Math.max(1, getResources().getDisplayMetrics().heightPixels);
        int sample = 1;
        while (bounds.outWidth / sample > targetW * 2 || bounds.outHeight / sample > targetH * 2) sample *= 2;
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inSampleSize = sample;
        android.graphics.Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(imageUri)) {
            bitmap = in == null ? null : android.graphics.BitmapFactory.decodeStream(in, null, options);
        } catch (Exception e) { bitmap = null; }
        if (bitmap == null) { showError("Could not preview this image."); return; }
        final android.graphics.Bitmap previewBitmap = bitmap;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(4));
        ImageView preview = new ImageView(this);
        preview.setImageBitmap(bitmap);
        preview.setBackgroundColor(Color.BLACK);
        preview.setScaleType(ImageView.ScaleType.MATRIX);
        float screenRatio = getResources().getDisplayMetrics().heightPixels
            / (float) Math.max(1, getResources().getDisplayMetrics().widthPixels);
        int previewHeight = Math.min(dp(300), Math.max(dp(180),
            getResources().getDisplayMetrics().heightPixels - dp(250)));
        int previewWidth = Math.min(dp(230), Math.max(dp(110), Math.round(previewHeight / screenRatio)));
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(Color.BLACK);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        panel.addView(previewFrame, new LinearLayout.LayoutParams(previewWidth, previewHeight));

        TextView hint = new TextView(this);
        hint.setText("Preview on the Exalt screen. Choose how the image fills the screen, then zoom to adjust the crop.");
        hint.setTextColor(ThemeUtils.text(this));
        hint.setPadding(0, dp(8), 0, dp(4));
        panel.addView(hint);

        String[] modes = {"Fill / crop", "Fit entire image", "Stretch"};
        String savedFit = prefs.getString("kept_wallpaper_fit", "crop");
        float initialZoom = Math.max(0.5f, Math.min(2f, prefs.getFloat("kept_wallpaper_zoom", 1f)));
        int selected = "fit".equals(savedFit) ? 1 : ("stretch".equals(savedFit) ? 2 : 0);
        RadioGroup fits = new RadioGroup(this);
        fits.setOrientation(RadioGroup.HORIZONTAL);
        int[] ids = {View.generateViewId(), View.generateViewId(), View.generateViewId()};
        for (int i = 0; i < modes.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(modes[i]);
            option.setTextSize(11);
            option.setTextColor(ThemeUtils.text(this));
            option.setId(ids[i]);
            fits.addView(option, new RadioGroup.LayoutParams(0, -2, 1));
            if (i == selected) option.setChecked(true);
        }
        final String[] previewFit = {savedFit};
        final float[] previewZoom = {initialZoom};
        Runnable updatePreview = () -> {
            if (preview.getWidth() <= 0 || preview.getHeight() <= 0) return;
            float imageW = previewBitmap.getWidth(), imageH = previewBitmap.getHeight();
            float viewW = preview.getWidth(), viewH = preview.getHeight();
            float sx = viewW / imageW, sy = viewH / imageH;
            float scale = "fit".equals(previewFit[0]) ? Math.min(sx, sy)
                : ("stretch".equals(previewFit[0]) ? -1f : Math.max(sx, sy));
            float zoomFactor = Math.max(0.5f, Math.min(2f, previewZoom[0]));
            float scaleX = "stretch".equals(previewFit[0]) ? sx * zoomFactor : scale * zoomFactor;
            float scaleY = "stretch".equals(previewFit[0]) ? sy * zoomFactor : scale * zoomFactor;
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setScale(scaleX, scaleY);
            matrix.postTranslate((viewW - imageW * scaleX) / 2f,
                (viewH - imageH * scaleY) / 2f);
            preview.setImageMatrix(matrix);
        };
        preview.post(updatePreview);
        fits.setOnCheckedChangeListener((group, id) -> {
            previewFit[0] = id == ids[1] ? "fit" : (id == ids[2] ? "stretch" : "crop");
            preview.post(updatePreview);
        });
        panel.addView(fits);

        TextView zoomLabel = new TextView(this);
        zoomLabel.setText("Zoom");
        zoomLabel.setTextColor(ThemeUtils.text(this));
        panel.addView(zoomLabel);
        SeekBar zoom = new SeekBar(this);
        zoom.setMax(100);
        zoom.setProgress(Math.max(0, Math.min(100, Math.round((initialZoom - 0.5f) / 1.5f * 100f))));
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float factor = 0.5f + progress * 0.015f;
                previewZoom[0] = factor;
                preview.post(updatePreview);
                zoomLabel.setText("Zoom  " + Math.round(factor * 100) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        zoomLabel.setText("Zoom  " + Math.round(initialZoom * 100) + "%");
        panel.addView(zoom);

        ScrollView previewScroll = new ScrollView(this);
        previewScroll.setFillViewport(false);
        previewScroll.addView(panel);
        new FullScreenDialogBuilder(this).setTitle("Keep wallpaper preview").setView(previewScroll)
            .setPositiveButton("Apply", (dialog, which) -> {
                String fit = fits.getCheckedRadioButtonId() == ids[1] ? "fit" :
                    (fits.getCheckedRadioButtonId() == ids[2] ? "stretch" : "crop");
                float factor = 0.5f + zoom.getProgress() * 0.015f;
                try {
                    if (!editingSavedImage) {
                        try (InputStream in = getContentResolver().openInputStream(imageUri);
                             OutputStream out = new FileOutputStream(new File(getFilesDir(), "kept_wallpaper.png"))) {
                            if (in == null) throw new IOException("The selected image could not be opened.");
                            byte[] buffer = new byte[8192]; int count;
                            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                        }
                    }
                    prefs.edit().putBoolean("keep_wallpaper", true)
                        .putString("kept_wallpaper_fit", fit)
                        .putFloat("kept_wallpaper_zoom", factor).apply();
                    Toast.makeText(this, "Wallpaper saved for restoration after restart.", Toast.LENGTH_LONG).show();
                    recreate();
                } catch (Exception e) {
                    prefs.edit().putBoolean("keep_wallpaper", false).apply();
                    showError("Could not save that wallpaper image: " + e.getMessage());
                }
            }).setNegativeButton("Cancel", (dialog, which) -> {
                if (!editingSavedImage) prefs.edit().putBoolean("keep_wallpaper", false).apply();
            }).show();
    }

    private void chooseKeptWallpaperFit() {
        String[] choices={"Fill / crop","Fit entire image","Stretch"};
        String current=prefs.getString("kept_wallpaper_fit","crop");
        int selected="fit".equals(current)?1:("stretch".equals(current)?2:0);
        new FullScreenDialogBuilder(this).setTitle("Kept wallpaper appearance")
            .setSingleChoiceItems(choices,selected,(dialog,which)->{
                String value=which==1?"fit":(which==2?"stretch":"crop");
                prefs.edit().putString("kept_wallpaper_fit",value).apply();
                dialog.dismiss();
            }).setNegativeButton("Later",null).show();
    }

    private void showWallpaperPreview(Uri imageUri) {
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(imageUri)) {
            if(in==null) throw new IOException("Could not open the selected image.");
            android.graphics.BitmapFactory.decodeStream(in,null,bounds);
        } catch(Exception e) { showError("Could not inspect this image: "+e.getMessage()); return; }
        int sample=1, w=Math.max(1,getResources().getDisplayMetrics().widthPixels), h=Math.max(1,getResources().getDisplayMetrics().heightPixels);
        while(bounds.outWidth/sample>w*2 || bounds.outHeight/sample>h*2) sample*=2;
        android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options(); opts.inSampleSize=sample;
        android.graphics.Bitmap bitmap;
        try(InputStream in=getContentResolver().openInputStream(imageUri)) { bitmap=in==null?null:android.graphics.BitmapFactory.decodeStream(in,null,opts); }
        catch(Exception e) { bitmap=null; }
        if(bitmap==null){showError("Could not preview this image.");return;}
        LinearLayout panel=new LinearLayout(this); panel.setPadding(dp(12),dp(8),dp(12),dp(8)); panel.setOrientation(LinearLayout.VERTICAL);
        ImageView preview=new ImageView(this); preview.setImageBitmap(bitmap); preview.setBackgroundColor(Color.BLACK);
        String saved=prefs.getString("wallpaper_fit","crop");
        preview.setScaleType("fit".equals(saved)?ImageView.ScaleType.FIT_CENTER:
            ("stretch".equals(saved)?ImageView.ScaleType.FIT_XY:ImageView.ScaleType.CENTER_CROP));
        panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));
        TextView hint=new TextView(this); hint.setText("Preview how the image will appear behind your apps. Choose the fit below.");
        hint.setTextColor(ThemeUtils.text(this)); hint.setPadding(0,dp(8),0,dp(8)); panel.addView(hint);
        String[] fits={"Fill / crop","Fit entire image","Stretch"};
        int selected="fit".equals(saved)?1:("stretch".equals(saved)?2:0);
        RadioGroup group=new RadioGroup(this); group.setOrientation(RadioGroup.VERTICAL);
        final int[] fitIds={View.generateViewId(),View.generateViewId(),View.generateViewId()};
        for(int i=0;i<fits.length;i++){RadioButton rb=new RadioButton(this);rb.setText(fits[i]);rb.setTextColor(ThemeUtils.text(this));rb.setId(fitIds[i]);group.addView(rb);if(i==selected)rb.setChecked(true);}
        group.setOnCheckedChangeListener((g,id)->preview.setScaleType(id==fitIds[1]?ImageView.ScaleType.FIT_CENTER:(id==fitIds[2]?ImageView.ScaleType.FIT_XY:ImageView.ScaleType.CENTER_CROP)));
        panel.addView(group);
        AlertDialog dialog=new FullScreenDialogBuilder(this).setTitle("Wallpaper preview").setView(panel)
            .setPositiveButton("Apply",(d,which)->{
                String fit=group.getCheckedRadioButtonId()==fitIds[1]?"fit":(group.getCheckedRadioButtonId()==fitIds[2]?"stretch":"crop");
                try {
                    File savedImage = new File(getFilesDir(), "custom_background.png");
                    try (InputStream input = getContentResolver().openInputStream(imageUri);
                         OutputStream output = new FileOutputStream(savedImage)) {
                        if (input == null) throw new IOException("The selected image could not be opened.");
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        output.flush();
                    }
                    prefs.edit().putString("custom_background_path", savedImage.getAbsolutePath())
                        .remove("custom_background_uri").putString("wallpaper_fit",fit)
                        .putInt("background",4).apply();
                    recreate();
                } catch(Exception e){showError("Could not save that wallpaper: "+e.getMessage());}
            }).setNegativeButton("Cancel",null).create();
        dialog.show();
    }

    private void showError(String message) {
        new FullScreenDialogBuilder(this).setMessage(message).setPositiveButton("OK", null).show();
    }
}
