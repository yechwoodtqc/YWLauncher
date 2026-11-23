package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Camera-intent destination that returns to the device's actual Home screen. */
public final class CameraHomeActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
        finish();
    }
}
