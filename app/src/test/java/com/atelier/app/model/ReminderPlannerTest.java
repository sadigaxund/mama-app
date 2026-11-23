package com.atelier.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReminderPlannerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final List<Integer> DEFAULT = Arrays.asList(3, 1);

    private static OrderReminderInput order(long id, int daysLeft, boolean done, ReminderMode mode,
                                            Integer... custom) {
        return new OrderReminderInput(id, TODAY.plusDays(daysLeft), done, mode, Arrays.asList(custom));
    }

    private static List<ReminderItem> plan(OrderReminderInput... orders) {
        return ReminderPlanner.plan(TODAY, Arrays.asList(orders), DEFAULT);
    }

    @Test
    public void defaultModeUsesDefaultDays() {
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.DUE_IN, 3)),
                plan(order(1, 3, false, ReminderMode.DEFAULT)));
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.DUE_IN, 1)),
                plan(order(1, 1, false, ReminderMode.DEFAULT)));
        assertTrue(plan(order(1, 2, false, ReminderMode.DEFAULT)).isEmpty());
        assertTrue(plan(order(1, 4, false, ReminderMode.DEFAULT)).isEmpty());
    }

    @Test
    public void customModeUsesItsOwnDaysNotTheDefault() {
        assertTrue(plan(order(1, 3, false, ReminderMode.CUSTOM, 7, 2)).isEmpty());
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.DUE_IN, 7)),
                plan(order(1, 7, false, ReminderMode.CUSTOM, 7, 2)));
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.DUE_IN, 2)),
                plan(order(1, 2, false, ReminderMode.CUSTOM, 7, 2)));
    }

    @Test
    public void dueTodayOnTheDeadline() {
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.DUE_TODAY, 0)),
                plan(order(1, 0, false, ReminderMode.DEFAULT)));
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.DUE_TODAY, 0)),
                plan(order(1, 0, false, ReminderMode.CUSTOM, 5)));
    }

    @Test
    public void overdueEveryDayAfterTheDeadline() {
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.OVERDUE, 1)),
                plan(order(1, -1, false, ReminderMode.DEFAULT)));
        assertEquals(Collections.singletonList(new ReminderItem(1, ReminderItem.Type.OVERDUE, 40)),
                plan(order(1, -40, false, ReminderMode.CUSTOM, 5)));
    }

    @Test
    public void doneOrdersNeverRemind() {
        assertTrue(plan(order(1, 3, true, ReminderMode.DEFAULT), order(2, 0, true, ReminderMode.DEFAULT),
                order(3, -5, true, ReminderMode.DEFAULT)).isEmpty());
    }

    @Test
    public void offOrdersNeverRemind() {
        assertTrue(plan(order(1, 3, false, ReminderMode.OFF), order(2, 0, false, ReminderMode.OFF),
                order(3, -5, false, ReminderMode.OFF)).isEmpty());
    }

    @Test
    public void anOrderMatchingSeveralDaysGetsOneItem() {
        // Days 3 and 1 both listed twice over: the order is 3 days out, so one DUE_IN(3).
        List<ReminderItem> r = ReminderPlanner.plan(TODAY,
                Collections.singletonList(order(1, 3, false, ReminderMode.DEFAULT)),
                Arrays.asList(3, 3, 1));
        assertEquals(1, r.size());
        assertEquals(3, r.get(0).days);
    }

    @Test
    public void emptyDefaultListStillRemindsOnAndAfterTheDeadline() {
        List<ReminderItem> r = ReminderPlanner.plan(TODAY, Arrays.asList(
                order(1, 3, false, ReminderMode.DEFAULT), order(2, 0, false, ReminderMode.DEFAULT),
                order(3, -1, false, ReminderMode.DEFAULT)), Collections.<Integer>emptyList());
        assertEquals(2, r.size());
    }

    @Test
    public void resultIsOverdueThenTodayThenSoon() {
        List<ReminderItem> r = plan(order(1, 1, false, ReminderMode.DEFAULT),
                order(2, 0, false, ReminderMode.DEFAULT), order(3, -2, false, ReminderMode.DEFAULT),
                order(4, -5, false, ReminderMode.DEFAULT), order(5, 3, false, ReminderMode.DEFAULT));
        assertEquals(Arrays.asList(
                new ReminderItem(4, ReminderItem.Type.OVERDUE, 5),
                new ReminderItem(3, ReminderItem.Type.OVERDUE, 2),
                new ReminderItem(2, ReminderItem.Type.DUE_TODAY, 0),
                new ReminderItem(1, ReminderItem.Type.DUE_IN, 1),
                new ReminderItem(5, ReminderItem.Type.DUE_IN, 3)), r);
    }

    @Test
    public void nextMorningIsTodayBeforeTheTimeAndTomorrowAtOrAfterIt() {
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 0),
                ReminderPlanner.nextMorning(LocalDateTime.of(2026, 11, 23, 8, 59), 9 * 60));
        assertEquals(LocalDateTime.of(2026, 11, 24, 9, 0),
                ReminderPlanner.nextMorning(LocalDateTime.of(2026, 11, 23, 9, 0), 9 * 60));
        assertEquals(LocalDateTime.of(2026, 11, 24, 0, 30),
                ReminderPlanner.nextMorning(LocalDateTime.of(2026, 11, 23, 23, 59), 30));
    }
}
