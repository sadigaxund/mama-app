package com.atelier.app.share;

import com.atelier.app.model.Appointment;
import com.atelier.app.model.Kind;
import com.atelier.app.model.LedgerEntry;
import com.atelier.app.model.LedgerType;
import com.atelier.app.model.Money;
import com.atelier.app.model.Order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * The localized content of a shared order. Text, image and PDF all read this one object, so they
 * never disagree. It has no android imports; OrderSummaryTest holds the line rules.
 */
public final class OrderSummary {

    /** The words and formatters a summary needs. The caller fills them from resources. */
    public static final class Labels {
        /** "Order #%1$d". */
        public String header = "Order #%1$d";
        /** "Customer: %1$s". Used by the text form only. */
        public String customer = "Customer: %1$s";
        /** "Phone: %1$s". Used by the text form only. */
        public String phone = "Phone: %1$s";
        /** "Stage: %1$s". */
        public String stage = "Stage: %1$s";
        /** "Deadline: %1$s". */
        public String deadline = "Deadline: %1$s";
        /** "Next visit: %1$s". */
        public String nextVisit = "Next visit: %1$s";
        public String kindTailoring = "Tailoring";
        public String kindRepair = "Repair";
        public String price = "Work price";
        public String expenseDefault = "Expense";
        public String paymentDefault = "Payment";
        /** "+ %1$s". */
        public String expenseAmount = "+ %1$s";
        /** "- %1$s". */
        public String paymentAmount = "- %1$s";
        public String balance = "Balance due";
        public String overpaid = "Overpaid";
        /** The heading above the notes. */
        public String notes = "Notes";
        /** "Date: %1$s". */
        public String date = "Date: %1$s";
        /** Minor units to "190 ₼". */
        public LongFunction<String> money = m -> Money.formatMinor(m);
        /** A date to its display text. */
        public Function<LocalDate, String> dateText = LocalDate::toString;
        /** A stored visit instant to its display text. */
        public LongFunction<String> visitText = Long::toString;
    }

    /** One line of the money table. {@code total} marks the balance line. */
    public static final class MoneyRow {
        public final String label;
        public final String amount;
        public final boolean total;

        MoneyRow(String label, String amount, boolean total) {
            this.label = label;
            this.amount = amount;
            this.total = total;
        }
    }

    public final long orderId;
    public final Kind kind;
    public final String header;
    /** "Tailoring · Dress", or the kind alone when the type is unknown. */
    public final String kindLine;
    public final String customer;
    /** Null when the order has no phone. */
    public final String phone;
    public final String stageLine;
    public final String deadlineLine;
    /** Null when no visit is booked. */
    public final String nextVisitLine;
    public final List<MoneyRow> money;
    /** Null when the order has no notes. */
    public final String notes;
    public final String notesHeading;
    public final String dateLine;
    /** The mail subject: the header and the customer. */
    public final String subject;
    private final Labels labels;

    private OrderSummary(Labels l, Order o, String typeName, String stageName,
                         List<MoneyRow> money, Appointment next, LocalDate today) {
        this.labels = l;
        this.orderId = o.id;
        this.kind = o.kind;
        this.header = String.format(l.header, o.id);
        String kindName = o.kind == Kind.TAILORING ? l.kindTailoring : l.kindRepair;
        this.kindLine = typeName == null || typeName.trim().isEmpty()
                ? kindName : kindName + " · " + typeName;
        this.customer = o.customerName;
        this.phone = blank(o.phone) ? null : o.phone.trim();
        this.stageLine = String.format(l.stage, stageName);
        this.deadlineLine = String.format(l.deadline, l.dateText.apply(o.deadline));
        this.nextVisitLine = next == null ? null
                : String.format(l.nextVisit, l.visitText.apply(next.startsAt));
        this.money = Collections.unmodifiableList(money);
        this.notes = blank(o.notes) ? null : o.notes.trim();
        this.notesHeading = l.notes;
        this.dateLine = String.format(l.date, l.dateText.apply(today));
        this.subject = header + " · " + o.customerName;
    }

    /**
     * Builds the summary. {@code typeName} and {@code next} may be null. The balance line is
     * work price + expenses - payments, from {@link Money#balanceDue}.
     */
    public static OrderSummary build(Labels l, Order o, String typeName, String stageName,
                                     List<LedgerEntry> entries, Appointment next, LocalDate today) {
        List<MoneyRow> rows = new ArrayList<>();
        rows.add(new MoneyRow(l.price, l.money.apply(o.workPrice), false));
        for (LedgerEntry e : entries) {
            boolean expense = e.type == LedgerType.EXPENSE;
            String label = blank(e.title) ? (expense ? l.expenseDefault : l.paymentDefault)
                    : e.title.trim();
            String amount = String.format(expense ? l.expenseAmount : l.paymentAmount,
                    l.money.apply(e.amount));
            rows.add(new MoneyRow(label, amount, false));
        }
        long balance = Money.balanceDue(o.workPrice, entries);
        if (balance < 0) {
            rows.add(new MoneyRow(l.overpaid, l.money.apply(-balance), true));
        } else {
            rows.add(new MoneyRow(l.balance, l.money.apply(balance), true));
        }
        return new OrderSummary(l, o, typeName, stageName, rows, next, today);
    }

    /** The plain-text form: one fact per line, a blank line between blocks. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append('\n');
        sb.append(kindLine).append('\n');
        sb.append(String.format(labels.customer, customer)).append('\n');
        if (phone != null) {
            sb.append(String.format(labels.phone, phone)).append('\n');
        }
        sb.append(stageLine).append('\n');
        sb.append(deadlineLine).append('\n');
        if (nextVisitLine != null) {
            sb.append(nextVisitLine).append('\n');
        }
        sb.append('\n');
        for (MoneyRow r : money) {
            sb.append(r.label).append(": ").append(r.amount).append('\n');
        }
        if (notes != null) {
            sb.append('\n').append(notesHeading).append(":\n").append(notes).append('\n');
        }
        sb.append('\n').append(dateLine);
        return sb.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
