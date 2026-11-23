package com.mama.app.backup;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/** The daily JobScheduler job: unmetered network, survives a reboot. */
public final class BackupScheduler {
    private static final String TAG = "MamaApp";
    static final int JOB_ID = 1401;

    private BackupScheduler() {}

    /** Schedules the job when it is not already pending, so a process start keeps its period. */
    public static void ensureScheduled(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js == null || !BackupPrefs.signedIn(context)) {
            return;
        }
        if (js.getPendingJob(JOB_ID) != null) {
            return;
        }
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, BackupJobService.class))
                .setPeriodic(TimeUnit.HOURS.toMillis(24))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .build();
        int r = js.schedule(info);
        Log.d(TAG, "backup job scheduled, result " + r);
    }

    public static void cancel(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js != null) {
            js.cancel(JOB_ID);
        }
    }
}
