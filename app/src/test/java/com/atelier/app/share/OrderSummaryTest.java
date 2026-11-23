package com.atelier.app.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.atelier.app.model.Appointment;
import com.atelier.app.model.Kind;
import com.atelier.app.model.LedgerEntry;
import com.atelier.app.model.LedgerType;
import com.atelier.app.model.Order;
import com.atelier.app.model.ReminderMode;
import com.atelier.app.model.Stage;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrderSummaryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private static OrderSummary.Labels labels() {
        OrderSummary.Labels l = new OrderSummary.Labels();
        l.money = m -> com.atelier.app.model.Money.formatMinor(m) + " ₼";
        l.dateText = d -> d.getDayOfMonth() + "." + d.getMonthValue();
        l.visitText = at -> "visit" + at;
        return l;
    }

    private static Order order(long price, String phone, String notes) {
        return new Order(12, "Ivanova", phone, Kind.TAILORING, 1, price,
                LocalDate.of(2026, 11, 28), notes, Stage.FITTING_1, 0, 0, ReminderMode.DEFAULT);
    }

    private static LedgerEntry entry(LedgerType t, long amount, String title) {
        return new LedgerEntry(1, 12, t, amount, title, 0);
    }

    @Test
    public void moneyRowsFollowPriceEntriesBalance() {
        List<LedgerEntry> es = Arrays.asList(
                entry(LedgerType.EXPENSE, 4000, "Fabric"),
                entry(LedgerType.PAYMENT, 10000, null));
        OrderSummary s = OrderSummary.build(labels(), order(25000, null, null), "Dress", "Fitting 1",
                es, null, TODAY);
        assertEquals(4, s.money.size());
        assertEquals("Work price", s.money.get(0).label);
        assertEquals("250 ₼", s.money.get(0).amount);
        assertEquals("Fabric", s.money.get(1).label);
        assertEquals("+ 40 ₼", s.money.get(1).amount);
        assertEquals("Payment", s.money.get(2).label);
        assertEquals("- 100 ₼", s.money.get(2).amount);
        assertEquals("Balance due", s.money.get(3).label);
        assertEquals("190 ₼", s.money.get(3).amount);
        assertTrue(s.money.get(3).total);
    }

    @Test
    public void overpaidShowsPositiveAmount() {
        List<LedgerEntry> es = Arrays.asList(entry(LedgerType.PAYMENT, 30000, ""));
        OrderSummary s = OrderSummary.build(labels(), order(25000, null, null), null, "Fitting 1",
                es, null, TODAY);
        OrderSummary.MoneyRow last = s.money.get(s.money.size() - 1);
        assertEquals("Overpaid", last.label);
        assertEquals("50 ₼", last.amount);
    }

    @Test
    public void headerKindAndOptionalLines() {
        OrderSummary s = OrderSummary.build(labels(), order(100, " ", "  "), "Dress", "Fitting 1",
                new ArrayList<>(), null, TODAY);
        assertEquals("Order #12", s.header);
        assertEquals("Tailoring · Dress", s.kindLine);
        assertEquals("Order #12 · Ivanova", s.subject);
        assertNull(s.phone);
        assertNull(s.notes);
        assertNull(s.nextVisitLine);
        assertEquals("Deadline: 28.11", s.deadlineLine);
        assertEquals("Date: 23.11", s.dateLine);
        OrderSummary noType = OrderSummary.build(labels(), order(100, null, null), null, "x",
                new ArrayList<>(), null, TODAY);
        assertEquals("Tailoring", noType.kindLine);
    }

    @Test
    public void textHasEveryBlock() {
        OrderSummary s = OrderSummary.build(labels(), order(25000, "+994501", "Hem 2 cm"), "Dress",
                "Fitting 1", new ArrayList<>(), new Appointment(1, 12, 77L, ""), TODAY);
        String expected = "Order #12\nTailoring · Dress\nCustomer: Ivanova\nPhone: +994501\n"
                + "Stage: Fitting 1\nDeadline: 28.11\nNext visit: visit77\n\n"
                + "Work price: 250 ₼\nBalance due: 250 ₼\n\nNotes:\nHem 2 cm\n\nDate: 23.11";
        assertEquals(expected, s.toText());
    }
}
