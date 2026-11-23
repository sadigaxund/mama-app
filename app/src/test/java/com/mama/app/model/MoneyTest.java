package com.mama.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MoneyTest {
    private static LedgerEntry e(LedgerType t, long amount) {
        return new LedgerEntry(0, 1, t, amount, null, 0);
    }

    @Test
    public void balanceWithNoEntriesIsWorkPrice() {
        assertEquals(50000, Money.balanceDue(50000, new ArrayList<LedgerEntry>()));
    }

    @Test
    public void balanceAddsExpensesAndSubtractsPayments() {
        List<LedgerEntry> l = new ArrayList<>();
        l.add(e(LedgerType.PAYMENT, 10000));
        l.add(e(LedgerType.EXPENSE, 2500));
        l.add(e(LedgerType.PAYMENT, 5000));
        l.add(e(LedgerType.EXPENSE, 500));
        assertEquals(50000 + 3000 - 15000, Money.balanceDue(50000, l));
    }

    @Test
    public void overpaymentIsNegative() {
        List<LedgerEntry> l = new ArrayList<>();
        l.add(e(LedgerType.PAYMENT, 7000));
        assertEquals(-2000, Money.balanceDue(5000, l));
    }

    @Test
    public void zeroWorkPriceWithExpense() {
        List<LedgerEntry> l = new ArrayList<>();
        l.add(e(LedgerType.EXPENSE, 100));
        assertEquals(100, Money.balanceDue(0, l));
    }

    @Test
    public void parseAcceptedForms() {
        assertEquals(25000, Money.parseMinor("250"));
        assertEquals(25050, Money.parseMinor("250.5"));
        assertEquals(25050, Money.parseMinor("250,50"));
        assertEquals(25005, Money.parseMinor("250.05"));
        assertEquals(0, Money.parseMinor("0"));
        assertEquals(5, Money.parseMinor("0,05"));
        assertEquals(25000, Money.parseMinor("  250  "));
        assertEquals(25000, Money.parseMinor("250.00"));
    }

    private static void rejects(String s) {
        try {
            Money.parseMinor(s);
            fail("accepted: " + s);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void parseRejectsBadInput() {
        rejects(null);
        rejects("");
        rejects("  ");
        rejects("-5");
        rejects("+5");
        rejects("abc");
        rejects("12a");
        rejects("1.234");
        rejects("1,234");
        rejects("1.2.3");
        rejects(".5");
        rejects("5.");
        rejects("1 000");
        rejects("9999999999999999999");
    }

    @Test
    public void formatWholeAndFraction() {
        assertEquals("250", Money.formatMinor(25000));
        assertEquals("250.50", Money.formatMinor(25050));
        assertEquals("250.05", Money.formatMinor(25005));
        assertEquals("0", Money.formatMinor(0));
        assertEquals("0.05", Money.formatMinor(5));
        assertEquals("-20", Money.formatMinor(-2000));
        assertEquals("-0.50", Money.formatMinor(-50));
    }

    @Test
    public void formatThenParseRoundTrips() {
        long[] samples = {0, 1, 5, 99, 100, 101, 25050, 123456789};
        for (long s : samples) {
            assertEquals(s, Money.parseMinor(Money.formatMinor(s)));
        }
    }
}
