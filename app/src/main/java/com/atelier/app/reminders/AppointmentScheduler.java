package com.atelier.app.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.List;

import com.atelier.app.data.AppointmentRepository;
import com.atelier.app.data.DbHelper;
import com.atelier.app.model.Appointment;
import com.atelier.app.model.VisitLeads;
import com.atelier.app.ui.Prefs;

/** One inexact alarm per future appointment and chosen lead time. */
public final class AppointmentScheduler implements AppointmentListener {
    private static final String TAG = "Atelier";
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
    static final String EXTRA_LEAD_MINUTES = "lead_minutes";

    /** The data URI makes each (appointment, lead) pair a distinct PendingIntent. */
    private static PendingIntent pending(Context ctx, long appointmentId, int leadMinutes) {
        Intent i = new Intent(ctx, AppointmentReceiver.class)
                .setData(Uri.parse("atelier://appointment/" + appointmentId + "/" + leadMinutes))
                .putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
                .putExtra(EXTRA_LEAD_MINUTES, leadMinutes);
        return PendingIntent.getBroadcast(ctx, (int) appointmentId, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The one alarm per appointment that versions with a single lead set. */
    private static PendingIntent pendingSingleLead(Context ctx, long appointmentId) {
        Intent i = new Intent(ctx, AppointmentReceiver.class)
                .setData(Uri.parse("atelier://appointment/" + appointmentId));
        return PendingIntent.getBroadcast(ctx, (int) appointmentId, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Sets an alarm for each chosen lead whose trigger time is still ahead, and cancels every other lead. */
    static void schedule(Context ctx, Appointment a, long nowMillis) {
        List<Integer> leads = Prefs.visitLeads(ctx);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(pendingSingleLead(ctx, a.id));
        for (int lead : VisitLeads.OPTIONS) {
            long trigger = a.startsAt - lead * 60_000L;
            if (leads.contains(lead) && trigger > nowMillis) {
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW_MILLIS, pending(ctx, a.id, lead));
                Log.d(TAG, "appointment " + a.id + " alarm " + lead + " min before set for " + trigger);
            } else {
                am.cancel(pending(ctx, a.id, lead));
            }
        }
    }

    static void cancel(Context ctx, long appointmentId) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(pendingSingleLead(ctx, appointmentId));
        for (int lead : VisitLeads.OPTIONS) {
            am.cancel(pending(ctx, appointmentId, lead));
        }
        Log.d(TAG, "appointment " + appointmentId + " alarms cancelled");
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
