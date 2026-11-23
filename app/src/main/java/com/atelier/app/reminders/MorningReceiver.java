package com.atelier.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Fires at the morning time: posts today's notification group, then sets the next day's alarm. */
public class MorningReceiver extends BroadcastReceiver {
    private static final String TAG = "Atelier";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                MorningNotifier.postOnce(app);
            } catch (RuntimeException e) {
                // A failed post must not stop tomorrow's alarm, so the schedule below still runs.
                Log.e(TAG, "morning notification failed", e);
            } finally {
                MorningScheduler.schedule(app);
                result.finish();
            }
        }, "morning-reminder").start();
    }
}
