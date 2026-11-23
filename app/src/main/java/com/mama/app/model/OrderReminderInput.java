package com.mama.app.model;

import java.time.LocalDate;
import java.util.List;

/** What ReminderPlanner needs to know about one order. customDays matters only in CUSTOM mode. */
public final class OrderReminderInput {
    public final long orderId;
    public final LocalDate deadline;
    public final boolean done;
    public final ReminderMode mode;
    public final List<Integer> customDays;

    public OrderReminderInput(long orderId, LocalDate deadline, boolean done, ReminderMode mode,
                              List<Integer> customDays) {
        this.orderId = orderId;
        this.deadline = deadline;
        this.done = done;
        this.mode = mode;
        this.customDays = customDays;
    }
}
