package com.atelier.app.backup;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.util.Log;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Google sign-in and the Drive access token, through AuthorizationClient with the one scope
 * drive.file. This is the only class that talks to Google Play Services.
 */
public final class DriveAuth {
    private static final String TAG = "Atelier";
    public static final String SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final long TOKEN_WAIT_SECONDS = 60;

    /** Why an interactive sign-in did not finish. */
    public enum Failure { PLAY_SERVICES, NOT_CONFIGURED, NETWORK, CANCELLED, OTHER }

    /** Result of an interactive sign-in, delivered on the main thread. */
    public interface Callback {
        void onSuccess();

        void onFailure(Failure failure);
    }

    private DriveAuth() {}

    private static List<Scope> scopes() {
        return Collections.singletonList(new Scope(SCOPE));
    }

    private static AuthorizationRequest request(Context context) {
        AuthorizationRequest.Builder b = AuthorizationRequest.builder().setRequestedScopes(scopes());
        String email = BackupPrefs.email(context);
        if (email != null) {
            b.setAccount(new Account(email, "com.google"));
        }
        return b.build();
    }

    // ---- interactive sign-in ----

    /** Starts sign-in. A consent screen, when needed, returns through {@link #onActivityResult}. */
    public static void signIn(final Activity activity, final int requestCode, final Callback callback) {
        int play;
        try {
            play = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
        } catch (RuntimeException e) {
            // Any failure of the availability check means the services cannot be used.
            Log.w(TAG, "play services check failed", e);
            callback.onFailure(Failure.PLAY_SERVICES);
            return;
        }
        if (play != ConnectionResult.SUCCESS) {
            Log.w(TAG, "play services unavailable, code " + play);
            callback.onFailure(Failure.PLAY_SERVICES);
            return;
        }
        try {
            // A fresh sign-in must not reuse an account the user signed out of.
            AuthorizationRequest req = AuthorizationRequest.builder().setRequestedScopes(scopes()).build();
            Identity.getAuthorizationClient(activity).authorize(req)
                    .addOnSuccessListener(result -> {
                        if (result.hasResolution()) {
                            try {
                                IntentSender sender = result.getPendingIntent().getIntentSender();
                                activity.startIntentSenderForResult(sender, requestCode, null, 0, 0, 0);
                            } catch (IntentSender.SendIntentException | RuntimeException e) {
                                // The consent screen could not open; tell the user instead of failing silently.
                                Log.w(TAG, "consent screen did not start", e);
                                callback.onFailure(Failure.OTHER);
                            }
                        } else {
                            finishSignIn(activity, result, callback);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "authorize failed", e);
                        callback.onFailure(classify(e));
                    });
        } catch (RuntimeException e) {
            // The client can throw before it returns a task, for example with the services half installed.
            Log.w(TAG, "authorize did not start", e);
            callback.onFailure(Failure.OTHER);
        }
    }

    /** Reads the consent screen result. Call from the Activity's onActivityResult. */
    public static void onActivityResult(Activity activity, int resultCode, Intent data, Callback callback) {
        Log.d(TAG, "consent result code " + resultCode + ", data " + (data == null ? "null" : "present"));
        if (data == null) {
            callback.onFailure(Failure.CANCELLED);
            return;
        }
        try {
            AuthorizationResult result =
                    Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data);
            finishSignIn(activity, result, callback);
        } catch (ApiException e) {
            Log.w(TAG, "consent result failed, status " + e.getStatusCode(), e);
            callback.onFailure(classify(e));
        } catch (RuntimeException e) {
            // A malformed result intent: report a plain failure.
            Log.w(TAG, "consent result unreadable", e);
            callback.onFailure(Failure.OTHER);
        }
    }

    private static void finishSignIn(Context context, AuthorizationResult result, Callback callback) {
        if (result.getAccessToken() == null) {
            Log.w(TAG, "authorization came back without a token");
            callback.onFailure(Failure.OTHER);
            return;
        }
        String email = null;
        GoogleSignInAccount account = result.toGoogleSignInAccount();
        if (account != null) {
            email = account.getEmail();
        }
        BackupPrefs.setSignedIn(context, email);
        callback.onSuccess();
    }

    static Failure classify(Throwable e) {
        if (e instanceof ApiException) {
            return classify(((ApiException) e).getStatusCode(), e.getMessage());
        }
        return Failure.OTHER;
    }

    /**
     * Play Services reports a missing OAuth client as status 8 with UNREGISTERED_ON_API_CONSOLE
     * in the message (seen on a device), and sometimes as status 10.
     */
    static Failure classify(int statusCode, String message) {
        if (message != null && message.contains("UNREGISTERED_ON_API_CONSOLE")) {
            return Failure.NOT_CONFIGURED;
        }
        return classify(statusCode);
    }

    /** Maps a Google API status code. 10 is DEVELOPER_ERROR, which a missing OAuth client causes. */
    static Failure classify(int statusCode) {
        switch (statusCode) {
            case 10:
                return Failure.NOT_CONFIGURED;
            case 7:
            case 15:
                return Failure.NETWORK;
            case 16:
            case 12501:
                return Failure.CANCELLED;
            case 1:
            case 2:
            case 3:
            case 9:
            case 17:
                return Failure.PLAY_SERVICES;
            default:
                return Failure.OTHER;
        }
    }

    // ---- background token ----

    /**
     * A token for a background thread. Never opens UI: when the user must act, this throws AUTH
     * and the caller marks the state as needing sign-in. Must not run on the main thread.
     */
    public static String accessTokenBlocking(Context context) throws DriveException {
        try {
            AuthorizationClient client = Identity.getAuthorizationClient(context);
            AuthorizationResult result =
                    Tasks.await(client.authorize(request(context)), TOKEN_WAIT_SECONDS, TimeUnit.SECONDS);
            if (result.hasResolution() || result.getAccessToken() == null) {
                throw new DriveException(DriveException.Reason.AUTH, 0, "sign-in needed");
            }
            return result.getAccessToken();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Failure f = classify(cause);
            Log.w(TAG, "background token failed: " + f, cause);
            throw new DriveException(f == Failure.NETWORK ? DriveException.Reason.NETWORK
                    : DriveException.Reason.AUTH, 0, "token failed", cause);
        } catch (TimeoutException e) {
            throw new DriveException(DriveException.Reason.NETWORK, 0, "token timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriveException(DriveException.Reason.OTHER, 0, "interrupted", e);
        } catch (RuntimeException e) {
            // Play Services missing or half installed: the same as not signed in, never a crash.
            Log.w(TAG, "background token unavailable", e);
            throw new DriveException(DriveException.Reason.AUTH, 0, "token unavailable", e);
        }
    }

    /** Drops a token Drive rejected, so the next request gets a fresh one. Background thread only. */
    public static void clearTokenBlocking(Context context, String token) {
        try {
            Tasks.await(Identity.getAuthorizationClient(context)
                    .clearToken(ClearTokenRequest.builder().setToken(token).build()),
                    TOKEN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // A token that cannot be cleared expires by itself, so go on.
            Log.w(TAG, "clearToken failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- sign-out ----

    /**
     * Cancels the job and forgets the account here at once, then asks Google to revoke the grant
     * on a background thread. A failed revoke leaves the grant in the user's Google account.
     */
    public static void signOut(Context context) {
        final Context app = context.getApplicationContext();
        final String email = BackupPrefs.email(app);
        BackupScheduler.cancel(app);
        BackupPrefs.clearSignIn(app);
        new Thread(() -> {
            try {
                RevokeAccessRequest.Builder b = RevokeAccessRequest.builder().setScopes(scopes());
                if (email != null) {
                    b.setAccount(new Account(email, "com.google"));
                }
                Tasks.await(Identity.getAuthorizationClient(app).revokeAccess(b.build()),
                        TOKEN_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                // Local sign-out already happened; the grant can be removed in the Google account page.
                Log.w(TAG, "revokeAccess failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "backup-signout").start();
    }
}
