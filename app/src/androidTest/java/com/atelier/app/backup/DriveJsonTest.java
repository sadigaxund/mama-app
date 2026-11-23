package com.atelier.app.backup;

import android.test.AndroidTestCase;

import org.json.JSONObject;

/** Drive JSON handling needs the real org.json, so it runs on a device. */
public class DriveJsonTest extends AndroidTestCase {
    private static final String SAMPLE = "{\"kind\":\"drive#fileList\",\"nextPageToken\":\"TOK\","
            + "\"files\":[{\"id\":\"1abc\",\"name\":\"orders.db\",\"modifiedTime\":\"2023-11-14T22:13:20.123Z\"},"
            + "{\"id\":\"2def\",\"name\":\"3-17.jpg\"}]}";

    public void testParsesListPage() throws Exception {
        DriveClient.FilePage page = DriveClient.parseFilePage(SAMPLE);
        assertEquals(2, page.files.size());
        assertEquals("1abc", page.files.get(0).id);
        assertEquals("orders.db", page.files.get(0).name);
        assertEquals(1_700_000_000_123L, page.files.get(0).modifiedMillis);
        assertEquals(0, page.files.get(1).modifiedMillis);
        assertEquals("TOK", page.nextPageToken);
    }

    public void testEmptyListHasNoNextPage() throws Exception {
        DriveClient.FilePage page = DriveClient.parseFilePage("{\"files\":[]}");
        assertEquals(0, page.files.size());
        assertNull(page.nextPageToken);
        assertEquals(0, DriveClient.parseFilePage("{}").files.size());
    }

    public void testErrorBodiesMapToReason() {
        String quota = "{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"storageQuotaExceeded\"}]}}";
        String rate = "{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"userRateLimitExceeded\"}]}}";
        String perm = "{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}";
        assertEquals(DriveException.Reason.QUOTA, DriveClient.mapError(403, quota));
        assertEquals(DriveException.Reason.QUOTA, DriveClient.mapError(403, rate));
        assertEquals(DriveException.Reason.AUTH, DriveClient.mapError(403, perm));
        assertEquals(DriveException.Reason.AUTH, DriveClient.mapError(403, "<html>not json</html>"));
    }

    public void testMetadataCarriesParentOnlyForCreate() throws Exception {
        JSONObject create = new JSONObject(DriveClient.metadataJson("a.jpg", "P1"));
        assertEquals("a.jpg", create.getString("name"));
        assertEquals("P1", create.getJSONArray("parents").getString(0));
        assertFalse(new JSONObject(DriveClient.metadataJson("a.jpg", null)).has("parents"));
    }
}
