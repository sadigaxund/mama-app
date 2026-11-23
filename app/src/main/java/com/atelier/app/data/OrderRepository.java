package com.atelier.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.atelier.app.model.Kind;
import com.atelier.app.model.LedgerType;
import com.atelier.app.model.Order;
import com.atelier.app.model.OrderFilter;
import com.atelier.app.model.ReminderMode;
import com.atelier.app.model.Stage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderRepository {
    private static final String[] COLS = {
        "id", "customer_name", "phone", "kind", "garment_type_id", "work_price", "deadline",
        "notes", "stage", "created_at", "stage_changed_at", "reminder_mode"
    };

    private final DbHelper helper;

    public OrderRepository(DbHelper helper) {
        this.helper = helper;
    }

    private static void validate(Order o) {
        if (o.customerName == null || o.customerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is blank");
        }
        if (o.workPrice < 0) {
            throw new IllegalArgumentException("Work price is negative");
        }
        if (o.kind == null || o.stage == null || o.deadline == null || o.reminderMode == null) {
            throw new IllegalArgumentException("Kind, stage, deadline and reminder mode are required");
        }
    }

    /**
     * Inserts the order and returns its new id. The order's own id, created_at and
     * stage_changed_at are ignored: both times are set to nowMillis. A deposit above zero adds
     * a PAYMENT ledger row with a null title in the same transaction.
     */
    public long insert(Order o, long depositMinor, long nowMillis) {
        validate(o);
        if (depositMinor < 0) {
            throw new IllegalArgumentException("Deposit is negative");
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = values(o);
            v.put("created_at", nowMillis);
            v.put("stage_changed_at", nowMillis);
            long id = db.insertOrThrow("orders", null, v);
            if (depositMinor > 0) {
                ContentValues l = new ContentValues();
                l.put("order_id", id);
                l.put("type", LedgerType.PAYMENT.name());
                l.put("amount", depositMinor);
                l.putNull("title");
                l.put("created_at", nowMillis);
                db.insertOrThrow("ledger_entry", null, l);
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    /** Updates the editable fields. Stage, created_at and reminder mode are not touched. */
    public void update(Order o) {
        validate(o);
        ContentValues v = values(o);
        v.remove("stage");
        v.remove("reminder_mode");
        int n = helper.getWritableDatabase().update("orders", v, "id = ?", new String[]{Long.toString(o.id)});
        if (n == 0) {
            throw new IllegalArgumentException("No order with id " + o.id);
        }
    }

    private static ContentValues values(Order o) {
        ContentValues v = new ContentValues();
        v.put("customer_name", o.customerName.trim());
        v.put("phone", o.phone);
        v.put("kind", o.kind.name());
        v.put("garment_type_id", o.garmentTypeId);
        v.put("work_price", o.workPrice);
        v.put("deadline", o.deadline.toEpochDay());
        v.put("notes", o.notes);
        v.put("stage", o.stage.name());
        v.put("reminder_mode", o.reminderMode.name());
        return v;
    }

    /** The number of orders in every stage. */
    public long count() {
        try (android.database.Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM orders", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete("orders", "id = ?", new String[]{Long.toString(id)});
    }

    /** Returns the order, or null when the id does not exist. */
    public Order get(long id) {
        try (Cursor c = helper.getReadableDatabase().query("orders", COLS, "id = ?",
                new String[]{Long.toString(id)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    static Order read(Cursor c) {
        return new Order(c.getLong(0), c.getString(1), c.getString(2),
                Kind.valueOf(c.getString(3)), c.getLong(4), c.getLong(5),
                LocalDate.ofEpochDay(c.getLong(6)), c.getString(7), Stage.valueOf(c.getString(8)),
                c.getLong(9), c.getLong(10), ReminderMode.valueOf(c.getString(11)));
    }

    /** Active stages: deadline ascending, then id. DONE: stage_changed_at descending, then id. */
    public List<Order> listByStage(Stage stage) {
        String order = stage == Stage.DONE ? "stage_changed_at DESC, id DESC" : "deadline ASC, id ASC";
        List<Order> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("orders", COLS, "stage = ?",
                new String[]{stage.name()}, null, null, order)) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        return out;
    }

    /** Orders of a stage that pass the filter, in listByStage order. */
    public List<Order> listByStage(Stage stage, OrderFilter filter) {
        List<Order> out = new ArrayList<>();
        for (Order o : listByStage(stage)) {
            if (filter.matches(o)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Like countsByStage, counting only orders that pass the filter. */
    public Map<Stage, Integer> countsByStage(OrderFilter filter) {
        Map<Stage, Integer> out = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) {
            out.put(s, 0);
        }
        try (Cursor c = helper.getReadableDatabase().query("orders", COLS, null, null, null, null, null)) {
            while (c.moveToNext()) {
                Order o = read(c);
                if (filter.matches(o)) {
                    out.put(o.stage, out.get(o.stage) + 1);
                }
            }
        }
        return out;
    }

    /** Order count per stage. Every stage has an entry, zero when empty. */
    public Map<Stage, Integer> countsByStage() {
        Map<Stage, Integer> out = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) {
            out.put(s, 0);
        }
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT stage, COUNT(*) FROM orders GROUP BY stage", null)) {
            while (c.moveToNext()) {
                out.put(Stage.valueOf(c.getString(0)), c.getInt(1));
            }
        }
        return out;
    }

    /**
     * Moves the order to a new stage and stamps stage_changed_at. Throws IllegalStateException
     * for a move Stage.canMoveTo forbids, and IllegalArgumentException for an unknown id.
     */
    public void moveTo(long id, Stage target, long nowMillis) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            Order o = get(id);
            if (o == null) {
                throw new IllegalArgumentException("No order with id " + id);
            }
            if (!o.stage.canMoveTo(target)) {
                throw new IllegalStateException("Cannot move from " + o.stage + " to " + target);
            }
            ContentValues v = new ContentValues();
            v.put("stage", target.name());
            v.put("stage_changed_at", nowMillis);
            db.update("orders", v, "id = ?", new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Puts the order back on a stage with its old stage_changed_at. For undoing a move only:
     * it skips the move rules, because the move being undone may not be an allowed move back
     * (FITTING_1 to DONE is undone to FITTING_1). Throws IllegalArgumentException for an
     * unknown id.
     */
    public void restoreStage(long id, Stage stage, long stageChangedAt) {
        ContentValues v = new ContentValues();
        v.put("stage", stage.name());
        v.put("stage_changed_at", stageChangedAt);
        int n = helper.getWritableDatabase().update("orders", v, "id = ?", new String[]{Long.toString(id)});
        if (n == 0) {
            throw new IllegalArgumentException("No order with id " + id);
        }
    }

    /**
     * Matches customer name or phone as a case-insensitive substring, or the order id exactly.
     * A leading "#" on the query is ignored for the id match. Blank query returns nothing.
     * Case folding runs in Java because SQLite LIKE folds ASCII only, not Cyrillic.
     */
    public List<Order> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        String idText = q.startsWith("#") ? q.substring(1) : q;
        List<Order> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("orders", COLS, null, null,
                null, null, "deadline ASC, id ASC")) {
            while (c.moveToNext()) {
                Order o = read(c);
                boolean hit = o.customerName.toLowerCase(Locale.ROOT).contains(q)
                        || (o.phone != null && o.phone.toLowerCase(Locale.ROOT).contains(q))
                        || Long.toString(o.id).equals(idText);
                if (hit) {
                    out.add(o);
                }
            }
        }
        return out;
    }

    /** search(query) narrowed by the filter, active stages first by deadline, then DONE newest first. */
    public List<Order> search(String query, OrderFilter filter) {
        List<Order> out = new ArrayList<>();
        for (Order o : search(query)) {
            if (filter.matches(o)) {
                out.add(o);
            }
        }
        Collections.sort(out, OrderFilter.SEARCH_ORDER);
        return out;
    }

    public ReminderMode getReminderMode(long orderId) {
        Order o = get(orderId);
        if (o == null) {
            throw new IllegalArgumentException("No order with id " + orderId);
        }
        return o.reminderMode;
    }

    /** Custom days, ascending. Empty unless the mode is CUSTOM. */
    public List<Integer> getCustomDays(long orderId) {
        List<Integer> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("order_reminder",
                new String[]{"days_before"}, "order_id = ?", new String[]{Long.toString(orderId)},
                null, null, "days_before ASC")) {
            while (c.moveToNext()) {
                out.add(c.getInt(0));
            }
        }
        return out;
    }

    /** DEFAULT and OFF delete the custom rows. */
    public void setReminderMode(long orderId, ReminderMode mode) {
        if (mode == ReminderMode.CUSTOM) {
            throw new IllegalArgumentException("Use setCustomReminders for CUSTOM");
        }
        writeReminders(orderId, mode, Collections.<Integer>emptyList());
    }

    /** Sets mode CUSTOM and replaces the rows. Needs at least one day, each 1 or more. */
    public void setCustomReminders(long orderId, List<Integer> daysBefore) {
        if (daysBefore == null || daysBefore.isEmpty()) {
            throw new IllegalArgumentException("Custom reminders need at least one day");
        }
        for (Integer d : daysBefore) {
            if (d == null || d < 1) {
                throw new IllegalArgumentException("days_before must be 1 or more");
            }
        }
        writeReminders(orderId, ReminderMode.CUSTOM, daysBefore);
    }

    private void writeReminders(long orderId, ReminderMode mode, List<Integer> days) {
        SQLiteDatabase db = helper.getWritableDatabase();
        String[] arg = {Long.toString(orderId)};
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("reminder_mode", mode.name());
            if (db.update("orders", v, "id = ?", arg) == 0) {
                throw new IllegalArgumentException("No order with id " + orderId);
            }
            db.delete("order_reminder", "order_id = ?", arg);
            for (Integer d : new java.util.TreeSet<>(days)) {
                ContentValues r = new ContentValues();
                r.put("order_id", orderId);
                r.put("days_before", d);
                db.insertOrThrow("order_reminder", null, r);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
