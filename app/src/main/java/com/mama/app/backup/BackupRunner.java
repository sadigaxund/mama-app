package com.mama.app.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mama.app.data.DbHelper;
import com.mama.app.data.OrderRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One backup run: photos first, the database last, nothing deleted in Drive. The run is
 * blocking, so callers use a background thread. One run at a time per process.
 */
public final class BackupRunner {
    private static final String TAG = "MamaApp";
    public static final String ROOT_FOLDER = "Mama App";
    public static final String PHOTOS_FOLDER = "photos";
    public static final String DB_FILE = "orders.db";

    public enum Outcome { SUCCESS, UNCHANGED, NOT_SIGNED_IN, NOTHING_TO_BACK_UP, ALREADY_RUNNING, FAILED }

    public enum Phase { IDLE, PREPARING, PHOTOS, DATABASE }

    /** What the run is doing now, for Settings. */
    public static final class Progress {
        public final Phase phase;
        public final int done;
        public final int total;

        Progress(Phase phase, int done, int total) {
            this.phase = phase;
            this.done = done;
            this.total = total;
        }
    }

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Progress progress = new Progress(Phase.IDLE, 0, 0);
    private static volatile Runnable listener;

    private BackupRunner() {}

    public static boolean isRunning() {
        return RUNNING.get();
    }

    public static Progress progress() {
        return progress;
    }

    /** Called on the main thread when the progress changes. Pass null to remove it. */
    public static void setListener(Runnable r) {
        listener = r;
    }

    private static void publish(Phase phase, int done, int total) {
        progress = new Progress(phase, done, total);
        final Runnable l = listener;
        if (l != null) {
            MAIN.post(l);
        }
    }

    public static Outcome run(Context context, boolean force) {
        final Context app = context.getApplicationContext();
        if (!BackupPrefs.signedIn(app)) {
            return Outcome.NOT_SIGNED_IN;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            return Outcome.ALREADY_RUNNING;
        }
        publish(Phase.PREPARING, 0, 0);
        try {
            return runOnce(app, force, true);
        } catch (DriveException e) {
            Log.w(TAG, "backup failed: " + e.reason + " " + e.getMessage(), e);
            BackupPrefs.recordFailure(app, System.currentTimeMillis(), failureOf(e));
            return Outcome.FAILED;
        } catch (IOException e) {
            Log.w(TAG, "backup failed on local files", e);
            BackupPrefs.recordFailure(app, System.currentTimeMillis(), BackupPrefs.Failure.OTHER);
            return Outcome.FAILED;
        } catch (RuntimeException e) {
            // A background run must end in a recorded failure, never a crash dialog.
            Log.e(TAG, "backup failed unexpectedly", e);
            BackupPrefs.recordFailure(app, System.currentTimeMillis(), BackupPrefs.Failure.OTHER);
            return Outcome.FAILED;
        } finally {
            RUNNING.set(false);
            publish(Phase.IDLE, 0, 0);
        }
    }

    static BackupPrefs.Failure failureOf(DriveException e) {
        switch (e.reason) {
            case AUTH:
                return BackupPrefs.Failure.NEEDS_SIGN_IN;
            case NETWORK:
                return BackupPrefs.Failure.NETWORK;
            case QUOTA:
                return BackupPrefs.Failure.QUOTA;
            default:
                return BackupPrefs.Failure.OTHER;
        }
    }

    private static Outcome runOnce(Context app, boolean force, boolean mayRetry)
            throws DriveException, IOException {
        File dbFile = app.getDatabasePath(DbHelper.DB_NAME);
        File photoDir = new File(app.getFilesDir(), "photos");
        List<String> localPhotos = listPhotos(photoDir);

        DbHelper helper = new DbHelper(app);
        try {
            // An empty app must not overwrite a backup that a restore could still use.
            if (new OrderRepository(helper).count() == 0) {
                return Outcome.NOTHING_TO_BACK_UP;
            }
            String fingerprint = BackupFingerprint.compute(dbFile.length(), dbFile.lastModified(), localPhotos);
            if (!BackupFingerprint.needsRun(force, BackupPrefs.lastFingerprint(app), fingerprint)) {
                return Outcome.UNCHANGED;
            }

            String token = DriveAuth.accessTokenBlocking(app);
            try {
                upload(app, new DriveClient(token), helper, photoDir, localPhotos);
            } catch (DriveException e) {
                if (e.reason == DriveException.Reason.AUTH && mayRetry) {
                    // The cached token may have gone stale: drop it and try once more with a fresh one.
                    DriveAuth.clearTokenBlocking(app, token);
                    return runOnce(app, force, false);
                }
                throw e;
            }
            BackupPrefs.recordSuccess(app, System.currentTimeMillis(), fingerprint);
            return Outcome.SUCCESS;
        } finally {
            helper.close();
        }
    }

    private static void upload(Context app, DriveClient drive, DbHelper helper, File photoDir,
                               List<String> localPhotos) throws DriveException, IOException {
        String[] ids = ensureFolders(app, drive);
        String folderId = ids[0];
        String photosId = ids[1];

        List<String> remote = new ArrayList<>();
        for (DriveClient.DriveFile f : drive.listChildren(photosId)) {
            remote.add(f.name);
        }
        List<String> todo = BackupFingerprint.toUpload(localPhotos, remote);
        int done = 0;
        for (String name : todo) {
            publish(Phase.PHOTOS, done, todo.size());
            File f = new File(photoDir, name);
            if (f.isFile()) {
                drive.upload(name, photosId, "image/jpeg", f, null);
            }
            done++;
        }

        publish(Phase.DATABASE, 0, 1);
        File snapshot = new File(new File(app.getCacheDir(), "backup"), DB_FILE);
        BackupDb.snapshot(helper.getWritableDatabase(), snapshot);
        try {
            DriveClient.DriveFile existing = drive.findFile(folderId, DB_FILE);
            drive.upload(DB_FILE, folderId, "application/x-sqlite3", snapshot, existing == null ? null : existing.id);
        } finally {
            if (snapshot.exists() && !snapshot.delete()) {
                Log.w(TAG, "snapshot not removed: " + snapshot);
            }
        }
    }

    /** The "Mama App" and "photos" folder ids. Cached ids that 404 or sit in the trash are looked up again. */
    static String[] ensureFolders(Context app, DriveClient drive) throws DriveException {
        String folder = BackupPrefs.folderId(app);
        String photos = BackupPrefs.photosFolderId(app);
        if (folder == null || !drive.folderUsable(folder)) {
            folder = drive.findFolder(ROOT_FOLDER, "root");
            if (folder == null) {
                folder = drive.createFolder(ROOT_FOLDER, "root");
            }
            photos = null;
        }
        if (photos == null || !drive.folderUsable(photos)) {
            photos = drive.findFolder(PHOTOS_FOLDER, folder);
            if (photos == null) {
                photos = drive.createFolder(PHOTOS_FOLDER, folder);
            }
        }
        BackupPrefs.setFolderIds(app, folder, photos);
        return new String[]{folder, photos};
    }

    static List<String> listPhotos(File dir) {
        List<String> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".jpg")) {
                    out.add(f.getName());
                }
            }
        }
        return out;
    }
}
