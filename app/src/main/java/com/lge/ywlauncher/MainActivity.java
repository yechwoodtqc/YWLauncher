package com.lge.ywlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root, appsArea, toolbar;
    private ScrollView scroll;
    private ImageButton settingsButton;
    private TextView pageIndicator, selectionCount;
    private LinearLayout pageDots;
    private Button hideSelected, uninstallSelected, cancelSelection;
    private final ArrayList<ResolveInfo> apps = new ArrayList<>();
    private final LinkedHashSet<String> selectedApps = new LinkedHashSet<>();
    private int currentPage = 0;
    private String appliedTheme;
    private int appliedAccent;
    private final ArrayList<String> uninstallQueue = new ArrayList<>();
    private String currentUninstallPackage;
    private boolean processingUninstalls;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        appliedTheme = getSharedPreferences("launcher", 0).getString("theme_mode", "system");
        appliedAccent = ThemeUtils.accent(this);
        build();
        loadApps();
        scroll.post(() -> { if (!isFinishing()) { renderApps(); focusFirstApp(); } });
        postLaunchNotification();
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(2), dp(2), dp(2), dp(2));
        applyBackground();

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);

        pageIndicator = new TextView(this);
        pageIndicator.setTextColor(ThemeUtils.secondaryText(this));
        pageIndicator.setTextSize(12);
        pageIndicator.setGravity(Gravity.CENTER);
        toolbar.addView(pageIndicator, new LinearLayout.LayoutParams(dp(58), -1));

        View spacer = new View(this);
        toolbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        selectionCount = new TextView(this);
        selectionCount.setTextColor(ThemeUtils.text(this));
        selectionCount.setTextSize(13);
        toolbar.addView(selectionCount);

        hideSelected = new Button(this);
        hideSelected.setText("Hide");
        hideSelected.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_hide_modern, 0, 0, 0);
        hideSelected.setCompoundDrawablePadding(dp(4));
        hideSelected.setOnClickListener(v -> hideSelectedApps());
        ThemeUtils.tint(hideSelected, this);
        toolbar.addView(hideSelected, new LinearLayout.LayoutParams(-2, dp(42)));

        uninstallSelected = new Button(this);
        uninstallSelected.setText("Uninstall");
        uninstallSelected.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_uninstall_modern, 0, 0, 0);
        uninstallSelected.setCompoundDrawablePadding(dp(4));
        uninstallSelected.setOnClickListener(v -> uninstallSelectedApps());
        ThemeUtils.tint(uninstallSelected, this);
        toolbar.addView(uninstallSelected, new LinearLayout.LayoutParams(-2, dp(42)));

        cancelSelection = new Button(this);
        cancelSelection.setText("Cancel");
        cancelSelection.setOnClickListener(v -> clearSelection());
        toolbar.addView(cancelSelection, new LinearLayout.LayoutParams(-2, dp(42)));

        settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings_modern);
        settingsButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        settingsButton.setPadding(dp(7), dp(7), dp(7), dp(7));
        settingsButton.setContentDescription("Settings");
        settingsButton.setBackground(ThemeUtils.focusOutlineBackground(this));
        settingsButton.setFocusable(true);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(54), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        appsArea = new LinearLayout(this);
        appsArea.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(appsArea, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageDots = new LinearLayout(this);
        pageDots.setGravity(Gravity.CENTER);
        pageDots.setOrientation(LinearLayout.HORIZONTAL);
        pageDots.setPadding(0, dp(3), 0, dp(3));
        root.addView(pageDots, new LinearLayout.LayoutParams(-1, dp(18)));
        setContentView(root);
        updateSelectionToolbar();
    }

    private void applyBackground() {
        int background = getSharedPreferences("launcher", 0).getInt("background", 0);
        int custom = getSharedPreferences("launcher", 0).getInt("custom_background", Color.rgb(25, 35, 48));
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (background == 1) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(Color.rgb(18, 48, 78));
        } else if (background == 2) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(Color.BLACK);
        } else if (background == 3) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(custom);
        } else if (background == 4) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            boolean applied = false;
            String imagePath = getSharedPreferences("launcher", 0).getString("custom_background_path", "");
            if (imagePath != null && !imagePath.isEmpty()) {
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(imagePath, bounds);
                    int targetW = Math.max(1, getResources().getDisplayMetrics().widthPixels);
                    int targetH = Math.max(1, getResources().getDisplayMetrics().heightPixels);
                    int sample = 1;
                    while (bounds.outWidth > 0 && bounds.outHeight > 0 && (bounds.outWidth / sample > targetW * 2 || bounds.outHeight / sample > targetH * 2)) sample *= 2;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
                    if (bitmap != null) {
                        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                        drawable.setGravity(Gravity.FILL);
                        root.setBackground(drawable);
                        applied = true;
                    }
                } catch (Throwable ignored) { }
            }
            String imageUri = getSharedPreferences("launcher", 0).getString("custom_background_uri", "");
            if (!applied && imageUri != null && !imageUri.isEmpty()) {
                try {
                    Uri uri = Uri.parse(imageUri);
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input != null) BitmapFactory.decodeStream(input, null, bounds);
                    }
                    int targetW = Math.max(1, getResources().getDisplayMetrics().widthPixels);
                    int targetH = Math.max(1, getResources().getDisplayMetrics().heightPixels);
                    int sample = 1;
                    while (bounds.outWidth / sample > targetW * 2 || bounds.outHeight / sample > targetH * 2) sample *= 2;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input != null) {
                            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
                            if (bitmap != null) {
                                BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                                drawable.setGravity(Gravity.FILL);
                                root.setBackground(drawable);
                                applied = true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (!applied) root.setBackgroundColor(custom);
        } else if (background == 5) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundResource(R.drawable.bg_exalt_gradient);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }

    private ArrayList<ResolveInfo> queryApps() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<ResolveInfo> result = new ArrayList<>(getPackageManager().queryIntentActivities(query, 0));
        for (int i = result.size() - 1; i >= 0; i--) {
            if (MainActivity.class.getName().equals(result.get(i).activityInfo.name)) result.remove(i);
        }
        return result;
    }

    private Set<String> defaultIncludedActivities(List<ResolveInfo> available) {
        Set<String> names = new HashSet<>();
        for (ResolveInfo item : available) {
            if ((item.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) names.add(item.activityInfo.name);
        }
        return names;
    }

    private Set<String> normalizedIncludedActivities(Set<String> saved, List<ResolveInfo> available) {
        if (saved == null) return defaultIncludedActivities(available);
        Set<String> result = new HashSet<>();
        for (ResolveInfo item : available) {
            if (saved.contains(item.activityInfo.name) || saved.contains(item.activityInfo.packageName)) result.add(item.activityInfo.name);
        }
        return result;
    }

    private Map<String, Integer> orderedPackageIndex() {
        Map<String, Integer> result = new HashMap<>();
        String raw = getSharedPreferences("launcher", 0).getString("ordered_apps", "");
        if (raw != null) {
            String[] values = raw.split("\\n");
            for (int i = 0; i < values.length; i++) if (!values[i].trim().isEmpty() && !result.containsKey(values[i])) result.put(values[i], i);
        }
        return result;
    }

    private void loadApps() {
        List<ResolveInfo> found = queryApps();
        Map<String, Integer> order = orderedPackageIndex();
        Collections.sort(found, (a, b) -> {
            Integer ai = order.get(a.activityInfo.name), bi = order.get(b.activityInfo.name);
            if (ai != null || bi != null) {
                if (ai == null) return 1;
                if (bi == null) return -1;
                int compared = ai.compareTo(bi);
                if (compared != 0) return compared;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(), b.loadLabel(getPackageManager()).toString());
        });
        android.content.SharedPreferences appPrefs = getSharedPreferences("launcher", 0);
        Set<String> savedIncluded = appPrefs.getStringSet("included", null);
        Set<String> included = normalizedIncludedActivities(savedIncluded, found);
        boolean hadKnownApps = appPrefs.contains("known_apps");
        Set<String> known = new HashSet<>(appPrefs.getStringSet("known_apps", new HashSet<String>()));
        boolean changed = false;
        for (ResolveInfo app : found) {
            String name = app.activityInfo.name;
            if (savedIncluded == null) { included.add(name); changed = true; }
            else if (hadKnownApps && !known.contains(name)) { included.add(name); changed = true; }
            known.add(name);
        }
        if (changed || savedIncluded == null) appPrefs.edit().putStringSet("included", new HashSet<>(included)).apply();
        appPrefs.edit().putStringSet("known_apps", known).apply();
        apps.clear();
        for (ResolveInfo app : found) if (included.contains(app.activityInfo.name)) apps.add(app);
        selectedApps.retainAll(activityNames(apps));
        renderApps();
    }

    private Set<String> activityNames(List<ResolveInfo> source) {
        Set<String> names = new HashSet<>();
        for (ResolveInfo app : source) names.add(app.activityInfo.name);
        return names;
    }

    private void renderApps() {
        if (appsArea == null) return;
        if (pageDots != null) pageDots.setVisibility(View.GONE);
        appsArea.removeAllViews();
        boolean grid = "grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"));
        pageIndicator.setVisibility(View.GONE);
        if (apps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No apps selected. Open Settings to choose apps.");
            empty.setTextSize(14); empty.setTextColor(ThemeUtils.text(this)); empty.setGravity(Gravity.CENTER);
            appsArea.addView(empty, new LinearLayout.LayoutParams(-1, -1));
            return;
        }
        if (!grid) {
            currentPage = 0;
            for (ResolveInfo app : apps) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(60));
                appsArea.addView(createAppItem(app, false, 60), lp);
            }
            return;
        }
        int rows = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", 3), 2, 6);
        int columns = clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        int pageSize = rows * columns;
        int pageCount = Math.max(1, (apps.size() + pageSize - 1) / pageSize);
        currentPage = clamp(currentPage, 0, pageCount - 1);
        pageIndicator.setText("");
        renderPageDots(pageCount);
        int first = currentPage * pageSize;
        int end = Math.min(apps.size(), first + pageSize);
        int cellHeightDp = 64;
        if (scroll.getHeight() > 0) {
            int available = Math.round(scroll.getHeight() / getResources().getDisplayMetrics().density);
            cellHeightDp = Math.max(28, available / rows);
        }
        int index = first;
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setWeightSum(columns);
            for (int col = 0; col < columns; col++) {
                if (index < end) {
                    LinearLayout item = createAppItem(apps.get(index++), true, cellHeightDp);
                    row.addView(item, new LinearLayout.LayoutParams(0, 0, 1));
                    ((LinearLayout.LayoutParams)item.getLayoutParams()).height = -1;
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1));
                }
            }
            appsArea.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private void renderPageDots(int count) {
        if (pageDots == null) return;
        pageDots.removeAllViews();
        boolean grid = "grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"));
        pageDots.setVisibility(grid && count > 1 ? View.VISIBLE : View.GONE);
        if (!grid || count <= 1) return;
        for (int i = 0; i < count; i++) {
            final int page = i;
            TextView dot = new TextView(this);
            dot.setText(i == currentPage ? "●" : "•");
            dot.setTextSize(i == currentPage ? 12 : 10);
            dot.setTextColor(i == currentPage ? ThemeUtils.accent(this) : ThemeUtils.secondaryText(this));
            if (i == currentPage) dot.setTypeface(null, android.graphics.Typeface.BOLD);
            dot.setGravity(Gravity.CENTER);
            dot.setContentDescription("Page " + (i + 1));
            dot.setFocusable(false);
            dot.setPadding(dp(4), 0, dp(4), 0);
            dot.setOnClickListener(v -> {
                int delta = page - currentPage;
                if (delta != 0) changePage(delta, 0);
            });
            pageDots.addView(dot, new LinearLayout.LayoutParams(-2, -1));
        }
    }

    private void focusFirstApp() {
        if (appsArea.getChildCount() == 0) return;
        View first = appsArea.getChildAt(0);
        if (first instanceof ViewGroup) {
            ViewGroup row = (ViewGroup) first;
            for (int i = 0; i < row.getChildCount(); i++) {
                View candidate = row.getChildAt(i);
                if (candidate.isFocusable() && candidate.isEnabled() && candidate.getVisibility() == View.VISIBLE) { candidate.requestFocus(); return; }
            }
        } else if (first.isFocusable()) first.requestFocus();
    }

    private void changePage(int delta, int targetColumn) {
        currentPage = clamp(currentPage + delta, 0, pageCount() - 1);
        if (appsArea != null) {
            appsArea.animate().cancel();
            appsArea.setAlpha(0.72f);
            appsArea.setTranslationX(delta > 0 ? dp(14) : -dp(14));
        }
        renderApps();
        if (appsArea != null) appsArea.animate().alpha(1f).translationX(0f).setDuration(180).start();
        int rows = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", 3), 2, 6);
        int columns = clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        int index = currentPage * rows * columns + clamp(targetColumn, 0, columns - 1);
        if (appsArea.getChildCount() > 0 && appsArea.getChildAt(0) instanceof ViewGroup) {
            ViewGroup firstRow = (ViewGroup) appsArea.getChildAt(0);
            View target = null;
            int rowIndex = Math.min(rows - 1, Math.max(0, (index - currentPage * rows * columns) / columns));
            if (rowIndex < appsArea.getChildCount()) {
                ViewGroup row = (ViewGroup) appsArea.getChildAt(rowIndex);
                int col = Math.min(columns - 1, Math.max(0, index % columns));
                if (col < row.getChildCount()) target = row.getChildAt(col);
            }
            if (target != null && target.isFocusable()) target.requestFocus(); else focusFirstApp();
        }
    }

    private int pageCount() {
        int size = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", 3), 2, 6)
            * clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        return Math.max(1, (apps.size() + size - 1) / size);
    }

    private LinearLayout createAppItem(ResolveInfo app, boolean grid, int cellHeightDp) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(grid ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        item.setPadding(dp(3), dp(2), dp(3), dp(2));
        item.setFocusable(true); item.setClickable(true);
        item.setBackground(ThemeUtils.focusBackground(this));
        int iconSize = clamp(getSharedPreferences("launcher", 0).getInt("icon_size", 40), 18, 56);
        boolean showLabel = !grid || cellHeightDp >= 42;
        if (grid) iconSize = Math.min(iconSize, Math.max(18, cellHeightDp - (showLabel ? 22 : 4)));
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(app.loadIcon(getPackageManager())); }
        catch (Exception ignored) { icon.setImageResource(android.R.drawable.sym_def_app_icon); }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(grid ? iconSize : Math.min(iconSize, 44)), dp(grid ? iconSize : Math.min(iconSize, 44)));
        if (!grid) iconParams.setMargins(dp(8), 0, dp(12), 0);
        item.addView(icon, iconParams);
        TextView label = new TextView(this);
        label.setText(app.loadLabel(getPackageManager()));
        label.setTextColor(ThemeUtils.text(this)); label.setTextSize(grid ? 11 : 14);
        label.setGravity(grid ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(2), dp(1), dp(2), dp(1));
        if (!showLabel) label.setVisibility(View.GONE);
        item.addView(label, grid ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
        String name = app.activityInfo.name;
        item.setTag(name);
        item.setSelected(selectedApps.contains(name));
        item.setOnFocusChangeListener((view, focused) -> {
            boolean selected = selectedApps.contains(name);
            label.setTextColor(focused || selected ? ThemeUtils.focusText(this) : ThemeUtils.text(this));
            icon.setAlpha(focused || selected ? 1.0f : 0.94f);
            item.setBackground(appItemFocusBackground(focused, selected));
        });
        item.setOnClickListener(v -> {
            if (!selectedApps.isEmpty()) toggleSelection(name);
            else launchApp(app);
        });
        item.setOnLongClickListener(v -> { toggleSelection(name); return true; });
        if (selectedApps.contains(name)) item.setBackground(appItemFocusBackground(item.hasFocus(), true));
        return item;
    }

    private android.graphics.drawable.Drawable appItemFocusBackground(boolean focused, boolean selected) {
        if (!focused && !selected) return ThemeUtils.focusBackground(this);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(dp(10));
        boolean outline = getSharedPreferences("launcher", 0).getBoolean("focus_outline_only", false);
        if (focused && outline) {
            shape.setColor(selected ? 0x4426a7ff : Color.TRANSPARENT);
            shape.setStroke(dp(3), selected ? Color.WHITE : ThemeUtils.accent(this));
        } else if (focused) {
            shape.setColor(ThemeUtils.accent(this));
            shape.setStroke(dp(2), selected ? Color.WHITE : ThemeUtils.accent(this));
        } else {
            shape.setColor(0x6626a7ff);
            shape.setStroke(dp(2), ThemeUtils.accent(this));
        }
        return shape;
    }

    private void toggleSelection(String name) {
        if (selectedApps.contains(name)) selectedApps.remove(name); else selectedApps.add(name);
        updateSelectionToolbar();
        renderApps();
        appsArea.post(() -> {
            View target = findAppItem(appsArea, name);
            if (target != null) target.requestFocus();
        });
    }

    private View findAppItem(ViewGroup parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (name.equals(child.getTag())) return child;
            if (child instanceof ViewGroup) {
                View found = findAppItem((ViewGroup) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void clearSelection() { selectedApps.clear(); updateSelectionToolbar(); renderApps(); }

    private void updateSelectionToolbar() {
        boolean selecting = !selectedApps.isEmpty();
        if (selectionCount != null) {
            selectionCount.setVisibility(selecting ? View.VISIBLE : View.GONE);
            selectionCount.setText(selectedApps.size() + " selected");
        }
        if (hideSelected != null) hideSelected.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (uninstallSelected != null) uninstallSelected.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (cancelSelection != null) cancelSelection.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (settingsButton != null) settingsButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
    }

    private void hideSelectedApps() {
        Set<String> saved = getSharedPreferences("launcher", 0).getStringSet("included", null);
        Set<String> included = normalizedIncludedActivities(saved, queryApps());
        included.removeAll(selectedApps);
        getSharedPreferences("launcher", 0).edit().putStringSet("included", included).apply();
        selectedApps.clear(); currentPage = 0; loadApps(); updateSelectionToolbar();
    }

    private void uninstallSelectedApps() {
        ArrayList<String> packages = new ArrayList<>();
        for (ResolveInfo app : apps) {
            if (selectedApps.contains(app.activityInfo.name) && !packages.contains(app.activityInfo.packageName)
                && !app.activityInfo.packageName.equals(getPackageName())) packages.add(app.activityInfo.packageName);
        }
        if (packages.isEmpty()) {
            Toast.makeText(this, "The selected apps cannot be uninstalled from here.", Toast.LENGTH_LONG).show();
            return;
        }
        uninstallQueue.clear();
        uninstallQueue.addAll(packages);
        processingUninstalls = true;
        clearSelection();
        launchNextUninstall();
    }

    private void launchNextUninstall() {
        if (uninstallQueue.isEmpty()) {
            processingUninstalls = false;
            currentUninstallPackage = null;
            Toast.makeText(this, "Finished processing selected apps.", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUninstallPackage = uninstallQueue.remove(0);
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + currentUninstallPackage)));
        } catch (Exception e) {
            processingUninstalls = false;
            currentUninstallPackage = null;
            uninstallQueue.clear();
            Toast.makeText(this, "Could not open Android's uninstall screen.", Toast.LENGTH_LONG).show();
        }
    }

    private void launchApp(ResolveInfo app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(app.activityInfo.packageName, app.activityInfo.name);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) { Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show(); }
    }

    private void postLaunchNotification() {
        SharedPreferences prefs = getSharedPreferences("launcher", 0);
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (!prefs.getBoolean("notify", true)) { manager.cancel(42); return; }
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("yw_launcher", "Launcher access", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pending = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder notification = android.os.Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "yw_launcher") : new Notification.Builder(this);
        boolean showIcon = prefs.getBoolean("status_icon", false);
        notification.setSmallIcon(showIcon ? R.drawable.ic_launcher_notification : R.drawable.ic_notification_hidden)
            .setContentTitle("YW Launcher").setContentText("Press to open your app launcher")
            .setContentIntent(pending).setAutoCancel(false).setOngoing(true);
        if (!showIcon) notification.setVisibility(Notification.VISIBILITY_SECRET);
        manager.notify(42, notification.build());
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            try {
                Intent dial = new Intent(Intent.ACTION_DIAL);
                dial.setData(Uri.parse("tel:"));
                startActivity(dial);
                return true;
            } catch (Exception ignored) { }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
        if ("grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"))
            && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof String) {
                int index = -1;
                for (int i = 0; i < apps.size(); i++) if (apps.get(i).activityInfo.name.equals(focused.getTag())) { index = i; break; }
                int columns = clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
                if (index >= 0 && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index % columns == columns - 1 && currentPage + 1 < pageCount()) {
                    changePage(1, 0); return true;
                }
                if (index >= 0 && keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index % columns == 0 && currentPage > 0) {
                    changePage(-1, columns - 1); return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onResume() {
        super.onResume();
        if (root == null) return;
        String mode = getSharedPreferences("launcher", 0).getString("theme_mode", "system");
        int accent = ThemeUtils.accent(this);
        if (!Objects.equals(appliedTheme, mode) || appliedAccent != accent) { recreate(); return; }
        applyBackground(); loadApps(); postLaunchNotification();
        if (processingUninstalls && currentUninstallPackage != null) {
            boolean stillInstalled = true;
            try { getPackageManager().getPackageInfo(currentUninstallPackage, 0); }
            catch (PackageManager.NameNotFoundException e) { stillInstalled = false; }
            if (stillInstalled) {
                processingUninstalls = false;
                currentUninstallPackage = null;
                uninstallQueue.clear();
                Toast.makeText(this, "Uninstall canceled. Select the remaining apps to continue.", Toast.LENGTH_LONG).show();
            } else {
                currentUninstallPackage = null;
                launchNextUninstall();
            }
        }
    }
}
