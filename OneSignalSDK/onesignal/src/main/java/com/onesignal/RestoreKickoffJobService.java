package com.onesignal;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.Process;
import android.support.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RestoreKickoffJobService extends OneSignalJobServiceBase {

    @Override
    void startProcessing(JobService jobService, JobParameters jobParameters) {
        Thread.currentThread().setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        NotificationRestorer.restore(getApplicationContext());
    }
}
