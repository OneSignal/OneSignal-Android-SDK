package com.onesignal;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.support.annotation.RequiresApi;

/**
 * Copyright 2017 OneSignal
 * Created by alamgir on 9/22/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class SyncJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        OneSignalSyncUtils.doBackgroundSync(getApplicationContext(),
                new OneSignalSyncUtils.OreoSyncRunnable(this, jobParameters));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
