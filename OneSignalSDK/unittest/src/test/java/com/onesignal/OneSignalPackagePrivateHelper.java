package com.onesignal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.influence.OSTrackerFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.robolectric.util.Scheduler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.robolectric.Shadows.shadowOf;

public class OneSignalPackagePrivateHelper {
   public static final String IN_APP_MESSAGES_JSON_KEY = com.onesignal.OSInAppMessageController.IN_APP_MESSAGES_JSON_KEY;

   private static abstract class RunnableArg<T> {
      abstract void run(T object) throws Exception;
   }

   private static void processNetworkHandles(RunnableArg runnable) throws Exception {
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

   public static OSSessionManager.SessionListener OneSignal_getSessionListener() {
      return OneSignal.getSessionListener();
   }

   public static void OneSignal_setSharedPreferences(OSSharedPreferences preferences) {
      OneSignal.setSharedPreferences(preferences);
   }

   public static void OneSignal_setSessionManager(OSSessionManager sessionManager) {
      OneSignal.setSessionManager(sessionManager);
   }

   public static void OneSignal_setTrackerFactory(OSTrackerFactory trackerFactory) {
      OneSignal.setTrackerFactory(trackerFactory);
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

   public static void GcmBroadcastReceiver_onReceived(Context context, Bundle bundle) {
      GCMBroadcastReceiver receiver = new GCMBroadcastReceiver();
      Intent intent = new Intent();
      intent.setAction("com.google.android.c2dm.intent.RECEIVE");
      intent.putExtras(bundle);
      receiver.onReceive(context,intent);
   }

   public static int NotificationBundleProcessor_Process(Context context, boolean restoring, JSONObject jsonPayload, NotificationExtenderService.OverrideSettings overrideSettings) {
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = jsonPayload;
      notifJob.overrideSettings = overrideSettings;
      return NotificationBundleProcessor.ProcessJobForDisplay(notifJob);
   }

   public static class NotificationTable extends OneSignalDbContract.NotificationTable { }
   public static class InAppMessageTable extends OneSignalDbContract.InAppMessageTable { }
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

   public static class GCMBroadcastReceiver extends com.onesignal.GcmBroadcastReceiver {}

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

   public static void NotificationSummaryManager_updateSummaryNotificationAfterChildRemoved(Context context, OneSignalDb db, String group, boolean dismissed) {
      NotificationSummaryManager.updateSummaryNotificationAfterChildRemoved(context, db, group, dismissed);
   }

   public class TestOneSignalPrefs extends com.onesignal.OneSignalPrefs {}

   public static void OneSignal_onAppLostFocus() {
      OneSignal.onAppLostFocus();
   }

   public static DelayedConsentInitializationParameters OneSignal_delayedInitParams() {
      return OneSignal.delayedInitParams;
   }

   public static boolean OneSignal_requiresUserPrivacyConsent() {
      return OneSignal.requiresUserPrivacyConsent;
   }

   public static String OneSignal_appId() {
      return OneSignal.appId;
   }

   public static void OneSignal_setAppId(String appId) {
      OneSignal.appId = appId;
   }

   static public class OSSharedPreferencesWrapper extends com.onesignal.OSSharedPreferencesWrapper {}

   static public class RemoteOutcomeParams extends OneSignalRemoteParams.InfluenceParams {

      public RemoteOutcomeParams() {
         this(true, true, true);
      }

      public RemoteOutcomeParams(boolean direct, boolean indirect, boolean unattributed) {
         directEnabled = direct;
         indirectEnabled = indirect;
         unattributedEnabled = unattributed;
      }
   }

   public static class BadgeCountUpdater extends com.onesignal.BadgeCountUpdater {
      public static void update(OneSignalDb db, Context context) {
         com.onesignal.BadgeCountUpdater.update(db, context);
      }
   }

   public static class NotificationLimitManager extends com.onesignal.NotificationLimitManager {
      public static void clearOldestOverLimitFallback(Context context, int notifsToMakeRoomFor) {
         com.onesignal.NotificationLimitManager.clearOldestOverLimitFallback(context, notifsToMakeRoomFor);
      }

      public static void clearOldestOverLimitStandard(Context context, int notifsToMakeRoomFor) throws Throwable {
         com.onesignal.NotificationLimitManager.clearOldestOverLimitStandard(context, notifsToMakeRoomFor);
      }
   }

   public class OneSignalDbContract extends com.onesignal.OneSignalDbContract {}

   /** In-App Messaging Helpers */

   public static class OSTestInAppMessage extends com.onesignal.OSInAppMessage {

      public OSTestInAppMessage(@NonNull String messageId, int displaysQuantity, long lastDisplayTime, boolean displayed, Set<String> clickIds) {
         super(messageId, clickIds, displayed, new OSInAppMessageRedisplayStats(displaysQuantity, lastDisplayTime));
      }

      OSTestInAppMessage(JSONObject json) throws JSONException {
         super(json);
      }

      OSTestInAppMessage(com.onesignal.OSInAppMessage inAppMessage) throws JSONException {
         super(inAppMessage.toJSONObject());
      }

      @Override
      protected ArrayList<ArrayList<OSTrigger>> parseTriggerJson(JSONArray triggersJson) throws JSONException {
         ArrayList<ArrayList<OSTrigger>> parsedTriggers = new ArrayList<>();

         for (int i = 0; i < triggersJson.length(); i++) {
            JSONArray ands = triggersJson.getJSONArray(i);

            ArrayList<OSTrigger> parsed = new ArrayList<>();

            for (int j = 0; j < ands.length(); j++) {
               OSTrigger trig = new OSTestTrigger(ands.getJSONObject(j));

               parsed.add(trig);
            }

            parsedTriggers.add(parsed);
         }

         return parsedTriggers;
      }

      @Override
      public void setDisplayDuration(double displayDuration) {
         super.setDisplayDuration(displayDuration);
      }

      @NonNull
      @Override
      public Set<String> getClickedClickIds() {
         return super.getClickedClickIds();
      }

      @Override
      public boolean isClickAvailable(String clickId) {
         return super.isClickAvailable(clickId);
      }

      @Override
      public  void clearClickIds() {
         super.clearClickIds();
      }

      @Override
      public  void addClickId(String clickId) {
         super.addClickId(clickId);
      }

      @Override
      public double getDisplayDuration() {
         return super.getDisplayDuration();
      }

      @Override
      public OSTestInAppMessageDisplayStats getRedisplayStats() {
         return new OSTestInAppMessageDisplayStats(super.getRedisplayStats());
      }

      @Override
      public void setRedisplayStats(int displayQuantity, long lastDisplayTime) {
         super.setRedisplayStats(displayQuantity, lastDisplayTime);
      }

      public JSONObject toJSONObject() {
         return super.toJSONObject();
      }
   }

   public static class OSTestInAppMessageDisplayStats extends OSInAppMessageRedisplayStats {

      private OSInAppMessageRedisplayStats displayStats;

      OSTestInAppMessageDisplayStats(OSInAppMessageRedisplayStats displayStats) {
         this.displayStats = displayStats;
      }

      @Override
      public void setDisplayStats(OSInAppMessageRedisplayStats displayStats) {
         this.displayStats.setDisplayStats(displayStats);
      }

      @Override
      public long getLastDisplayTime() {
         return this.displayStats.getLastDisplayTime();
      }

      @Override
      public void setLastDisplayTime(long lastDisplayTime) {
         this.displayStats.setLastDisplayTime(lastDisplayTime);
      }

      public void setLastDisplayTimeToCurrent() {
         this.displayStats.setLastDisplayTime(System.currentTimeMillis() / 1000);
      }

      @Override
      public void incrementDisplayQuantity() {
         this.displayStats.incrementDisplayQuantity();
      }

      @Override
      public int getDisplayQuantity() {
         return this.displayStats.getDisplayQuantity();
      }

      @Override
      public void setDisplayQuantity(int displayQuantity) {
         this.displayStats.setDisplayQuantity(displayQuantity);
      }

      @Override
      public int getDisplayLimit() {
         return this.displayStats.getDisplayLimit();
      }

      @Override
      public void setDisplayLimit(int displayLimit) {
         this.displayStats.setDisplayLimit(displayLimit);
      }

      @Override
      public long getDisplayDelay() {
         return this.displayStats.getDisplayDelay();
      }

      @Override
      public void setDisplayDelay(long displayDelay) {
         this.displayStats.setDisplayDelay(displayDelay);
      }

      @Override
      public boolean shouldDisplayAgain() {
         return this.displayStats.shouldDisplayAgain();
      }

      @Override
      public boolean isDelayTimeSatisfied() {
         return this.displayStats.isDelayTimeSatisfied();
      }

      @Override
      public boolean isRedisplayEnabled() {
         return this.displayStats.isRedisplayEnabled();
      }
   }

   public static class OSTestTrigger extends com.onesignal.OSTrigger {
      public OSTestTrigger(JSONObject json) throws JSONException {
         super(json);
      }
   }

   public static class OSTestInAppMessageAction extends com.onesignal.OSInAppMessageAction {
      public boolean closes() {
         return closesMessage;
      }
      public String getClickId() { return clickId; }

      public OSTestInAppMessageAction(JSONObject json) throws JSONException {
         super(json);
      }
   }

   public static void dismissCurrentMessage() {
      com.onesignal.OSInAppMessage message = OneSignal.getInAppMessageController().getCurrentDisplayedInAppMessage();
      if (message != null)
         OneSignal.getInAppMessageController().messageWasDismissed(message);
   }

   public static boolean isInAppMessageShowing() {
      return OneSignal.getInAppMessageController().isInAppMessageShowing();
   }

   public static String getShowingInAppMessageId() {
      return OneSignal.getInAppMessageController().getCurrentDisplayedInAppMessage().messageId;
   }

   public static ArrayList<com.onesignal.OSInAppMessage> getInAppMessageDisplayQueue() {
      return OneSignal.getInAppMessageController().getInAppMessageDisplayQueue();
   }

   public static void onMessageActionOccurredOnMessage(@NonNull final com.onesignal.OSInAppMessage message, @NonNull final JSONObject actionJson) throws JSONException {
      OneSignal.getInAppMessageController().onMessageActionOccurredOnMessage(message, actionJson);
   }

   public static void onMessageWasShown(@NonNull com.onesignal.OSInAppMessage message) {
      OneSignal.getInAppMessageController().onMessageWasShown(message);
   }

   public static List<OSTestInAppMessage> getRedisplayInAppMessages() {
      List<OSInAppMessage> messages = OneSignal.getInAppMessageController().getRedisplayedInAppMessages();
      List<OSTestInAppMessage> testMessages = new ArrayList<>();

      for (OSInAppMessage message : messages) {
         try {
            OSTestInAppMessage testInAppMessage = new OSTestInAppMessage(message);
            testInAppMessage.getRedisplayStats().setDisplayStats(message.getRedisplayStats());
            testMessages.add(testInAppMessage);

         } catch (JSONException e) {
            e.printStackTrace();
         }
      }
      return testMessages;
   }

   public static boolean hasConfigChangeFlag(Activity activity, int configChangeFlag) {
      return OSUtils.hasConfigChangeFlag(activity, configChangeFlag);
   }

   public static int getDeviceType() {
      return new OSUtils().getDeviceType();
   }

   public abstract class UserState extends com.onesignal.UserState {
      UserState(String inPersistKey, boolean load) {
         super(inPersistKey, load);
      }
   }

   public static class WebViewManager extends com.onesignal.WebViewManager {

      public static void callDismissAndAwaitNextMessage() {
         lastInstance.dismissAndAwaitNextMessage(null);
      }

      public void dismissAndAwaitNextMessage(@Nullable final OneSignalGenericCallback callback) {
         super.dismissAndAwaitNextMessage(callback);
      }

      protected WebViewManager(@NonNull com.onesignal.OSInAppMessage message, @NonNull Activity activity) {
         super(message, activity);
      }
   }


   public static class JSONUtils extends com.onesignal.JSONUtils {


   }

   public static class GenerateNotification extends com.onesignal.GenerateNotification {}

   public static class NotificationBundleProcessor extends com.onesignal.NotificationBundleProcessor {}

   public static class OSNotificationFormatHelper extends com.onesignal.OSNotificationFormatHelper {}

   public static class NotificationPayloadProcessorHMS extends com.onesignal.NotificationPayloadProcessorHMS {}
}
