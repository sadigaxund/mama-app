package com.atelier.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;

import com.atelier.app.model.GarmentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GarmentTypeRepository {
    private static final String COLS = "id, name, archived";
    private final DbHelper helper;

    public GarmentTypeRepository(DbHelper helper) {
        this.helper = helper;
    }

    public List<GarmentType> listActive() {
        return query("archived = 0");
    }

    public List<GarmentType> listAll() {
        return query(null);
    }

    private List<GarmentType> query(String where) {
        List<GarmentType> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("garment_type",
                new String[]{"id", "name", "archived"}, where, null, null, null, "name COLLATE NOCASE, id")) {
            while (c.moveToNext()) {
                out.add(read(c));
            }
        }
        return out;
    }

    static GarmentType read(Cursor c) {
        return new GarmentType(c.getLong(0), c.getString(1), c.getInt(2) != 0);
    }

    /** Adds a type and returns its id. Throws DuplicateGarmentTypeException ignoring case. */
    public long add(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Garment type name is blank");
        }
        String trimmed = name.trim();
        // SQLite NOCASE folds ASCII only, so Cyrillic duplicates are caught here.
        for (GarmentType t : listAll()) {
            if (t.name.toLowerCase(Locale.ROOT).equals(trimmed.toLowerCase(Locale.ROOT))) {
                throw new DuplicateGarmentTypeException(trimmed);
            }
        }
        ContentValues v = new ContentValues();
        v.put("name", trimmed);
        v.put("archived", 0);
        try {
            return helper.getWritableDatabase().insertOrThrow("garment_type", null, v);
        } catch (SQLiteConstraintException e) {
            throw new DuplicateGarmentTypeException(trimmed);
        }
    }

    public void archive(long id) {
        setArchived(id, 1);
    }

    public void unarchive(long id) {
        setArchived(id, 0);
    }

    private void setArchived(long id, int value) {
        ContentValues v = new ContentValues();
        v.put("archived", value);
        helper.getWritableDatabase().update("garment_type", v, "id = ?", new String[]{Long.toString(id)});
    }

    /** Returns the type, or null when the id does not exist. */
    public GarmentType get(long id) {
        try (Cursor c = helper.getReadableDatabase().query("garment_type",
                new String[]{"id", "name", "archived"}, "id = ?", new String[]{Long.toString(id)},
                null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
}
