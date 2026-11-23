package com.mama.app.photo;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import com.mama.app.data.DbHelper;
import com.mama.app.data.PhotoRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Order photos in {@code filesDir/photos}. Imports shrink the long edge to {@link #MAX_EDGE},
 * apply the EXIF orientation and save a JPEG. Thumbnails come from a shared in-memory cache.
 */
public final class PhotoStore {
    private static final String TAG = "MamaApp";
    public static final int MAX_EDGE = 2048;
    static final int QUALITY = 85;

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
    private static final ExecutorService IMPORT_EXEC = Executors.newSingleThreadExecutor();
    private static final ExecutorService DECODE_EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Result of {@link #importAsync}, delivered on the main thread. */
    public interface ImportListener {
        void onImported(int added, int failed);
    }

    private final Context app;
    private final File dir;

    public PhotoStore(Context context) {
        this.app = context.getApplicationContext();
        this.dir = new File(app.getFilesDir(), "photos");
    }

    public File file(String fileName) {
        return new File(dir, fileName);
    }

    /** Deletes the file and drops its cached bitmaps. Returns false when a file stayed behind. */
    public boolean delete(String fileName) {
        for (String key : CACHE.snapshot().keySet()) {
            if (key.startsWith(fileName + "#")) {
                CACHE.remove(key);
            }
        }
        File f = file(fileName);
        if (f.exists() && !f.delete()) {
            Log.w(TAG, "photo file not deleted: " + fileName);
            return false;
        }
        return true;
    }

    // ---- import ----

    /**
     * Copies the image at {@code uri} into the photo folder and returns the new file name,
     * {@code <orderId>-<epochMillis>.jpg}. Blocking: call it off the UI thread.
     */
    public String importPhoto(long orderId, Uri uri) throws IOException {
        ContentResolver cr = app.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(cr, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Not an image: " + uri);
        }
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = open(cr, uri)) {
            orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException e) {
            // A file without readable EXIF is still a photo. It is stored as it came.
            Log.w(TAG, "photo exif unreadable: " + e.getMessage());
        }
        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longEdge / (sample * 2) >= MAX_EDGE) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream in = open(cr, uri)) {
            decoded = BitmapFactory.decodeStream(in, null, opts);
        }
        if (decoded == null) {
            throw new IOException("Cannot decode: " + uri);
        }
        Bitmap out = transform(decoded, orientation);
        if (out != decoded) {
            decoded.recycle();
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        long stamp = System.currentTimeMillis();
        File target = new File(dir, orderId + "-" + stamp + ".jpg");
        while (target.exists()) {
            stamp++;
            target = new File(dir, orderId + "-" + stamp + ".jpg");
        }
        boolean ok = false;
        try (FileOutputStream os = new FileOutputStream(target)) {
            ok = out.compress(Bitmap.CompressFormat.JPEG, QUALITY, os);
        } finally {
            out.recycle();
            if (!ok) {
                target.delete();
            }
        }
        if (!ok) {
            throw new IOException("JPEG write failed: " + target.getName());
        }
        return target.getName();
    }

    private static InputStream open(ContentResolver cr, Uri uri) throws IOException {
        InputStream in = cr.openInputStream(uri);
        if (in == null) {
            throw new IOException("Cannot open " + uri);
        }
        return in;
    }

    /** Applies the EXIF orientation, then shrinks so the long edge is at most MAX_EDGE. */
    static Bitmap transform(Bitmap src, int orientation) {
        Matrix m = orientationMatrix(orientation);
        float scale = Math.min(1f, (float) MAX_EDGE / Math.max(src.getWidth(), src.getHeight()));
        if (scale < 1f) {
            m.postScale(scale, scale);
        }
        if (m.isIdentity()) {
            return src;
        }
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    static Matrix orientationMatrix(int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.setRotate(180);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.setRotate(90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.setRotate(-90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.setRotate(-90);
                break;
            default:
                break;
        }
        return m;
    }

    /**
     * Imports every uri in order on one background thread, adds a row per written file, deletes
     * {@code tempToDelete} when given, and reports counts on the main thread. The rows are added
     * here, not by the caller, so a screen that closes meanwhile loses no photo.
     */
    public void importAsync(long orderId, List<Uri> uris, File tempToDelete, ImportListener listener) {
        List<Uri> copy = new java.util.ArrayList<>(uris);
        IMPORT_EXEC.execute(() -> {
            int added = 0;
            int failed = 0;
            DbHelper helper = new DbHelper(app);
            try {
                PhotoRepository repo = new PhotoRepository(helper);
                for (Uri u : copy) {
                    try {
                        String name = importPhoto(orderId, u);
                        repo.add(orderId, name, System.currentTimeMillis());
                        added++;
                    } catch (IOException | RuntimeException e) {
                        // One bad image must not stop the rest of the batch. It is counted and logged.
                        Log.w(TAG, "photo import failed: " + e);
                        failed++;
                    }
                }
            } finally {
                helper.close();
                if (tempToDelete != null && tempToDelete.exists() && !tempToDelete.delete()) {
                    Log.w(TAG, "temp photo not deleted: " + tempToDelete.getName());
                }
            }
            final int a = added;
            final int f = failed;
            MAIN.post(() -> listener.onImported(a, f));
        });
    }

    // ---- thumbnails ----

    private static String key(String fileName, String kind, int size) {
        return fileName + "#" + kind + size;
    }

    /** A cached square-crop thumbnail, or null when it is not in memory. */
    public Bitmap cachedThumbnail(String fileName, int sizePx) {
        return CACHE.get(key(fileName, "t", sizePx));
    }

    /** A bitmap whose short edge is at least sizePx, for a centre-crop. Null when unreadable. */
    public Bitmap loadThumbnail(String fileName, int sizePx) {
        String k = key(fileName, "t", sizePx);
        Bitmap hit = CACHE.get(k);
        if (hit != null) {
            return hit;
        }
        Bitmap b = decode(file(fileName), sizePx, true);
        if (b != null) {
            CACHE.put(k, b);
        }
        return b;
    }

    /** A bitmap whose long edge is at least maxEdgePx, for fit-centre display. Null when unreadable. */
    public Bitmap loadFit(String fileName, int maxEdgePx) {
        String k = key(fileName, "f", maxEdgePx);
        Bitmap hit = CACHE.get(k);
        if (hit != null) {
            return hit;
        }
        Bitmap b = decode(file(fileName), maxEdgePx, false);
        if (b != null) {
            CACHE.put(k, b);
        }
        return b;
    }

    public void loadThumbnailAsync(String fileName, int sizePx, Consumer<Bitmap> done) {
        DECODE_EXEC.execute(() -> {
            Bitmap b = loadThumbnail(fileName, sizePx);
            MAIN.post(() -> done.accept(b));
        });
    }

    public void loadFitAsync(String fileName, int maxEdgePx, Consumer<Bitmap> done) {
        DECODE_EXEC.execute(() -> {
            Bitmap b = loadFit(fileName, maxEdgePx);
            MAIN.post(() -> done.accept(b));
        });
    }

    /** Halves the sample while the result stays at least {@code target} on the checked edge. */
    private static Bitmap decode(File f, int target, boolean shortEdge) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "photo unreadable: " + f.getName());
            return null;
        }
        int edge = shortEdge ? Math.min(bounds.outWidth, bounds.outHeight)
                : Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (edge / (sample * 2) >= target) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), opts);
    }
}
