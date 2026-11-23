package com.mama.app.photo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhotoStoreTest extends AndroidTestCase {
    private static final long ORDER = 77;
    private PhotoStore store;
    private final List<File> cleanup = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        store = new PhotoStore(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        for (File f : cleanup) {
            f.delete();
        }
        super.tearDown();
    }

    /** Left half red, right half blue, saved as a JPEG with the given EXIF orientation. */
    private File makeJpeg(int w, int h, int orientation) throws IOException {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLUE);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(Color.RED);
        c.drawRect(0, 0, w / 2f, h, p);
        File f = new File(getContext().getCacheDir(), "src-" + System.nanoTime() + ".jpg");
        cleanup.add(f);
        try (FileOutputStream os = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 95, os);
        }
        ExifInterface exif = new ExifInterface(f.getPath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
        exif.saveAttributes();
        return f;
    }

    private Bitmap importAndDecode(File src) throws IOException {
        String name = store.importPhoto(ORDER, Uri.fromFile(src));
        File out = store.file(name);
        cleanup.add(out);
        assertTrue(name.matches("77-\\d+\\.jpg"));
        assertTrue(out.isFile());
        Bitmap b = BitmapFactory.decodeFile(out.getPath());
        assertNotNull(b);
        return b;
    }

    public void testLongEdgeCappedAndRotated() throws Exception {
        // 3000x1000 stored sideways: orientation 6 means "rotate 90 to view", so the result is portrait.
        Bitmap b = importAndDecode(makeJpeg(3000, 1000, ExifInterface.ORIENTATION_ROTATE_90));
        assertEquals(PhotoStore.MAX_EDGE, Math.max(b.getWidth(), b.getHeight()));
        assertTrue(b.getHeight() > b.getWidth());
        assertEquals(683, b.getWidth(), 1);
        // Rotating 90 clockwise moves the red left half to the top.
        assertTrue(Color.red(b.getPixel(b.getWidth() / 2, 50)) > 180);
        assertTrue(Color.blue(b.getPixel(b.getWidth() / 2, b.getHeight() - 50)) > 180);
    }

    public void testSmallImageKeepsSize() throws Exception {
        Bitmap b = importAndDecode(makeJpeg(800, 600, ExifInterface.ORIENTATION_NORMAL));
        assertEquals(800, b.getWidth());
        assertEquals(600, b.getHeight());
        assertTrue(Color.red(b.getPixel(50, 300)) > 180);
        assertTrue(Color.blue(b.getPixel(750, 300)) > 180);
    }

    public void testRotate180SwapsSides() throws Exception {
        Bitmap b = importAndDecode(makeJpeg(400, 200, ExifInterface.ORIENTATION_ROTATE_180));
        assertEquals(400, b.getWidth());
        assertTrue(Color.blue(b.getPixel(50, 100)) > 180);
        assertTrue(Color.red(b.getPixel(350, 100)) > 180);
    }

    public void testNotAnImageIsRejected() throws Exception {
        File f = new File(getContext().getCacheDir(), "notes.txt");
        cleanup.add(f);
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write("hello".getBytes());
        }
        try {
            store.importPhoto(ORDER, Uri.fromFile(f));
            fail("expected IOException");
        } catch (IOException expected) {
            // the text file is not an image
        }
    }

    public void testSameMillisecondGetsDistinctNames() throws Exception {
        File src = makeJpeg(100, 100, ExifInterface.ORIENTATION_NORMAL);
        String a = store.importPhoto(ORDER, Uri.fromFile(src));
        String b = store.importPhoto(ORDER, Uri.fromFile(src));
        cleanup.add(store.file(a));
        cleanup.add(store.file(b));
        assertFalse(a.equals(b));
    }

    public void testThumbnailCacheAndDelete() throws Exception {
        String name = store.importPhoto(ORDER, Uri.fromFile(makeJpeg(1000, 800, ExifInterface.ORIENTATION_NORMAL)));
        cleanup.add(store.file(name));
        assertNull(store.cachedThumbnail(name, 100));
        Bitmap t = store.loadThumbnail(name, 100);
        assertNotNull(t);
        assertTrue(Math.min(t.getWidth(), t.getHeight()) >= 100);
        assertSame(t, store.loadThumbnail(name, 100));
        assertSame(t, store.cachedThumbnail(name, 100));
        assertTrue(store.delete(name));
        assertFalse(store.file(name).exists());
        assertNull(store.cachedThumbnail(name, 100));
        assertNull(store.loadThumbnail(name, 100));
    }
}
