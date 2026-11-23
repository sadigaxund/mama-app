package com.mama.app.backup;

import android.content.Context;
import android.content.SharedPreferences;

/** The SharedPreferences file "backup": sign-in state, cached Drive folder ids and the last run. */
public final class BackupPrefs {
    public static final String FILE = "backup";
    private static final String KEY_SIGNED_IN = "signed_in";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NEEDS_SIGN_IN = "needs_sign_in";
    private static final String KEY_PROMPT_SHOWN = "first_prompt_shown";
    private static final String KEY_FOLDER_ID = "folder_id";
    private static final String KEY_PHOTOS_FOLDER_ID = "photos_folder_id";
    private static final String KEY_LAST_SUCCESS = "last_success";
    private static final String KEY_LAST_FINGERPRINT = "last_fingerprint";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_ERROR_AT = "last_error_at";

    /** Why the last run failed. {@link #NEEDS_SIGN_IN} means the background token needs the user. */
    public enum Failure { AUTH, NETWORK, QUOTA, OTHER, NEEDS_SIGN_IN }

    private BackupPrefs() {}

    private static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean signedIn(Context c) {
        return get(c).getBoolean(KEY_SIGNED_IN, false);
    }

    /** The account email, or null when the API did not give one. */
    public static String email(Context c) {
        return get(c).getString(KEY_EMAIL, null);
    }

    public static void setSignedIn(Context c, String email) {
        SharedPreferences.Editor e = get(c).edit().putBoolean(KEY_SIGNED_IN, true)
                .putBoolean(KEY_NEEDS_SIGN_IN, false).remove(KEY_LAST_ERROR);
        if (email == null || email.isEmpty()) {
            e.remove(KEY_EMAIL);
        } else {
            e.putString(KEY_EMAIL, email);
        }
        e.commit();
    }

    /** Forgets the account, the cached folder ids and the last run. */
    public static void clearSignIn(Context c) {
        get(c).edit().clear().putBoolean(KEY_PROMPT_SHOWN, true).commit();
    }

    public static boolean promptShown(Context c) {
        return get(c).getBoolean(KEY_PROMPT_SHOWN, false);
    }

    public static void setPromptShown(Context c) {
        get(c).edit().putBoolean(KEY_PROMPT_SHOWN, true).commit();
    }

    public static String folderId(Context c) {
        return get(c).getString(KEY_FOLDER_ID, null);
    }

    public static String photosFolderId(Context c) {
        return get(c).getString(KEY_PHOTOS_FOLDER_ID, null);
    }

    public static void setFolderIds(Context c, String folderId, String photosFolderId) {
        get(c).edit().putString(KEY_FOLDER_ID, folderId).putString(KEY_PHOTOS_FOLDER_ID, photosFolderId).commit();
    }

    /** Time of the last successful run, or 0 when there was none. */
    public static long lastSuccess(Context c) {
        return get(c).getLong(KEY_LAST_SUCCESS, 0);
    }

    public static String lastFingerprint(Context c) {
        return get(c).getString(KEY_LAST_FINGERPRINT, null);
    }

    public static void recordSuccess(Context c, long nowMillis, String fingerprint) {
        get(c).edit().putLong(KEY_LAST_SUCCESS, nowMillis).putString(KEY_LAST_FINGERPRINT, fingerprint)
                .remove(KEY_LAST_ERROR).remove(KEY_LAST_ERROR_AT).putBoolean(KEY_NEEDS_SIGN_IN, false).commit();
    }

    public static void recordFailure(Context c, long nowMillis, Failure reason) {
        get(c).edit().putString(KEY_LAST_ERROR, reason.name()).putLong(KEY_LAST_ERROR_AT, nowMillis)
                .putBoolean(KEY_NEEDS_SIGN_IN, reason == Failure.NEEDS_SIGN_IN || reason == Failure.AUTH).commit();
    }

    /** Forget the fingerprint so the next run uploads again. */
    public static void clearFingerprint(Context c) {
        get(c).edit().remove(KEY_LAST_FINGERPRINT).commit();
    }

    /** The reason of the last failed run, or null when the last run succeeded or none ran. */
    public static Failure lastFailure(Context c) {
        String s = get(c).getString(KEY_LAST_ERROR, null);
        if (s == null) {
            return null;
        }
        try {
            return Failure.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Failure.OTHER;
        }
    }

    public static long lastFailureAt(Context c) {
        return get(c).getLong(KEY_LAST_ERROR_AT, 0);
    }

    public static boolean needsSignIn(Context c) {
        return signedIn(c) && get(c).getBoolean(KEY_NEEDS_SIGN_IN, false);
    }
}
