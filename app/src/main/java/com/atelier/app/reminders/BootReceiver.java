package com.atelier.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Sets every alarm again after a reboot, a clock or time-zone change, or an app update. */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "Atelier";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                MorningScheduler.schedule(app);
                AppointmentScheduler.rescheduleAll(app);
                Log.d(TAG, "alarms rescheduled after " + action);
            } catch (RuntimeException e) {
                // The next app start sets them again, so log and go on.
                Log.e(TAG, "reschedule after " + action + " failed", e);
            } finally {
                result.finish();
            }
        }, "reschedule").start();
    }
}
