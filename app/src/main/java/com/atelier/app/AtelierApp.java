package com.atelier.app;

import android.app.Application;

import com.atelier.app.backup.BackupScheduler;
import com.atelier.app.reminders.AppointmentScheduler;
import com.atelier.app.reminders.MorningScheduler;
import com.atelier.app.reminders.Notifications;
import com.atelier.app.reminders.Reminders;

/** Runs in every process start, including one started by an alarm: channels, hooks and the alarms. */
public class AtelierApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createChannels(this);
        Reminders.setAppointmentListener(new AppointmentScheduler(this));
        MorningScheduler.schedule(this);
        BackupScheduler.ensureScheduled(this);
        // A force-stop clears every alarm, so each process start sets the visit alarms again.
        new Thread(() -> AppointmentScheduler.rescheduleAll(this), "reschedule").start();
    }
}
