package com.lge.ywlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.Intent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class FileBrowserActivity extends Activity {
    public static final String EXTRA_MODE="mode", EXTRA_FILTER="filter", EXTRA_PATH="path";
    private File current;
    private String mode, filter;
    private TextView location;
    private ListView list;
    private ArrayList<File> entries=new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mode=getIntent().getStringExtra(EXTRA_MODE); filter=getIntent().getStringExtra(EXTRA_FILTER);
        File root=Environment.getExternalStorageDirectory();
        if(root==null || !root.exists() || !root.canRead()) root=getExternalFilesDir(null);
        if(root==null) root=getFilesDir();
        current=root;
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText("Choose "+("save".equals(mode)?"save location":"file"));title.setTextSize(19);title.setPadding(16,14,16,10);
        page.addView(title);
        location=new TextView(this);location.setPadding(16,6,16,6);location.setTextSize(12);page.addView(location);
        list=new ListView(this);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button up=new Button(this);up.setText("Up");up.setOnClickListener(v->{if(current.getParentFile()!=null)navigate(current.getParentFile());});
        Button choose=new Button(this);choose.setText("Save here");choose.setVisibility("save".equals(mode)?View.VISIBLE:View.GONE);choose.setOnClickListener(v->showSaveName());
        Button cancel=new Button(this);cancel.setText("Cancel");cancel.setOnClickListener(v->finish());
        buttons.addView(up,new LinearLayout.LayoutParams(0,-2,1));buttons.addView(choose,new LinearLayout.LayoutParams(0,-2,1));buttons.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(buttons);setContentView(page);
        list.setOnItemClickListener((parent,view,position,id)->{
            File file=entries.get(position);
            if(file.isDirectory()){navigate(file);return;}
            if("open".equals(mode)){finishWith(file);}
        });
        list.setOnItemLongClickListener((parent,view,position,id)->{
            File file=entries.get(position);
            if(file.isFile() && "save".equals(mode)){new AlertDialog.Builder(this).setMessage("Replace "+file.getName()+"?").setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->finishWith(file)).show();return true;}
            return false;
        });
        navigate(current);
    }

    private void navigate(File folder) {
        if(folder==null || !folder.isDirectory() || !folder.canRead()) return;
        current=folder;location.setText(folder.getAbsolutePath());
        entries.clear(); File[] files=folder.listFiles();
        if(files!=null) for(File f:files) {
            if(f.isDirectory() || matches(f)) entries.add(f);
        }
        Collections.sort(entries,(a,b)->{
            if(a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        ArrayList<String> labels=new ArrayList<>();
        for(File f:entries) labels.add((f.isDirectory()?"[Folder]  ":"")+f.getName());
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels));
    }

    private boolean matches(File f) {
        if("all".equals(filter)) return true;
        String n=f.getName().toLowerCase(Locale.US);
        if("image".equals(filter)) return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")||n.endsWith(".webp")||n.endsWith(".bmp")||n.endsWith(".gif");
        if("json".equals(filter)) return n.endsWith(".json");
        if("text".equals(filter)) return n.endsWith(".txt")||n.endsWith(".log");
        return true;
    }

    private void showSaveName() {
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setText("json".equals(filter)?"ywlauncher-t9-dictionary.json":("text".equals(filter)?"ywlauncher-logcat.txt":"ywlauncher-backup.txt"));
        new AlertDialog.Builder(this).setTitle("File name").setView(name).setNegativeButton("Cancel",(d,w)->finish())
            .setPositiveButton("Save",(d,w)->{
                String value=name.getText().toString().trim();
                if(value.isEmpty() || value.contains("/") || value.contains("\\") || value.equals(".") || value.equals("..")) {
                    Toast.makeText(this,"Enter a valid file name.",Toast.LENGTH_LONG).show();showSaveName();return;
                }
                File target=new File(current,value);
                if(target.exists()) new AlertDialog.Builder(this).setMessage("Replace existing file?").setNegativeButton("Cancel",null)
                    .setPositiveButton("Replace",(dd,ww)->finishWith(target)).show();
                else finishWith(target);
            }).show();
    }

    private void finishWith(File file) {
        Intent result=new Intent();result.putExtra(EXTRA_PATH,file.getAbsolutePath());setResult(RESULT_OK,result);finish();
    }

    @Override public void onBackPressed() {
        File parent=current==null?null:current.getParentFile();
        if(parent!=null && parent.canRead()) navigate(parent); else super.onBackPressed();
    }
}