package com.atelier.app.model;

/** One deadline reminder for one order on one day. days is the days left or the days late. */
public final class ReminderItem {
    public enum Type { DUE_IN, DUE_TODAY, OVERDUE }

    public final long orderId;
    public final Type type;
    /** DUE_IN: days until the deadline. OVERDUE: days past it. DUE_TODAY: 0. */
    public final int days;

    public ReminderItem(long orderId, Type type, int days) {
        this.orderId = orderId;
        this.type = type;
        this.days = days;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ReminderItem)) {
            return false;
        }
        ReminderItem r = (ReminderItem) o;
        return orderId == r.orderId && type == r.type && days == r.days;
    }

    @Override
    public int hashCode() {
        return (int) (orderId * 31 + type.ordinal()) * 31 + days;
    }

    @Override
    public String toString() {
        return type + "(" + days + ") #" + orderId;
    }
}
