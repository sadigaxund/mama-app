package com.mama.app.data;

import android.content.ContentValues;
import android.database.Cursor;

import com.mama.app.model.Appointment;

import java.util.ArrayList;
import java.util.List;

public class AppointmentRepository {
    private static final String[] COLS = {"id", "order_id", "starts_at", "note"};
    private final DbHelper helper;

    public AppointmentRepository(DbHelper helper) {
        this.helper = helper;
    }

    public long add(long orderId, long startsAtMillis, String note) {
        ContentValues v = new ContentValues();
        v.put("order_id", orderId);
        v.put("starts_at", startsAtMillis);
        v.put("note", note);
        return helper.getWritableDatabase().insertOrThrow("appointment", null, v);
    }

    public void update(long id, long startsAtMillis, String note) {
        ContentValues v = new ContentValues();
        v.put("starts_at", startsAtMillis);
        v.put("note", note);
        int n = helper.getWritableDatabase().update("appointment", v, "id = ?", new String[]{Long.toString(id)});
        if (n == 0) {
            throw new IllegalArgumentException("No appointment with id " + id);
        }
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete("appointment", "id = ?", new String[]{Long.toString(id)});
    }

    /** Earliest first. */
    public List<Appointment> listForOrder(long orderId) {
        return query("order_id = ?", new String[]{Long.toString(orderId)}, null);
    }

    /** The earliest appointment starting at or after nowMillis, or null. */
    public Appointment nextUpcoming(long orderId, long nowMillis) {
        List<Appointment> r = query("order_id = ? AND starts_at >= ?",
                new String[]{Long.toString(orderId), Long.toString(nowMillis)}, "1");
        return r.isEmpty() ? null : r.get(0);
    }

    /** Appointments with from <= starts_at < to, earliest first, across all orders. */
    public List<Appointment> listBetween(long fromMillis, long toMillis) {
        return query("starts_at >= ? AND starts_at < ?",
                new String[]{Long.toString(fromMillis), Long.toString(toMillis)}, null);
    }

    private List<Appointment> query(String where, String[] args, String limit) {
        List<Appointment> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("appointment", COLS, where, args,
                null, null, "starts_at ASC, id ASC", limit)) {
            while (c.moveToNext()) {
                out.add(new Appointment(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3)));
            }
        }
        return out;
    }
}
