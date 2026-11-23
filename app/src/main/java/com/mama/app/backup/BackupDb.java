package com.mama.app.backup;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mama.app.data.DbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Works on database files: a consistent copy of the live database, and checks of a downloaded
 * copy. The only SQL here reads a file that is not the live schema's own tables.
 */
public final class BackupDb {
    private BackupDb() {}

    /** Why a downloaded database cannot be restored. */
    public enum Check { OK, NOT_A_DATABASE, NO_ORDERS_TABLE, APP_TOO_OLD }

    /**
     * Copies the database file to {@code target} while holding an exclusive transaction, so no
     * writer changes the file during the copy. A write-ahead log is folded into the file first.
     */
    public static void snapshot(SQLiteDatabase db, File target) throws IOException {
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        if (db.isWriteAheadLoggingEnabled()) {
            try (Cursor c = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                c.moveToFirst();
            }
        }
        File tmp = new File(target.getPath() + ".tmp");
        db.beginTransaction();
        try {
            copy(new File(db.getPath()), tmp);
        } finally {
            db.endTransaction();
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("cannot replace " + target);
        }
        if (!tmp.renameTo(target)) {
            throw new IOException("cannot move " + tmp);
        }
    }

    /** Opens a copy read-only and checks it has the orders table and a version the app can read. */
    public static Check validate(File file) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try (Cursor c = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='orders'", null)) {
                if (!c.moveToFirst()) {
                    return Check.NO_ORDERS_TABLE;
                }
            }
            return db.getVersion() > DbHelper.DB_VERSION ? Check.APP_TOO_OLD : Check.OK;
        } catch (RuntimeException e) {
            // SQLiteException and its kin: the file is not a readable SQLite database.
            return Check.NOT_A_DATABASE;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    /** The photo file names the copy's order_photo table lists. */
    public static List<String> photoNames(File file) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (Cursor c = db.rawQuery("SELECT file_name FROM order_photo", null)) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        } finally {
            db.close();
        }
        return out;
    }

    static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }
}
