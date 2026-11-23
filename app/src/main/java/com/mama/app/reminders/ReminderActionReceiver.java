package com.mama.app.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mama.app.data.DbHelper;
import com.mama.app.data.OrderRepository;
import com.mama.app.model.ReminderMode;
import com.mama.app.model.Stage;

/** Handles the "Done" and "Stop reminding" buttons of a morning notification. */
public class ReminderActionReceiver extends BroadcastReceiver {
    private static final String TAG = "MamaApp";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final String action = intent.getAction();
        final long orderId = intent.getLongExtra(MorningNotifier.EXTRA_ORDER_ID, 0);
        if (action == null || orderId == 0) {
            Log.w(TAG, "reminder action without order: " + action);
            return;
        }
        final PendingResult result = goAsync();
        new Thread(() -> {
            DbHelper db = new DbHelper(app);
            try {
                OrderRepository orders = new OrderRepository(db);
                if (MorningNotifier.ACTION_DONE.equals(action)) {
                    orders.moveTo(orderId, Stage.DONE, System.currentTimeMillis());
                    Log.d(TAG, "order #" + orderId + " done from notification");
                } else if (MorningNotifier.ACTION_STOP.equals(action)) {
                    orders.setReminderMode(orderId, ReminderMode.OFF);
                    Log.d(TAG, "order #" + orderId + " reminders off from notification");
                }
            } catch (RuntimeException e) {
                // The order was deleted or already done since the notification was posted.
                Log.w(TAG, "reminder action " + action + " failed for #" + orderId, e);
            } finally {
                db.close();
                Notifications.cancelOrder(app, orderId);
                result.finish();
            }
        }, "reminder-action").start();
    }
}
