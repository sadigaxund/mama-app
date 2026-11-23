package com.atelier.app.reminders;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.util.Log;

import com.atelier.app.MainActivity;
import com.atelier.app.R;
import com.atelier.app.data.AppointmentRepository;
import com.atelier.app.data.DbHelper;
import com.atelier.app.data.GarmentTypeRepository;
import com.atelier.app.data.OrderRepository;
import com.atelier.app.model.Appointment;
import com.atelier.app.model.GarmentType;
import com.atelier.app.model.Order;
import com.atelier.app.ui.Format;
import com.atelier.app.ui.Intents;
import com.atelier.app.ui.Prefs;

/** Builds and posts the reminder of one appointment. */
public final class AppointmentNotifier {
    private static final String TAG = "Atelier";

    private AppointmentNotifier() {}

    /** Does nothing when the appointment or its order is gone. Runs on a background thread. */
    public static void post(Context appContext, long appointmentId) {
        Context ctx = Notifications.localized(appContext);
        DbHelper db = new DbHelper(ctx);
        try {
            Appointment a = new AppointmentRepository(db).get(appointmentId);
            Order o = a == null ? null : new OrderRepository(db).get(a.orderId);
            if (a == null || o == null) {
                Log.d(TAG, "appointment " + appointmentId + " is gone, no reminder");
                return;
            }
            GarmentType type = new GarmentTypeRepository(db).get(o.garmentTypeId);
            String time = Format.time(Format.toLocal(a.startsAt).toLocalTime());
            String line = ctx.getString(R.string.notif_visit_text,
                    ctx.getString(MainActivity.stageLabel(o.stage)), type == null ? "" : type.name, time);
            String text = a.note == null ? line : line + "\n" + a.note;

            Notification.Builder b = new Notification.Builder(ctx, Notifications.CHANNEL_APPOINTMENTS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ctx.getColor(R.color.accent))
                    .setCategory(Notification.CATEGORY_EVENT)
                    .setContentTitle(ctx.getString(titleFor(Prefs.visitLeadMinutes(ctx)), o.customerName))
                    .setContentText(line)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setWhen(a.startsAt)
                    .setShowWhen(true)
                    .setAutoCancel(true)
                    .setContentIntent(Notifications.openOrder(ctx, o.id));
            if (o.phone != null && !o.phone.trim().isEmpty()) {
                Intent dial = Intents.dial(o.phone).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pi = PendingIntent.getActivity(ctx, (int) a.id, dial,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                b.addAction(new Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_notification),
                        ctx.getString(R.string.notif_call), pi).build());
            }
            ctx.getSystemService(NotificationManager.class).notify(Notifications.appointmentId(a.id), b.build());
            Log.d(TAG, "appointment " + a.id + " reminder posted");
        } finally {
            db.close();
        }
    }

    /** The title format for a lead time. An off lead (only a debug post gets here) reads as one hour. */
    private static int titleFor(int leadMinutes) {
        switch (leadMinutes) {
            case 15: return R.string.notif_visit_15m;
            case 30: return R.string.notif_visit_30m;
            case 120: return R.string.notif_visit_2h;
            case 24 * 60: return R.string.notif_visit_1d;
            default: return R.string.notif_visit_1h;
        }
    }
}
