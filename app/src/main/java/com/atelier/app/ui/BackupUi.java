package com.atelier.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.atelier.app.MainActivity;
import com.atelier.app.R;
import com.atelier.app.backup.BackupPrefs;
import com.atelier.app.backup.BackupRunner;
import com.atelier.app.backup.BackupScheduler;
import com.atelier.app.backup.DriveAuth;
import com.atelier.app.backup.DriveException;
import com.atelier.app.backup.RestoreRunner;
import com.atelier.app.reminders.AppointmentScheduler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Dialogs and flows of the Drive backup, shared by the board and Settings. No Google imports. */
public final class BackupUi {
    private static final String TAG = "Atelier";
    public static final int REQUEST_SIGN_IN = 9014;

    private BackupUi() {}

    // ---- first launch ----

    /** Offers sign-in once, on the first board start. */
    public static void maybeShowFirstLaunch(final Activity a, final Runnable onChanged) {
        if (BackupPrefs.promptShown(a)) {
            return;
        }
        BackupPrefs.setPromptShown(a);
        new AlertDialog.Builder(a)
                .setTitle(R.string.backup_prompt_title)
                .setMessage(R.string.backup_prompt_text)
                .setPositiveButton(R.string.backup_prompt_sign_in, (d, w) -> signIn(a, onChanged))
                .setNegativeButton(R.string.backup_prompt_later, null)
                .show();
    }

    // ---- sign-in ----

    private static Runnable pendingChanged;

    public static void signIn(final Activity a, final Runnable onChanged) {
        pendingChanged = onChanged;
        DriveAuth.signIn(a, REQUEST_SIGN_IN, callback(a, onChanged));
    }

    /** Forward the Activity's onActivityResult here. Returns true when the request was ours. */
    public static boolean onActivityResult(Activity a, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_SIGN_IN) {
            return false;
        }
        Runnable changed = pendingChanged;
        pendingChanged = null;
        DriveAuth.onActivityResult(a, resultCode, data, callback(a, changed));
        return true;
    }

    private static DriveAuth.Callback callback(final Activity a, final Runnable onChanged) {
        return new DriveAuth.Callback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "sign-in succeeded");
                if (a.isFinishing() || a.isDestroyed()) {
                    return;
                }
                Toast.makeText(a, R.string.backup_connected_toast, Toast.LENGTH_SHORT).show();
                BackupScheduler.ensureScheduled(a);
                if (onChanged != null) {
                    onChanged.run();
                }
                afterSignIn(a, onChanged);
            }

            @Override
            public void onFailure(DriveAuth.Failure failure) {
                Log.d(TAG, "sign-in failed: " + failure);
                if (a.isFinishing() || a.isDestroyed()) {
                    return;
                }
                showSignInFailure(a, failure);
            }
        };
    }

    private static void showSignInFailure(Activity a, DriveAuth.Failure failure) {
        if (failure == DriveAuth.Failure.CANCELLED) {
            Toast.makeText(a, R.string.signin_cancelled, Toast.LENGTH_SHORT).show();
            return;
        }
        int msg;
        switch (failure) {
            case PLAY_SERVICES:
                msg = R.string.signin_fail_play;
                break;
            case NOT_CONFIGURED:
                msg = R.string.signin_fail_config;
                break;
            case NETWORK:
                msg = R.string.signin_fail_network;
                break;
            default:
                msg = R.string.signin_fail_other;
        }
        new AlertDialog.Builder(a)
                .setTitle(R.string.signin_fail_title)
                .setMessage(msg)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    /** With no orders, offer the Drive copy. With orders, make the first backup. */
    private static void afterSignIn(final Activity a, final Runnable onChanged) {
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            try {
                if (RestoreRunner.hasOrders(app)) {
                    BackupRunner.run(app, false);
                } else {
                    final RestoreRunner.Found found = RestoreRunner.find(app);
                    if (found != null) {
                        a.runOnUiThread(() -> {
                            if (!a.isFinishing() && !a.isDestroyed()) {
                                offerRestore(a, found);
                            }
                        });
                    }
                }
            } catch (DriveException e) {
                Log.w(TAG, "after sign-in lookup failed: " + e.reason, e);
            }
            if (onChanged != null) {
                a.runOnUiThread(onChanged);
            }
        }, "backup-after-signin").start();
    }

    public static void confirmSignOut(final Activity a, final Runnable onChanged) {
        new AlertDialog.Builder(a)
                .setTitle(R.string.backup_sign_out_title)
                .setMessage(R.string.backup_sign_out_text)
                .setPositiveButton(R.string.backup_sign_out, (d, w) -> {
                    DriveAuth.signOut(a);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ---- back up now ----

    public static void backUpNow(final Context context) {
        if (BackupRunner.isRunning()) {
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(() -> BackupRunner.run(app, true), "backup-now").start();
    }

    // ---- restore ----

    /** The Settings row: explains why not, or looks for a copy and offers it. */
    public static void restoreFromSettings(final Activity a) {
        if (!BackupPrefs.signedIn(a)) {
            message(a, R.string.backup_restore_title, R.string.restore_need_sign_in);
            return;
        }
        if (RestoreRunner.hasOrders(a)) {
            message(a, R.string.restore_has_orders_title, R.string.restore_has_orders_text);
            return;
        }
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            int failure = 0;
            RestoreRunner.Found found = null;
            try {
                found = RestoreRunner.find(app);
            } catch (DriveException e) {
                Log.w(TAG, "restore lookup failed: " + e.reason, e);
                failure = driveMessage(e);
            }
            final RestoreRunner.Found f = found;
            final int fail = failure;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) {
                    return;
                }
                if (fail != 0) {
                    message(a, R.string.backup_restore_title, fail);
                } else if (f == null) {
                    message(a, R.string.backup_restore_title, R.string.restore_none);
                } else {
                    offerRestore(a, f);
                }
            });
        }, "restore-find").start();
    }

    static void offerRestore(final Activity a, final RestoreRunner.Found found) {
        LocalDateTime t = Format.toLocal(found.modifiedMillis);
        String when = Format.dateFull(t.toLocalDate()) + " " + Format.time(t.toLocalTime());
        new AlertDialog.Builder(a)
                .setTitle(R.string.backup_restore_title)
                .setMessage(a.getString(R.string.restore_found, when))
                .setPositiveButton(R.string.restore_action, (d, w) -> runRestore(a, found))
                .setNegativeButton(R.string.restore_not_now, null)
                .show();
    }

    private static void runRestore(final Activity a, final RestoreRunner.Found found) {
        final AlertDialog busy = new AlertDialog.Builder(a)
                .setMessage(R.string.restore_working)
                .setCancelable(false)
                .create();
        busy.show();
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            RestoreRunner.Result result = null;
            int failure = 0;
            try {
                result = RestoreRunner.restore(app, found);
            } catch (DriveException e) {
                Log.w(TAG, "restore failed: " + e.reason, e);
                failure = driveMessage(e);
            } catch (IOException e) {
                Log.w(TAG, "restore failed on local files", e);
                failure = R.string.restore_fail_other;
            }
            if (result == RestoreRunner.Result.RESTORED) {
                // The restored appointments need their alarms.
                AppointmentScheduler.rescheduleAll(app);
            }
            final RestoreRunner.Result res = result;
            final int fail = failure;
            a.runOnUiThread(() -> {
                busy.dismiss();
                if (res == RestoreRunner.Result.RESTORED) {
                    Toast.makeText(a, R.string.restore_done, Toast.LENGTH_LONG).show();
                    a.startActivity(new Intent(a, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    a.finish();
                } else if (res == RestoreRunner.Result.APP_TOO_OLD) {
                    message(a, R.string.backup_restore_title, R.string.restore_too_old);
                } else if (res == RestoreRunner.Result.INVALID_BACKUP) {
                    message(a, R.string.backup_restore_title, R.string.restore_invalid);
                } else if (res == RestoreRunner.Result.HAS_ORDERS) {
                    message(a, R.string.restore_has_orders_title, R.string.restore_has_orders_text);
                } else {
                    message(a, R.string.backup_restore_title, fail != 0 ? fail : R.string.restore_fail_other);
                }
            });
        }, "restore").start();
    }

    private static int driveMessage(DriveException e) {
        switch (e.reason) {
            case AUTH:
                return R.string.restore_need_sign_in;
            case NETWORK:
                return R.string.restore_fail_network;
            default:
                return R.string.restore_fail_other;
        }
    }

    private static void message(Activity a, int title, int text) {
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    // ---- Settings texts ----

    /** "today 03:12", "yesterday 23:40" or "05.03 14:00", from a past instant. */
    public static String whenText(Context c, long millis) {
        LocalDateTime t = Format.toLocal(millis);
        LocalDate today = LocalDate.now();
        String time = Format.time(t.toLocalTime());
        if (t.toLocalDate().equals(today)) {
            return c.getString(R.string.backup_last_today, time);
        }
        if (t.toLocalDate().equals(today.minusDays(1))) {
            return c.getString(R.string.backup_last_yesterday, time);
        }
        return Format.date(t.toLocalDate(), today) + " " + time;
    }

    /** The reason of a failed run, in the user's language. */
    public static String failureText(Context c, BackupPrefs.Failure f) {
        switch (f) {
            case NETWORK:
                return c.getString(R.string.backup_fail_network);
            case QUOTA:
                return c.getString(R.string.backup_fail_quota);
            case AUTH:
            case NEEDS_SIGN_IN:
                return c.getString(R.string.backup_fail_auth);
            default:
                return c.getString(R.string.backup_fail_other);
        }
    }

    /** What the running backup is doing. */
    public static String progressText(Context c, BackupRunner.Progress p) {
        switch (p.phase) {
            case PHOTOS:
                return c.getString(R.string.backup_progress_photos, p.done, p.total);
            case DATABASE:
                return c.getString(R.string.backup_progress_db);
            default:
                return c.getString(R.string.backup_progress_prepare);
        }
    }
}
