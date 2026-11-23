package com.mama.app.backup;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Drive REST v3 over HttpURLConnection. One instance holds one access token. */
public final class DriveClient {
    private static final String TAG = "MamaApp";
    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3";
    static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final int CONNECT_MILLIS = 30_000;
    private static final int READ_MILLIS = 60_000;

    /** A file or folder in a listing. {@code modifiedMillis} is 0 when Drive sent no time. */
    public static final class DriveFile {
        public final String id;
        public final String name;
        public final long modifiedMillis;

        public DriveFile(String id, String name, long modifiedMillis) {
            this.id = id;
            this.name = name;
            this.modifiedMillis = modifiedMillis;
        }
    }

    /** One page of a files.list answer. */
    public static final class FilePage {
        public final List<DriveFile> files;
        public final String nextPageToken;

        FilePage(List<DriveFile> files, String nextPageToken) {
            this.files = files;
            this.nextPageToken = nextPageToken;
        }
    }

    private final String token;

    public DriveClient(String accessToken) {
        this.token = accessToken;
    }

    // ---- pure helpers, covered by JVM tests ----

    /** Parses a files.list body. A missing next page token is null. */
    static FilePage parseFilePage(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONArray arr = o.optJSONArray("files");
        List<DriveFile> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                out.add(new DriveFile(f.getString("id"), f.optString("name", ""),
                        parseTime(f.optString("modifiedTime", null))));
            }
        }
        String next = o.has("nextPageToken") ? o.getString("nextPageToken") : null;
        return new FilePage(out, next);
    }

    /** RFC 3339 time to epoch millis, 0 when absent or unreadable. */
    static long parseTime(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    /** Escapes a value for a single-quoted Drive query string. */
    static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * Sorts an HTTP error into a reason. 401 is AUTH. 403 and 429 with a quota or rate reason
     * are QUOTA, other 403 are AUTH. 5xx and 408 are NETWORK, because a retry may succeed.
     */
    static DriveException.Reason mapError(int status, String body) {
        if (status == 401) {
            return DriveException.Reason.AUTH;
        }
        if (status == 403 || status == 429) {
            String reason = errorReason(body);
            if (status == 429 || reason.contains("quota") || reason.contains("ratelimit")
                    || reason.contains("storage")) {
                return DriveException.Reason.QUOTA;
            }
            return DriveException.Reason.AUTH;
        }
        if (status == 408 || (status >= 500 && status <= 599)) {
            return DriveException.Reason.NETWORK;
        }
        return DriveException.Reason.OTHER;
    }

    private static String errorReason(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        try {
            JSONArray errors = new JSONObject(body).getJSONObject("error").optJSONArray("errors");
            StringBuilder sb = new StringBuilder();
            if (errors != null) {
                for (int i = 0; i < errors.length(); i++) {
                    sb.append(errors.getJSONObject(i).optString("reason", "")).append(' ');
                }
            }
            return sb.toString().toLowerCase(java.util.Locale.ROOT);
        } catch (JSONException e) {
            // An error body that is not Drive JSON carries no reason; the status alone decides.
            return "";
        }
    }

    static String metadataJson(String name, String parentId) throws JSONException {
        JSONObject o = new JSONObject().put("name", name);
        if (parentId != null) {
            o.put("parents", new JSONArray().put(parentId));
        }
        return o.toString();
    }

    // ---- folders ----

    /** The first folder called {@code name} under {@code parentId}, or null. */
    public String findFolder(String name, String parentId) throws DriveException {
        String q = "mimeType=" + quote(FOLDER_MIME) + " and name=" + quote(name)
                + " and " + quote(parentId) + " in parents and trashed=false";
        FilePage page = list(q, null, "nextPageToken,files(id,name)");
        return page.files.isEmpty() ? null : page.files.get(0).id;
    }

    public String createFolder(String name, String parentId) throws DriveException {
        try {
            JSONObject body = new JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
                    .put("parents", new JSONArray().put(parentId));
            String res = send("POST", API + "/files?fields=id", body.toString().getBytes(StandardCharsets.UTF_8),
                    "application/json; charset=UTF-8");
            return new JSONObject(res).getString("id");
        } catch (JSONException e) {
            throw new DriveException(DriveException.Reason.OTHER, 0, "bad folder answer", e);
        }
    }

    /** True when the folder exists and is not in the trash. A 404 gives false. */
    public boolean folderUsable(String id) throws DriveException {
        try {
            String res = send("GET", API + "/files/" + enc(id) + "?fields=id,trashed", null, null);
            return !new JSONObject(res).optBoolean("trashed", false);
        } catch (DriveException e) {
            if (e.httpStatus == 404) {
                return false;
            }
            throw e;
        } catch (JSONException e) {
            throw new DriveException(DriveException.Reason.OTHER, 0, "bad folder answer", e);
        }
    }

    // ---- listing ----

    /** Files directly inside a folder, every page. */
    public List<DriveFile> listChildren(String folderId) throws DriveException {
        return listAll(quote(folderId) + " in parents and trashed=false and mimeType!=" + quote(FOLDER_MIME));
    }

    /** The first file called {@code name} inside a folder, or null. */
    public DriveFile findFile(String folderId, String name) throws DriveException {
        List<DriveFile> all = listAll(quote(folderId) + " in parents and trashed=false and name=" + quote(name));
        return all.isEmpty() ? null : all.get(0);
    }

    private List<DriveFile> listAll(String q) throws DriveException {
        List<DriveFile> out = new ArrayList<>();
        String pageToken = null;
        do {
            FilePage page = list(q, pageToken, "nextPageToken,files(id,name,modifiedTime)");
            out.addAll(page.files);
            pageToken = page.nextPageToken;
        } while (pageToken != null);
        return out;
    }

    private FilePage list(String q, String pageToken, String fields) throws DriveException {
        String url = API + "/files?q=" + enc(q) + "&spaces=drive&pageSize=1000&fields=" + enc(fields)
                + (pageToken == null ? "" : "&pageToken=" + enc(pageToken));
        String res = send("GET", url, null, null);
        try {
            return parseFilePage(res);
        } catch (JSONException e) {
            throw new DriveException(DriveException.Reason.OTHER, 0, "bad list answer", e);
        }
    }

    // ---- upload and download ----

    /**
     * Multipart upload. With {@code existingId} the file is updated, else created inside
     * {@code parentId}. Returns the file id.
     */
    public String upload(String name, String parentId, String mime, File file, String existingId)
            throws DriveException {
        final String boundary = "mama" + Long.toHexString(System.nanoTime());
        try {
            String meta = metadataJson(name, existingId == null ? parentId : null);
            byte[] head = ("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                    + meta + "\r\n--" + boundary + "\r\nContent-Type: " + mime + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            // PATCH goes as a POST with an override header: HttpURLConnection has no PATCH.
            String url = UPLOAD + "/files" + (existingId == null ? "" : "/" + enc(existingId))
                    + "?uploadType=multipart&fields=id";
            HttpURLConnection c = open(url);
            try {
                c.setRequestMethod("POST");
                if (existingId != null) {
                    c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                }
                c.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(head.length + file.length() + tail.length);
                try (OutputStream out = c.getOutputStream(); InputStream in = new FileInputStream(file)) {
                    out.write(head);
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    out.write(tail);
                }
                return new JSONObject(finish(c)).getString("id");
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            throw new DriveException(DriveException.Reason.NETWORK, 0, "upload failed: " + e.getMessage(), e);
        } catch (JSONException e) {
            throw new DriveException(DriveException.Reason.OTHER, 0, "bad upload answer", e);
        }
    }

    /** Downloads a file's content to {@code target}. A partial file is never left at {@code target}. */
    public void download(String fileId, File target) throws DriveException {
        File part = new File(target.getPath() + ".part");
        HttpURLConnection c = null;
        try {
            c = open(API + "/files/" + enc(fileId) + "?alt=media");
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            if (code < 200 || code > 299) {
                throw error(code, readAll(c.getErrorStream()));
            }
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("cannot replace " + target);
            }
            if (!part.renameTo(target)) {
                throw new IOException("cannot move " + part + " to " + target);
            }
        } catch (IOException e) {
            if (part.exists() && !part.delete()) {
                Log.w(TAG, "partial download not removed: " + part);
            }
            throw new DriveException(DriveException.Reason.NETWORK, 0, "download failed: " + e.getMessage(), e);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    // ---- HTTP plumbing ----

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MILLIS);
        c.setReadTimeout(READ_MILLIS);
        c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    private String send(String method, String url, byte[] body, String contentType) throws DriveException {
        HttpURLConnection c = null;
        try {
            c = open(url);
            c.setRequestMethod(method);
            if (body != null) {
                c.setRequestProperty("Content-Type", contentType);
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            return finish(c);
        } catch (IOException e) {
            throw new DriveException(DriveException.Reason.NETWORK, 0, "network error: " + e.getMessage(), e);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String finish(HttpURLConnection c) throws IOException, DriveException {
        int code = c.getResponseCode();
        if (code >= 200 && code <= 299) {
            return readAll(c.getInputStream());
        }
        throw error(code, readAll(c.getErrorStream()));
    }

    private static DriveException error(int status, String body) {
        Log.w(TAG, "drive http " + status + " " + (body.length() > 300 ? body.substring(0, 300) : body));
        return new DriveException(mapError(status, body), status, "HTTP " + status);
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream src = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = src.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported.
            throw new IllegalStateException(e);
        }
    }
}
