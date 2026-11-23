package com.mama.app.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MorningPolicyTest {
    private static final LocalDate D = LocalDate.of(2026, 11, 23);
    private static final int NINE = 9 * 60;

    private static LocalDateTime at(int h, int m) {
        return D.atTime(h, m);
    }

    @Test
    public void beforeTheTimeSchedulesToday() {
        assertEquals(MorningPolicy.Decision.SCHEDULE_TODAY,
                MorningPolicy.decide(at(8, 59), NINE, D.minusDays(1), true));
        assertEquals(MorningPolicy.Decision.SCHEDULE_TODAY, MorningPolicy.decide(at(8, 59), NINE, null, true));
    }

    @Test
    public void beforeTheTimeButAlreadyPostedSchedulesTomorrow() {
        assertEquals(MorningPolicy.Decision.SCHEDULE_TOMORROW, MorningPolicy.decide(at(8, 0), NINE, D, true));
    }

    @Test
    public void insideTheWindowNotYetPostedPostsNow() {
        assertEquals(MorningPolicy.Decision.POST_NOW_THEN_TOMORROW,
                MorningPolicy.decide(at(9, 0), NINE, D.minusDays(1), true));
        assertEquals(MorningPolicy.Decision.POST_NOW_THEN_TOMORROW, MorningPolicy.decide(at(20, 59), NINE, null, true));
    }

    @Test
    public void afterTheTimeAlreadyPostedSchedulesTomorrow() {
        assertEquals(MorningPolicy.Decision.SCHEDULE_TOMORROW, MorningPolicy.decide(at(10, 0), NINE, D, true));
    }

    @Test
    public void after21NotPostedSchedulesTomorrow() {
        assertEquals(MorningPolicy.Decision.SCHEDULE_TOMORROW, MorningPolicy.decide(at(21, 0), NINE, null, true));
        assertEquals(MorningPolicy.Decision.SCHEDULE_TOMORROW, MorningPolicy.decide(at(23, 30), NINE, D.minusDays(1), true));
    }

    @Test
    public void disabledIsOff() {
        assertEquals(MorningPolicy.Decision.OFF, MorningPolicy.decide(at(10, 0), NINE, null, false));
        assertEquals(MorningPolicy.Decision.OFF, MorningPolicy.decide(at(8, 0), NINE, null, false));
    }
}
