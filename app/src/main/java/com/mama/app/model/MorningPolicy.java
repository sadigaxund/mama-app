package com.mama.app.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Decides what to do about the morning notification at a given moment. MorningPolicyTest holds the rules. */
public final class MorningPolicy {
    public enum Decision { OFF, SCHEDULE_TODAY, SCHEDULE_TOMORROW, POST_NOW_THEN_TOMORROW }

    /** A missed morning is posted late only before this local time. */
    public static final LocalTime CATCH_UP_UNTIL = LocalTime.of(21, 0);

    private MorningPolicy() {}

    /** lastPosted is the date of the last post, or null when there is none. */
    public static Decision decide(LocalDateTime now, int setMinutes, LocalDate lastPosted, boolean enabled) {
        if (!enabled) {
            return Decision.OFF;
        }
        LocalDate today = now.toLocalDate();
        boolean posted = today.equals(lastPosted);
        if (now.toLocalTime().isBefore(LocalTime.of(setMinutes / 60, setMinutes % 60))) {
            return posted ? Decision.SCHEDULE_TOMORROW : Decision.SCHEDULE_TODAY;
        }
        if (posted || !now.toLocalTime().isBefore(CATCH_UP_UNTIL)) {
            return Decision.SCHEDULE_TOMORROW;
        }
        return Decision.POST_NOW_THEN_TOMORROW;
    }
}
