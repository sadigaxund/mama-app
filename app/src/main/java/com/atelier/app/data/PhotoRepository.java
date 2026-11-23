package com.atelier.app.data;

import android.content.ContentValues;
import android.database.Cursor;

import com.atelier.app.model.OrderPhoto;

import java.util.ArrayList;
import java.util.List;

/** Rows only. Files in app storage are the caller's job. */
public class PhotoRepository {
    private static final String[] COLS = {"id", "order_id", "file_name", "created_at"};
    private final DbHelper helper;

    public PhotoRepository(DbHelper helper) {
        this.helper = helper;
    }

    public long add(long orderId, String fileName, long nowMillis) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Photo file name is blank");
        }
        ContentValues v = new ContentValues();
        v.put("order_id", orderId);
        v.put("file_name", fileName);
        v.put("created_at", nowMillis);
        return helper.getWritableDatabase().insertOrThrow("order_photo", null, v);
    }

    public void delete(long id) {
        helper.getWritableDatabase().delete("order_photo", "id = ?", new String[]{Long.toString(id)});
    }

    /** Newest first. */
    public List<OrderPhoto> listForOrder(long orderId) {
        List<OrderPhoto> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("order_photo", COLS, "order_id = ?",
                new String[]{Long.toString(orderId)}, null, null, "created_at DESC, id DESC")) {
            while (c.moveToNext()) {
                out.add(new OrderPhoto(c.getLong(0), c.getLong(1), c.getString(2), c.getLong(3)));
            }
        }
        return out;
    }

    /** The newest photo, or null. */
    public OrderPhoto newest(long orderId) {
        List<OrderPhoto> all = listForOrder(orderId);
        return all.isEmpty() ? null : all.get(0);
    }
}
