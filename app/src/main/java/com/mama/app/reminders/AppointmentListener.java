package com.mama.app.reminders;

import com.mama.app.model.Appointment;

/** Told when an appointment is saved or deleted. Reminder scheduling plugs in here. */
public interface AppointmentListener {
    /** Called after an appointment was added or changed. */
    void onSaved(Appointment appointment);

    /** Called after an appointment was deleted. */
    void onDeleted(long id);

    /** The default: does nothing. */
    AppointmentListener NOOP = new AppointmentListener() {
        @Override public void onSaved(Appointment appointment) {}

        @Override public void onDeleted(long id) {}
    };
}
