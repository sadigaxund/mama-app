package com.atelier.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** The notification permission (Android 13 and newer): asked once, at the first need. */
public final class NotificationPermission {
    public static final int REQUEST_CODE = 301;

    private NotificationPermission() {}

    /** Shows the system dialog the first time only, and only when the permission is missing. */
    public static void requestOnce(Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || Prefs.get(activity).getBoolean(Prefs.KEY_NOTIFICATIONS_ASKED, false)) {
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Prefs.get(activity).edit().putBoolean(Prefs.KEY_NOTIFICATIONS_ASKED, true).apply();
        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
    }

    /** The system page where the user turns this app's notifications back on. */
    public static Intent settingsIntent(Context context) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    }
}
