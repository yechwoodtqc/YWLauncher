package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

public class ChooseAppsActivity extends Activity {
    private final ArrayList<ResolveInfo> apps = new ArrayList<>();
    private final Set<String> included = new LinkedHashSet<>();
    private LinearLayout grid;
    private ScrollView scroll;
    private int columns = 3;

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeUtils.apply(this);
        android.content.SharedPreferences prefs = getSharedPreferences("launcher", 0);
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        apps.addAll(getPackageManager().queryIntentActivities(query, 0));
        for (int i=apps.size()-1;i>=0;i--) if (MainActivity.class.getName().equals(apps.get(i).activityInfo.name)) apps.remove(i);
        Collections.sort(apps, (a,b)->String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString()));
        Set<String> old = prefs.getStringSet("included", null);
        for (ResolveInfo app:apps) {
            boolean system=(app.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0;
            if(old==null ? !system : old.contains(app.activityInfo.name)||old.contains(app.activityInfo.packageName)) included.add(app.activityInfo.name);
        }
        columns = Math.max(2, Math.min(4, prefs.getInt("grid_columns", 3)));

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.surface(this));
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back=new ImageButton(this); back.setImageResource(R.drawable.ic_back_modern); back.setColorFilter(ThemeUtils.text(this));
        back.setContentDescription("Back"); back.setBackground(ThemeUtils.focusOutlineBackground(this));
        back.setOnClickListener(v->finish()); bar.addView(back,new LinearLayout.LayoutParams(dp(46),dp(48)));
        TextView title=new TextView(this); title.setText("Choose apps"); title.setTextSize(19); title.setTypeface(null,android.graphics.Typeface.BOLD); title.setTextColor(ThemeUtils.text(this)); title.setGravity(Gravity.CENTER);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        View balance=new View(this); bar.addView(balance,new LinearLayout.LayoutParams(dp(46),dp(48)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));

        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); grid.setPadding(dp(3),dp(4),dp(3),dp(4));
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout footer=new LinearLayout(this); footer.setGravity(Gravity.CENTER);
        Button save=new Button(this); save.setText("Save"); ThemeUtils.tint(save,this); save.setTextColor(ThemeUtils.text(this)); save.setBackground(ThemeUtils.focusOutlineBackground(this));
        Button cancel=new Button(this); cancel.setText("Cancel"); ThemeUtils.tint(cancel,this); cancel.setTextColor(ThemeUtils.text(this)); cancel.setBackground(ThemeUtils.focusOutlineBackground(this));
        footer.addView(save,new LinearLayout.LayoutParams(0,dp(48),1)); footer.addView(cancel,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(54)));
        save.setOnClickListener(v->{
            ArrayList<String> order=new ArrayList<>(); String raw=prefs.getString("ordered_apps","");
            if(raw!=null) for(String n:raw.split("\\n")) if(included.contains(n)&&!order.contains(n)) order.add(n);
            for(ResolveInfo app:apps) if(included.contains(app.activityInfo.name)&&!order.contains(app.activityInfo.name)) order.add(app.activityInfo.name);
            prefs.edit().putStringSet("included",new HashSet<>(included)).putString("ordered_apps",join(order)).apply();
            Toast.makeText(this,"App list saved",Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); finish();
        });
        cancel.setOnClickListener(v->finish());
        setContentView(root);
        render();
        scroll.post(()->{View first=findFirstFocusable(grid);if(first!=null)first.requestFocus();else save.requestFocus();});
    }

    private String join(List<String> values){StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append('\n');b.append(v);}return b.toString();}
    private View findFirstFocusable(ViewGroup group){for(int i=0;i<group.getChildCount();i++){View v=group.getChildAt(i);if(v.isFocusable())return v;if(v instanceof ViewGroup){View f=findFirstFocusable((ViewGroup)v);if(f!=null)return f;}}return null;}

    private void render(){
        grid.removeAllViews();
        int rows=(int)Math.ceil(apps.size()/(double)columns);
        int h=Math.max(dp(78),getResources().getDisplayMetrics().heightPixels/Math.max(3,rows+2));
        for(int r=0;r<rows;r++){
            LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER); line.setOrientation(LinearLayout.HORIZONTAL);
            for(int c=0;c<columns;c++){
                int index=r*columns+c;
                if(index>=apps.size()){line.addView(new View(this),new LinearLayout.LayoutParams(0,h,1));continue;}
                ResolveInfo app=apps.get(index); String name=app.activityInfo.name;
                FrameLayout cell=new FrameLayout(this); cell.setFocusable(true); cell.setClickable(true); cell.setTag(name);
                cell.setBackground(ThemeUtils.focusBackground(this));
                LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER);
                ImageView icon=new ImageView(this);
                try{icon.setImageDrawable(app.loadIcon(getPackageManager()));}catch(Exception e){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
                body.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
                TextView label=new TextView(this);label.setText(app.loadLabel(getPackageManager()));label.setTextSize(11);label.setTextColor(ThemeUtils.text(this));label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                body.addView(label,new LinearLayout.LayoutParams(-1,-2)); cell.addView(body,new FrameLayout.LayoutParams(-1,-1));
                TextView check=new TextView(this);check.setText("✓");check.setTextSize(19);check.setTypeface(null,android.graphics.Typeface.BOLD);check.setTextColor(Color.WHITE);check.setGravity(Gravity.CENTER);
                GradientDrawable mark=new GradientDrawable();mark.setColor(0xff168b43);mark.setShape(GradientDrawable.OVAL);check.setBackground(mark);
                FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(25),dp(25),Gravity.TOP|Gravity.END);cp.setMargins(0,dp(2),dp(2),0);cell.addView(check,cp);
                check.setVisibility(included.contains(name)?View.VISIBLE:View.GONE);
                cell.setOnFocusChangeListener((v,focus)->v.setBackground(focus?ThemeUtils.focusOutlineBackground(this):ThemeUtils.focusBackground(this)));
                cell.setOnClickListener(v->{if(included.contains(name))included.remove(name);else included.add(name);check.setVisibility(included.contains(name)?View.VISIBLE:View.GONE);});
                cell.setOnLongClickListener(v->{try{Intent launch=new Intent(Intent.ACTION_MAIN);launch.addCategory(Intent.CATEGORY_LAUNCHER);launch.setClassName(app.activityInfo.packageName,app.activityInfo.name);startActivity(launch);}catch(Exception ignored){}return true;});
                line.addView(cell,new LinearLayout.LayoutParams(0,h,1));
            }
            grid.addView(line,new LinearLayout.LayoutParams(-1,h));
        }
    }
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){return super.onKeyDown(keyCode,event);}
}
