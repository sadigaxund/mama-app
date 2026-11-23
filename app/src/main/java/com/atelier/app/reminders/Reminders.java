package com.atelier.app.reminders;

/** The place the app looks up its reminder hooks. */
public final class Reminders {
    private static volatile AppointmentListener appointmentListener = AppointmentListener.NOOP;

    private Reminders() {}

    public static AppointmentListener appointmentListener() {
        return appointmentListener;
    }

    public static void setAppointmentListener(AppointmentListener listener) {
        appointmentListener = listener == null ? AppointmentListener.NOOP : listener;
    }
}
