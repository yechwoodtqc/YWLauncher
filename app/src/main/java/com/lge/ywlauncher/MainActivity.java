package com.lge.ywlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout root, appsArea;
    private ScrollView scroll;
    private ImageButton previousPage, nextPage, settingsButton;
    private TextView pageIndicator;
    private final ArrayList<ResolveInfo> apps = new ArrayList<>();
    private int currentPage = 0;
    private String appliedTheme;
    private int appliedAccent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        appliedTheme = getSharedPreferences("launcher", 0).getString("theme_mode", "system");
        appliedAccent = ThemeUtils.accent(this);
        build();
        loadApps();
        postLaunchNotification();
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(2), dp(4), dp(2));
        applyBackground();

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);

        previousPage = createToolButton(android.R.drawable.ic_media_previous, "Previous page");
        previousPage.setOnClickListener(v -> changePage(-1));
        toolbar.addView(previousPage, new LinearLayout.LayoutParams(dp(44), dp(44)));

        pageIndicator = new TextView(this);
        pageIndicator.setTextColor(ThemeUtils.text(this));
        pageIndicator.setTextSize(12);
        pageIndicator.setGravity(Gravity.CENTER);
        toolbar.addView(pageIndicator, new LinearLayout.LayoutParams(dp(54), -1));

        nextPage = createToolButton(android.R.drawable.ic_media_next, "Next page");
        nextPage.setOnClickListener(v -> changePage(1));
        toolbar.addView(nextPage, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View spacer = new View(this);
        toolbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        settingsButton = createToolButton(android.R.drawable.ic_menu_preferences, "Settings");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(46)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        appsArea = new LinearLayout(this);
        appsArea.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(appsArea, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private ImageButton createToolButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setContentDescription(description);
        button.setBackground(ThemeUtils.focusBackground(this));
        button.setColorFilter(ThemeUtils.text(this));
        button.setFocusable(true);
        return button;
    }

    private void applyBackground() {
        int background = getSharedPreferences("launcher", 0).getInt("background", 0);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (background == 1) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(Color.rgb(18, 48, 78));
        } else if (background == 2) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(Color.BLACK);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            root.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private ArrayList<ResolveInfo> queryApps() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<ResolveInfo> found = new ArrayList<>(getPackageManager().queryIntentActivities(query, 0));
        ArrayList<ResolveInfo> result = new ArrayList<>();
        for (ResolveInfo item : found) {
            if (!MainActivity.class.getName().equals(item.activityInfo.name)) result.add(item);
        }
        return result;
    }

    private Set<String> defaultIncludedActivities(List<ResolveInfo> available) {
        Set<String> names = new HashSet<>();
        for (ResolveInfo item : available) {
            boolean system = (item.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!system) names.add(item.activityInfo.name);
        }
        return names;
    }

    private Set<String> normalizedIncludedActivities(Set<String> saved, List<ResolveInfo> available) {
        if (saved == null) return defaultIncludedActivities(available);
        Set<String> result = new HashSet<>();
        for (ResolveInfo item : available) {
            if (saved.contains(item.activityInfo.name) || saved.contains(item.activityInfo.packageName)) {
                result.add(item.activityInfo.name);
            }
        }
        return result;
    }

    private void loadApps() {
        List<ResolveInfo> found = queryApps();
        final Map<String, Integer> order = orderedPackageIndex();
        Collections.sort(found, (a, b) -> {
            Integer ai = order.get(a.activityInfo.name);
            Integer bi = order.get(b.activityInfo.name);
            if (ai != null || bi != null) {
                if (ai == null) return 1;
                if (bi == null) return -1;
                int compared = ai.compareTo(bi);
                if (compared != 0) return compared;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(
                a.loadLabel(getPackageManager()).toString(),
                b.loadLabel(getPackageManager()).toString());
        });

        Set<String> saved = getSharedPreferences("launcher", 0).getStringSet("included", null);
        Set<String> included = normalizedIncludedActivities(saved, found);
        apps.clear();
        for (ResolveInfo item : found) {
            if (included.contains(item.activityInfo.name)) apps.add(item);
        }
        renderApps();
    }

    private void renderApps() {
        appsArea.removeAllViews();
        boolean grid = "grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"));
        previousPage.setVisibility(grid ? View.VISIBLE : View.GONE);
        nextPage.setVisibility(grid ? View.VISIBLE : View.GONE);
        pageIndicator.setVisibility(grid ? View.VISIBLE : View.GONE);

        if (apps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No apps selected. Open Settings to choose apps.");
            empty.setTextSize(14);
            empty.setTextColor(ThemeUtils.text(this));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            appsArea.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        if (!grid) {
            currentPage = 0;
            for (ResolveInfo app : apps) {
                appsArea.addView(createAppItem(app, false),
                    new LinearLayout.LayoutParams(-1, dp(60)));
            }
            return;
        }

        int rows = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", 3), 2, 6);
        int columns = clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        int pageSize = rows * columns;
        int pageCount = Math.max(1, (apps.size() + pageSize - 1) / pageSize);
        currentPage = clamp(currentPage, 0, pageCount - 1);
        pageIndicator.setText((currentPage + 1) + " / " + pageCount);
        previousPage.setEnabled(currentPage > 0);
        nextPage.setEnabled(currentPage + 1 < pageCount);
        previousPage.setAlpha(currentPage > 0 ? 1.0f : 0.30f);
        nextPage.setAlpha(currentPage + 1 < pageCount ? 1.0f : 0.30f);

        int first = currentPage * pageSize;
        int end = Math.min(apps.size(), first + pageSize);
        int rowsToShow = Math.min(rows, (end - first + columns - 1) / columns);
        int index = first;
        for (int rowIndex = 0; rowIndex < rowsToShow; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int col = 0; col < columns; col++) {
                if (index < end) {
                    LinearLayout item = createAppItem(apps.get(index++), true);
                    row.addView(item, new LinearLayout.LayoutParams(0, dp(64), 1));
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(64), 1));
                }
            }
            appsArea.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
        }
    }

    private int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private void changePage(int delta) {
        currentPage += delta;
        renderApps();
        if (appsArea.getChildCount() > 0 && appsArea.getChildAt(0) instanceof ViewGroup) {
            ViewGroup firstRow = (ViewGroup) appsArea.getChildAt(0);
            if (firstRow.getChildCount() > 0) firstRow.getChildAt(0).requestFocus();
        }
    }

    private LinearLayout createAppItem(ResolveInfo app, boolean grid) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOrientation(grid ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        item.setPadding(dp(3), dp(2), dp(3), dp(2));
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackground(ThemeUtils.focusBackground(this));

        int iconSize = clamp(getSharedPreferences("launcher", 0).getInt("icon_size", 40), 28, 56);
        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(app.loadIcon(getPackageManager()));
        } catch (Exception ignored) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = grid
            ? new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize))
            : new LinearLayout.LayoutParams(dp(Math.min(iconSize, 44)), dp(Math.min(iconSize, 44)));
        if (grid) iconParams.gravity = Gravity.CENTER;
        else iconParams.setMargins(dp(8), 0, dp(12), 0);
        item.addView(icon, iconParams);

        TextView label = new TextView(this);
        label.setText(app.loadLabel(getPackageManager()));
        label.setTextColor(ThemeUtils.text(this));
        label.setTextSize(grid ? 11 : 14);
        label.setGravity(Gravity.CENTER_VERTICAL | (grid ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT));
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(2), dp(1), dp(2), dp(1));
        LinearLayout.LayoutParams labelParams = grid
            ? new LinearLayout.LayoutParams(-1, -2)
            : new LinearLayout.LayoutParams(0, -2, 1);
        item.addView(label, labelParams);

        item.setOnFocusChangeListener((view, focused) -> {
            label.setTextColor(focused ? ThemeUtils.onAccent(this) : ThemeUtils.text(this));
            icon.setAlpha(focused ? 1.0f : 0.95f);
        });
        item.setOnClickListener(v -> launchApp(app));
        item.setOnLongClickListener(v -> {
            showAppOptions(app, item);
            return true;
        });
        return item;
    }

    private void launchApp(ResolveInfo app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(app.activityInfo.packageName, app.activityInfo.name);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch app", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppOptions(ResolveInfo app, View item) {
        String[] options = {"Open", "Uninstall", "Hide from app list"};
        new AlertDialog.Builder(this)
            .setTitle(app.loadLabel(getPackageManager()))
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    launchApp(app);
                } else if (which == 1) {
                    if (app.activityInfo.packageName.equals(getPackageName())) {
                        Toast.makeText(this, "This screen cannot uninstall itself.", Toast.LENGTH_SHORT).show();
                    } else {
                        Intent uninstall = new Intent(Intent.ACTION_DELETE,
                            android.net.Uri.parse("package:" + app.activityInfo.packageName));
                        startActivity(uninstall);
                    }
                } else {
                    SharedPreferences prefs = getSharedPreferences("launcher", 0);
                    Set<String> saved = prefs.getStringSet("included", null);
                    Set<String> included = normalizedIncludedActivities(saved, queryApps());
                    included.remove(app.activityInfo.name);
                    prefs.edit().putStringSet("included", included).apply();
                    currentPage = 0;
                    loadApps();
                }
            }).show();
    }

    private Map<String, Integer> orderedPackageIndex() {
        Map<String, Integer> result = new HashMap<>();
        String raw = getSharedPreferences("launcher", 0).getString("ordered_apps", "");
        if (raw != null && !raw.isEmpty()) {
            String[] values = raw.split("\\n");
            for (int i = 0; i < values.length; i++) {
                if (!values[i].trim().isEmpty() && !result.containsKey(values[i])) result.put(values[i], i);
            }
        }
        return result;
    }

    private void postLaunchNotification() {
        SharedPreferences prefs = getSharedPreferences("launcher", 0);
        if (!prefs.getBoolean("notify", true)) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(42);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel("yw_launcher", "Launcher access", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = android.os.Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "yw_launcher") : new Notification.Builder(this);
        boolean showIcon = prefs.getBoolean("status_icon", false);
        notification.setSmallIcon(showIcon ? android.R.drawable.ic_menu_view : R.drawable.ic_notification_hidden)
            .setContentTitle("YW Launcher")
            .setContentText("Press to open your app launcher")
            .setContentIntent(pending)
            .setAutoCancel(false)
            .setOngoing(true);
        if (!showIcon) notification.setVisibility(Notification.VISIBILITY_SECRET);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(42, notification.build());
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && "grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"))) {
            if (currentPage > 0 && firstFocusedItemInRow()) { changePage(-1); return true; }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && "grid".equals(getSharedPreferences("launcher", 0).getString("layout_mode", "grid"))) {
            if (currentPage + 1 < pageCount()) {
                View focused = getCurrentFocus();
                if (focused != null && focused.getParent() instanceof ViewGroup) {
                    ViewGroup row = (ViewGroup) focused.getParent();
                    if (row.getChildCount() > 0 && row.getChildAt(row.getChildCount() - 1) == focused) {
                        changePage(1); return true;
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean firstFocusedItemInRow() {
        View focused = getCurrentFocus();
        if (focused == null || !(focused.getParent() instanceof ViewGroup)) return false;
        ViewGroup row = (ViewGroup) focused.getParent();
        return row.getChildCount() > 0 && row.getChildAt(0) == focused;
    }

    private int pageCount() {
        int size = clamp(getSharedPreferences("launcher", 0).getInt("grid_rows", 3), 2, 6)
            * clamp(getSharedPreferences("launcher", 0).getInt("grid_columns", 3), 2, 6);
        return Math.max(1, (apps.size() + size - 1) / size);
    }

    @Override protected void onResume() {
        super.onResume();
        if (root == null) return;
        String mode = getSharedPreferences("launcher", 0).getString("theme_mode", "system");
        int accent = ThemeUtils.accent(this);
        if (!Objects.equals(appliedTheme, mode) || appliedAccent != accent) {
            recreate();
            return;
        }
        applyBackground();
        loadApps();
        postLaunchNotification();
    }
}
