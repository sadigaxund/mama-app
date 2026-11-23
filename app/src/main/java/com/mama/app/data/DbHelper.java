package com.mama.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Owns the schema. Pass a null name for an in-memory database in tests. */
public class DbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "mama.db";
    public static final int DB_VERSION = 1;

    static final String[] SEED_TYPES = {
        "Костюм", "Платье", "Брюки", "Юбка", "Пиджак", "Рубашка", "Пальто"
    };

    public DbHelper(Context context) {
        this(context, DB_NAME);
    }

    public DbHelper(Context context, String name) {
        super(context, name, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE garment_type ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                + "archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE orders ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_name TEXT NOT NULL,"
                + "phone TEXT,"
                + "kind TEXT NOT NULL,"
                + "garment_type_id INTEGER NOT NULL REFERENCES garment_type(id),"
                + "work_price INTEGER NOT NULL,"
                + "deadline INTEGER NOT NULL,"
                + "notes TEXT,"
                + "stage TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL,"
                + "stage_changed_at INTEGER NOT NULL,"
                + "reminder_mode TEXT NOT NULL DEFAULT 'DEFAULT')");
        db.execSQL("CREATE INDEX idx_orders_stage ON orders(stage)");
        db.execSQL("CREATE INDEX idx_orders_deadline ON orders(deadline)");
        db.execSQL("CREATE TABLE ledger_entry ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,"
                + "type TEXT NOT NULL,"
                + "amount INTEGER NOT NULL CHECK (amount > 0),"
                + "title TEXT,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_ledger_order ON ledger_entry(order_id)");
        db.execSQL("CREATE TABLE order_reminder ("
                + "order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,"
                + "days_before INTEGER NOT NULL CHECK (days_before >= 1),"
                + "PRIMARY KEY (order_id, days_before))");
        db.execSQL("CREATE INDEX idx_reminder_order ON order_reminder(order_id)");
        db.execSQL("CREATE TABLE appointment ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,"
                + "starts_at INTEGER NOT NULL,"
                + "note TEXT)");
        db.execSQL("CREATE INDEX idx_appointment_order ON appointment(order_id)");
        db.execSQL("CREATE INDEX idx_appointment_starts ON appointment(starts_at)");
        db.execSQL("CREATE TABLE order_photo ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,"
                + "file_name TEXT NOT NULL,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_photo_order ON order_photo(order_id)");

        for (String name : SEED_TYPES) {
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("archived", 0);
            db.insertOrThrow("garment_type", null, v);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first schema. No migrations exist yet.
    }
}
