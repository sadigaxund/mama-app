package com.mama.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Fires a lead time before an appointment and posts its reminder. */
public class AppointmentReceiver extends BroadcastReceiver {
    private static final String TAG = "MamaApp";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final long id = intent.getLongExtra(AppointmentScheduler.EXTRA_APPOINTMENT_ID, 0);
        if (id == 0) {
            Log.w(TAG, "appointment alarm without id");
            return;
        }
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AppointmentNotifier.post(app, id);
            } catch (RuntimeException e) {
                // One failed reminder must not crash the process that other alarms share.
                Log.e(TAG, "appointment notification failed for " + id, e);
            } finally {
                result.finish();
            }
        }, "appointment-reminder").start();
    }
}
