package com.mama.app.ui;

import android.app.Activity;
import android.content.Context;

/** Every Activity extends this so the in-app language applies. */
public abstract class BaseActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleSupport.wrap(newBase));
    }
}
