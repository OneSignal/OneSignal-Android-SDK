package com.onesignal;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;
import android.util.Log;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class GcmIntentJobService extends JobService {
   
   // - Runs on the main thread.
   // - Wakelock is held until jobFinished is called.
   @Override
   public boolean onStartJob(JobParameters jobParameters) {
      final PersistableBundle extras = jobParameters.getExtras();
      if (extras == null)
         return false;
      
      final JobService jobService = this;
      final JobParameters finalJobParameters = jobParameters;
      new Thread(new Runnable() {
         public void run() {
            startProcessing(jobService, finalJobParameters, extras);
         }
      }).start();
   
      // true as we created a thread and need it to hold a wakelock.
      return true;
   }
   
   private static void startProcessing(JobService jobService, JobParameters jobParameters, PersistableBundle extras) {
      Bundle bundle = new Bundle();
      for(String key : extras.keySet()) {
         Object obj = extras.get(key);
         if (obj instanceof String)
            bundle.putString(key, (String)obj);
         else if (obj instanceof Long)
            bundle.putLong(key, (Long)obj);
      }
      
      
//
//      bundle.putString("json_payload", extras.getString("json_payload"));
//      bundle.putLong("timestamp", extras.getLong("timestamp"));
   
      NotificationBundleProcessor.ProcessFromGCMIntentService(jobService, bundle, null);
   
      // Must be called to release the wake lock.
      jobService.jobFinished(jobParameters, false);
   }
   
   @Override
   public boolean onStopJob(JobParameters jobParameters) {
      // TODO: Check if this normally fires. When jobFinished is called maybe?
      Log.e("OneSignal", "GcmIntentJobService.onStopJob!!!!!!!!!!!!!!!!!!!!!!!");
      return true;
   }
}
