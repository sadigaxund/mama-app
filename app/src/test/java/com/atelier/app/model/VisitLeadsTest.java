package com.atelier.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class VisitLeadsTest {
    @Test
    public void parseSortsLargestFirstAndDropsDuplicates() {
        assertEquals(Arrays.asList(1440, 60, 15), VisitLeads.parse("15,60,1440,60"));
    }

    @Test
    public void parseDropsValuesThatAreNotOffered() {
        assertEquals(Arrays.asList(30), VisitLeads.parse("x,0,45,-15,30, ,99999999999"));
    }

    @Test
    public void emptyAndNullParseToOff() {
        assertTrue(VisitLeads.parse("").isEmpty());
        assertTrue(VisitLeads.parse(null).isEmpty());
    }

    @Test
    public void csvRoundTrips() {
        String csv = VisitLeads.toCsv(Arrays.asList(60, 1440, 120));
        assertEquals("1440,120,60", csv);
        assertEquals(Arrays.asList(1440, 120, 60), VisitLeads.parse(csv));
        assertEquals("", VisitLeads.toCsv(Collections.<Integer>emptyList()));
    }

    @Test
    public void singleLeadFromOlderVersion() {
        assertEquals(Arrays.asList(60), VisitLeads.fromSingle(60));
        assertTrue(VisitLeads.fromSingle(0).isEmpty());
        assertTrue(VisitLeads.fromSingle(45).isEmpty());
    }
}
