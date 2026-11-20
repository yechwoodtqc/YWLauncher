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
    private final ArrayList<Choice> choices = new ArrayList<>();
    private int selected = 0;
    private int previewIndex = 0;
    private boolean previewShowing = false;
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
        choices.add(new Choice("Blue", "Classic launcher blue", 1, 0xff12304e));
        choices.add(new Choice("Black", "Solid black background", 2, Color.BLACK));
        for (int i=0;i<PALETTE.length;i++) choices.add(new Choice(PALETTE_NAMES[i], "Color background", CUSTOM_COLOR, PALETTE[i]));
        choices.add(new Choice("System wallpaper", "Use the phone's wallpaper", SYSTEM, Color.TRANSPARENT));
        choices.add(new Choice("LG Exalt", "Original blue-to-purple gradient", EXALT, 0xff2452b0));
        choices.add(new Choice("LG Classic", "Classic flip-phone menu background", CLASSIC, 0xff233d70));
        choices.add(new Choice("Custom image", "Choose a picture from the phone", CUSTOM_IMAGE, 0xff34495e));
        int saved = prefs.getInt("background", defaultBackground());
        if (saved == 0) saved = defaultBackground();
        int savedColor = prefs.getInt("custom_background", PALETTE[0]);
        for (int i=0;i<choices.size();i++) {
            Choice c=choices.get(i);
            if (c.value==saved && (c.value!=CUSTOM_COLOR || c.color==savedColor)) { selected=i; break; }
        }
        if (saved==CUSTOM_COLOR) for(int i=0;i<PALETTE.length;i++) if(PALETTE[i]==savedColor) selected=i;
    }

    private int defaultBackground() {
        String model=(android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL).toLowerCase(Locale.US);
        if(model.contains("l125dl")||model.contains("l125")||model.contains("classic")) return CLASSIC;
        if(model.contains("vn220")||model.contains("exalt")||model.contains("lg-ax")) return EXALT;
        return SYSTEM;
    }

    private void showGrid() {
        previewShowing = false;
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        root = pageRoot();
        addToolbar(root, "Choose background", () -> finish(), null);
        TextView hint = label("Choose a background. Select one to preview it with your apps.", 13, false);
        hint.setPadding(dp(14),dp(8),dp(14),dp(8));
        root.addView(hint);
        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int columns=2;
        for(int start=0;start<choices.size();start+=columns) {
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for(int col=0;col<columns;col++) {
                int index=start+col;
                if(index>=choices.size()) { row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(92),1)); continue; }
                Choice choice=choices.get(index);
                LinearLayout tile=new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(8),dp(8),dp(8),dp(8));
                tile.setFocusable(true); tile.setClickable(true);
                tile.setBackground(ThemeUtils.focusBackground(this));
                View swatch=new View(this);
                if(choice.value==EXALT) swatch.setBackground(new ExaltBackgroundDrawable());
                else if(choice.value==CLASSIC) swatch.setBackgroundResource(R.drawable.bg_lg_classic_menu);
                else if(choice.value==SYSTEM) swatch.setBackgroundColor(0xff52616f);
                else swatch.setBackgroundColor(choice.color);
                tile.addView(swatch,new LinearLayout.LayoutParams(-1,dp(46)));
                TextView title=label(choice.title,14,true);
                title.setGravity(Gravity.CENTER);
                tile.addView(title);
                tile.setOnClickListener(v->{
                    selected=index;
                    if(choice.value==CUSTOM_IMAGE) {
                        String savedImage=prefs.getString("custom_background_path","");
                        if(pendingImage!=null || (!savedImage.isEmpty() && new File(savedImage).isFile())) showPreview();
                        else pickImage();
                    } else showPreview();
                });
                tile.setOnFocusChangeListener((v,f)->{
                    if(f) selected=index;
                    tile.setBackground(f?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this));
                });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(100),1);
                lp.setMargins(dp(5),dp(5),dp(5),dp(5));
                row.addView(tile,lp);
            }
            grid.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        scroll.addView(grid);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button close=button("Cancel",()->finish());
        root.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));
        setContentView(root);
        if(grid.getChildCount()>0 && ((LinearLayout)grid.getChildAt(0)).getChildCount()>0)
            ((LinearLayout)grid.getChildAt(0)).getChildAt(0).requestFocus();
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
        Button left=button("‹",back);
        bar.addView(left,new LinearLayout.LayoutParams(dp(52),dp(48)));
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

    private void showPreview() {
        previewShowing = true;
        previewIndex=selected;
        Choice choice=currentChoice();
        root=pageRoot();
        if (choice.value==SYSTEM) root.setBackgroundColor(Color.TRANSPARENT);
        addToolbar(root,"Preview · "+choice.title,()->showGrid(),()->stepPreview(1));
        TextView hint=label("Use LEFT / RIGHT to preview other backgrounds. Save applies the selection.",12,false);
        hint.setPadding(dp(12),dp(4),dp(12),dp(6));
        root.addView(hint);
        ScrollView previewScroll=new ScrollView(this);
        LinearLayout mock=new LinearLayout(this);
        mock.setOrientation(LinearLayout.VERTICAL);
        mock.setPadding(dp(4),dp(4),dp(4),dp(4));
        applyPreviewBackground(mock,choice);
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
        previewScroll.addView(mock);
        root.addView(previewScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);
        actions.setPadding(dp(8),dp(4),dp(8),dp(4));
        Button previous=button("‹ Previous",()->stepPreview(-1));
        Button save=button("Save",()->saveChoice());
        Button next=button("Next ›",()->stepPreview(1));
        actions.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(actions);
        setContentView(root);
        save.requestFocus();
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

    private void applyPreviewBackground(LinearLayout target,Choice choice) {
        if(choice.value==SYSTEM) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            target.setBackgroundColor(Color.TRANSPARENT);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            if(choice.value==EXALT) target.setBackground(new ExaltBackgroundDrawable());
            else if(choice.value==CLASSIC) target.setBackgroundResource(R.drawable.bg_lg_classic_menu);
            else if(choice.value==CUSTOM_IMAGE && pendingImage!=null)
                target.setBackground(new WallpaperDrawable(pendingImage,"crop"));
            else if(choice.value==CUSTOM_IMAGE) {
                String path=prefs.getString("custom_background_path","");
                Bitmap bitmap=path.isEmpty()?null:decodePreviewBitmap(path);
                if(bitmap!=null) target.setBackground(new WallpaperDrawable(bitmap,prefs.getString("wallpaper_fit","crop")));
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
        if(currentChoice().value==CUSTOM_IMAGE && pendingImage==null) {
            String path=prefs.getString("custom_background_path","");
            if(path.isEmpty()) { pickImage(); return; }
        }
        showPreview();
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
            showPreview();
        } catch(Exception e) {
            android.widget.Toast.makeText(this,"Could not preview that image.",android.widget.Toast.LENGTH_LONG).show();
            showGrid();
        }
    }

    private void saveChoice() {
        Choice choice=currentChoice();
        SharedPreferences.Editor edit=prefs.edit();
        if(choice.value==CUSTOM_COLOR) {
            edit.putInt("custom_background",choice.color).putInt("background",CUSTOM_COLOR);
        } else if(choice.value==CUSTOM_IMAGE) {
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
            edit.putString("wallpaper_fit","crop").putInt("background",CUSTOM_IMAGE);
        } else {
            edit.putInt("background",choice.value);
        }
        edit.apply();
        setResult(RESULT_OK);
        finish();
    }

    @Override public boolean onKeyDown(int keyCode,KeyEvent event) {
        if (previewShowing) {
            if (keyCode==KeyEvent.KEYCODE_DPAD_LEFT) { stepPreview(-1); return true; }
            if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT) { stepPreview(1); return true; }
            if (keyCode==KeyEvent.KEYCODE_SOFT_LEFT) { showGrid(); return true; }
            if (keyCode==KeyEvent.KEYCODE_SOFT_RIGHT || keyCode==KeyEvent.KEYCODE_ENTER
                || keyCode==KeyEvent.KEYCODE_DPAD_CENTER) { saveChoice(); return true; }
            if (keyCode==KeyEvent.KEYCODE_BACK) { showGrid(); return true; }
        } else {
            if (keyCode==KeyEvent.KEYCODE_SOFT_LEFT || keyCode==KeyEvent.KEYCODE_BACK) { finish(); return true; }
            if (keyCode==KeyEvent.KEYCODE_SOFT_RIGHT) { showPreview(); return true; }
        }
        return super.onKeyDown(keyCode,event);
    }
}
