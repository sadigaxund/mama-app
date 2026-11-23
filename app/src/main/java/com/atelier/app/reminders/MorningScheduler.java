package com.atelier.app.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.atelier.app.model.MorningPolicy;
import com.atelier.app.ui.Format;
import com.atelier.app.ui.Prefs;

import java.time.LocalDateTime;

/** Keeps one inexact alarm set for the next morning notification. */
public final class MorningScheduler {
    private static final String TAG = "Atelier";
    /** The window from docs/design.md, "Scheduling": a reminder can arrive up to 10 minutes late. */
    private static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private MorningScheduler() {}

    private static PendingIntent pending(Context ctx) {
        return PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, MorningReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Sets the alarm for the next occurrence of the saved time, or cancels it when the switch is off. */
    public static void schedule(Context context) {
        Context ctx = context.getApplicationContext();
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(ctx);
        if (!Prefs.morningOn(ctx)) {
            am.cancel(pi);
            Log.d(TAG, "morning alarm cancelled");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int minutes = Prefs.morningMinutes(ctx);
        MorningPolicy.Decision d = MorningPolicy.decide(now, minutes, Prefs.morningLastPosted(ctx), true);
        LocalDateTime next = d == MorningPolicy.Decision.SCHEDULE_TODAY
                ? now.toLocalDate().atTime(minutes / 60, minutes % 60)
                : now.toLocalDate().plusDays(1).atTime(minutes / 60, minutes % 60);
        if (d == MorningPolicy.Decision.POST_NOW_THEN_TOMORROW) {
            // The alarm for today was lost (reboot, force-stop, late start). Post now, once.
            Log.d(TAG, "morning: catch-up post");
            new Thread(() -> {
                try {
                    MorningNotifier.postOnce(ctx);
                } catch (RuntimeException e) {
                    // A failed catch-up must not break scheduling; the next start tries again.
                    Log.e(TAG, "morning catch-up failed", e);
                }
            }, "morning-catchup").start();
        }
        am.setWindow(AlarmManager.RTC_WAKEUP, Format.toMillis(next), WINDOW_MILLIS, pi);
        Log.d(TAG, "morning alarm set for " + next);
    }
}
