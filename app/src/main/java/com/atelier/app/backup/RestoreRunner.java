package com.atelier.app.backup;

import android.content.Context;
import android.util.Log;

import com.atelier.app.data.DbHelper;
import com.atelier.app.data.OrderRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Finds the Drive backup and restores it into an app that has no orders. Blocking. */
public final class RestoreRunner {
    private static final String TAG = "Atelier";

    /** The Drive copy of the database. */
    public static final class Found {
        public final String fileId;
        public final long modifiedMillis;
        final String photosFolderId;

        Found(String fileId, long modifiedMillis, String photosFolderId) {
            this.fileId = fileId;
            this.modifiedMillis = modifiedMillis;
            this.photosFolderId = photosFolderId;
        }
    }

    public enum Result { RESTORED, APP_TOO_OLD, INVALID_BACKUP, HAS_ORDERS }

    private RestoreRunner() {}

    /** True when the app holds at least one order. */
    public static boolean hasOrders(Context context) {
        DbHelper helper = new DbHelper(context);
        try {
            return new OrderRepository(helper).count() > 0;
        } finally {
            helper.close();
        }
    }

    /** The backup in Drive, or null when there is none. Never creates anything. */
    public static Found find(Context context) throws DriveException {
        DriveClient drive = new DriveClient(DriveAuth.accessTokenBlocking(context));
        String folder = drive.findFolder(BackupRunner.ROOT_FOLDER, "root");
        if (folder == null) {
            return null;
        }
        DriveClient.DriveFile db = drive.findFile(folder, BackupRunner.DB_FILE);
        if (db == null) {
            return null;
        }
        return new Found(db.id, db.modifiedMillis, drive.findFolder(BackupRunner.PHOTOS_FOLDER, folder));
    }

    /**
     * Downloads and checks the copy, downloads the photos it lists, then replaces the database.
     * Nothing local changes before every download has finished. Callers restart the UI after
     * {@link Result#RESTORED}.
     */
    public static Result restore(Context context, Found found) throws DriveException, IOException {
        Context app = context.getApplicationContext();
        if (hasOrders(app)) {
            return Result.HAS_ORDERS;
        }
        DriveClient drive = new DriveClient(DriveAuth.accessTokenBlocking(app));
        File dir = new File(app.getCacheDir(), "restore");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        File downloaded = new File(dir, BackupRunner.DB_FILE);
        try {
            drive.download(found.fileId, downloaded);
            BackupDb.Check check = BackupDb.validate(downloaded);
            if (check == BackupDb.Check.APP_TOO_OLD) {
                return Result.APP_TOO_OLD;
            }
            if (check != BackupDb.Check.OK) {
                Log.w(TAG, "backup file refused: " + check);
                return Result.INVALID_BACKUP;
            }
            downloadPhotos(app, drive, found, BackupDb.photoNames(downloaded));
            replaceDatabase(app, downloaded);
            BackupPrefs.clearFingerprint(app);
            return Result.RESTORED;
        } finally {
            if (downloaded.exists() && !downloaded.delete()) {
                Log.w(TAG, "restore file not removed: " + downloaded);
            }
        }
    }

    private static void downloadPhotos(Context app, DriveClient drive, Found found, List<String> names)
            throws DriveException, IOException {
        File photoDir = new File(app.getFilesDir(), "photos");
        if (!photoDir.isDirectory() && !photoDir.mkdirs()) {
            throw new IOException("cannot create " + photoDir);
        }
        java.util.Map<String, String> remote = new java.util.HashMap<>();
        if (found.photosFolderId != null) {
            for (DriveClient.DriveFile f : drive.listChildren(found.photosFolderId)) {
                remote.put(f.name, f.id);
            }
        }
        for (String name : names) {
            File target = new File(photoDir, name);
            if (target.isFile()) {
                continue;
            }
            String id = remote.get(name);
            if (id == null) {
                // The copy lists a photo that Drive no longer holds. The order still restores.
                Log.w(TAG, "photo missing in Drive: " + name);
                continue;
            }
            drive.download(id, target);
        }
    }

    static void replaceDatabase(Context app, File downloaded) throws IOException {
        DbHelper.closeAllOnAppDatabase();
        File db = app.getDatabasePath(DbHelper.DB_NAME);
        for (String suffix : new String[]{"-journal", "-wal", "-shm"}) {
            File side = new File(db.getPath() + suffix);
            if (side.exists() && !side.delete()) {
                throw new IOException("cannot remove " + side);
            }
        }
        File next = new File(db.getPath() + ".restore");
        BackupDb.copy(downloaded, next);
        if (!next.renameTo(db)) {
            throw new IOException("cannot move " + next + " over " + db);
        }
    }
}
