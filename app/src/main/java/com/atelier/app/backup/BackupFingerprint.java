package com.atelier.app.backup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure decisions of a backup run: what changed and which photos to upload. */
public final class BackupFingerprint {
    private BackupFingerprint() {}

    /** Hash of the database size and time plus the sorted set of local photo names. */
    public static String compute(long dbSize, long dbModified, Collection<String> photoNames) {
        List<String> names = new ArrayList<>(new HashSet<>(photoNames));
        Collections.sort(names);
        StringBuilder sb = new StringBuilder();
        sb.append(dbSize).append('|').append(dbModified);
        for (String n : names) {
            sb.append('|').append(n);
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required on every Java platform, so this cannot happen.
            throw new IllegalStateException(e);
        }
    }

    /** True when a run is needed: forced, no earlier run, or a different fingerprint. */
    public static boolean needsRun(boolean force, String last, String now) {
        return force || last == null || !last.equals(now);
    }

    /** Local names missing from Drive, sorted. */
    public static List<String> toUpload(Collection<String> local, Collection<String> remote) {
        Set<String> have = new HashSet<>(remote);
        List<String> out = new ArrayList<>();
        for (String n : new HashSet<>(local)) {
            if (!have.contains(n)) {
                out.add(n);
            }
        }
        Collections.sort(out);
        return out;
    }
}
