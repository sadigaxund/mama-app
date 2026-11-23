package com.atelier.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDateTime;

public class FormatTimeTest {
    @Test
    public void localTimeSurvivesTheRoundTrip() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 28, 15, 0);
        assertEquals(t, Format.toLocal(Format.toMillis(t)));
    }

    @Test
    public void startsAtIsTheSystemZoneInstant() {
        LocalDateTime t = LocalDateTime.of(2026, 3, 5, 9, 30);
        assertEquals(t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), Format.toMillis(t));
    }
}
