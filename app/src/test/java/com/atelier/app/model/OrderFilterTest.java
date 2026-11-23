package com.atelier.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class OrderFilterTest {
    private static Order order(long id, Kind kind, long type, Stage stage, int day, long changed) {
        return new Order(id, "N" + id, null, kind, type, 0, LocalDate.ofEpochDay(day), null,
                stage, 0, changed, ReminderMode.DEFAULT);
    }

    @Test
    public void noneMatchesEverythingAndIsInactive() {
        assertFalse(OrderFilter.NONE.isActive());
        assertTrue(OrderFilter.NONE.matches(order(1, Kind.REPAIR, 9, Stage.CUTTING, 1, 0)));
    }

    @Test
    public void kindFilter() {
        OrderFilter f = OrderFilter.NONE.withKind(Kind.REPAIR);
        assertTrue(f.isActive());
        assertTrue(f.matches(order(1, Kind.REPAIR, 1, Stage.ACCEPTED, 1, 0)));
        assertFalse(f.matches(order(2, Kind.TAILORING, 1, Stage.ACCEPTED, 1, 0)));
    }

    @Test
    public void typeFilter() {
        OrderFilter f = OrderFilter.NONE.withGarmentType(3);
        assertTrue(f.isActive());
        assertTrue(f.matches(order(1, Kind.REPAIR, 3, Stage.ACCEPTED, 1, 0)));
        assertFalse(f.matches(order(2, Kind.REPAIR, 4, Stage.ACCEPTED, 1, 0)));
    }

    @Test
    public void kindAndTypeCombine() {
        OrderFilter f = new OrderFilter(Kind.TAILORING, 3);
        assertTrue(f.matches(order(1, Kind.TAILORING, 3, Stage.DONE, 1, 0)));
        assertFalse(f.matches(order(2, Kind.REPAIR, 3, Stage.DONE, 1, 0)));
        assertFalse(f.matches(order(3, Kind.TAILORING, 4, Stage.DONE, 1, 0)));
        assertFalse(f.withKind(null).withGarmentType(0).isActive());
    }

    @Test
    public void searchOrderPutsActiveFirstByDeadlineThenDoneNewestFirst() {
        List<Order> l = new ArrayList<>();
        l.add(order(1, Kind.REPAIR, 1, Stage.DONE, 1, 100));
        l.add(order(2, Kind.REPAIR, 1, Stage.CUTTING, 9, 0));
        l.add(order(3, Kind.REPAIR, 1, Stage.DONE, 2, 500));
        l.add(order(4, Kind.REPAIR, 1, Stage.ACCEPTED, 5, 0));
        l.add(order(5, Kind.REPAIR, 1, Stage.FITTING_1, 5, 0));
        Collections.sort(l, OrderFilter.SEARCH_ORDER);
        StringBuilder ids = new StringBuilder();
        for (Order o : l) {
            ids.append(o.id);
        }
        assertEquals("45231", ids.toString());
    }

    @Test
    public void cyrillicLowerCaseFoldsLikeTheRepositorySearch() {
        // OrderRepository.search lower-cases both sides with Locale.ROOT.
        assertEquals("иванова анна", "Иванова Анна".toLowerCase(Locale.ROOT));
        assertTrue("Иванова Анна".toLowerCase(Locale.ROOT).contains("ИВАНОВА".toLowerCase(Locale.ROOT)));
    }
}
