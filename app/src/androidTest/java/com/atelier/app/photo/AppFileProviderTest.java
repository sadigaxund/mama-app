package com.atelier.app.photo;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.system.Os;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

public class AppFileProviderTest extends AndroidTestCase {
    private File secret;
    private File linked;

    @Override
    protected void tearDown() throws Exception {
        if (secret != null) {
            secret.delete();
        }
        if (linked != null) {
            linked.delete();
        }
        new File(getContext().getCacheDir(), "camera/t.jpg").delete();
        new File(getContext().getCacheDir(), "shared/doc.pdf").delete();
        super.tearDown();
    }

    private Uri raw(String encodedPath) {
        return Uri.parse("content://" + AppFileProvider.authority(getContext()) + encodedPath);
    }

    private void assertRejected(String encodedPath) {
        Uri u = raw(encodedPath);
        try {
            AppFileProvider.resolve(getContext(), u);
            fail("resolved " + encodedPath);
        } catch (FileNotFoundException expected) {
            // refused
        }
        try {
            getContext().getContentResolver().openInputStream(u).close();
            fail("opened " + encodedPath);
        } catch (FileNotFoundException expected) {
            // refused through the resolver too
        } catch (java.io.IOException e) {
            fail("unexpected " + e);
        }
    }

    public void testEscapingPathsAreRejected() {
        assertRejected("/camera/../x.jpg");
        assertRejected("/camera/%2e%2e/%2e%2e/files/x");
        assertRejected("/shared/..%2F..%2Ffiles/x");
        assertRejected("/shared/a/../../databases/atelier.db");
        assertRejected("/databases/atelier.db");
        assertRejected("/camera");
        assertRejected("/");
        assertRejected("/cache/x");
    }

    public void testSymlinkOutOfFolderIsRejected() throws Exception {
        Context c = getContext();
        secret = new File(c.getFilesDir(), "secret.txt");
        try (OutputStream os = new java.io.FileOutputStream(secret)) {
            os.write(1);
        }
        File shared = new File(c.getCacheDir(), "shared");
        shared.mkdirs();
        linked = new File(shared, "link.txt");
        linked.delete();
        Os.symlink(secret.getPath(), linked.getPath());
        assertRejected("/shared/link.txt");
    }

    public void testWriteReadQueryAndType() throws Exception {
        Context c = getContext();
        Uri cam = AppFileProvider.uriFor(c, AppFileProvider.DIR_CAMERA, "t.jpg");
        try (OutputStream os = c.getContentResolver().openOutputStream(cam, "w")) {
            os.write(new byte[]{1, 2, 3, 4, 5});
        }
        try (InputStream in = c.getContentResolver().openInputStream(cam)) {
            assertEquals(5, in.available());
        }
        try (Cursor q = c.getContentResolver().query(cam, null, null, null, null)) {
            assertTrue(q.moveToFirst());
            assertEquals("t.jpg", q.getString(q.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)));
            assertEquals(5L, q.getLong(q.getColumnIndexOrThrow(OpenableColumns.SIZE)));
        }
        assertEquals("image/jpeg", c.getContentResolver().getType(cam));
        assertEquals("application/pdf", c.getContentResolver().getType(
                AppFileProvider.uriFor(c, AppFileProvider.DIR_SHARED, "doc.PDF")));
        assertEquals("image/png", c.getContentResolver().getType(
                AppFileProvider.uriFor(c, AppFileProvider.DIR_SHARED, "a.png")));
    }
}
