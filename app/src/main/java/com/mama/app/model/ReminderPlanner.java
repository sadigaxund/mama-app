package com.mama.app.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Decides which deadline reminders an order gets today. ReminderPlannerTest holds the rules. */
public final class ReminderPlanner {
    private ReminderPlanner() {}

    /** Overdue first (most late first), then due today, then due soon (nearest first), ties by order id. */
    private static final Comparator<ReminderItem> ORDER = (a, b) -> {
        int t = Integer.compare(rank(a.type), rank(b.type));
        if (t != 0) {
            return t;
        }
        if (a.type == ReminderItem.Type.OVERDUE && a.days != b.days) {
            return Integer.compare(b.days, a.days);
        }
        if (a.type == ReminderItem.Type.DUE_IN && a.days != b.days) {
            return Integer.compare(a.days, b.days);
        }
        return Long.compare(a.orderId, b.orderId);
    };

    private static int rank(ReminderItem.Type t) {
        return t == ReminderItem.Type.OVERDUE ? 0 : t == ReminderItem.Type.DUE_TODAY ? 1 : 2;
    }

    /** At most one item per order. DONE orders and OFF orders get none. */
    public static List<ReminderItem> plan(LocalDate today, List<OrderReminderInput> orders,
                                          List<Integer> defaultDays) {
        List<ReminderItem> out = new ArrayList<>();
        for (OrderReminderInput o : orders) {
            if (o.done || o.mode == ReminderMode.OFF) {
                continue;
            }
            long left = ChronoUnit.DAYS.between(today, o.deadline);
            if (left < 0) {
                out.add(new ReminderItem(o.orderId, ReminderItem.Type.OVERDUE, (int) -left));
            } else if (left == 0) {
                out.add(new ReminderItem(o.orderId, ReminderItem.Type.DUE_TODAY, 0));
            } else {
                List<Integer> days = o.mode == ReminderMode.CUSTOM ? o.customDays : defaultDays;
                if (days != null && days.contains((int) left)) {
                    out.add(new ReminderItem(o.orderId, ReminderItem.Type.DUE_IN, (int) left));
                }
            }
        }
        Collections.sort(out, ORDER);
        return out;
    }

    /** The first moment strictly after now whose time of day is minutesAfterMidnight. */
    public static LocalDateTime nextMorning(LocalDateTime now, int minutesAfterMidnight) {
        LocalDateTime t = now.toLocalDate().atTime(LocalTime.of(minutesAfterMidnight / 60, minutesAfterMidnight % 60));
        return t.isAfter(now) ? t : t.plusDays(1);
    }
}
