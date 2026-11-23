package com.mama.app.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.mama.app.data.AppointmentRepository;
import com.mama.app.data.DbHelper;
import com.mama.app.model.Appointment;
import com.mama.app.ui.Prefs;

/** One inexact alarm per future appointment, set a lead time before it starts. */
public final class AppointmentScheduler implements AppointmentListener {
    private static final String TAG = "MamaApp";
    private static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private final Context context;

    public AppointmentScheduler(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onSaved(Appointment appointment) {
        schedule(context, appointment, System.currentTimeMillis());
    }

    @Override
    public void onDeleted(long id) {
        cancel(context, id);
    }

    static final String EXTRA_APPOINTMENT_ID = "appointment_id";

    private static PendingIntent pending(Context ctx, long appointmentId) {
        Intent i = new Intent(ctx, AppointmentReceiver.class)
                .setData(Uri.parse("mama://appointment/" + appointmentId))
                .putExtra(EXTRA_APPOINTMENT_ID, appointmentId);
        return PendingIntent.getBroadcast(ctx, (int) appointmentId, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Sets the alarm, or cancels it when the lead is off or the trigger time is already past. */
    static void schedule(Context ctx, Appointment a, long nowMillis) {
        int lead = Prefs.visitLeadMinutes(ctx);
        long trigger = a.startsAt - lead * 60_000L;
        if (lead <= 0 || trigger <= nowMillis) {
            cancel(ctx, a.id);
            return;
        }
        ctx.getSystemService(AlarmManager.class).setWindow(AlarmManager.RTC_WAKEUP, trigger,
                WINDOW_MILLIS, pending(ctx, a.id));
        Log.d(TAG, "appointment " + a.id + " alarm set for " + trigger);
    }

    static void cancel(Context ctx, long appointmentId) {
        ctx.getSystemService(AlarmManager.class).cancel(pending(ctx, appointmentId));
        Log.d(TAG, "appointment " + appointmentId + " alarm cancelled");
    }

    /** Sets or cancels the alarm of every future appointment. Reads the database: call it off the main thread. */
    public static void rescheduleAll(Context context) {
        Context ctx = context.getApplicationContext();
        long now = System.currentTimeMillis();
        DbHelper db = new DbHelper(ctx);
        try {
            for (Appointment a : new AppointmentRepository(db).listBetween(now, Long.MAX_VALUE)) {
                schedule(ctx, a, now);
            }
        } finally {
            db.close();
        }
    }
}
