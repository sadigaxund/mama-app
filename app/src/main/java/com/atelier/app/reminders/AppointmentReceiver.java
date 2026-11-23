package com.atelier.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Fires a lead time before an appointment and posts its reminder. */
public class AppointmentReceiver extends BroadcastReceiver {
    private static final String TAG = "Atelier";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final long id = intent.getLongExtra(AppointmentScheduler.EXTRA_APPOINTMENT_ID, 0);
        // An alarm set by a version with a single lead has no lead extra; that lead was most likely the 1 h default.
        final int lead = intent.getIntExtra(AppointmentScheduler.EXTRA_LEAD_MINUTES, 60);
        if (id == 0) {
            Log.w(TAG, "appointment alarm without id");
            return;
        }
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AppointmentNotifier.post(app, id, lead);
            } catch (RuntimeException e) {
                // One failed reminder must not crash the process that other alarms share.
                Log.e(TAG, "appointment notification failed for " + id, e);
            } finally {
                result.finish();
            }
        }, "appointment-reminder").start();
    }
}
