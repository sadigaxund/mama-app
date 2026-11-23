package com.mama.app;

import android.app.Application;

import com.mama.app.backup.BackupScheduler;
import com.mama.app.reminders.AppointmentScheduler;
import com.mama.app.reminders.MorningScheduler;
import com.mama.app.reminders.Notifications;
import com.mama.app.reminders.Reminders;

/** Runs in every process start, including one started by an alarm: channels, hooks and the alarms. */
public class MamaApp extends Application {
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
