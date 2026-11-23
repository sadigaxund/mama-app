package com.atelier.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ReminderDaysTest {
    @Test
    public void parsesDefaultList() {
        assertEquals(Arrays.asList(3, 1), ReminderDays.parse("3,1"));
    }

    @Test
    public void parseSortsLargestFirstAndDropsDuplicates() {
        assertEquals(Arrays.asList(7, 2, 1), ReminderDays.parse("1, 7,2,7"));
    }

    @Test
    public void parseDropsGarbageAndOutOfRange() {
        assertEquals(Arrays.asList(365, 5), ReminderDays.parse("x,0,-3,5,366,365,2.5, ,99999999999"));
    }

    @Test
    public void parseOfNullOrEmptyIsEmpty() {
        assertTrue(ReminderDays.parse(null).isEmpty());
        assertTrue(ReminderDays.parse("").isEmpty());
    }

    @Test
    public void toCsvIsNormalised() {
        assertEquals("7,2", ReminderDays.toCsv(Arrays.asList(2, 7, 2, 0)));
        assertEquals("", ReminderDays.toCsv(Collections.<Integer>emptyList()));
    }

    @Test
    public void rangeBounds() {
        assertTrue(ReminderDays.isValid(1));
        assertTrue(ReminderDays.isValid(365));
        assertFalse(ReminderDays.isValid(0));
        assertFalse(ReminderDays.isValid(366));
    }
}
