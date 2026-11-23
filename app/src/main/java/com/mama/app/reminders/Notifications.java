package com.mama.app.reminders;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;

import com.mama.app.MainActivity;
import com.mama.app.OrderDetailActivity;
import com.mama.app.R;
import com.mama.app.ui.LocaleSupport;

/** Channel ids, notification ids and shared helpers for every reminder notification. */
public final class Notifications {
    public static final String CHANNEL_MORNING = "morning";
    public static final String CHANNEL_APPOINTMENTS = "appointments";
    public static final String GROUP_MORNING = "morning_group";

    static final int SUMMARY_ID = 1;
    private static final int ORDER_BASE = 1_000_000;
    private static final int APPOINTMENT_BASE = 1_500_000_000;

    private Notifications() {}

    /** The id of an order's morning notification. */
    public static int orderId(long orderId) {
        return ORDER_BASE + (int) orderId;
    }

    static boolean isOrderNotificationId(int id) {
        return id >= ORDER_BASE && id < APPOINTMENT_BASE;
    }

    static int appointmentId(long appointmentId) {
        return APPOINTMENT_BASE + (int) appointmentId;
    }

    /** The first word of a customer name, the surname in "Surname Name". Keeps titles on one line. */
    static String shortName(String customerName) {
        String t = customerName.trim();
        int space = t.indexOf(' ');
        return space < 0 ? t : t.substring(0, space);
    }

    /** A context whose strings follow the in-app language, for receivers that have no Activity. */
    static Context localized(Context context) {
        return LocaleSupport.wrap(context.getApplicationContext());
    }

    /** Creates both channels. Safe to call again: it also refreshes the names after a language change. */
    public static void createChannels(Context context) {
        Context lc = localized(context);
        NotificationManager nm = lc.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_MORNING,
                lc.getString(R.string.channel_morning), NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_APPOINTMENTS,
                lc.getString(R.string.channel_appointments), NotificationManager.IMPORTANCE_HIGH));
    }

    /** Removes an order's morning notification, if it is showing. */
    public static void cancelOrder(Context context, long orderId) {
        context.getSystemService(NotificationManager.class).cancel(orderId(orderId));
    }

    /** Opens the board. */
    static PendingIntent openBoard(Context ctx) {
        return PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Opens the order's detail screen with the board underneath it. Request code 4 * id. */
    static PendingIntent openOrder(Context ctx, long orderId) {
        return stack(ctx, orderId, 0, OrderDetailActivity.intent(ctx, orderId));
    }

    /** Opens a screen over the order's detail screen over the board. Request code 4 * id + slot. */
    static PendingIntent stack(Context ctx, long orderId, int slot, Intent top) {
        TaskStackBuilder b = TaskStackBuilder.create(ctx)
                .addNextIntent(new Intent(ctx, MainActivity.class));
        if (slot != 0) {
            b.addNextIntent(OrderDetailActivity.intent(ctx, orderId));
        }
        b.addNextIntent(top);
        return b.getPendingIntent((int) (4 * orderId + slot),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
