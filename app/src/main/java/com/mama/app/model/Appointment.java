package com.mama.app.model;

public final class Appointment {
    public final long id;
    public final long orderId;
    public final long startsAt;
    public final String note;

    public Appointment(long id, long orderId, long startsAt, String note) {
        this.id = id;
        this.orderId = orderId;
        this.startsAt = startsAt;
        this.note = note;
    }
}
