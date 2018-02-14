package com.onesignal;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.robolectric.util.Scheduler;

import java.util.Map;
import java.util.Set;

import static org.robolectric.Shadows.shadowOf;

public class OneSignalPackagePrivateHelper {

   private static abstract class RunnableArg<T> {
      abstract void run(T object);
   }

   static private void processNetworkHandles(RunnableArg runnable) {
      Set<Map.Entry<Integer, UserStateSynchronizer.NetworkHandlerThread>> entrySet;

      entrySet = OneSignalStateSynchronizer.getPushStateSynchronizer().networkHandlerThreads.entrySet();
      for (Map.Entry<Integer, UserStateSynchronizer.NetworkHandlerThread> handlerThread : entrySet)
         runnable.run(handlerThread.getValue());

      entrySet = OneSignalStateSynchronizer.getEmailStateSynchronizer().networkHandlerThreads.entrySet();
      for (Map.Entry<Integer, UserStateSynchronizer.NetworkHandlerThread> handlerThread : entrySet)
         runnable.run(handlerThread.getValue());
   }

   private static boolean startedRunnable;
   public static boolean runAllNetworkRunnables() {
      startedRunnable = false;

      RunnableArg runnable = new RunnableArg<UserStateSynchronizer.NetworkHandlerThread>() {
         @Override
         void run(UserStateSynchronizer.NetworkHandlerThread handlerThread) {
            Scheduler scheduler = shadowOf(handlerThread.getLooper()).getScheduler();
            while (scheduler.runOneTask())
               startedRunnable = true;
         }
      };

      processNetworkHandles(runnable);

      return startedRunnable;
   }

   public static boolean runFocusRunnables() {
      Looper looper = ActivityLifecycleHandler.focusHandlerThread.getHandlerLooper();
      if (looper == null)
         return false;
      
      Scheduler scheduler = shadowOf(looper).getScheduler();
      if (scheduler == null)
         return false;

      boolean ranTask = false;
      while (scheduler.runOneTask())
         ranTask = true;

      return ranTask;
   }

   public static void resetRunnables() {
      RunnableArg runnable = new RunnableArg<UserStateSynchronizer.NetworkHandlerThread>() {
         @Override
         void run(UserStateSynchronizer.NetworkHandlerThread handlerThread) {
            handlerThread.stopScheduledRunnable();
         }
      };

      processNetworkHandles(runnable);

      Looper looper = ActivityLifecycleHandler.focusHandlerThread.getHandlerLooper();
      if (looper == null) return;

      shadowOf(looper).reset();
   }

   public static void OneSignal_sendPurchases(JSONArray purchases, boolean newAsExisting, OneSignalRestClient.ResponseHandler responseHandler) {
      OneSignal.sendPurchases(purchases, newAsExisting, responseHandler);
   }

   public static void SyncService_onTaskRemoved(Service service) {
      SyncService.onTaskRemoved(service);
   }

   public static JSONObject bundleAsJSONObject(Bundle bundle) {
      return NotificationBundleProcessor.bundleAsJSONObject(bundle);
   }

   public static BundleCompat createInternalPayloadBundle(Bundle bundle) {
      BundleCompat retBundle = BundleCompatFactory.getInstance();
      retBundle.putString("json_payload", OneSignalPackagePrivateHelper.bundleAsJSONObject(bundle).toString());
      return retBundle;
   }

   public static void NotificationBundleProcessor_ProcessFromGCMIntentService(Context context, Bundle bundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      NotificationBundleProcessor.ProcessFromGCMIntentService(context, createInternalPayloadBundle(bundle), overrideSettings);
   }

   public static void NotificationBundleProcessor_ProcessFromGCMIntentService_NoWrap(Context context, BundleCompat bundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      NotificationBundleProcessor.ProcessFromGCMIntentService(context, bundle, overrideSettings);
   }

   public static boolean GcmBroadcastReceiver_processBundle(Context context, Bundle bundle) {
      NotificationBundleProcessor.ProcessedBundleResult processedResult = NotificationBundleProcessor.processBundleFromReceiver(context, bundle);
      return processedResult.processed();
   }

   public static int NotificationBundleProcessor_Process(Context context, boolean restoring, JSONObject jsonPayload, NotificationExtenderService.OverrideSettings overrideSettings) {
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = jsonPayload;
      notifJob.overrideSettings = overrideSettings;
      return NotificationBundleProcessor.ProcessJobForDisplay(notifJob);
   }

   public static class NotificationTable extends OneSignalDbContract.NotificationTable { }
   public static class NotificationRestorer extends com.onesignal.NotificationRestorer { }
   public static class NotificationGenerationJob extends com.onesignal.NotificationGenerationJob {
      NotificationGenerationJob(Context context) {
         super(context);
      }
   }

   public static String NotificationChannelManager_createNotificationChannel(Context context, JSONObject payload) {
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = payload;
      return NotificationChannelManager.createNotificationChannel(notifJob);
   }
   
   public static void NotificationChannelManager_processChannelList(Context context, JSONObject jsonObject) {
      NotificationChannelManager.processChannelList(context, jsonObject);
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
