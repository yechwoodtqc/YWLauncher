package com.lge.ywlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Deep-link trampoline that opens the main YW Launcher screen. */
public final class HomeLinkActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }
}
