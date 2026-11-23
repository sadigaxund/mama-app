package com.mama.app.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class BackupLogicTest {
    @Test
    public void fingerprintIgnoresPhotoOrderAndDuplicates() {
        String a = BackupFingerprint.compute(100, 5, Arrays.asList("b.jpg", "a.jpg"));
        String b = BackupFingerprint.compute(100, 5, Arrays.asList("a.jpg", "b.jpg", "a.jpg"));
        assertEquals(a, b);
    }

    @Test
    public void fingerprintChangesWithSizeTimeOrPhotos() {
        String base = BackupFingerprint.compute(100, 5, Collections.singletonList("a.jpg"));
        assertNotEquals(base, BackupFingerprint.compute(101, 5, Collections.singletonList("a.jpg")));
        assertNotEquals(base, BackupFingerprint.compute(100, 6, Collections.singletonList("a.jpg")));
        assertNotEquals(base, BackupFingerprint.compute(100, 5, Arrays.asList("a.jpg", "b.jpg")));
        assertNotEquals(base, BackupFingerprint.compute(100, 5, Collections.<String>emptyList()));
    }

    @Test
    public void runIsSkippedOnlyWhenUnchangedAndNotForced() {
        assertFalse(BackupFingerprint.needsRun(false, "x", "x"));
        assertTrue(BackupFingerprint.needsRun(true, "x", "x"));
        assertTrue(BackupFingerprint.needsRun(false, null, "x"));
        assertTrue(BackupFingerprint.needsRun(false, "x", "y"));
    }

    @Test
    public void uploadsOnlyPhotosMissingInDrive() {
        assertEquals(Arrays.asList("2-9.jpg", "3-1.jpg"),
                BackupFingerprint.toUpload(Arrays.asList("3-1.jpg", "1-1.jpg", "2-9.jpg"),
                        Arrays.asList("1-1.jpg", "old.jpg")));
        assertTrue(BackupFingerprint.toUpload(Collections.singletonList("a.jpg"),
                Collections.singletonList("a.jpg")).isEmpty());
    }

    @Test
    public void httpStatusMapsToReason() {
        assertEquals(DriveException.Reason.AUTH, DriveClient.mapError(401, null));
        assertEquals(DriveException.Reason.QUOTA, DriveClient.mapError(429, null));
        assertEquals(DriveException.Reason.AUTH, DriveClient.mapError(403, null));
        assertEquals(DriveException.Reason.NETWORK, DriveClient.mapError(503, null));
        assertEquals(DriveException.Reason.NETWORK, DriveClient.mapError(408, null));
        assertEquals(DriveException.Reason.OTHER, DriveClient.mapError(404, null));
        assertEquals(DriveException.Reason.OTHER, DriveClient.mapError(400, null));
    }

    @Test
    public void queryValuesAreEscaped() {
        assertEquals("'a\\'b'", DriveClient.quote("a'b"));
        assertEquals("'a\\\\b'", DriveClient.quote("a\\b"));
    }

    @Test
    public void rfc3339TimeParses() {
        assertEquals(1_700_000_000_123L, DriveClient.parseTime("2023-11-14T22:13:20.123Z"));
        assertEquals(0, DriveClient.parseTime("garbage"));
        assertEquals(0, DriveClient.parseTime(null));
    }

    @Test
    public void googleStatusCodesMapToFailures() {
        assertEquals(DriveAuth.Failure.NOT_CONFIGURED, DriveAuth.classify(10));
        assertEquals(DriveAuth.Failure.NETWORK, DriveAuth.classify(7));
        assertEquals(DriveAuth.Failure.CANCELLED, DriveAuth.classify(12501));
        assertEquals(DriveAuth.Failure.PLAY_SERVICES, DriveAuth.classify(17));
        assertEquals(DriveAuth.Failure.OTHER, DriveAuth.classify(8));
        assertEquals(DriveAuth.Failure.NOT_CONFIGURED,
                DriveAuth.classify(8, "8: [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE]."));
        assertEquals(DriveAuth.Failure.OTHER, DriveAuth.classify(8, "something else"));
    }
}
