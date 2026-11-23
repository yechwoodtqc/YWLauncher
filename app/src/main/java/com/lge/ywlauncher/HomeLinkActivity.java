package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Home4Flip-compatible contact-link entry point.
 *
 * This activity is deliberately separate from MainActivity: opening the
 * home4flip.go contact URL asks Android to show the device's configured HOME
 * screen instead of opening YW Launcher's application grid.
 */
public final class HomeLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        startActivity(homeIntent);
        finish();
    }
}
