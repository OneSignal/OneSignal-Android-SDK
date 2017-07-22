package com.onesignal;
    
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;
import android.util.Log;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NotificationRestoreJobService extends OneSignalJobServiceBase {
   
   @Override
   void startProcessing(JobService jobService, JobParameters jobParameters) {
      NotificationRestorer.restore(jobService);
   }
}
