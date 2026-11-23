package com.atelier.app.model;

import java.time.LocalDate;

/** An order row. Money is in minor units. Use id 0 for a new, unsaved order. */
public final class Order {
    public final long id;
    public final String customerName;
    public final String phone;
    public final Kind kind;
    public final long garmentTypeId;
    public final long workPrice;
    public final LocalDate deadline;
    public final String notes;
    public final Stage stage;
    public final long createdAt;
    public final long stageChangedAt;
    public final ReminderMode reminderMode;

    public Order(long id, String customerName, String phone, Kind kind, long garmentTypeId,
                 long workPrice, LocalDate deadline, String notes, Stage stage,
                 long createdAt, long stageChangedAt, ReminderMode reminderMode) {
        this.id = id;
        this.customerName = customerName;
        this.phone = phone;
        this.kind = kind;
        this.garmentTypeId = garmentTypeId;
        this.workPrice = workPrice;
        this.deadline = deadline;
        this.notes = notes;
        this.stage = stage;
        this.createdAt = createdAt;
        this.stageChangedAt = stageChangedAt;
        this.reminderMode = reminderMode;
    }
}
