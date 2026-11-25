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
    private Button hideSelected, uninstallSelected;
    private ImageButton cancelSelection;
    private final ArrayList<ResolveInfo> apps = new ArrayList<>();
    private final LinkedHashSet<String> selectedApps = new LinkedHashSet<>();
    private int currentPage = 0;
    private String appliedTheme;
    private int appliedAccent;
    private final ArrayList<String> uninstallQueue = new ArrayList<>();
    private String currentUninstallPackage;
    private boolean processingUninstalls;
    private float pageTouchDownX, pageTouchDownY;
    private boolean pagingGesture;
    private int pageTouchSlop = 8;
    private boolean resetGridOnNextResume;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pageTouchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        resetGridOnNextResume = state == null;
        ThemeUtils.apply(this);
        appliedTheme = getSharedPreferences("launcher", 0).getString("theme_mode", "system");
        appliedAccent = ThemeUtils.accent(this);
        build();
        updateRecentTaskDescription();
        getWindow().getDecorView().post(() -> {
            if (!isFinishing()) updateRecentTaskDescription();
        });
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
        selectionCount.setVisibility(View.GONE);

        hideSelected = new Button(this);
        hideSelected.setText("");
        hideSelected.setContentDescription("Hide selected apps");
        hideSelected.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_hide_modern, 0, 0, 0);
        hideSelected.setPadding(dp(8), 0, dp(8), 0);
        hideSelected.setOnClickListener(v -> hideSelectedApps());
        hideSelected.setBackgroundColor(Color.TRANSPARENT);
        hideSelected.setPadding(dp(8), 0, dp(8), 0);
        hideSelected.setOnFocusChangeListener((v, focused) -> hideSelected.setBackground(focused ? ThemeUtils.focusOutlineBackground(this) : new ColorDrawable(Color.TRANSPARENT)));
        hideSelected.setFocusable(true);
        toolbar.addView(hideSelected, new LinearLayout.LayoutParams(dp(40), dp(42)));

        uninstallSelected = new Button(this);
        uninstallSelected.setText("");
        uninstallSelected.setContentDescription("Uninstall selected apps");
        uninstallSelected.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_uninstall_modern, 0, 0, 0);
        uninstallSelected.setPadding(dp(8), 0, dp(8), 0);
        uninstallSelected.setOnClickListener(v -> uninstallSelectedApps());
        uninstallSelected.setBackgroundColor(Color.TRANSPARENT);
        uninstallSelected.setPadding(dp(8), 0, dp(8), 0);
        uninstallSelected.setOnFocusChangeListener((v, focused) -> uninstallSelected.setBackground(focused ? ThemeUtils.focusOutlineBackground(this) : new ColorDrawable(Color.TRANSPARENT)));
        uninstallSelected.setFocusable(true);
        toolbar.addView(uninstallSelected, new LinearLayout.LayoutParams(dp(40), dp(42)));

        settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings_modern);
        settingsButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        settingsButton.setPadding(dp(7), dp(7), dp(7), dp(7));
        settingsButton.setContentDescription("Settings");
        settingsButton.setBackground(ThemeUtils.focusOutlineBackground(this));
        settingsButton.setFocusable(true);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(40), dp(42)));

        // Keep the clear-selection X as the final control, flush to the
        // right edge of the toolbar on narrow flip-phone screens.
        cancelSelection = new ImageButton(this);
        cancelSelection.setImageResource(R.drawable.ic_close_modern);
        cancelSelection.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cancelSelection.setContentDescription("Clear selection");
        cancelSelection.setPadding(dp(8), dp(8), dp(8), dp(8));
        cancelSelection.setBackground(ThemeUtils.focusOutlineBackground(this));
        cancelSelection.setOnClickListener(v -> clearSelection());
        toolbar.addView(cancelSelection, new LinearLayout.LayoutParams(dp(42), dp(42)));

        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));

        scroll = new ScrollView(this) {
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    pageTouchDownX = event.getX();
                    pageTouchDownY = event.getY();
                    pagingGesture = false;
                } else if (action == android.view.MotionEvent.ACTION_MOVE) {
                    float dx = event.getX() - pageTouchDownX;
                    float dy = event.getY() - pageTouchDownY;
                    if (Math.abs(dx) > pageTouchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        if ("grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"))
                            && pageCount() > 1) {
                            pagingGesture = true;
                            return true;
                        }
                    }
                }
                return super.onInterceptTouchEvent(event);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP && pagingGesture) {
                    float dx = event.getX() - pageTouchDownX;
                    int direction = dx < 0 ? 1 : -1;
                    pagingGesture = false;
                    if ((direction > 0 && currentPage < pageCount() - 1)
                        || (direction < 0 && currentPage > 0)) {
                        changePage(direction, 0, direction > 0 ? 0 :
                            clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6) - 1);
                    }
                    return true;
                }
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) pagingGesture = false;
                return pagingGesture || super.onTouchEvent(event);
            }
        };
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
        root.addView(pageDots, new LinearLayout.LayoutParams(-1, dp(26)));
        setContentView(root);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        settingsButton.setBackground(ThemeUtils.focusBackground(this));
        settingsButton.setOnFocusChangeListener((v, focused) -> settingsButton.setBackground(
            focused ? ThemeUtils.focusOutlineBackground(this) : ThemeUtils.focusBackground(this)));
        updateSelectionToolbar();
    }

    private int defaultIconSize() {
        String model = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).toLowerCase(Locale.US);
        return (model.contains("exalt") || model.contains("vn220") || model.contains("lg-ax")) ? 48 : 40;
    }

    private int defaultBackgroundForDevice() {
        String model = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).toLowerCase(Locale.US);
        if (model.contains("l125dl") || model.contains("l125") || model.contains("classic")) return 5;
        if (model.contains("vn220") || model.contains("exalt") || model.contains("lg-ax")) return 5;
        return 0;
    }

    private void applyBackground() {
        int background = getSharedPreferences("launcher", 0).getInt("background", defaultBackgroundForDevice());
        // Treat the legacy value 0 as "device default". Older builds saved 0
        // for the initial wallpaper setting, preventing model-specific LG
        // Classic/Exalt defaults from ever being applied after an upgrade.
        if (background == 0) background = defaultBackgroundForDevice();
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
                        SharedPreferences wallpaperPrefs = getSharedPreferences("launcher", 0);
                        String fit = wallpaperPrefs.getString("wallpaper_fit", "crop");
                        root.setBackground(new WallpaperDrawable(bitmap, fit,
                            wallpaperPrefs.getFloat("custom_background_zoom", 1f),
                            wallpaperPrefs.getFloat("custom_background_rotation", 0f)));
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
                                SharedPreferences wallpaperPrefs = getSharedPreferences("launcher", 0);
                                String fit = wallpaperPrefs.getString("wallpaper_fit", "crop");
                                root.setBackground(new WallpaperDrawable(bitmap, fit,
                                    wallpaperPrefs.getFloat("custom_background_zoom", 1f),
                                    wallpaperPrefs.getFloat("custom_background_rotation", 0f),
                                    wallpaperPrefs.getFloat("custom_background_pan_x", 0f),
                                    wallpaperPrefs.getFloat("custom_background_pan_y", 0f)));
                                applied = true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (!applied) root.setBackgroundColor(custom);
        } else if (background == 5) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackground(new ExaltBackgroundDrawable());
        } else if (background >= 8 && background <= 17) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackground(new PresetBackgroundDrawable(background));
        } else if (background == 6) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackground(new ExaltBackgroundDrawable());
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void updateRecentTaskDescription() {
        if (android.os.Build.VERSION.SDK_INT < 21) return;
        int accent = ThemeUtils.accent(this);
        accent = Color.rgb(Color.red(accent), Color.green(accent), Color.blue(accent));
        try {
            // LG's Android 6 Recents build does not consistently fall back to
            // ActivityInfo.icon when TaskDescription has no bitmap. Always
            // provide the actual launcher artwork as an explicit task icon.
            setTaskDescription(new ActivityManager.TaskDescription(
                "YW Launcher", createTaskIcon(R.drawable.ic_launcher), accent));
        } catch (RuntimeException ignored) {
            setTaskDescription(new ActivityManager.TaskDescription("YW Launcher"));
        }
    }

    private android.graphics.Bitmap createTaskIcon(int resourceId) {
        android.graphics.drawable.Drawable drawable = null;
        try {
            drawable = getPackageManager().getActivityIcon(new ComponentName(this, MainActivity.class));
        } catch (Exception activityIconError) {
            try { drawable = getPackageManager().getApplicationIcon(getApplicationInfo()); }
            catch (Exception appIconError) {
                try { drawable = getDrawable(resourceId); }
                catch (Exception ignored) { return null; }
            }
        }
        if (drawable == null) {
            try { drawable = getDrawable(resourceId); }
            catch (Exception ignored) { return null; }
        }
        // Supply a generously sized, density-tagged bitmap. Several Android 6
        // OEM task switchers discard vector/activity icons and only render the
        // bitmap stored in TaskDescription.
        int size = Math.max(128, dp(96));
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.setDensity(getResources().getDisplayMetrics().densityDpi);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }

    private int defaultGridRows() { String m=(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL).toLowerCase(Locale.US); return (m.contains("exalt")||m.contains("vn220")||m.contains("lg-ax"))?4:3; }

    private float prefsFontScale() { return getSharedPreferences("launcher", 0).getInt("font_size_percent", 100) / 100f; }

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
        // Keep the non-system-only default above, but preserve every explicit
        // user choice here, including system apps checked in Launcher Settings.
        for (ResolveInfo item : available) {
            if (saved.contains(item.activityInfo.name) || saved.contains(item.activityInfo.packageName))
                result.add(item.activityInfo.name);
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
            if (savedIncluded == null) { changed = true; }
            else if (hadKnownApps && !known.contains(name)
                && (app.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) { included.add(name); changed = true; }
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
        String layoutMode = getSharedPreferences("launcher", 0).getString("layout_mode", "grid");
        boolean singleGrid = "single_grid".equals(layoutMode);
        boolean grid = !"list".equals(layoutMode);
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
        int columns = clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        int configuredRows = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", defaultGridRows()), 2, 6);
        int rows = singleGrid ? Math.max(1, (apps.size() + columns - 1) / columns) : configuredRows;
        int pageSize = rows * columns;
        int pageCount = singleGrid ? 1 : Math.max(1, (apps.size() + pageSize - 1) / pageSize);
        currentPage = clamp(currentPage, 0, pageCount - 1);
        pageIndicator.setText("");
        renderPageDots(pageCount);
        int first = currentPage * pageSize;
        int end = Math.min(apps.size(), first + pageSize);
        int cellHeightDp = 64;
        if (singleGrid) {
            cellHeightDp = Math.max(72, Math.min(92, 78));
        } else if (scroll.getHeight() > 0) {
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
            appsArea.addView(row, singleGrid
                ? new LinearLayout.LayoutParams(-1, dp(cellHeightDp))
                : new LinearLayout.LayoutParams(-1, 0, 1));
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
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable marker = new android.graphics.drawable.GradientDrawable();
            marker.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            marker.setColor(i == currentPage ? ThemeUtils.accent(this) : Color.WHITE);
            marker.setStroke(dp(2), i == currentPage ? Color.WHITE : 0xff172536);
            dot.setBackground(marker);
            if (android.os.Build.VERSION.SDK_INT >= 21) dot.setElevation(dp(2));
            dot.setContentDescription("Page " + (i + 1));
            dot.setFocusable(false);
            dot.setOnClickListener(v -> {
                int delta = page - currentPage;
                if (delta != 0) changePage(delta, 0, 0);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(i == currentPage ? 13 : 11), dp(i == currentPage ? 13 : 11));
            params.setMargins(dp(5), 0, dp(5), 0);
            pageDots.addView(dot, params);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // A fresh launcher entry always starts at the top of the app grid.
        // Returning from Settings does not call onNewIntent, so its position is preserved.
        resetGridOnNextResume = true;
        currentPage = 0;
        if (scroll != null) scroll.post(() -> {
            scroll.scrollTo(0, 0);
            renderApps();
            focusFirstApp();
        });
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

    private void changePage(int delta, int targetRow, int targetColumn) {
        currentPage = clamp(currentPage + delta, 0, pageCount() - 1);
        if (appsArea != null) {
            appsArea.animate().cancel();
            // Slide the incoming page horizontally in the direction of travel.
            // Keep it fully opaque so the grid feels like one continuous page.
            appsArea.setAlpha(1f);
            appsArea.setTranslationX(delta > 0 ? dp(44) : -dp(44));
        }
        renderApps();
        if (appsArea != null) {
            appsArea.animate()
                .translationX(0f)
                .setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .start();
        }
        int rows = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", defaultGridRows()), 2, 6);
        int columns = clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        int pageStart = currentPage * rows * columns;
        int pageEnd = Math.min(apps.size(), pageStart + rows * columns);
        int index = pageStart + clamp(targetRow, 0, rows - 1) * columns + clamp(targetColumn, 0, columns - 1);
        if (index >= pageEnd) index = Math.max(pageStart, pageEnd - 1);
        int local = index - pageStart, rowIndex = local / columns, col = local % columns;
        final int focusRow = rowIndex;
        final int focusColumn = col;
        if (appsArea != null) appsArea.post(() -> {
            View target = null;
            if (focusRow < appsArea.getChildCount() && appsArea.getChildAt(focusRow) instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) appsArea.getChildAt(focusRow);
                if (focusColumn < row.getChildCount()) target = row.getChildAt(focusColumn);
            }
            if (target != null && target.isFocusable()) target.requestFocus();
            else focusFirstApp();
        });
    }

    private int pageCount() {
        if ("single_grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"))) return 1;
        int size = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", defaultGridRows()), 2, 6)
            * clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        return Math.max(1, (apps.size() + size - 1) / size);
    }

    private LinearLayout createAppItem(ResolveInfo app, boolean grid, int cellHeightDp) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(grid ? Gravity.CENTER : (Gravity.CENTER_VERTICAL | Gravity.LEFT));
        item.setOrientation(grid ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        item.setPadding(dp(3), dp(2), dp(3), dp(2));
        item.setFocusable(true); item.setClickable(true);
        item.setBackground(ThemeUtils.focusBackground(this));
        int iconSize = clamp(getSharedPreferences("launcher", 0).getInt("icon_size", defaultIconSize()), 18, 56);
        boolean showLabel = !grid || cellHeightDp >= 42;
        // Keep icon dimensions constant before and after the first layout pass.
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(app.loadIcon(getPackageManager())); }
        catch (Exception ignored) { icon.setImageResource(android.R.drawable.sym_def_app_icon); }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(grid ? iconSize : Math.min(iconSize, 44)), dp(grid ? iconSize : Math.min(iconSize, 44)));
        if (!grid) iconParams.setMargins(dp(8), 0, dp(12), 0);
        item.addView(icon, iconParams);
        TextView label = new TextView(this);
        label.setText(app.loadLabel(getPackageManager()));
        label.setTextColor(ThemeUtils.text(this)); label.setTextSize((grid ? 11 : 14) * prefsFontScale());
        label.setGravity(grid ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(2), dp(1), dp(2), dp(1));
        if (!showLabel) label.setVisibility(View.GONE);
        item.addView(label, grid ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
        String name = app.activityInfo.name;
        item.setTag(name);
        item.setSelected(selectedApps.contains(name));
        item.setOnFocusChangeListener((view, focused) -> {
            if (focused) getSharedPreferences("launcher", 0).edit()
                .putString("last_focused_app", name).apply();
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
        boolean outline = getSharedPreferences("launcher", 0).getBoolean("focus_outline_only", true);
        if (outline) {
            // Outline mode keeps selected and focused cells on a known surface,
            // so the normal theme text color remains readable.
            shape.setColor(selected ? ThemeUtils.surface(this) : Color.TRANSPARENT);
            shape.setStroke(dp(focused ? 3 : 2), ThemeUtils.accent(this));
        } else {
            // Filled mode uses a solid accent and a contrast-calculated text color.
            shape.setColor(ThemeUtils.accent(this));
            shape.setStroke(dp(2), ThemeUtils.accent(this));
        }
        return shape;
    }

    private void toggleSelection(String name) {
        if (selectedApps.contains(name)) selectedApps.remove(name); else selectedApps.add(name);
        // Update the existing cell in place. Rebuilding appsArea here detaches
        // the focused view and makes Android briefly move focus to the toolbar.
        View target = findAppItem(appsArea, name);
        if (target != null) {
            target.setSelected(selectedApps.contains(name));
            target.setBackground(appItemFocusBackground(target.hasFocus(), selectedApps.contains(name)));
            if (target instanceof ViewGroup) {
                for (int i = 0; i < ((ViewGroup) target).getChildCount(); i++) {
                    View child = ((ViewGroup) target).getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(target.hasFocus() || selectedApps.contains(name)
                            ? ThemeUtils.focusText(this) : ThemeUtils.text(this));
                    }
                    if (child instanceof ImageView) child.setAlpha(target.hasFocus() || selectedApps.contains(name) ? 1f : 0.94f);
                }
            }
        }
        updateSelectionToolbar();
        // Do not request focus again: the original app cell never lost it.
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

    private void clearSelection() {
        selectedApps.clear(); updateSelectionToolbar(); renderApps();
        if (scroll != null) scroll.post(() -> { scroll.scrollTo(0, 0); focusFirstApp(); });
    }

    private void updateSelectionToolbar() {
        boolean selecting = !selectedApps.isEmpty();
        if (selectionCount != null) {
            selectionCount.setVisibility(View.GONE);
            selectionCount.setText(selectedApps.size() + " selected");
        }
        if (hideSelected != null) { hideSelected.setVisibility(selecting ? View.VISIBLE : View.GONE); hideSelected.setFocusable(selecting); }
        if (uninstallSelected != null) { uninstallSelected.setVisibility(selecting ? View.VISIBLE : View.GONE); uninstallSelected.setFocusable(selecting); }
        if (cancelSelection != null) { cancelSelection.setVisibility(selecting ? View.VISIBLE : View.GONE); cancelSelection.setFocusable(selecting); }
        if (settingsButton != null) { settingsButton.setVisibility(selecting ? View.GONE : View.VISIBLE); settingsButton.setFocusable(!selecting); }
    }

    private void hideSelectedApps() {
        final Set<String> toHide = new HashSet<>(selectedApps);
        if (toHide.isEmpty()) return;
        ArrayList<View> disappearing = new ArrayList<>();
        collectAppViews(appsArea, toHide, disappearing);
        for (View view : disappearing) {
            view.animate().alpha(0f).scaleX(0.78f).scaleY(0.78f)
                .setDuration(180).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        }
        appsArea.postDelayed(() -> {
            Set<String> saved = getSharedPreferences("launcher", 0).getStringSet("included", null);
            Set<String> included = normalizedIncludedActivities(saved, queryApps());
            included.removeAll(toHide);
            getSharedPreferences("launcher", 0).edit().putStringSet("included", included).apply();
            selectedApps.clear(); currentPage = 0; loadApps(); updateSelectionToolbar();
            ArrayList<View> arriving = new ArrayList<>();
            collectAppViews(appsArea, null, arriving);
            for (int i=0;i<arriving.size();i++) {
                View view=arriving.get(i);
                view.setAlpha(0f); view.setTranslationY(dp(10));
                view.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(180,i*14))
                    .setDuration(190).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        }, 190);
    }

    private void collectAppViews(View view, Set<String> names, ArrayList<View> output) {
        if (view == null) return;
        Object tag = view.getTag();
        if (tag instanceof String && (names == null || names.contains((String) tag))) {
            output.add(view);
            return;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) collectAppViews(group.getChildAt(i), names, output);
        }
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
            .setLargeIcon(NotificationIconUtils.createLargeIcon(this, R.drawable.ic_launcher))
            .setContentTitle("YW Launcher").setContentText("Press to open your app launcher")
            .setContentIntent(pending).setAutoCancel(false).setOngoing(true);
        manager.notify(42, notification.build());
    }

    private int gridRowForFocusedView(View focused) {
        if (focused == null || appsArea == null) return 0;
        View current = focused;
        while (current.getParent() instanceof View && current.getParent() != appsArea) {
            current = (View) current.getParent();
        }
        if (current.getParent() != appsArea) return 0;
        return Math.max(0, appsArea.indexOfChild(current));
    }

    private boolean handleGridPageNavigation(int keyCode) {
        if (!"grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"))
            || (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT)) return false;
        View focused = getCurrentFocus();
        if (focused == null || !(focused.getTag() instanceof String)) return false;
        int index = -1;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).activityInfo.name.equals(focused.getTag())) { index = i; break; }
        }
        if (index < 0) return false;
        int columns = clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        int actualRow = gridRowForFocusedView(focused);
        int rows = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", defaultGridRows()), 2, 6);
        int pageSize = rows * columns;
        int localIndex = index - currentPage * pageSize;
        int column = localIndex % columns;
        boolean atRightEdge = column == columns - 1
            || index + 1 >= Math.min(apps.size(), (currentPage + 1) * pageSize);
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && atRightEdge) {
            if (currentPage + 1 < pageCount()) changePage(1, Math.max(0, actualRow), 0);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && column == 0) {
            if (currentPage > 0) changePage(-1, Math.max(0, actualRow), columns - 1);
            return true;
        }
        return false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // Intercept the horizontal page-edge move before Android's default
        // focus search can jump to another row on the outgoing page.
        if (event.getAction() == KeyEvent.ACTION_DOWN && handleGridPageNavigation(event.getKeyCode())) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            try {
                Intent dial = new Intent(Intent.ACTION_DIAL);
                char digit = event != null && event.getNumber() != 0 ? event.getNumber() : (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
                dial.setData(Uri.fromParts("tel", String.valueOf(digit), null));
                startActivity(dial);
                return true;
            } catch (Exception ignored) { }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
        // Grid paging is intercepted once in dispatchKeyEvent(), before default focus search.
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && android.os.Build.VERSION.SDK_INT >= 21) {
            updateRecentTaskDescription();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                for (ActivityManager.AppTask task : ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getAppTasks())
                    task.setExcludeFromRecents(false);
            } catch (Exception ignored) { }
            // Re-apply after OEM task metadata updates; LG Android 6 Recents
            // can otherwise replace the launcher task's explicit icon.
            updateRecentTaskDescription();
            getWindow().getDecorView().post(() -> {
                if (!isFinishing()) updateRecentTaskDescription();
            });
        }
        if (root == null) return;
        String mode = getSharedPreferences("launcher", 0).getString("theme_mode", "system");
        int accent = ThemeUtils.accent(this);
        if (!Objects.equals(appliedTheme, mode) || appliedAccent != accent) { recreate(); return; }
        // loadApps() rebuilds the grid. Preserve the focused app by its stable
        // activity name so returning from Settings does not send D-pad focus
        // back to the toolbar or lose the user's position.
        boolean resetGrid = resetGridOnNextResume;
        resetGridOnNextResume = false;
        View focusedBeforeRefresh = getCurrentFocus();
        String focusedAppName = resetGrid ? null : (focusedBeforeRefresh != null
            && focusedBeforeRefresh.getTag() instanceof String
            ? (String) focusedBeforeRefresh.getTag()
            : getSharedPreferences("launcher", 0).getString("last_focused_app", null));
        if (resetGrid) {
            currentPage = 0;
            scroll.scrollTo(0, 0);
        }
        applyBackground();
        loadApps();
        if (resetGrid) {
            scroll.post(() -> { scroll.scrollTo(0, 0); focusFirstApp(); });
        } else if (focusedAppName != null) {
            final String restoreName = focusedAppName;
            appsArea.post(() -> {
                View restored = findAppItem(appsArea, restoreName);
                if (restored != null) restored.requestFocus();
                else focusFirstApp();
            });
        }
        postLaunchNotification();
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
