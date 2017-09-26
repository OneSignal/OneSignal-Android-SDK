package com.onesignal;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;

/**
 * Copyright 2017 OneSignal
 * Created by alamgir on 9/22/17.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RestoreJobService extends JobService {

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        final Bundle extras = new Bundle(jobParameters.getExtras());
        if(extras == null)
            return false;

        new Thread(new Runnable() {
            public void run() {
                NotificationBundleProcessor.ProcessFromRestorerJobService(getApplicationContext(),
                        new BundleCompatBundle(extras));
                jobFinished(jobParameters, false);
            }
        }, "OS_RESTORE_JOB_SERVICE").start();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
