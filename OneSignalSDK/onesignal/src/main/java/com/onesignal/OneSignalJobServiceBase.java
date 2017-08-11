package com.onesignal;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.support.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
abstract class OneSignalJobServiceBase extends JobService {
   
   // - Runs on the main thread.
   // - Wakelock is held until jobFinished is called.
   @Override
   public boolean onStartJob(JobParameters jobParameters) {
      if (jobParameters.getExtras() == null)
         return false;
      
      final JobService jobService = this;
      final JobParameters finalJobParameters = jobParameters;
      new Thread(new Runnable() {
         public void run() {
            startProcessing(jobService, finalJobParameters);
            jobFinished(finalJobParameters, false);
         }
      }, "OS_JOBSERVICE_BASE").start();
      
      // true as we created a thread and need it to hold a wakelock.
      return true;
   }
   
   @Override
   public boolean onStopJob(JobParameters jobParameters) {
      return true;
   }
   
   abstract void startProcessing(JobService jobService, JobParameters jobParameters);
}
