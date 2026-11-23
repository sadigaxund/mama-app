package com.mama.app.data;

import android.content.ContentValues;
import android.database.Cursor;

import com.mama.app.model.LedgerEntry;
import com.mama.app.model.LedgerType;
import com.mama.app.model.Money;
import com.mama.app.model.Order;

import java.util.ArrayList;
import java.util.List;

public class LedgerRepository {
    private final DbHelper helper;

    public LedgerRepository(DbHelper helper) {
        this.helper = helper;
    }

    /** Adds an entry and returns its id. Amount must be above zero. Title may be null. */
    public long add(long orderId, LedgerType type, long amountMinor, String title, long nowMillis) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (type == null) {
            throw new IllegalArgumentException("Ledger type is required");
        }
        ContentValues v = new ContentValues();
        v.put("order_id", orderId);
        v.put("type", type.name());
        v.put("amount", amountMinor);
        v.put("title", title);
        v.put("created_at", nowMillis);
        return helper.getWritableDatabase().insertOrThrow("ledger_entry", null, v);
    }

    public void delete(long entryId) {
        helper.getWritableDatabase().delete("ledger_entry", "id = ?", new String[]{Long.toString(entryId)});
    }

    /** Oldest first. */
    public List<LedgerEntry> listForOrder(long orderId) {
        List<LedgerEntry> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("ledger_entry",
                new String[]{"id", "order_id", "type", "amount", "title", "created_at"},
                "order_id = ?", new String[]{Long.toString(orderId)}, null, null, "created_at ASC, id ASC")) {
            while (c.moveToNext()) {
                out.add(new LedgerEntry(c.getLong(0), c.getLong(1), LedgerType.valueOf(c.getString(2)),
                        c.getLong(3), c.getString(4), c.getLong(5)));
            }
        }
        return out;
    }

    /** work price + expenses - payments, from the stored rows. */
    public long balanceDue(long orderId) {
        Order o = new OrderRepository(helper).get(orderId);
        if (o == null) {
            throw new IllegalArgumentException("No order with id " + orderId);
        }
        return Money.balanceDue(o.workPrice, listForOrder(orderId));
    }
}
