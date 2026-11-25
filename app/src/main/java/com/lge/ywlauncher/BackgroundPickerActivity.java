package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Full-screen background chooser with an app-grid preview and D-pad navigation. */
public final class BackgroundPickerActivity extends Activity {
    private static final int PICK_IMAGE = 812;
    private static final int[] PALETTE = {
        0xff173f91, 0xff2452b0, 0xff283b55, 0xff111820,
        0xff5145b5, 0xff7839ad, 0xff087e8b, 0xff315b45
    };
    private static final String[] PALETTE_NAMES = {
        "Deep blue", "Royal blue", "Slate", "Black",
        "Purple", "Violet", "Teal", "Forest"
    };
    private static final int SYSTEM = 7, EXALT = 5, CLASSIC = 6, CUSTOM_IMAGE = 4, CUSTOM_COLOR = 3;
    private static final int PRESET_FIRST = 8, PRESET_LAST = 17;
    private final ArrayList<Choice> choices = new ArrayList<>();
    private int selected = 0;
    private int previewIndex = 0;
    private boolean previewShowing = false;
    private float previewZoom = 1f, previewRotation = 0f;
    private float previewPanX = 0f, previewPanY = 0f;
    private Bitmap pendingImage;
    private String pendingImagePath = "";
    private LinearLayout root;
    private SharedPreferences prefs;

    private static final class Choice {
        final String title, subtitle;
        final int value, color;
        Choice(String title, String subtitle, int value, int color) {
            this.title=title; this.subtitle=subtitle; this.value=value; this.color=color;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        prefs = getSharedPreferences("launcher", 0);
        buildChoices();
        showGrid();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void buildChoices() {
        choices.clear();
        // Curated gradient backgrounds replace the old flat-color and LG Classic choices.
        choices.add(new Choice("Aurora", "Deep purple with flowing green and cyan light", EXALT, 0xff2452b0));
        choices.add(new Choice("Midnight", "Deep navy with a violet glow", 8, 0xff30265d));
        choices.add(new Choice("Ocean", "Layered blue with a cool cyan glow", 9, 0xff075f83));
        choices.add(new Choice("Sunset", "Warm amber fading into plum", 10, 0xff82476d));
        choices.add(new Choice("Forest", "Deep evergreen with emerald light", 11, 0xff145b4c));
        choices.add(new Choice("Graphite", "Soft charcoal with slate highlights", 12, 0xff354454));
        choices.add(new Choice("Ember", "Warm copper light over deep charcoal", 13, 0xff713d36));
        choices.add(new Choice("Glacier", "Cool blue with an icy cyan glow", 14, 0xff286a80));
        choices.add(new Choice("Plum", "Rich violet with a soft orchid glow", 15, 0xff573b78));
        choices.add(new Choice("Dune", "Warm sand and bronze tones", 16, 0xff806044));
        choices.add(new Choice("Borealis", "Deep teal with northern-light green", 17, 0xff12635e));
        choices.add(new Choice("System wallpaper", "Use the phone's wallpaper", SYSTEM, Color.TRANSPARENT));
        choices.add(new Choice("Pick wallpaper", "Choose a picture from the phone", CUSTOM_IMAGE, 0xff34495e));
        int saved = prefs.getInt("background", defaultBackground());
        if (saved == 0 || saved == CLASSIC) saved = defaultBackground();
        int savedColor = prefs.getInt("custom_background", PALETTE[0]);
        for (int i=0;i<choices.size();i++) {
            Choice c=choices.get(i);
            if (c.value==saved && (c.value!=CUSTOM_COLOR || c.color==savedColor)) { selected=i; break; }
        }
        if (saved==CUSTOM_COLOR) for(int i=0;i<PALETTE.length;i++) if(PALETTE[i]==savedColor) selected=i;
    }

    private int defaultBackground() {
        String model=(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL).toLowerCase(Locale.US);
        if(model.contains("l125dl")||model.contains("l125")||model.contains("classic")) return EXALT;
        if(model.contains("vn220")||model.contains("exalt")||model.contains("lg-ax")) return EXALT;
        return SYSTEM;
    }

    private void showGrid() {
        previewShowing = false;
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setBackgroundDrawable(new ColorDrawable(ThemeUtils.surface(this)));
        root = pageRoot();
        addToolbar(root, "Choose background", () -> finish(), null);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(2), dp(10), dp(8));
        for (int index = 0; index < choices.size(); index++) {
            final int choiceIndex = index;
            Choice choice = choices.get(index);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), dp(8), dp(12), dp(8));
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackground(ThemeUtils.focusBackground(this));

            View swatch = new View(this);
            if (choice.value == EXALT) swatch.setBackground(new ExaltBackgroundDrawable());
            else if (choice.value >= PRESET_FIRST && choice.value <= PRESET_LAST)
                swatch.setBackground(new PresetBackgroundDrawable(choice.value));
            else if (choice.value == SYSTEM) {
                swatch.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xff627d91, 0xff26394a}));
            } else swatch.setBackgroundColor(choice.color);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(58), dp(44));
            swatchParams.rightMargin = dp(14);
            item.addView(swatch, swatchParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = label(choice.title, 15, true);
            TextView subtitle = label(choice.subtitle, 11, false);
            subtitle.setTextColor(ThemeUtils.secondaryText(this));
            labels.addView(title);
            labels.addView(subtitle);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            boolean isCurrent = isCurrentBackground(choice);
            TextView marker = label(isCurrent ? "✓" : "", 22, true);
            marker.setTextColor(ThemeUtils.accent(this));
            marker.setGravity(Gravity.CENTER);
            item.addView(marker, new LinearLayout.LayoutParams(dp(28), dp(44)));

            item.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    selected = choiceIndex;
                }
                marker.setText(isCurrent ? "✓" : "");
                marker.setTextColor(ThemeUtils.accent(this));
                item.setBackground(focused ? ThemeUtils.focusOutlineBackground(this) : ThemeUtils.focusBackground(this));
            });
            item.setOnClickListener(v -> {
                selected = choiceIndex;
                previewZoom = Math.max(1f, prefs.getFloat("custom_background_zoom", 1f));
                previewRotation = prefs.getFloat("custom_background_rotation", 0f);
                previewPanX = prefs.getFloat("custom_background_pan_x", 0f);
                previewPanY = prefs.getFloat("custom_background_pan_y", 0f);
                showPreview();
            });
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, dp(64));
            itemParams.bottomMargin = dp(4);
            list.addView(item, itemParams);
        }
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(softKeyBar("CANCEL", "SAVE", "PREVIEW", () -> finish(),
            () -> { previewIndex = selected; saveChoice(); }, () -> showPreview()));
        setContentView(root);
        int focusIndex = Math.max(0, Math.min(selected, list.getChildCount() - 1));
        if (focusIndex >= 0 && focusIndex < list.getChildCount()) list.getChildAt(focusIndex).requestFocus();
    }

    private LinearLayout softKeyBar(String leftLabel, String middleLabel, String rightLabel,
            Runnable leftAction, Runnable middleAction, Runnable rightAction) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), 0, dp(10), 0);
        bar.setBackgroundColor(0xff929292);
        String[] labels = {leftLabel, middleLabel, rightLabel};
        Runnable[] actions = {leftAction, middleAction, rightAction};
        for (int i = 0; i < labels.length; i++) {
            final Runnable action = actions[i];
            TextView key = label(labels[i], 15, true);
            key.setTextColor(Color.WHITE);
            key.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            key.setPadding(dp(2), 0, dp(2), dp(2));
            key.setFocusable(true);
            key.setClickable(true);
            key.setBackground(ThemeUtils.focusBackground(this));
            key.setOnFocusChangeListener((view, focused) ->
                key.setBackground(focused ? ThemeUtils.focusOutlineBackground(this) : ThemeUtils.focusBackground(this)));
            key.setOnClickListener(v -> action.run());
            bar.addView(key, new LinearLayout.LayoutParams(0, dp(28), 1));
        }
        return bar;
    }

    private LinearLayout pageRoot() {
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ThemeUtils.surface(this));
        return page;
    }

    private TextView label(String value,float size,boolean bold) {
        TextView text=new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(ThemeUtils.text(this));
        if(bold) text.setTypeface(null,android.graphics.Typeface.BOLD);
        return text;
    }

    private Button button(String text,Runnable action) {
        Button button=new Button(this);
        button.setText(text); button.setFocusable(true);
        ThemeUtils.tint(button,this);
        button.setOnClickListener(v->action.run());
        return button;
    }

    private void addToolbar(LinearLayout page,String title,Runnable back,Runnable next) {
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton left = new ImageButton(this);
        left.setImageResource(R.drawable.ic_back_modern);
        left.setColorFilter(ThemeUtils.text(this));
        left.setContentDescription("Back");
        left.setBackground(ThemeUtils.focusBackground(this));
        left.setFocusable(true);
        left.setOnClickListener(v -> back.run());
        bar.addView(left,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=label(title,17,true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));
        if(next!=null) {
            Button right=button("›",next);
            bar.addView(right,new LinearLayout.LayoutParams(dp(52),dp(48)));
        }
        page.addView(bar,new LinearLayout.LayoutParams(-1,dp(50)));
    }

    private Choice currentChoice() { return choices.get(Math.max(0,Math.min(choices.size()-1,previewIndex))); }
    private Choice currentChoiceForSelected() { return choices.get(Math.max(0,Math.min(choices.size()-1,selected))); }

    private boolean isCurrentBackground(Choice choice) {
        int saved = prefs.getInt("background", defaultBackground());
        if (choice.value == CUSTOM_COLOR) return false;
        return saved == choice.value;
    }

    private void showPreview() {
        previewShowing = true;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        previewIndex=selected;
        Choice choice=currentChoice();
        root=pageRoot();
        if (choice.value==SYSTEM) {
            root.setBackgroundColor(Color.TRANSPARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        if (choice.value != CUSTOM_IMAGE) {
            TextView heading = label(choice.title, 18, true);
            heading.setGravity(Gravity.CENTER);
            root.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));
        }
        ScrollView previewScroll=new ScrollView(this);
        previewScroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout mock=new LinearLayout(this);
        mock.setOrientation(LinearLayout.VERTICAL);
        mock.setPadding(dp(4),dp(4),dp(4),dp(4));
        mock.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout appGrid=new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        List<ResolveInfo> apps=visibleApps();
        int columns=Math.max(2,Math.min(4,prefs.getInt("grid_columns",3)));
        int iconSize=Math.max(28,Math.min(52,prefs.getInt("icon_size",40)));
        for(int i=0;i<apps.size();i+=columns) {
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for(int c=0;c<columns;c++) {
                int index=i+c;
                if(index>=apps.size()) { row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(82),1)); continue; }
                ResolveInfo info=apps.get(index);
                LinearLayout cell=new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2),dp(4),dp(2),dp(4));
                ImageView icon=new ImageView(this);
                try { icon.setImageDrawable(info.loadIcon(getPackageManager())); }
                catch(Exception ignored) { icon.setImageResource(android.R.drawable.sym_def_app_icon); }
                cell.addView(icon,new LinearLayout.LayoutParams(dp(iconSize),dp(iconSize)));
                TextView name=label(info.loadLabel(getPackageManager()).toString(),10,false);
                name.setGravity(Gravity.CENTER); name.setMaxLines(1); name.setSingleLine(true);
                name.setTextColor(Color.WHITE);
                cell.addView(name,new LinearLayout.LayoutParams(-1,-2));
                row.addView(cell,new LinearLayout.LayoutParams(0,dp(82),1));
            }
            appGrid.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        if(apps.isEmpty()) {
            TextView empty=label("Your selected apps will appear here.",13,false);
            appGrid.addView(empty);
        }
        mock.addView(appGrid,new LinearLayout.LayoutParams(-1,-2));
        if (choice.value == CUSTOM_IMAGE) {
            Button selectPicture = button("Select custom picture", this::pickImage);
            LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(48));
            chooseParams.setMargins(dp(44), dp(8), dp(44), dp(8));
            mock.addView(selectPicture, 0, chooseParams);
        }
        previewScroll.addView(mock);
        android.widget.FrameLayout previewFrame = new android.widget.FrameLayout(this);
        applyPreviewBackground(previewFrame, choice);
        previewFrame.addView(previewScroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        TextView leftArrow = label("‹", 38, true);
        leftArrow.setTextColor(Color.WHITE);
        leftArrow.setGravity(Gravity.CENTER);
        leftArrow.setBackgroundColor(Color.TRANSPARENT);
        leftArrow.setOnClickListener(v -> stepPreview(-1));
        android.widget.FrameLayout.LayoutParams leftParams = new android.widget.FrameLayout.LayoutParams(dp(38), -1, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        previewFrame.addView(leftArrow, leftParams);
        TextView rightArrow = label("›", 38, true);
        rightArrow.setTextColor(Color.WHITE);
        rightArrow.setGravity(Gravity.CENTER);
        rightArrow.setBackgroundColor(Color.TRANSPARENT);
        rightArrow.setOnClickListener(v -> stepPreview(1));
        android.widget.FrameLayout.LayoutParams rightParams = new android.widget.FrameLayout.LayoutParams(dp(38), -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        previewFrame.addView(rightArrow, rightParams);
        root.addView(previewFrame,new LinearLayout.LayoutParams(-1,0,1));
        if (choice.value == CUSTOM_IMAGE) {
            boolean imageReady = hasCustomImage();
            root.addView(softKeyBar("ZOOM −", imageReady ? "SAVE" : "OPEN", "ZOOM +",
                () -> { previewZoom = Math.max(1f, previewZoom - 0.1f); if (previewZoom <= 1.01f) { previewPanX=0f; previewPanY=0f; } showPreview(); },
                () -> { if (hasCustomImage()) saveChoice(); else pickImage(); },
                () -> { previewZoom = Math.min(3f, previewZoom + 0.1f); showPreview(); }));
        } else {
            root.addView(softKeyBar("PREVIOUS", "SAVE", "NEXT",
                () -> stepPreview(-1), () -> saveChoice(), () -> stepPreview(1)));
        }
        setContentView(root);
    }

    private List<ResolveInfo> visibleApps() {
        Intent query=new Intent(Intent.ACTION_MAIN); query.addCategory(Intent.CATEGORY_LAUNCHER);
        ArrayList<ResolveInfo> found=new ArrayList<>(getPackageManager().queryIntentActivities(query,0));
        Set<String> included=prefs.getStringSet("included",null);
        for(int i=found.size()-1;i>=0;i--) {
            ResolveInfo app=found.get(i);
            if(MainActivity.class.getName().equals(app.activityInfo.name)
                || BackgroundPickerActivity.class.getName().equals(app.activityInfo.name)
                || HomeLinkActivity.class.getName().equals(app.activityInfo.name)) { found.remove(i); continue; }
            if(included!=null && !included.contains(app.activityInfo.name)
                && !included.contains(app.activityInfo.packageName)) { found.remove(i); continue; }
            if(included==null && (app.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0) found.remove(i);
        }
        String raw=prefs.getString("ordered_apps","");
        final ArrayList<String> order=new ArrayList<>();
        if(raw!=null) Collections.addAll(order,raw.split("\n"));
        Collections.sort(found,(a,b)->{
            int ai=order.indexOf(a.activityInfo.name),bi=order.indexOf(b.activityInfo.name);
            if(ai>=0||bi>=0) { if(ai<0)return 1;if(bi<0)return -1;if(ai!=bi)return ai-bi; }
            return String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString());
        });
        return found;
    }

    private void applyPreviewBackground(View target,Choice choice) {
        if(choice.value==SYSTEM) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            target.setBackgroundColor(Color.TRANSPARENT);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            if(choice.value==EXALT) target.setBackground(new ExaltBackgroundDrawable());
            else if(choice.value>=PRESET_FIRST && choice.value<=PRESET_LAST)
                target.setBackground(new PresetBackgroundDrawable(choice.value));
            else if(choice.value==CUSTOM_IMAGE && pendingImage!=null)
                target.setBackground(new WallpaperDrawable(pendingImage,"crop",previewZoom,previewRotation,previewPanX,previewPanY));
            else if(choice.value==CUSTOM_IMAGE) {
                String path=prefs.getString("custom_background_path","");
                Bitmap bitmap=path.isEmpty()?null:decodePreviewBitmap(path);
                if(bitmap!=null) target.setBackground(new WallpaperDrawable(bitmap,prefs.getString("wallpaper_fit","crop"),previewZoom,previewRotation,previewPanX,previewPanY));
                else target.setBackgroundColor(0xff34495e);
            } else target.setBackgroundColor(choice.color);
        }
    }

    private Bitmap decodePreviewBitmap(String path) {
        try {
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(path,bounds);
            int maxW=Math.max(1,getResources().getDisplayMetrics().widthPixels);
            int maxH=Math.max(1,getResources().getDisplayMetrics().heightPixels);
            int sample=1;
            while(bounds.outWidth/sample>maxW*2 || bounds.outHeight/sample>maxH*2) sample*=2;
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=sample;
            return BitmapFactory.decodeFile(path,options);
        } catch (Exception ignored) { return null; }
    }

    private void stepPreview(int delta) {
        previewIndex=(previewIndex+delta+choices.size())%choices.size();
        selected=previewIndex;
        previewZoom = Math.max(1f, prefs.getFloat("custom_background_zoom", 1f));
        previewRotation = prefs.getFloat("custom_background_rotation", 0f);
        previewPanX = prefs.getFloat("custom_background_pan_x", 0f);
        previewPanY = prefs.getFloat("custom_background_pan_y", 0f);
        showPreview();
    }

    private boolean hasCustomImage() {
        if (pendingImage != null && !pendingImage.isRecycled()
            && pendingImagePath != null && new File(pendingImagePath).isFile()) return true;
        String saved = prefs.getString("custom_background_path", "");
        if (saved.isEmpty()) return false;
        File file = new File(saved);
        if (!file.isFile() || file.length() <= 0) return false;
        Bitmap decoded = decodePreviewBitmap(saved);
        if (decoded == null) return false;
        decoded.recycle();
        return true;
    }

    private void pickImage() {
        Intent picker=new Intent(Intent.ACTION_GET_CONTENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*"});
        try { startActivityForResult(Intent.createChooser(picker,"Choose background image"),PICK_IMAGE); }
        catch(Exception e) { android.widget.Toast.makeText(this,"No file picker is available.",android.widget.Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_IMAGE || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        try {
            File temp=new File(getFilesDir(),"background_preview.png");
            try(InputStream input=getContentResolver().openInputStream(data.getData());
                FileOutputStream output=new FileOutputStream(temp)) {
                if(input==null) throw new java.io.IOException("Selected image could not be opened.");
                byte[] buffer=new byte[8192]; int count;
                while((count=input.read(buffer))!=-1) output.write(buffer,0,count);
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(temp.getAbsolutePath(),bounds);
            int sample=1, maxW=Math.max(1,getResources().getDisplayMetrics().widthPixels), maxH=Math.max(1,getResources().getDisplayMetrics().heightPixels);
            while(bounds.outWidth/sample>maxW*2 || bounds.outHeight/sample>maxH*2) sample*=2;
            BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=sample;
            pendingImage=BitmapFactory.decodeFile(temp.getAbsolutePath(),opts);
            if(pendingImage==null) throw new java.io.IOException("Selected file is not a supported image.");
            pendingImagePath=temp.getAbsolutePath();
            previewIndex=selected;
            previewZoom=1f;
            previewRotation=0f;
            previewPanX=0f; previewPanY=0f;
            showPreview();
        } catch(Exception e) {
            android.widget.Toast.makeText(this,"Could not preview that image.",android.widget.Toast.LENGTH_LONG).show();
            showGrid();
        }
    }

    private void saveChoice() {
        Choice choice=currentChoice();
        SharedPreferences.Editor edit=prefs.edit();
        if(choice.value==CUSTOM_IMAGE) {
            File source=new File(pendingImagePath);
            if(!source.isFile()) {
                String saved=prefs.getString("custom_background_path","");
                if(saved.isEmpty() || !new File(saved).isFile()) {
                    android.widget.Toast.makeText(this,"Choose an image before saving.",android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                try(InputStream input=new FileInputStream(source);
                    FileOutputStream output=new FileOutputStream(new File(getFilesDir(),"custom_background.png"))) {
                    byte[] buffer=new byte[8192]; int count;
                    while((count=input.read(buffer))!=-1) output.write(buffer,0,count);
                } catch(Exception e) {
                    android.widget.Toast.makeText(this,"Could not save the selected image.",android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                edit.putString("custom_background_path",new File(getFilesDir(),"custom_background.png").getAbsolutePath())
                    .remove("custom_background_uri");
            }
            edit.putString("wallpaper_fit","crop").putInt("background",CUSTOM_IMAGE)
                .putFloat("custom_background_zoom", previewZoom)
                .putFloat("custom_background_rotation", previewRotation)
                .putFloat("custom_background_pan_x", previewPanX)
                .putFloat("custom_background_pan_y", previewPanY);
        } else {
            edit.putInt("background",choice.value);
        }
        edit.apply();
        setResult(RESULT_OK);
        finish();
    }

    @Override public boolean onKeyDown(int keyCode,KeyEvent event) {
        if (previewShowing) {
            if (keyCode==KeyEvent.KEYCODE_DPAD_LEFT || keyCode==KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode==KeyEvent.KEYCODE_DPAD_UP || keyCode==KeyEvent.KEYCODE_DPAD_DOWN) {
                if (currentChoice().value == CUSTOM_IMAGE && previewZoom > 1.01f) {
                    float step = dp(24);
                    if (keyCode==KeyEvent.KEYCODE_DPAD_LEFT) previewPanX += step;
                    if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) previewPanX -= step;
                    if (keyCode==KeyEvent.KEYCODE_DPAD_UP) previewPanY += step;
                    if (keyCode==KeyEvent.KEYCODE_DPAD_DOWN) previewPanY -= step;
                    showPreview();
                } else if (currentChoice().value == CUSTOM_IMAGE && keyCode==KeyEvent.KEYCODE_DPAD_UP) {
                    previewRotation=(previewRotation+270f)%360f; showPreview();
                } else if (currentChoice().value == CUSTOM_IMAGE && keyCode==KeyEvent.KEYCODE_DPAD_DOWN) {
                    previewRotation=(previewRotation+90f)%360f; showPreview();
                } else if (keyCode==KeyEvent.KEYCODE_DPAD_LEFT) stepPreview(-1);
                else if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) stepPreview(1);
                return true;
            }
            if (keyCode==KeyEvent.KEYCODE_SOFT_LEFT) {
                if (currentChoice().value == CUSTOM_IMAGE) { previewZoom = Math.max(1f, previewZoom - 0.1f); if (previewZoom <= 1.01f) { previewPanX=0f; previewPanY=0f; } showPreview(); }
                else stepPreview(-1);
                return true;
            }
            if (keyCode==KeyEvent.KEYCODE_SOFT_RIGHT) {
                if (currentChoice().value == CUSTOM_IMAGE) { previewZoom = Math.min(3f, previewZoom + 0.1f); showPreview(); }
                else saveChoice();
                return true;
            }
            if (keyCode==KeyEvent.KEYCODE_ENTER || keyCode==KeyEvent.KEYCODE_DPAD_CENTER) {
                if (currentChoice().value == CUSTOM_IMAGE && !hasCustomImage()) pickImage();
                else saveChoice();
                return true;
            }
            if (keyCode==KeyEvent.KEYCODE_BACK) {
                if (currentChoice().value == CUSTOM_IMAGE && previewZoom > 1.01f) {
                    previewZoom = 1f; previewPanX = 0f; previewPanY = 0f; showPreview();
                } else showGrid();
                return true;
            }
        } else {
            if (keyCode==KeyEvent.KEYCODE_SOFT_LEFT || keyCode==KeyEvent.KEYCODE_BACK) { finish(); return true; }
            if (keyCode==KeyEvent.KEYCODE_SOFT_RIGHT) { showPreview(); return true; }
            if (keyCode==KeyEvent.KEYCODE_DPAD_CENTER || keyCode==KeyEvent.KEYCODE_ENTER) {
                previewIndex = selected;
                saveChoice();
                return true;
            }
        }
        return super.onKeyDown(keyCode,event);
    }
}
