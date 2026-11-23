package com.mama.app.backup;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;

import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.data.PhotoRepository;
import com.mama.app.model.Kind;
import com.mama.app.model.Order;
import com.mama.app.model.ReminderMode;
import com.mama.app.model.Stage;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class BackupDbTest extends AndroidTestCase {
    private static final String NAME = "backup-test.db";
    private DbHelper helper;
    private File out;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getContext().deleteDatabase(NAME);
        helper = new DbHelper(getContext(), NAME);
        out = new File(getContext().getCacheDir(), "backup-test/orders.db");
    }

    @Override
    protected void tearDown() throws Exception {
        helper.close();
        getContext().deleteDatabase(NAME);
        out.delete();
        super.tearDown();
    }

    private long addOrder(String customer) {
        long type = new GarmentTypeRepository(helper).listActive().get(0).id;
        Order o = new Order(0, customer, null, Kind.TAILORING, type, 10000,
                LocalDate.of(2030, 1, 1), null, Stage.values()[0], 1L, 1L, ReminderMode.DEFAULT);
        return new OrderRepository(helper).insert(o, 0, 1L);
    }

    public void testSnapshotIsAValidDatabaseWithTheSameRows() throws Exception {
        long id1 = addOrder("Анна");
        long id2 = addOrder("Мария");
        new PhotoRepository(helper).add(id1, "1-1.jpg", 5L);
        new PhotoRepository(helper).add(id2, "2-1.jpg", 6L);

        BackupDb.snapshot(helper.getWritableDatabase(), out);

        assertTrue(out.isFile());
        assertEquals(BackupDb.Check.OK, BackupDb.validate(out));
        SQLiteDatabase copy = SQLiteDatabase.openDatabase(out.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (Cursor c = copy.rawQuery("SELECT customer_name FROM orders ORDER BY id", null)) {
            assertEquals(2, c.getCount());
            c.moveToFirst();
            assertEquals("Анна", c.getString(0));
            c.moveToNext();
            assertEquals("Мария", c.getString(0));
        } finally {
            copy.close();
        }
        List<String> photos = BackupDb.photoNames(out);
        assertEquals(2, photos.size());
        assertTrue(photos.contains("2-1.jpg"));
        assertFalse(new File(out.getPath() + ".tmp").exists());
    }

    public void testValidateRefusesDatabaseWithoutOrdersTable() {
        File f = new File(getContext().getCacheDir(), "no-orders.db");
        f.delete();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(f, null);
        db.execSQL("CREATE TABLE other (id INTEGER)");
        db.close();
        try {
            assertEquals(BackupDb.Check.NO_ORDERS_TABLE, BackupDb.validate(f));
        } finally {
            f.delete();
        }
    }

    public void testValidateRefusesNewerVersionAndGarbage() throws Exception {
        File f = new File(getContext().getCacheDir(), "newer.db");
        f.delete();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(f, null);
        db.execSQL("CREATE TABLE orders (id INTEGER)");
        db.setVersion(DbHelper.DB_VERSION + 1);
        db.close();
        File junk = new File(getContext().getCacheDir(), "junk.db");
        try {
            assertEquals(BackupDb.Check.APP_TOO_OLD, BackupDb.validate(f));
            java.io.FileOutputStream o = new java.io.FileOutputStream(junk);
            o.write("this is not sqlite, just some text padding to fill a page....".getBytes());
            o.close();
            assertEquals(BackupDb.Check.NOT_A_DATABASE, BackupDb.validate(junk));
        } finally {
            f.delete();
            junk.delete();
        }
    }
}
