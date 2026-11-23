package com.mama.app.backup;

import android.test.AndroidTestCase;

import com.mama.app.data.DbHelper;
import com.mama.app.data.GarmentTypeRepository;
import com.mama.app.data.OrderRepository;
import com.mama.app.model.Kind;
import com.mama.app.model.Order;
import com.mama.app.model.ReminderMode;
import com.mama.app.model.Stage;

import java.io.File;
import java.time.LocalDate;

/** Replaces the app database file under a live helper. Runs against mama.db, which it deletes at the end. */
public class RestoreSwapTest extends AndroidTestCase {
    private static final String SOURCE = "swap-source.db";

    @Override
    protected void tearDown() throws Exception {
        DbHelper.closeAllOnAppDatabase();
        getContext().deleteDatabase(DbHelper.DB_NAME);
        getContext().deleteDatabase(SOURCE);
        super.tearDown();
    }

    public void testLiveHelperSeesTheRestoredFile() throws Exception {
        getContext().deleteDatabase(DbHelper.DB_NAME);
        getContext().deleteDatabase(SOURCE);

        DbHelper source = new DbHelper(getContext(), SOURCE);
        long type = new GarmentTypeRepository(source).listActive().get(0).id;
        new OrderRepository(source).insert(new Order(0, "Анна", null, Kind.TAILORING, type, 5000,
                LocalDate.of(2030, 1, 1), null, Stage.values()[0], 1L, 1L, ReminderMode.DEFAULT), 0, 1L);
        File copy = new File(getContext().getCacheDir(), "swap-copy.db");
        BackupDb.snapshot(source.getWritableDatabase(), copy);
        source.close();

        DbHelper live = new DbHelper(getContext());
        OrderRepository orders = new OrderRepository(live);
        assertEquals(0, orders.count());

        RestoreRunner.replaceDatabase(getContext(), copy);
        copy.delete();

        assertEquals(1, orders.count());
        assertEquals(0, getContext().getDatabasePath(DbHelper.DB_NAME).getParentFile()
                .listFiles((d, n) -> n.startsWith(DbHelper.DB_NAME) && n.endsWith(".restore")).length);
    }
}
