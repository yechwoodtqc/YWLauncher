package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Camera-intent destination that opens the YW Home screen. */
public final class CameraHomeActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent home = new Intent(this, MainActivity.class);
        home.setAction(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_LAUNCHER);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }
}
