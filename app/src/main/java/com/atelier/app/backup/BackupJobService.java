package com.atelier.app.backup;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/** Runs the backup from the daily job on its own thread. Never opens UI. */
public class BackupJobService extends JobService {
    private static final String TAG = "Atelier";

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                BackupRunner.Outcome o = BackupRunner.run(getApplicationContext(), false);
                Log.d(TAG, "backup job finished: " + o);
            } finally {
                jobFinished(params, false);
            }
        }, "backup-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // The run notes its failure itself; the next period tries again.
        return false;
    }
}
