package com.mama.app.data;

import android.test.AndroidTestCase;

import com.mama.app.model.Appointment;
import com.mama.app.model.GarmentType;
import com.mama.app.model.Kind;
import com.mama.app.model.LedgerEntry;
import com.mama.app.model.LedgerType;
import com.mama.app.model.Order;
import com.mama.app.model.OrderFilter;
import com.mama.app.model.OrderPhoto;
import com.mama.app.model.ReminderMode;
import com.mama.app.model.Stage;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RepositoryTest extends AndroidTestCase {
    private DbHelper helper;
    private GarmentTypeRepository types;
    private OrderRepository orders;
    private LedgerRepository ledger;
    private AppointmentRepository appts;
    private PhotoRepository photos;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        helper = new DbHelper(getContext(), null);
        types = new GarmentTypeRepository(helper);
        orders = new OrderRepository(helper);
        ledger = new LedgerRepository(helper);
        appts = new AppointmentRepository(helper);
        photos = new PhotoRepository(helper);
    }

    @Override
    protected void tearDown() throws Exception {
        helper.close();
        super.tearDown();
    }

    private long typeId() {
        return types.listActive().get(0).id;
    }

    private Order draft(String name, Stage stage, long deadlineDay) {
        return new Order(0, name, "+99450" + name.length(), Kind.TAILORING, typeId(), 10000,
                LocalDate.ofEpochDay(deadlineDay), null, stage, 0, 0, ReminderMode.DEFAULT);
    }

    private long add(String name, Stage stage, long deadlineDay, long now) {
        return orders.insert(draft(name, stage, deadlineDay), 0, now);
    }

    public void testSeed() {
        List<GarmentType> all = types.listAll();
        assertEquals(7, all.size());
        assertNotNull(find(all, "Костюм"));
        assertNotNull(find(all, "Пальто"));
        assertEquals(7, types.listActive().size());
    }

    private static GarmentType find(List<GarmentType> l, String name) {
        for (GarmentType g : l) {
            if (g.name.equals(name)) {
                return g;
            }
        }
        return null;
    }

    public void testDuplicateTypeIgnoresCase() {
        try {
            types.add("костюм");
            fail("duplicate accepted");
        } catch (DuplicateGarmentTypeException expected) {
            // expected
        }
        assertEquals(7, types.listAll().size());
        long id = types.add("Жилет");
        assertEquals("Жилет", types.get(id).name);
        try {
            types.add("  ");
            fail("blank accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testArchiveAndUnarchive() {
        GarmentType t = types.listActive().get(0);
        types.archive(t.id);
        assertEquals(6, types.listActive().size());
        assertEquals(7, types.listAll().size());
        assertTrue(types.get(t.id).archived);
        types.unarchive(t.id);
        assertEquals(7, types.listActive().size());
        assertNull(types.get(99999));
    }

    public void testInsertWithDeposit() {
        long id = orders.insert(draft("Ivanova", Stage.ACCEPTED, 20000), 3000, 1000L);
        Order o = orders.get(id);
        assertEquals("Ivanova", o.customerName);
        assertEquals(LocalDate.ofEpochDay(20000), o.deadline);
        assertEquals(1000L, o.createdAt);
        assertEquals(1000L, o.stageChangedAt);
        assertEquals(ReminderMode.DEFAULT, o.reminderMode);
        List<LedgerEntry> l = ledger.listForOrder(id);
        assertEquals(1, l.size());
        assertEquals(LedgerType.PAYMENT, l.get(0).type);
        assertEquals(3000, l.get(0).amount);
        assertNull(l.get(0).title);
        assertEquals(7000, ledger.balanceDue(id));
    }

    public void testInsertWithoutDepositHasNoLedger() {
        long id = add("Petrov", Stage.ACCEPTED, 20000, 1);
        assertEquals(0, ledger.listForOrder(id).size());
        assertEquals(10000, ledger.balanceDue(id));
    }

    public void testValidation() {
        try {
            orders.insert(draft(" ", Stage.ACCEPTED, 1), 0, 1);
            fail("blank name accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        Order neg = new Order(0, "A", null, Kind.REPAIR, typeId(), -1, LocalDate.ofEpochDay(1),
                null, Stage.ACCEPTED, 0, 0, ReminderMode.DEFAULT);
        try {
            orders.insert(neg, 0, 1);
            fail("negative price accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        long id = add("B", Stage.ACCEPTED, 1, 1);
        try {
            ledger.add(id, LedgerType.PAYMENT, 0, null, 1);
            fail("zero amount accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            orders.setCustomReminders(id, Arrays.asList(3, 0));
            fail("zero days accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertEquals(0, orders.getCustomDays(id).size());
        assertEquals(0, orders.countsByStage().get(Stage.DONE).intValue());
        assertEquals(1, orders.countsByStage().get(Stage.ACCEPTED).intValue());
    }

    public void testUpdateKeepsStage() {
        long id = add("Old", Stage.CUTTING, 5, 1);
        Order o = orders.get(id);
        Order edited = new Order(id, "New", "1", Kind.REPAIR, o.garmentTypeId, 500,
                LocalDate.ofEpochDay(9), "n", Stage.ACCEPTED, 0, 0, ReminderMode.OFF);
        orders.update(edited);
        Order r = orders.get(id);
        assertEquals("New", r.customerName);
        assertEquals(Kind.REPAIR, r.kind);
        assertEquals(Stage.CUTTING, r.stage);
        assertEquals(ReminderMode.DEFAULT, r.reminderMode);
        assertEquals(1L, r.createdAt);
    }

    public void testMoveToValidAndInvalid() {
        long id = add("M", Stage.ACCEPTED, 5, 100);
        orders.moveTo(id, Stage.CUTTING, 200);
        Order o = orders.get(id);
        assertEquals(Stage.CUTTING, o.stage);
        assertEquals(200L, o.stageChangedAt);
        assertEquals(100L, o.createdAt);
        try {
            orders.moveTo(id, Stage.ALTERATIONS, 300);
            fail("invalid move accepted");
        } catch (IllegalStateException expected) {
            // expected
        }
        o = orders.get(id);
        assertEquals(Stage.CUTTING, o.stage);
        assertEquals(200L, o.stageChangedAt);
        orders.moveTo(id, Stage.DONE, 400);
        assertEquals(Stage.DONE, orders.get(id).stage);
        orders.moveTo(id, Stage.FITTING_2, 500);
        assertEquals(Stage.FITTING_2, orders.get(id).stage);
    }

    public void testRestoreStageIgnoresMoveRules() {
        long id = add("R", Stage.FITTING_1, 5, 100);
        orders.moveTo(id, Stage.DONE, 400);
        assertEquals(Stage.DONE, orders.get(id).stage);
        orders.restoreStage(id, Stage.FITTING_1, 100);
        Order o = orders.get(id);
        assertEquals(Stage.FITTING_1, o.stage);
        assertEquals(100L, o.stageChangedAt);
        assertEquals(100L, o.createdAt);
        try {
            orders.restoreStage(id + 999, Stage.CUTTING, 1);
            fail("unknown id accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testListByStageSorting() {
        long late = add("late", Stage.ACCEPTED, 30, 1);
        long soonB = add("soonB", Stage.ACCEPTED, 10, 2);
        long soonA = add("soonA", Stage.ACCEPTED, 10, 3);
        List<Order> l = orders.listByStage(Stage.ACCEPTED);
        assertEquals(soonB, l.get(0).id);
        assertEquals(soonA, l.get(1).id);
        assertEquals(late, l.get(2).id);

        long d1 = add("d1", Stage.ACCEPTED, 1, 1);
        long d2 = add("d2", Stage.ACCEPTED, 99, 1);
        orders.moveTo(d1, Stage.DONE, 1000);
        orders.moveTo(d2, Stage.DONE, 2000);
        List<Order> done = orders.listByStage(Stage.DONE);
        assertEquals(d2, done.get(0).id);
        assertEquals(d1, done.get(1).id);
    }

    public void testCounts() {
        add("a", Stage.ACCEPTED, 1, 1);
        add("b", Stage.ACCEPTED, 1, 1);
        long c = add("c", Stage.ACCEPTED, 1, 1);
        orders.moveTo(c, Stage.CUTTING, 2);
        Map<Stage, Integer> m = orders.countsByStage();
        assertEquals(6, m.size());
        assertEquals(2, m.get(Stage.ACCEPTED).intValue());
        assertEquals(1, m.get(Stage.CUTTING).intValue());
        assertEquals(0, m.get(Stage.DONE).intValue());
    }

    public void testSearch() {
        long a = orders.insert(new Order(0, "Hasanova Leyla", "+994501112233", Kind.TAILORING,
                typeId(), 1, LocalDate.ofEpochDay(1), null, Stage.ACCEPTED, 0, 0, ReminderMode.DEFAULT), 0, 1);
        long b = orders.insert(new Order(0, "Petrov", "0559998877", Kind.REPAIR,
                typeId(), 1, LocalDate.ofEpochDay(2), null, Stage.ACCEPTED, 0, 0, ReminderMode.DEFAULT), 0, 1);
        orders.moveTo(b, Stage.DONE, 5);
        long c = orders.insert(new Order(0, "Иванова Анна", null, Kind.REPAIR,
                typeId(), 1, LocalDate.ofEpochDay(3), null, Stage.ACCEPTED, 0, 0, ReminderMode.DEFAULT), 0, 1);
        assertEquals(c, orders.search("иванова").get(0).id);
        assertEquals(c, orders.search("ИВАНОВА").get(0).id);
        assertEquals(1, orders.search("hasan").size());
        assertEquals(a, orders.search("LEYLA").get(0).id);
        assertEquals(b, orders.search("9998877").get(0).id);
        assertEquals(1, orders.search(Long.toString(a)).size());
        assertEquals(a, orders.search("#" + a).get(0).id);
        assertEquals(a, orders.search("hasanova leyla").get(0).id);
        assertEquals(0, orders.search("zzz").size());
        assertEquals(0, orders.search("  ").size());
        assertEquals(0, orders.search("%").size());
    }

    public void testFilteredListCountsAndSearch() {
        long t1 = typeId();
        long t2 = types.listActive().get(1).id;
        long a = orders.insert(new Order(0, "Иванова Анна", null, Kind.TAILORING, t1, 1,
                LocalDate.ofEpochDay(5), null, Stage.CUTTING, 0, 0, ReminderMode.DEFAULT), 0, 1);
        long b = orders.insert(new Order(0, "Иванова Мария", null, Kind.REPAIR, t2, 1,
                LocalDate.ofEpochDay(2), null, Stage.CUTTING, 0, 0, ReminderMode.DEFAULT), 0, 1);
        long c = orders.insert(new Order(0, "Иванов Петр", null, Kind.REPAIR, t1, 1,
                LocalDate.ofEpochDay(9), null, Stage.ACCEPTED, 0, 0, ReminderMode.DEFAULT), 0, 1);
        orders.moveTo(c, Stage.DONE, 50);
        OrderFilter repair = OrderFilter.NONE.withKind(Kind.REPAIR);
        assertEquals(1, orders.listByStage(Stage.CUTTING, repair).size());
        assertEquals(b, orders.listByStage(Stage.CUTTING, repair).get(0).id);
        assertEquals(2, orders.listByStage(Stage.CUTTING, OrderFilter.NONE).size());
        assertEquals(1, (int) orders.countsByStage(repair).get(Stage.CUTTING));
        assertEquals(1, (int) orders.countsByStage(repair).get(Stage.DONE));
        assertEquals(0, (int) orders.countsByStage(OrderFilter.NONE.withGarmentType(t2)).get(Stage.DONE));
        assertEquals(2, orders.search("ИВАНОВА", OrderFilter.NONE).size());
        assertEquals(b, orders.search("иванова", repair).get(0).id);
        assertEquals(1, orders.search("иванов", OrderFilter.NONE.withGarmentType(t1).withKind(Kind.TAILORING)).size());
        // Active first by deadline, DONE last.
        List<Order> all = orders.search("иванов", OrderFilter.NONE);
        assertEquals(b, all.get(0).id);
        assertEquals(a, all.get(1).id);
        assertEquals(c, all.get(2).id);
    }

    public void testCascadeDelete() {
        long id = add("Gone", Stage.ACCEPTED, 5, 1);
        ledger.add(id, LedgerType.EXPENSE, 100, "buttons", 1);
        orders.setCustomReminders(id, Arrays.asList(2, 5));
        appts.add(id, 1000, null);
        photos.add(id, "a.jpg", 1);
        long other = add("Stays", Stage.ACCEPTED, 5, 1);
        ledger.add(other, LedgerType.EXPENSE, 100, null, 1);
        orders.delete(id);
        assertNull(orders.get(id));
        assertEquals(0, ledger.listForOrder(id).size());
        assertEquals(0, orders.getCustomDays(id).size());
        assertEquals(0, appts.listForOrder(id).size());
        assertEquals(0, photos.listForOrder(id).size());
        assertEquals(1, ledger.listForOrder(other).size());
    }

    public void testForeignKeyIsEnforced() {
        try {
            ledger.add(424242, LedgerType.PAYMENT, 100, null, 1);
            fail("orphan ledger row accepted");
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            // expected
        }
    }

    public void testCustomReminders() {
        long id = add("R", Stage.ACCEPTED, 5, 1);
        assertEquals(ReminderMode.DEFAULT, orders.getReminderMode(id));
        orders.setCustomReminders(id, Arrays.asList(5, 2, 2));
        assertEquals(ReminderMode.CUSTOM, orders.getReminderMode(id));
        assertEquals(Arrays.asList(2, 5), orders.getCustomDays(id));
        orders.setCustomReminders(id, Arrays.asList(7));
        assertEquals(Arrays.asList(7), orders.getCustomDays(id));
        orders.setReminderMode(id, ReminderMode.OFF);
        assertEquals(ReminderMode.OFF, orders.getReminderMode(id));
        assertEquals(0, orders.getCustomDays(id).size());
        orders.setCustomReminders(id, Arrays.asList(1));
        orders.setReminderMode(id, ReminderMode.DEFAULT);
        assertEquals(ReminderMode.DEFAULT, orders.getReminderMode(id));
        assertEquals(0, orders.getCustomDays(id).size());
        try {
            orders.setReminderMode(id, ReminderMode.CUSTOM);
            fail("CUSTOM without days accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testLedgerBalance() {
        long id = orders.insert(draft("L", Stage.ACCEPTED, 5), 2000, 1);
        ledger.add(id, LedgerType.EXPENSE, 1500, "fabric", 2);
        ledger.add(id, LedgerType.PAYMENT, 4000, null, 3);
        assertEquals(10000 + 1500 - 2000 - 4000, ledger.balanceDue(id));
        List<LedgerEntry> l = ledger.listForOrder(id);
        assertEquals(3, l.size());
        ledger.delete(l.get(2).id);
        assertEquals(10000 + 1500 - 2000, ledger.balanceDue(id));
    }

    public void testAppointmentNoteClearsAndOrderDeleteCascades() {
        long id = add("Casc", Stage.FITTING_1, 5, 1);
        long a = appts.add(id, 4000, "with note");
        appts.update(a, 4000, null);
        assertNull(appts.listForOrder(id).get(0).note);
        orders.delete(id);
        assertTrue(appts.listForOrder(id).isEmpty());
    }

    public void testAppointments() {
        long id = add("Ap", Stage.FITTING_1, 5, 1);
        long other = add("Other", Stage.FITTING_1, 5, 1);
        long past = appts.add(id, 1000, "past");
        long second = appts.add(id, 5000, "second");
        long first = appts.add(id, 3000, "first");
        appts.add(other, 2000, null);
        List<Appointment> l = appts.listForOrder(id);
        assertEquals(past, l.get(0).id);
        assertEquals(first, l.get(1).id);
        assertEquals(second, l.get(2).id);
        assertEquals(first, appts.nextUpcoming(id, 2000).id);
        assertEquals(first, appts.nextUpcoming(id, 3000).id);
        assertEquals(second, appts.nextUpcoming(id, 3001).id);
        assertNull(appts.nextUpcoming(id, 5001));
        assertEquals(2, appts.listBetween(2000, 5000).size());
        assertEquals(4, appts.listBetween(0, 5001).size());
        appts.update(first, 9000, "moved");
        assertEquals(second, appts.nextUpcoming(id, 2000).id);
        assertEquals("moved", appts.listForOrder(id).get(2).note);
        appts.delete(second);
        assertEquals(first, appts.nextUpcoming(id, 2000).id);
    }

    public void testAppointmentGet() {
        long id = add("Get", Stage.ACCEPTED, 5, 1);
        long a = appts.add(id, 5000, "n");
        assertEquals(5000, appts.get(a).startsAt);
        assertEquals("n", appts.get(a).note);
        assertNull(appts.get(a + 99));
        appts.delete(a);
        assertNull(appts.get(a));
    }

    public void testPhotos() {
        long id = add("Ph", Stage.ACCEPTED, 5, 1);
        assertNull(photos.newest(id));
        long p1 = photos.add(id, "one.jpg", 10);
        long p2 = photos.add(id, "two.jpg", 20);
        List<OrderPhoto> l = photos.listForOrder(id);
        assertEquals(p2, l.get(0).id);
        assertEquals(p1, l.get(1).id);
        assertEquals("two.jpg", photos.newest(id).fileName);
        photos.delete(p2);
        assertEquals("one.jpg", photos.newest(id).fileName);
    }
}
