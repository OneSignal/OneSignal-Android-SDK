package com.onesignal;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.robolectric.util.Scheduler;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.robolectric.Shadows.shadowOf;

public class OneSignalPackagePrivateHelper {

   private static abstract class RunnableArg<T> {
      abstract void run(T object) throws Exception;
   }

   static private void processNetworkHandles(RunnableArg runnable) throws Exception {
      Set<Map.Entry<Integer, UserStateSynchronizer.NetworkHandlerThread>> entrySet;

      entrySet = OneSignalStateSynchronizer.getPushStateSynchronizer().networkHandlerThreads.entrySet();
      for (Map.Entry<Integer, UserStateSynchronizer.NetworkHandlerThread> handlerThread : entrySet)
         runnable.run(handlerThread.getValue());

      entrySet = OneSignalStateSynchronizer.getEmailStateSynchronizer().networkHandlerThreads.entrySet();
      for (Map.Entry<Integer, UserStateSynchronizer.NetworkHandlerThread> handlerThread : entrySet)
         runnable.run(handlerThread.getValue());
   }

   private static boolean startedRunnable;
   public static boolean runAllNetworkRunnables() throws Exception {
      startedRunnable = false;

      RunnableArg runnable = new RunnableArg<UserStateSynchronizer.NetworkHandlerThread>() {
         @Override
         void run(UserStateSynchronizer.NetworkHandlerThread handlerThread) throws Exception {
            synchronized (handlerThread.mHandler) {
               Scheduler scheduler = shadowOf(handlerThread.getLooper()).getScheduler();
               while (scheduler.runOneTask())
                  startedRunnable = true;
            }
         }
      };

      processNetworkHandles(runnable);

      return startedRunnable;
   }

   private static boolean isExecutingRunnable(Scheduler scheduler) throws Exception {
      Field isExecutingRunnableField = Scheduler.class.getDeclaredField("isExecutingRunnable");
      isExecutingRunnableField.setAccessible(true);
      return (Boolean)isExecutingRunnableField.get(scheduler);
   }

   public static boolean runFocusRunnables() throws Exception {
      Looper looper = ActivityLifecycleHandler.focusHandlerThread.getHandlerLooper();
      if (looper == null)
         return false;
      
      final Scheduler scheduler = shadowOf(looper).getScheduler();
      if (scheduler == null)
         return false;

      // Need to check this before .size() as it will block
      if (isExecutingRunnable(scheduler))
         return false;

      if (scheduler.size() == 0)
         return false;

      Thread handlerThread = new Thread(new Runnable() {
         @Override
         public void run() {
            while (scheduler.runOneTask());
         }
      });
      handlerThread.start();

      while (true) {
         if (!ShadowOneSignalRestClient.isAFrozenThread(handlerThread))
            handlerThread.join(1);
         if (handlerThread.getState() == Thread.State.WAITING ||
             handlerThread.getState() == Thread.State.TERMINATED)
            break;
      }
      return true;
   }

   public static void OneSignal_sendPurchases(JSONArray purchases, boolean newAsExisting, OneSignalRestClient.ResponseHandler responseHandler) {
      OneSignal.sendPurchases(purchases, newAsExisting, responseHandler);
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

   public static class OneSignalSyncServiceUtils_SyncRunnable extends com.onesignal.OneSignalSyncServiceUtils.SyncRunnable {
      @Override
      protected void stopSync() {
      }
   }

   public static class PushRegistratorGCM extends com.onesignal.PushRegistratorGCM {}

   public static class OneSignalRestClient extends com.onesignal.OneSignalRestClient {
      public static abstract class ResponseHandler extends com.onesignal.OneSignalRestClient.ResponseHandler {
         @Override
         public void onSuccess(String response) {}
         @Override
         public void onFailure(int statusCode, String response, Throwable throwable) {}
      }
   }

   public static String NotificationChannelManager_createNotificationChannel(Context context, JSONObject payload) {
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = payload;
      return NotificationChannelManager.createNotificationChannel(notifJob);
   }
   
   public static void NotificationChannelManager_processChannelList(Context context, JSONArray jsonArray) {
      NotificationChannelManager.processChannelList(context, jsonArray);
   }
   
   public static void NotificationOpenedProcessor_processFromContext(Context context, Intent intent) {
      NotificationOpenedProcessor.processFromContext(context, intent);
   }
   
   public static void NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(Context context, SQLiteDatabase writableDb, String group, boolean dismissed) {
      NotificationSummaryManager.updateSummaryNotificationAfterChildRemoved(context, writableDb, group, dismissed);
   }

   public class OneSignalPrefs extends com.onesignal.OneSignalPrefs {}

   public static void OneSignal_onAppLostFocus() {
      OneSignal.onAppLostFocus();
   }

   public static DelayedConsentInitializationParameters OneSignal_delayedInitParams() { return OneSignal.delayedInitParams; }

   public static boolean OneSignal_requiresUserPrivacyConsent() { return OneSignal.requiresUserPrivacyConsent; }

   public static String OneSignal_appId() { return OneSignal.appId; }


   public static void OneSignal_setAppContext(Context context) { OneSignal.setAppContext(context); }

   static public class BadgeCountUpdater extends com.onesignal.BadgeCountUpdater {
      public static void update(SQLiteDatabase readableDb, Context context) {
         com.onesignal.BadgeCountUpdater.update(readableDb, context);
      }
   }

   static public class NotificationLimitManager extends com.onesignal.NotificationLimitManager {
      public static void clearOldestOverLimitFallback(Context context, int notifsToMakeRoomFor) {
         com.onesignal.NotificationLimitManager.clearOldestOverLimitFallback(context, notifsToMakeRoomFor);
      }

      public static void clearOldestOverLimitStandard(Context context, int notifsToMakeRoomFor) throws Throwable {
         com.onesignal.NotificationLimitManager.clearOldestOverLimitStandard(context, notifsToMakeRoomFor);
      }
   }

   public class OneSignalDbContract extends com.onesignal.OneSignalDbContract {}
}
