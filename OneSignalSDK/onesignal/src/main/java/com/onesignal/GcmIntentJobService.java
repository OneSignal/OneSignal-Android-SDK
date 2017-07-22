package com.onesignal;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.support.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
public class GcmIntentJobService extends OneSignalJobServiceBase {
   @Override
   void startProcessing(JobService jobService, JobParameters jobParameters) {
      NotificationBundleProcessor.ProcessFromGCMIntentService(jobService,
          new BundleCompatPersistableBundle(jobParameters.getExtras()),
          null);
   }
}
