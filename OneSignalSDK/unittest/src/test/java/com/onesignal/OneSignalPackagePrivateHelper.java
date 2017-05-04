package com.onesignal;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONObject;
import org.robolectric.util.Scheduler;

import java.util.Map;

import static org.robolectric.Shadows.shadowOf;

public class OneSignalPackagePrivateHelper {
   public static boolean runAllNetworkRunnables() {
      boolean startedRunnable = false;
      for (Map.Entry<Integer, OneSignalStateSynchronizer.NetworkHandlerThread> handlerThread : OneSignalStateSynchronizer.networkHandlerThreads.entrySet()) {
         Scheduler scheduler = shadowOf(handlerThread.getValue().getLooper()).getScheduler();
         if (scheduler.advanceToLastPostedRunnable())
            startedRunnable = true;
      }

      return startedRunnable;
   }

   public static boolean runFocusRunnables() {
      Looper looper = ActivityLifecycleHandler.focusHandlerThread.getHandlerLooper();
      if (looper == null)
         return false;
      
      Scheduler scheduler = shadowOf(looper).getScheduler();
      if (scheduler == null)
         return false;
      return scheduler.advanceToLastPostedRunnable();
   }

   public static void resetRunnables() {
      for (Map.Entry<Integer, OneSignalStateSynchronizer.NetworkHandlerThread> handlerThread : OneSignalStateSynchronizer.networkHandlerThreads.entrySet())
         handlerThread.getValue().stopScheduledRunnable();

      Looper looper = ActivityLifecycleHandler.focusHandlerThread.getHandlerLooper();
      if (looper == null) return;

      shadowOf(looper).reset();
   }

   public static void SyncService_onTaskRemoved(Service service) {
      SyncService.onTaskRemoved(service);
   }

   public static JSONObject bundleAsJSONObject(Bundle bundle) {
      return NotificationBundleProcessor.bundleAsJSONObject(bundle);
   }

   public static Bundle createInternalPayloadBundle(Bundle bundle) {
      Bundle retBundle = new Bundle();
      retBundle.putString("json_payload", OneSignalPackagePrivateHelper.bundleAsJSONObject(bundle).toString());
      return retBundle;
   }

   public static void NotificationBundleProcessor_ProcessFromGCMIntentService(Context context, Bundle bundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      NotificationBundleProcessor.ProcessFromGCMIntentService(context, createInternalPayloadBundle(bundle), overrideSettings);
   }

   public static void NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap(Context context, Bundle bundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      NotificationBundleProcessor.ProcessFromGCMIntentService(context, bundle, overrideSettings);
   }

   public static boolean GcmBroadcastReceiver_processBundle(Context context, Bundle bundle) {
      NotificationBundleProcessor.ProcessedBundleResult processedResult = NotificationBundleProcessor.processBundle(context, bundle);
      return processedResult.processed();
   }

   public static int NotificationBundleProcessor_Process(Context context, boolean restoring, JSONObject jsonPayload, NotificationExtenderService.OverrideSettings overrideSettings) {
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = jsonPayload;
      notifJob.overrideSettings = overrideSettings;
      return NotificationBundleProcessor.Process(notifJob);
   }

   public static class NotificationTable extends OneSignalDbContract.NotificationTable { }
   public static class NotificationRestorer extends com.onesignal.NotificationRestorer { }
   public static class NotificationGenerationJob extends com.onesignal.NotificationGenerationJob {
      NotificationGenerationJob(Context context) {
         super(context);
      }
   }
   
   
   public static void OneSignalRestClientPublic_getSync(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      OneSignalRestClient.getSync(url, responseHandler);
   }
   
   
   public static void NotificationOpenedProcessor_processFromContext(Context context, Intent intent) {
      NotificationOpenedProcessor.processFromContext(context, intent);
   }
   
   public static void NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(Context context, SQLiteDatabase writableDb, String group, boolean dismissed) {
      NotificationSummaryManager.updateSummaryNotificationAfterChildRemoved(context, writableDb, group, dismissed);
   }
}
