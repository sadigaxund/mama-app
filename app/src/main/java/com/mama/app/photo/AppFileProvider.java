package com.mama.app.photo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Serves files under {@code cacheDir/shared} and {@code cacheDir/camera} by path, for share
 * targets and the camera app. A path that resolves outside those two folders is refused.
 */
public class AppFileProvider extends ContentProvider {
    public static final String DIR_SHARED = "shared";
    public static final String DIR_CAMERA = "camera";

    public static String authority(Context context) {
        return context.getPackageName() + ".files";
    }

    /** The content uri for {@code cacheDir/<dir>/<name>}. {@code dir} is "shared" or "camera". */
    public static Uri uriFor(Context context, String dir, String name) {
        return new Uri.Builder().scheme("content").authority(authority(context))
                .appendPath(dir).appendPath(name).build();
    }

    /**
     * Maps a uri to its file. Throws FileNotFoundException when the first segment is not an
     * allowed folder or the canonical path leaves that folder.
     */
    static File resolve(Context context, Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 2) {
            throw new FileNotFoundException("Bad path: " + uri.getPath());
        }
        String top = segments.get(0);
        if (!DIR_SHARED.equals(top) && !DIR_CAMERA.equals(top)) {
            throw new FileNotFoundException("Folder not served: " + top);
        }
        File root = new File(context.getCacheDir(), top);
        File target = new File(root, String.join(File.separator, segments.subList(1, segments.size())));
        try {
            String rootPath = root.getCanonicalPath() + File.separator;
            if (!target.getCanonicalPath().startsWith(rootPath)) {
                throw new FileNotFoundException("Path escapes " + top);
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Bad path: " + e.getMessage());
        }
        return target;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(getContext(), uri);
        if (mode.contains("w")) {
            File parent = f.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new FileNotFoundException("Cannot create folder for " + f.getName());
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File f;
        try {
            f = resolve(getContext(), uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        MatrixCursor.RowBuilder row = c.newRow();
        for (String col : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                row.add(f.getName());
            } else if (OpenableColumns.SIZE.equals(col)) {
                row.add(f.length());
            } else {
                row.add(null);
            }
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) {
            return "application/octet-stream";
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read and write by file only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read and write by file only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read and write by file only");
    }
}
