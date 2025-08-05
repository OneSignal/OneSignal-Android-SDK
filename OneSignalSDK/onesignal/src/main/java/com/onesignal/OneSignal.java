/**
 * Modified MIT License
 *
 * Copyright 2019 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

import com.onesignal.debug.OneSignalLogEvent;
import com.onesignal.debug.OneSignalLogListener;
import com.onesignal.influence.data.OSTrackerFactory;
import com.onesignal.influence.domain.OSInfluence;
import com.onesignal.language.LanguageContext;
import com.onesignal.language.LanguageProviderAppDefined;
import com.onesignal.OneSignalStateSynchronizer.OSDeviceInfoError;
import com.onesignal.OneSignalStateSynchronizer.OSDeviceInfoCompletionHandler;
import com.onesignal.outcomes.data.OSOutcomeEventsFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID;
import static com.onesignal.NotificationBundleProcessor.newJsonArray;
import static com.onesignal.OSUtils.startThreadWithRetry;

/**
 * The main OneSignal class - this is where you will interface with the OneSignal SDK
 * <br/><br/>
 * <b>Reminder:</b> Add your {@code onesignal_app_id} to your build.gradle config in <i>android</i> > <i>defaultConfig</i>
 * <br/>
 * @see <a href="https://documentation.onesignal.com/docs/android-sdk-setup#section-1-gradle-setup">OneSignal Gradle Setup</a>
 */
public class OneSignal {

   // If the app is this amount time or longer in the background we will count the session as done
   static final long MIN_ON_SESSION_TIME_MILLIS = 30 * 1_000L;

    public enum LOG_LEVEL {
      NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
   }

   /**
    * An app entry type enum for knowing how the user foregrounded or backgrounded the app.
    * <br/><br/>
    * The enum also helps decide the type of session the user is in an is tracked in {@link OneSignal#mSessionManager}
    *  from the {@link OSSessionManager}.
    * <br/><br/>
    * {@link AppEntryAction#NOTIFICATION_CLICK} will always lead a overridden {@link com.onesignal.influence.domain.OSInfluenceType#DIRECT}.
    * {@link AppEntryAction#APP_OPEN} on a new session notifications within the attribution window
    *  parameter, this will lead to a {@link com.onesignal.influence.domain.OSInfluenceType#DIRECT}.
    * <br/><br/>
    * @see OneSignal#onAppFocus
    * @see OneSignal#onAppLostFocus
    * @see OneSignal#handleNotificationOpen
    */
   public enum AppEntryAction {

      /**
       * Entered the app through opening a notification
       */
      NOTIFICATION_CLICK,

      /**
       * Entered the app through clicking the icon
       */
      APP_OPEN,

      /**
       * App came from the background
       */
      APP_CLOSE;

      public boolean isNotificationClick() {
          return this.equals(NOTIFICATION_CLICK);
      }

      public boolean isAppOpen() {
          return this.equals(APP_OPEN);
      }

      public boolean isAppClose() {
          return this.equals(APP_CLOSE);
      }
   }

   /**
    * Implement this interface on a class with a default public constructor and provide class with namespace
    * as a value to a new `meta-data` tag with the key name of "com.onesignal.NotificationServiceExtension" in
    * your AndroidManifest.xml.
    *    ex. <meta-data android:name="com.onesignal.NotificationServiceExtension" android:value="com.company.MyNotificationExtensionService" />
    * <br/><br/>
    * Allows for modification of a notification by calling {@link OSNotification#mutableCopy}
    *    instance and passing it into {@link OSMutableNotification#setExtender(NotificationCompat.Extender)}
    * To display the notification, call {@link OSNotificationReceivedEvent#complete(OSNotification)} with a notification instance.
    * To omit displaying a notification call {@link OSNotificationReceivedEvent#complete(OSNotification)} with null.
    */
   public interface OSRemoteNotificationReceivedHandler {

      void remoteNotificationReceived(Context context, OSNotificationReceivedEvent notificationReceivedEvent);
   }

   /**
    * Meant to be implemented with {@link OneSignal#setNotificationWillShowInForegroundHandler(OSNotificationWillShowInForegroundHandler)}
    * <br/><br/>
    * Call {@link OSNotificationReceivedEvent#complete(OSNotification)} with null
    * for not displaying notification or {@link OSMutableNotification} to modify notification before displaying.
    * If {@link OSNotificationReceivedEvent#complete(OSNotification)} is not called within 25 seconds, original notification will be displayed.
    * <br/><br/>
    * TODO: Update docs with new NotificationReceivedHandler
    * @see <a href="https://documentation.onesignal.com/docs/android-native-sdk#notificationreceivedhandler">NotificationReceivedHandler | OneSignal Docs</a>
    */
   public interface OSNotificationWillShowInForegroundHandler {

      void notificationWillShowInForeground(OSNotificationReceivedEvent notificationReceivedEvent);
   }

   /**
    * An interface used to process a OneSignal notification the user just tapped on.
    * <br/>
    * Set this during OneSignal init in
    * {@link OneSignal#setNotificationOpenedHandler(OSNotificationOpenedHandler)}
    * <br/><br/>
    * @see <a href="https://documentation.onesignal.com/docs/android-native-sdk#notificationopenedhandler">NotificationOpenedHandler | OneSignal Docs</a>
    */
   public interface OSNotificationOpenedHandler {
      /**
       * Fires when a user taps on a notification.
       * @param result a {@link OSNotificationOpenedResult} with the user's response and properties of this notification
       */
      void notificationOpened(OSNotificationOpenedResult result);
   }

   /**
    * An interface used to process a OneSignal In-App Message the user just tapped on.
    * <br/>
    * Set this during OneSignal init in
    * {@link OneSignal#setInAppMessageClickHandler(OSInAppMessageClickHandler)}
    */
   public interface OSInAppMessageClickHandler {
      /**
       * Fires when a user taps on a clickable element in the notification such as a button or image
       * @param result a {@link OSInAppMessageAction}
       **/
      void inAppMessageClicked(OSInAppMessageAction result);
   }

   /**
    * Interface which you can implement and pass to {@link OneSignal#getTags(OSGetTagsHandler)} to
    * get all the tags set on a user
    * <br/><br/>
    * <b>Note:</b> the {@link #tagsAvailable(JSONObject)} callback does not run on the Main(UI)
    * Thread, so be aware when modifying UI in this method.
    */
   public interface OSGetTagsHandler {
      /**
       * <b>Note:</b> this callback does not run on the Main(UI)
       * Thread, so be aware when modifying UI in this method.
       * @param tags a JSONObject containing the OneSignal tags for the user in a key/value map
       */
      void tagsAvailable(JSONObject tags);
   }

   public interface ChangeTagsUpdateHandler {
      void onSuccess(JSONObject tags);
      void onFailure(SendTagsError error);
   }

   public static class SendTagsError {
      private String message;
      private int code;

      SendTagsError(int errorCode, String errorMessage) {
         this.message = errorMessage;
         this.code = errorCode;
      }

      public int getCode() { return code; }
      public String getMessage() { return message; }
   }

   public static class OSLanguageError {
      private int errorCode;
      private String message;

      OSLanguageError(int errorCode, String message) {
         this.errorCode = errorCode;
         this.message = message;
      }

      public int getCode() { return errorCode; }

      public String getMessage() { return message; }
   }

   public interface OSSetLanguageCompletionHandler {
      void onSuccess(String results);
      void onFailure(OSLanguageError error);
   }

   public enum ExternalIdErrorType {
      REQUIRES_EXTERNAL_ID_AUTH, INVALID_OPERATION, NETWORK
   }

   public static class ExternalIdError {
      private ExternalIdErrorType type;
      private String message;

      ExternalIdError(ExternalIdErrorType type, String message) {
         this.type = type;
         this.message = message;
      }

      public ExternalIdErrorType getType() {
         return type;
      }

      public String getMessage() {
         return message;
      }
   }

   public interface OSExternalUserIdUpdateCompletionHandler {
      void onSuccess(JSONObject results);
      void onFailure(ExternalIdError error);
   }

   interface OSInternalExternalUserIdUpdateCompletionHandler {
      void onComplete(String channel, boolean success);
   }

   public enum SMSErrorType {
      VALIDATION, REQUIRES_SMS_AUTH, INVALID_OPERATION, NETWORK
   }

   public static class OSSMSUpdateError {
      private SMSErrorType type;
      private String message;

      OSSMSUpdateError(SMSErrorType type, String message) {
         this.type = type;
         this.message = message;
      }

      public SMSErrorType getType() {
         return type;
      }

      public String getMessage() {
         return message;
      }
   }

   public interface OSSMSUpdateHandler {
      void onSuccess(JSONObject result);
      void onFailure(OSSMSUpdateError error);
   }

   private static OSSMSUpdateHandler smsUpdateHandler;
   private static OSSMSUpdateHandler smsLogoutHandler;

   public enum EmailErrorType {
      VALIDATION, REQUIRES_EMAIL_AUTH, INVALID_OPERATION, NETWORK
   }

   public static class EmailUpdateError {
      private EmailErrorType type;
      private String message;

      EmailUpdateError(EmailErrorType type, String message) {
         this.type = type;
         this.message = message;
      }

      public EmailErrorType getType() {
         return type;
      }

      public String getMessage() {
         return message;
      }
   }

   public interface EmailUpdateHandler {
      void onSuccess();
      void onFailure(EmailUpdateError error);
   }

   private static EmailUpdateHandler emailUpdateHandler;
   private static EmailUpdateHandler emailLogoutHandler;

   /**
    * Fires delegate when the notification was created or fails to be created.
    */
   public interface PostNotificationResponseHandler {
      void onSuccess(JSONObject response);
      void onFailure(JSONObject response);
   }

   /**
    * Fires when the User accepts or declines the notification permission prompt.
    */
   public interface PromptForPushNotificationPermissionResponseHandler {
      void response(boolean accepted);
   }

   interface EntryStateListener {
      // Fire with the last appEntryState that just ended.
      void onEntryStateChange(AppEntryAction appEntryState);
   }

   private static List<EntryStateListener> entryStateListeners = new ArrayList<>();
   static void callEntryStateListeners(AppEntryAction appEntryState) {
      List<EntryStateListener> entryStateListeners = new ArrayList<>(OneSignal.entryStateListeners);
      for (EntryStateListener sessionListener : entryStateListeners) {
         sessionListener.onEntryStateChange(appEntryState);
      }
   }

   static void addEntryStateListener(EntryStateListener sessionListener, AppEntryAction appEntryState) {
      // We only care for open and close changes
      if (!appEntryState.equals(AppEntryAction.NOTIFICATION_CLICK))
         entryStateListeners.add(sessionListener);
   }

   static void removeEntryStateListener(EntryStateListener sessionListener) {
      entryStateListeners.remove(sessionListener);
   }

   static Context appContext;
   static WeakReference<Activity> appActivity;
   static String appId;
   static String googleProjectNumber;

   @Nullable
   static Activity getCurrentActivity() {
      ActivityLifecycleHandler activityLifecycleHandler = ActivityLifecycleListener.getActivityLifecycleHandler();
      return activityLifecycleHandler != null ? activityLifecycleHandler.getCurActivity() : null;
   }

   private static LOG_LEVEL visualLogLevel = LOG_LEVEL.NONE;
   private static LOG_LEVEL logCatLevel = LOG_LEVEL.WARN;

   private static String userId = null;
   private static String emailId = null;
   private static String smsId = null;
   private static int subscribableStatus = Integer.MAX_VALUE;

   // changed from private to package-private for unit test access
   static LanguageContext languageContext = null;

   static OSRemoteNotificationReceivedHandler remoteNotificationReceivedHandler;
   static OSNotificationWillShowInForegroundHandler notificationWillShowInForegroundHandler;
   static OSNotificationOpenedHandler notificationOpenedHandler;
   static OSInAppMessageClickHandler inAppMessageClickHandler;

   // Is the init() of OneSignal SDK finished yet
   private static boolean initDone;
   static boolean isInitDone() {
      return initDone;
   }

   // Is the app in the inForeground or not
   private static boolean inForeground;
   static boolean isInForeground() {
      return inForeground;
   }
   static void setInForeground(boolean inForeground) {
      OneSignal.inForeground = inForeground;
   }

   // Tells the action taken to enter the app
   @NonNull private static AppEntryAction appEntryState = AppEntryAction.APP_CLOSE;
   static @NonNull AppEntryAction getAppEntryState() {
      return appEntryState;
   }

   private static TrackGooglePurchase trackGooglePurchase;
   private static TrackAmazonPurchase trackAmazonPurchase;
   private static TrackFirebaseAnalytics trackFirebaseAnalytics;

   private static final String VERSION = "040812";
   public static String getSdkVersionRaw() {
      return VERSION;
   }

   private static OSLogger logger = new OSLogWrapper();
   static OSLogger getLogger() {
      return logger;
   }

   private static FocusTimeController focusTimeController;
   private static OSSessionManager.SessionListener sessionListener = new OSSessionManager.SessionListener() {
         @Override
         public void onSessionEnding(@NonNull List<OSInfluence> lastInfluences) {
            if (outcomeEventsController == null)
               OneSignal.Log(LOG_LEVEL.WARN, "OneSignal onSessionEnding called before init!");
            if (outcomeEventsController != null)
               outcomeEventsController.cleanOutcomes();
            getFocusTimeController().onSessionEnded(lastInfluences);
         }
      };

   private static OSInAppMessageControllerFactory inAppMessageControllerFactory = new OSInAppMessageControllerFactory();
   static OSInAppMessageController getInAppMessageController() {
      return inAppMessageControllerFactory.getController(getDBHelperInstance(), taskController, getLogger(), getSharedPreferences(), languageContext);
   }
   private static OSTime time = new OSTimeImpl();
   private static OSRemoteParamController remoteParamController = new OSRemoteParamController();
   private static OSTaskController taskController = new OSTaskController(logger);
   private static OSTaskRemoteController taskRemoteController = new OSTaskRemoteController(remoteParamController, logger);
   private static OneSignalAPIClient apiClient = new OneSignalRestClientWrapper();
   private static OSSharedPreferences preferences = new OSSharedPreferencesWrapper();
   static OSSharedPreferences getSharedPreferences() {
      return preferences;
   }
   private static OSTrackerFactory mTrackerFactory;
   private static OSSessionManager mSessionManager;
   @Nullable private static OSOutcomeEventsController outcomeEventsController;
   @Nullable private static OSOutcomeEventsFactory outcomeEventsFactory;
   @Nullable private static OSNotificationDataController notificationDataController;
   private static final Object outcomeEventsControllerSyncLock = new Object() {};


   private static OSTrackerFactory getTrackerFactory(){
      if(mTrackerFactory == null){
         mTrackerFactory = new OSTrackerFactory(preferences, logger, time);
      }
      return mTrackerFactory;
   }

   private static OSSessionManager getOSSessionManager(){
      if(mSessionManager == null){
         mSessionManager = new OSSessionManager(sessionListener, getTrackerFactory(), logger);
      }
      return mSessionManager;
   }

   static OSOutcomeEventsController getOutcomeEventsController() {
      if (outcomeEventsController == null) {
         synchronized(outcomeEventsControllerSyncLock) {
            if (outcomeEventsController == null) {
               if (outcomeEventsFactory == null) {
                  OneSignalDbHelper dbHelper = getDBHelperInstance();
                  outcomeEventsFactory = new OSOutcomeEventsFactory(logger, apiClient, dbHelper, preferences);
               }
               outcomeEventsController = new OSOutcomeEventsController(getOSSessionManager(), outcomeEventsFactory);
            }
         }
      }
      return outcomeEventsController;
   }

   @SuppressWarnings("WeakerAccess")
   public static String sdkType = "native";
   private static String lastRegistrationId;

   @NonNull private static OSUtils osUtils = new OSUtils();

   private static boolean registerForPushFired, locationFired, getTagsCall, waitingToPostStateSync, androidParamsRequestStarted;

   private static LocationController.LocationPoint lastLocationPoint;

   private static Collection<JSONArray> unprocessedOpenedNotifs = new ArrayList<>();
   private static HashSet<String> postedOpenedNotifIds = new HashSet<>();
   private static final ArrayList<OSGetTagsHandler> pendingGetTagsHandlers = new ArrayList<>();

   private static DelayedConsentInitializationParameters delayedInitParams;
   static DelayedConsentInitializationParameters getDelayedInitParams() {
      return delayedInitParams;
   }

   // Start PermissionState
   private static OSPermissionState currentPermissionState;
   private static OSPermissionState getCurrentPermissionState(Context context) {
      if (context == null)
         return null;

      if (currentPermissionState == null) {
         currentPermissionState = new OSPermissionState(false);
         currentPermissionState.getObservable().addObserverStrong(new OSPermissionChangedInternalObserver());
      }

      return currentPermissionState;
   }

   static OSPermissionState lastPermissionState;
   private static OSPermissionState getLastPermissionState(Context context) {
      if (context == null)
         return null;

      if (lastPermissionState == null)
         lastPermissionState = new OSPermissionState(true);

      return lastPermissionState;
   }

   private static OSObservable<OSPermissionObserver, OSPermissionStateChanges> permissionStateChangesObserver;
   static OSObservable<OSPermissionObserver, OSPermissionStateChanges> getPermissionStateChangesObserver() {
      if (permissionStateChangesObserver == null)
         permissionStateChangesObserver = new OSObservable<>("onOSPermissionChanged", true);
      return permissionStateChangesObserver;
   }
   // End PermissionState

   // Start SubscriptionState
   private static OSSubscriptionState currentSubscriptionState;
   private static OSSubscriptionState getCurrentSubscriptionState(Context context) {
      if (context == null)
         return null;

      if (currentSubscriptionState == null) {
         currentSubscriptionState = new OSSubscriptionState(false, getCurrentPermissionState(context).areNotificationsEnabled());
         getCurrentPermissionState(context).getObservable().addObserver(currentSubscriptionState);
         currentSubscriptionState.getObservable().addObserverStrong(new OSSubscriptionChangedInternalObserver());
      }

      return currentSubscriptionState;
   }

   static OSSubscriptionState lastSubscriptionState;
   private static OSSubscriptionState getLastSubscriptionState(Context context) {
      if (context == null)
         return null;

      if (lastSubscriptionState == null)
         lastSubscriptionState = new OSSubscriptionState(true, false);

      return lastSubscriptionState;
   }

   private static OSObservable<OSSubscriptionObserver, OSSubscriptionStateChanges> subscriptionStateChangesObserver;
   static OSObservable<OSSubscriptionObserver, OSSubscriptionStateChanges> getSubscriptionStateChangesObserver() {
      if (subscriptionStateChangesObserver == null)
         subscriptionStateChangesObserver = new OSObservable<>("onOSSubscriptionChanged", true);
      return subscriptionStateChangesObserver;
   }
   // End SubscriptionState


   // Start EmailSubscriptionState
   private static OSEmailSubscriptionState currentEmailSubscriptionState;
   private static OSEmailSubscriptionState getCurrentEmailSubscriptionState(Context context) {
      if (context == null)
         return null;

      if (currentEmailSubscriptionState == null) {
         currentEmailSubscriptionState = new OSEmailSubscriptionState(false);
         currentEmailSubscriptionState.getObservable().addObserverStrong(new OSEmailSubscriptionChangedInternalObserver());
      }

      return currentEmailSubscriptionState;
   }
   static OSEmailSubscriptionState getEmailSubscriptionState() {
      return getCurrentEmailSubscriptionState(appContext);
   }

   static OSEmailSubscriptionState lastEmailSubscriptionState;
   private static OSEmailSubscriptionState getLastEmailSubscriptionState(Context context) {
      if (context == null)
         return null;

      if (lastEmailSubscriptionState == null)
         lastEmailSubscriptionState = new OSEmailSubscriptionState(true);

      return lastEmailSubscriptionState;
   }

   private static OSObservable<OSEmailSubscriptionObserver, OSEmailSubscriptionStateChanges> emailSubscriptionStateChangesObserver;
   static OSObservable<OSEmailSubscriptionObserver, OSEmailSubscriptionStateChanges> getEmailSubscriptionStateChangesObserver() {
      if (emailSubscriptionStateChangesObserver == null)
         emailSubscriptionStateChangesObserver = new OSObservable<>("onOSEmailSubscriptionChanged", true);
      return emailSubscriptionStateChangesObserver;
   }
   // End EmailSubscriptionState

    // Start SMSSubscriptionState
   private static OSSMSSubscriptionState currentSMSSubscriptionState;
   private static OSSMSSubscriptionState getCurrentSMSSubscriptionState(Context context) {
      if (context == null)
         return null;

      if (currentSMSSubscriptionState == null) {
         currentSMSSubscriptionState = new OSSMSSubscriptionState(false);
         currentSMSSubscriptionState.getObservable().addObserverStrong(new OSSMSSubscriptionChangedInternalObserver());
      }

      return currentSMSSubscriptionState;
   }
   static OSSMSSubscriptionState getSMSSubscriptionState() {
      return getCurrentSMSSubscriptionState(appContext);
   }

   static OSSMSSubscriptionState lastSMSSubscriptionState;
   private static OSSMSSubscriptionState getLastSMSSubscriptionState(Context context) {
      if (context == null)
         return null;

      if (lastSMSSubscriptionState == null)
         lastSMSSubscriptionState = new OSSMSSubscriptionState(true);

      return lastSMSSubscriptionState;
   }

   private static OSObservable<OSSMSSubscriptionObserver, OSSMSSubscriptionStateChanges> smsSubscriptionStateChangesObserver;
   static OSObservable<OSSMSSubscriptionObserver, OSSMSSubscriptionStateChanges> getSMSSubscriptionStateChangesObserver() {
      if (smsSubscriptionStateChangesObserver == null)
         smsSubscriptionStateChangesObserver = new OSObservable<>("onSMSSubscriptionChanged", true);
      return smsSubscriptionStateChangesObserver;
   }
   // End SMSSubscriptionState

   /**
    * Get the current user data, notification and permissions state.
    */
   @Nullable
   public static OSDeviceState getDeviceState() {
      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not get OSDeviceState");
         return null;
      }

      OSSubscriptionState subscriptionStatus = getCurrentSubscriptionState(appContext);
      OSPermissionState permissionStatus = getCurrentPermissionState(appContext);
      OSEmailSubscriptionState emailSubscriptionStatus = getCurrentEmailSubscriptionState(appContext);
      OSSMSSubscriptionState smsSubscriptionStatus = getCurrentSMSSubscriptionState(appContext);
      return new OSDeviceState(subscriptionStatus, permissionStatus, emailSubscriptionStatus, smsSubscriptionStatus);
   }

   private static class IAPUpdateJob {
      JSONArray toReport;
      boolean newAsExisting;
      OneSignalRestClient.ResponseHandler restResponseHandler;

      IAPUpdateJob(JSONArray toReport) {
         this.toReport = toReport;
      }
   }
   private static IAPUpdateJob iapUpdateJob;

   /**
    * If notifications are disabled for your app, unsubscribe the user from OneSignal.
    * This will happen when your users go to <i>Settings</i> > <i>Apps</i> and turn off notifications or
    * they long press your notifications and select "block notifications". This is {@code false} by default.
    * @param set if {@code false} - don't unsubscribe users<br/>
    *            if {@code true} - unsubscribe users when notifications are disabled<br/>
    *            the default is {@code false}
    * @return the builder you called this method on
    */
   public static void unsubscribeWhenNotificationsAreDisabled(final boolean set) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED + " operation from pending task queue.");
               unsubscribeWhenNotificationsAreDisabled(set);
            }
         });
         return;
      }

      // Already set by remote params
      if (getRemoteParamController().hasUnsubscribeNotificationKey()) {
         logger.warning("unsubscribeWhenNotificationsAreDisabled already called by remote params!, ignoring user set");
         return;
      }

      getRemoteParamController().saveUnsubscribeWhenNotificationsAreDisabled(set);
   }

   /**
    * 1/2 steps in OneSignal init, relying on initWithContext (usage order does not matter)
    * Sets the app id OneSignal should use in the application
    * This is should be set from all OneSignal entry points
    * @param newAppId - String app id associated with the OneSignal dashboard app
    */
   public static void setAppId(@NonNull String newAppId) {
      if (newAppId == null || newAppId.isEmpty()) {
         logger.warning("setAppId called with id: " +  newAppId + ", ignoring!");
         return;
      } else if (!newAppId.equals(appId)) {
         // Pre-check on app id to make sure init of SDK is performed properly
         //     Usually when the app id is changed during runtime so that SDK is reinitialized properly
         initDone = false;
         logger.verbose("setAppId called with id: " + newAppId + " changing id from: " + appId);
      }

      appId = newAppId;

      if (appContext == null) {
         logger.warning("appId set, but please call initWithContext(appContext) with Application context to complete OneSignal init!");
         return;
      }

      if (appActivity != null && appActivity.get() != null)
         init(appActivity.get());
      else
         init(appContext);
   }

   /**
    * 1/2 steps in OneSignal init, relying on setAppId (usage order does not matter)
    * Sets the global shared ApplicationContext for OneSignal
    * This is should be set from all OneSignal entry points
    *   - BroadcastReceivers, Services, and Activities
    * @param context - Context used by the Application of the app
    */
   public static void initWithContext(@NonNull Context context) {
      if (context == null) {
         logger.warning("initWithContext called with null context, ignoring!");
         return;
      }

      if (context instanceof Activity)
         appActivity = new WeakReference<>((Activity) context);

      boolean wasAppContextNull = (appContext == null);
      appContext = context.getApplicationContext();

      setupContextListeners(wasAppContextNull);
      setupPrivacyConsent(appContext);

      if (appId == null) {
         // Get the cached app id, if it exists
         String oldAppId = getSavedAppId();
         if (oldAppId == null) {
            logger.warning("appContext set, but please call setAppId(appId) with a valid appId to complete OneSignal init!");
         } else {
            logger.verbose("appContext set and cached app id found, calling setAppId with: " + oldAppId);
            setAppId(oldAppId);
         }
         return;
      } else {
         logger.verbose("initWithContext called with: " + context);
      }
      init(context);
   }

   static void setRemoteNotificationReceivedHandler(OSRemoteNotificationReceivedHandler callback) {
      if (remoteNotificationReceivedHandler == null)
         remoteNotificationReceivedHandler = callback;
   }

   public static void setNotificationWillShowInForegroundHandler(@Nullable OSNotificationWillShowInForegroundHandler callback) {
      notificationWillShowInForegroundHandler = callback;
   }

   public static void setInAppMessageLifecycleHandler(@Nullable final OSInAppMessageLifecycleHandler handler) {
      if (appContext == null) {
         logger.error("Waiting initWithContext. " +
                 "Moving " + OSTaskRemoteController.SET_IN_APP_MESSAGE_LIFECYCLE_HANDLER + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SET_IN_APP_MESSAGE_LIFECYCLE_HANDLER + " operation from pending queue.");
               setInAppMessageLifecycleHandler(handler);
            }
         });
         return;
      }
      getInAppMessageController().setInAppMessageLifecycleHandler(handler);
   }

   public static void setNotificationOpenedHandler(@Nullable OSNotificationOpenedHandler callback) {
      notificationOpenedHandler = callback;

      if (initDone && notificationOpenedHandler != null)
         fireCallbackForOpenedNotifications();
   }

   public static void setInAppMessageClickHandler(@Nullable OSInAppMessageClickHandler callback) {
      inAppMessageClickHandler = callback;
   }

   /**
    * Called after setAppId and initWithContext, depending on which one is called last (order does not matter)
    */
   synchronized private static void init(Context context) {
      logger.verbose("Starting OneSignal initialization!");
      OSNotificationController.setupNotificationServiceExtension(appContext);

      if (requiresUserPrivacyConsent() || !remoteParamController.isRemoteParamsCallDone()) {
         if (!remoteParamController.isRemoteParamsCallDone())
            logger.verbose("OneSignal SDK initialization delayed, " +
                    "waiting for remote params.");
         else
            logger.verbose("OneSignal SDK initialization delayed, " +
                    "waiting for privacy consent to be set.");

         delayedInitParams = new DelayedConsentInitializationParameters(appContext, appId);
         String lastAppId = appId;
         // Set app id null since OneSignal was not init fully
         appId = null;
         // Wrapper SDK's call init twice and pass null as the appId on the first call
         // the app ID is required to download parameters, so do not download params until the appID is provided
         if (lastAppId != null && context != null)
            makeAndroidParamsRequest(lastAppId, getUserId(), false);
         return;
      }

      // Keep last subscribed Status if already set
      subscribableStatus = subscribableStatus != Integer.MAX_VALUE ? subscribableStatus : osUtils.initializationChecker(appContext, appId);
      if (isSubscriptionStatusUninitializable())
          return;

      if (initDone) {
         if (notificationOpenedHandler != null)
            fireCallbackForOpenedNotifications();
         logger.debug("OneSignal SDK initialization already completed.");
         return;
      }

      handleActivityLifecycleHandler(context);
      // Clean saved init activity
      appActivity = null;

      OneSignalStateSynchronizer.initUserState();

      // Check and handle app id change of the current session
      handleAppIdChange();

      // Verify the session is an Amazon purchase and track it
      handleAmazonPurchase();

      OSPermissionChangedInternalObserver.handleInternalChanges(getCurrentPermissionState(appContext));

      // When the session reaches timeout threshold, start new session
      // This is where the LocationGMS prompt is triggered and shown to the user
      doSessionInit();

      if (notificationOpenedHandler != null)
         fireCallbackForOpenedNotifications();

      if (TrackGooglePurchase.CanTrack(appContext))
         trackGooglePurchase = new TrackGooglePurchase(appContext);

      if (TrackFirebaseAnalytics.CanTrack())
         trackFirebaseAnalytics = new TrackFirebaseAnalytics(appContext);

      initDone = true;
      OneSignal.Log(LOG_LEVEL.VERBOSE, "OneSignal SDK initialization done.");

      getOutcomeEventsController().sendSavedOutcomes();

      // Clean up any pending tasks that were queued up before initialization
      taskRemoteController.startPendingTasks();
   }

   static void onRemoteParamSet() {
      boolean initDelayed = reassignDelayedInitParams();
      if (!initDelayed && inForeground) // Remote Params called from onAppFocus
         onAppFocusLogic();
   }

   private static void setupContextListeners(boolean wasAppContextNull) {
      // Register the lifecycle listener of the app for state changes in activities with proper context
      ActivityLifecycleListener.registerActivityLifecycleCallbacks((Application) appContext);

      // Do work here that should only happen once or at the start of a new lifecycle
      if (wasAppContextNull) {
         // Set Language Context to null
         languageContext = new LanguageContext(preferences);

         // Prefs require a context to save
         // If the previous state of appContext was null, kick off write in-case it was waiting
         OneSignalPrefs.startDelayedWrite();

         OneSignalDbHelper dbHelper = getDBHelperInstance();
         notificationDataController = new OSNotificationDataController(dbHelper, logger);

         // Cleans out old cached data to prevent over using the storage on devices
         notificationDataController.cleanOldCachedData();

         getInAppMessageController().cleanCachedInAppMessages();

         if (outcomeEventsFactory == null)
            outcomeEventsFactory = new OSOutcomeEventsFactory(logger, apiClient, dbHelper, preferences);

         getOSSessionManager().initSessionFromCache();
         getOutcomeEventsController().cleanCachedUniqueOutcomes();
      }
   }

   private static void setupPrivacyConsent(Context context) {
      ApplicationInfo ai = ApplicationInfoHelper.Companion.getInfo(context);
      if (ai == null) {
         return;
      }
      Bundle bundle = ai.metaData;

      // Read the current privacy consent setting from AndroidManifest.xml
      String requireSetting = bundle.getString("com.onesignal.PrivacyConsent");
      if (requireSetting != null)
         setRequiresUserPrivacyConsent("ENABLE".equalsIgnoreCase(requireSetting));
   }

   private static void handleAppIdChange() {
      // Re-register user if the app id changed (might happen when a dev is testing)
      String oldAppId = getSavedAppId();
      if (oldAppId != null) {
         if (!oldAppId.equals(appId)) {
            Log(LOG_LEVEL.DEBUG, "App id has changed:\nFrom: " + oldAppId + "\n To: " + appId + "\nClearing the user id, app state, and remoteParams as they are no longer valid");
            saveAppId(appId);
            OneSignalStateSynchronizer.resetCurrentState();
            remoteParamController.clearRemoteParams();
         }
      } else {
         // First time setting an app id
         Log(LOG_LEVEL.DEBUG, "App id set for first time:  " + appId);
         BadgeCountUpdater.updateCount(0, appContext);
         saveAppId(appId);
      }
   }

   public static boolean userProvidedPrivacyConsent() {
      return remoteParamController.getSavedUserConsentStatus();
   }

   private static boolean isSubscriptionStatusUninitializable() {
      return subscribableStatus == OSUtils.UNINITIALIZABLE_STATUS;
   }

   private static void handleActivityLifecycleHandler(Context context) {
      ActivityLifecycleHandler activityLifecycleHandler = ActivityLifecycleListener.getActivityLifecycleHandler();
      boolean isContextActivity = context instanceof Activity;
      boolean isCurrentActivityNull = OneSignal.getCurrentActivity() == null;
      setInForeground(!isCurrentActivityNull || isContextActivity);
      logger.debug("OneSignal handleActivityLifecycleHandler inForeground: " + inForeground);

      if (inForeground) {
         if (isCurrentActivityNull && isContextActivity && activityLifecycleHandler != null) {
            activityLifecycleHandler.setCurActivity((Activity) context);
            activityLifecycleHandler.setNextResumeIsFirstActivity(true);
         }
         OSNotificationRestoreWorkManager.beginEnqueueingWork(context, false);
         getFocusTimeController().appForegrounded();
      } else if (activityLifecycleHandler != null) {
         activityLifecycleHandler.setNextResumeIsFirstActivity(true);
      }
   }

   private static void handleAmazonPurchase() {
      try {
         Class.forName("com.amazon.device.iap.PurchasingListener");
         trackAmazonPurchase = new TrackAmazonPurchase(appContext);
      } catch (ClassNotFoundException e) {}
   }

   // If the app is not in the inForeground yet do not make an on_session call yet.
   // If we don't have a OneSignal player_id yet make the call to create it regardless of focus
   private static void doSessionInit() {
      // Check session time to determine whether to start a new session or not
      if (shouldStartNewSession()) {
         logger.debug("Starting new session with appEntryState: " + getAppEntryState());

         OneSignalStateSynchronizer.setNewSession();
         getOutcomeEventsController().cleanOutcomes();
         getOSSessionManager().restartSessionIfNeeded(getAppEntryState());
         getInAppMessageController().resetSessionLaunchTime();
         setLastSessionTime(time.getCurrentTimeMillis());
      } else if (isInForeground()) {
         logger.debug("Continue on same session with appEntryState: " + getAppEntryState());
         getOSSessionManager().attemptSessionUpgrade(getAppEntryState());
      }

      getInAppMessageController().initWithCachedInAppMessages();

      // We still want register the user to OneSignal if the SDK was initialized
      //   in the background for the first time.
      if (!inForeground && hasUserId())
         logger.debug("doSessionInit on background with already registered user");

      startRegistrationOrOnSession();
   }

   private static void startRegistrationOrOnSession() {
      if (waitingToPostStateSync)
         return;
      waitingToPostStateSync = true;

      if (inForeground && OneSignalStateSynchronizer.getSyncAsNewSession())
         locationFired = false;

      startLocationUpdate();

      registerForPushFired = false;

      // This will also enable background player updates
      if (getRemoteParams() != null)
         registerForPushToken();
      else
         makeAndroidParamsRequest(appId, getUserId(), true);
   }

   private static void startLocationUpdate() {
      LocationController.LocationHandler locationHandler = new LocationController.LocationHandler() {
         @Override
         public LocationController.PermissionType getType() {
            return LocationController.PermissionType.STARTUP;
         }
         @Override
         public void onComplete(LocationController.LocationPoint point) {
            lastLocationPoint = point;
            locationFired = true;
            registerUser();
         }
      };

      LocationController.getLocation(appContext, false, false, locationHandler);
   }

   private static PushRegistrator mPushRegistrator;

   private static PushRegistrator getPushRegistrator() {
      if (mPushRegistrator != null)
         return mPushRegistrator;

      if (OSUtils.isFireOSDeviceType())
         mPushRegistrator = new PushRegistratorADM();
      else if (OSUtils.isAndroidDeviceType()) {
         if (OSUtils.hasFCMLibrary())
            mPushRegistrator = getPushRegistratorFCM();
      } else
         mPushRegistrator = new PushRegistratorHMS();

      return mPushRegistrator;
   }

   @NonNull
   static private PushRegistratorFCM getPushRegistratorFCM() {
      OneSignalRemoteParams.FCMParams fcmRemoteParams = remoteParamController.getRemoteParams().fcmParams;
      PushRegistratorFCM.Params fcmParams = null;
      if (fcmRemoteParams != null) {
         fcmParams = new PushRegistratorFCM.Params(fcmRemoteParams.projectId, fcmRemoteParams.appId, fcmRemoteParams.apiKey);
      }
      return new PushRegistratorFCM(appContext, fcmParams);
   }

   private static void registerForPushToken() {
      getPushRegistrator().registerForPush(appContext, googleProjectNumber, new PushRegistrator.RegisteredHandler() {
         @Override
         public void complete(String id, int status) {
            logger.debug("registerForPushToken completed with id: " + id + " status: " + status);
            if (status < UserState.PUSH_STATUS_SUBSCRIBED) {
               // Only allow errored subscribableStatuses if we have never gotten a token.
               //   This ensures the device will not later be marked unsubscribed due to a
               //   any inconsistencies returned by Google Play services.
               // Also do not override a config error status if we got a runtime error
               if (OneSignalStateSynchronizer.getRegistrationId() == null &&
                   (subscribableStatus == UserState.PUSH_STATUS_SUBSCRIBED ||
                    pushStatusRuntimeError(subscribableStatus)))
                  subscribableStatus = status;
            }
            else if (pushStatusRuntimeError(subscribableStatus))
               subscribableStatus = status;

            lastRegistrationId = id;
            registerForPushFired = true;
            getCurrentSubscriptionState(appContext).setPushToken(id);
            registerUser();
         }
      });
   }

   private static boolean pushStatusRuntimeError(int subscriptionStatus) {
      return subscriptionStatus < -6;
   }

   private static void makeAndroidParamsRequest(String appId, String userId, final boolean queuePushRegistration) {
      if (getRemoteParams() != null || androidParamsRequestStarted)
         return;

      androidParamsRequestStarted = true;
      OneSignalRemoteParams.makeAndroidParamsRequest(appId, userId, new OneSignalRemoteParams.Callback() {
         @Override
         public void complete(OneSignalRemoteParams.Params params) {
            androidParamsRequestStarted = false;
            if (params.googleProjectNumber != null)
               googleProjectNumber = params.googleProjectNumber;

            remoteParamController.saveRemoteParams(params, getTrackerFactory(), preferences, logger);
            onRemoteParamSet();

            NotificationChannelManager.processChannelList(
               OneSignal.appContext,
               params.notificationChannels
            );

            if (queuePushRegistration)
               registerForPushToken();
         }
      });
   }

   private static void fireCallbackForOpenedNotifications() {
      for (JSONArray dataArray : unprocessedOpenedNotifs)
         runNotificationOpenedCallback(dataArray);

      unprocessedOpenedNotifs.clear();
   }

   /**
    * TODO: Decide on a single logging method to use instead of using several all over the place
    * Please do not use this method for logging, it is meant solely to be
    * used by our wrapper SDK's.
    */
   public static void onesignalLog(LOG_LEVEL level, String message) {
      OneSignal.Log(level, message);
   }

   public static void provideUserConsent(boolean consent) {
      boolean previousConsentStatus = userProvidedPrivacyConsent();

      remoteParamController.saveUserConsentStatus(consent);

      if (!previousConsentStatus && consent && delayedInitParams != null) {
         OneSignal.Log(LOG_LEVEL.VERBOSE, "Privacy consent provided, reassigning all delayed init params and attempting init again...");
         reassignDelayedInitParams();
      }
   }

   private static boolean reassignDelayedInitParams() {
      if (initDone)
         return false;

      String delayedAppId;
      Context delayedContext;
      if (delayedInitParams == null) {
         // Get the cached app id, if it exists
         delayedAppId = getSavedAppId();
         delayedContext = appContext;
         logger.error("Trying to continue OneSignal with null delayed params");
      } else {
         delayedAppId = delayedInitParams.getAppId();
         delayedContext = delayedInitParams.getContext();
      }

      logger.debug("reassignDelayedInitParams with appContext: " + appContext);

      delayedInitParams = null;
      setAppId(delayedAppId);

      // Check to avoid extra initWithContext logging and logic
      if (!initDone) {
         if (delayedContext == null) {
            logger.error("Trying to continue OneSignal with null delayed params context");
            return false;
         }
         initWithContext(delayedContext);
      }
      return true;
   }

   static OneSignalRemoteParams.Params getRemoteParams() {
      return remoteParamController.getRemoteParams();
   }

   /**
    * Indicates if the SDK is still waiting for the user to provide consent
    */
   public static boolean requiresUserPrivacyConsent() {
      return appContext == null || isUserPrivacyConsentRequired() && !userProvidedPrivacyConsent();
   }

   /**
    * This method will be replaced by remote params set
    */
   public static void setRequiresUserPrivacyConsent(final boolean required) {
      // Already set by remote params
      if (getRemoteParamController().hasPrivacyConsentKey()) {
         logger.warning("setRequiresUserPrivacyConsent already called by remote params!, ignoring user set");
         return;
      }

      if (requiresUserPrivacyConsent() && !required) {
         OneSignal.Log(LOG_LEVEL.ERROR, "Cannot change requiresUserPrivacyConsent() from TRUE to FALSE");
         return;
      }

      getRemoteParamController().savePrivacyConsentRequired(required);
   }

   static boolean shouldLogUserPrivacyConsentErrorMessageForMethodName(String methodName) {
      if (requiresUserPrivacyConsent()) {
         if (methodName != null)
            OneSignal.Log(LOG_LEVEL.WARN, "Method " + methodName + " was called before the user provided privacy consent. Your application is set to require the user's privacy consent before the OneSignal SDK can be initialized. Please ensure the user has provided consent before calling this method. You can check the latest OneSignal consent status by calling OneSignal.userProvidedPrivacyConsent()");
         return true;
      }

      return false;
   }

   public static void setLogLevel(LOG_LEVEL inLogCatLevel, LOG_LEVEL inVisualLogLevel) {
      logCatLevel = inLogCatLevel; visualLogLevel = inVisualLogLevel;
   }

   /**
    * Enable logging to help debug if you run into an issue setting up OneSignal.
    * The following options are available with increasingly more information:
    * <br/>
    * - {@code NONE}
    * <br/>
    * - {@code FATAL}
    * <br/>
    * - {@code ERROR}
    * <br/>
    * - {@code WARN}
    * <br/>
    * - {@code INFO}
    * <br/>
    * - {@code DEBUG}
    * <br/>
    * - {@code VERBOSE}
    * @param inLogCatLevel Sets the logging level to print to the Android LogCat log
    * @param inVisualLogLevel Sets the logging level to show as alert dialogs
    */
   public static void setLogLevel(int inLogCatLevel, int inVisualLogLevel) {
      setLogLevel(getLogLevel(inLogCatLevel), getLogLevel(inVisualLogLevel));
   }

   private static OneSignal.LOG_LEVEL getLogLevel(int level) {
      switch(level) {
         case 0:
            return OneSignal.LOG_LEVEL.NONE;
         case 1:
            return OneSignal.LOG_LEVEL.FATAL;
         case 2:
            return OneSignal.LOG_LEVEL.ERROR;
         case 3:
            return OneSignal.LOG_LEVEL.WARN;
         case 4:
            return OneSignal.LOG_LEVEL.INFO;
         case 5:
            return OneSignal.LOG_LEVEL.DEBUG;
         case 6:
            return OneSignal.LOG_LEVEL.VERBOSE;
      }

      if (level < 0)
         return OneSignal.LOG_LEVEL.NONE;
      return OneSignal.LOG_LEVEL.VERBOSE;
   }

   static boolean atLogLevel(LOG_LEVEL level) {
      return level.compareTo(visualLogLevel) < 1 || level.compareTo(logCatLevel) < 1;
   }

   static void Log(@NonNull LOG_LEVEL level, @NonNull String message) {
      Log(level, message, null);
   }

   static void Log(@NonNull final LOG_LEVEL level, @NonNull String message, @Nullable Throwable throwable) {

      final String TAG = "OneSignal";

      if (level.compareTo(logCatLevel) < 1) {
         if (level == LOG_LEVEL.VERBOSE)
            Log.v(TAG, message, throwable);
         else if (level == LOG_LEVEL.DEBUG)
            Log.d(TAG, message, throwable);
         else if (level == LOG_LEVEL.INFO)
            Log.i(TAG, message, throwable);
         else if (level == LOG_LEVEL.WARN)
            Log.w(TAG, message, throwable);
         else if (level == LOG_LEVEL.ERROR || level == LOG_LEVEL.FATAL)
            Log.e(TAG, message, throwable);
      }

      if (level.compareTo(visualLogLevel) < 1 && OneSignal.getCurrentActivity() != null) {
         try {
            String fullMessage = message + "\n";
            if (throwable != null) {
               fullMessage += throwable.getMessage();
               StringWriter sw = new StringWriter();
               PrintWriter pw = new PrintWriter(sw);
               throwable.printStackTrace(pw);
               fullMessage += sw.toString();
            }

            final String finalFullMessage = fullMessage;
            OSUtils.runOnMainUIThread(new Runnable() {
               @Override
               public void run() {
                  if (OneSignal.getCurrentActivity() != null)
                     new AlertDialog.Builder(OneSignal.getCurrentActivity())
                         .setTitle(level.toString())
                         .setMessage(finalFullMessage)
                         .show();
               }
            });
         } catch(Throwable t) {
            Log.e(TAG, "Error showing logging message.", t);
         }
      }

      callLogListeners(level, message, throwable);
   }

   private static void callLogListeners(@NonNull final LOG_LEVEL level, @NonNull String message, @Nullable Throwable throwable) {
      String logEntry = message;
      if (throwable != null) {
         logEntry += "\n" + Log.getStackTraceString(throwable);
      }
      for (OneSignalLogListener listener : logListeners) {
         listener.onLogEvent(new OneSignalLogEvent(level, logEntry));
      }
   }

   private static final List<OneSignalLogListener> logListeners = new CopyOnWriteArrayList<>();

   public static void addLogListener(@NonNull OneSignalLogListener listener) {
      logListeners.add(listener);
   }

   public static void removeLogListener(@NonNull OneSignalLogListener listener) {
      logListeners.remove(listener);
   }

   static void logHttpError(String errorString, int statusCode, Throwable throwable, String errorResponse) {
      String jsonError = "";
      if (errorResponse != null && atLogLevel(LOG_LEVEL.INFO))
         jsonError = "\n" + errorResponse + "\n";
      Log(LOG_LEVEL.WARN, "HTTP code: " + statusCode + " " + errorString + jsonError, throwable);
   }

   // Returns true if there is active time that is non synced.
   @WorkerThread
   static void onAppLostFocus() {
      Log(LOG_LEVEL.DEBUG, "Application lost focus initDone: " + initDone);
      setInForeground(false);
      appEntryState = AppEntryAction.APP_CLOSE;

      setLastSessionTime(OneSignal.getTime().getCurrentTimeMillis());
      LocationController.onFocusChange();

      if (!initDone) {
         // Make sure remote param call has finish in order to know if privacyConsent is required
         if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.APP_LOST_FOCUS)) {
            logger.error("Waiting for remote params. " +
                    "Moving " + OSTaskRemoteController.APP_LOST_FOCUS + " operation to a pending task queue.");
            taskRemoteController.addTaskToQueue(new Runnable() {
               @Override
               public void run() {
                  logger.debug("Running " + OSTaskRemoteController.APP_LOST_FOCUS + " operation from a pending task queue.");
                  backgroundSyncLogic();
               }
            });
         }
         return;
      }

      backgroundSyncLogic();
   }

   static void backgroundSyncLogic() {
      if (inForeground)
         return;

      if (trackAmazonPurchase != null)
         trackAmazonPurchase.checkListener();

      getFocusTimeController().appBackgrounded();

      scheduleSyncService();
   }

   // Schedules location update or a player update if there are any unsynced changes
   private static boolean scheduleSyncService() {
      boolean unsyncedChanges = OneSignalStateSynchronizer.persist();
      logger.debug("OneSignal scheduleSyncService unsyncedChanges: " + unsyncedChanges);
      if (unsyncedChanges)
         OSSyncService.getInstance().scheduleSyncTask(appContext);

      boolean locationScheduled = LocationController.scheduleUpdate(appContext);
      logger.debug("OneSignal scheduleSyncService locationScheduled: " + locationScheduled);
      return locationScheduled || unsyncedChanges;
   }

   static void onAppFocus() {
      Log(LOG_LEVEL.DEBUG, "Application on focus");
      setInForeground(true);

      // If the app gains focus and has not been set to NOTIFICATION_CLICK yet we can assume this is a normal app open
      if (!appEntryState.equals(AppEntryAction.NOTIFICATION_CLICK)) {
         callEntryStateListeners(appEntryState);
         // Check again because listeners might have changed the appEntryState
         if (!appEntryState.equals(AppEntryAction.NOTIFICATION_CLICK))
            appEntryState = AppEntryAction.APP_OPEN;
      }

      LocationController.onFocusChange();
      NotificationPermissionController.INSTANCE.onAppForegrounded();

      if (OSUtils.shouldLogMissingAppIdError(appId))
         return;
      // Make sure remote param call has finish in order to know if privacyConsent is required
      if (!remoteParamController.isRemoteParamsCallDone()) {
         Log(LOG_LEVEL.DEBUG, "Delay onAppFocus logic due to missing remote params");
         makeAndroidParamsRequest(appId, getUserId(), false);
         return;
      }

      onAppFocusLogic();
   }

   static void onAppStartFocusLogic() {
      refreshNotificationPermissionState();
   }

   static void refreshNotificationPermissionState() {
      getCurrentPermissionState(appContext).refreshAsTo();
   }

   private static void onAppFocusLogic() {
      // Make sure without privacy consent, onAppFocus returns early
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("onAppFocus"))
         return;

      getFocusTimeController().appForegrounded();

      doSessionInit();

      if (trackGooglePurchase != null)
         trackGooglePurchase.trackIAP();

      OSNotificationRestoreWorkManager.beginEnqueueingWork(appContext, false);

      refreshNotificationPermissionState();

      if (trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled())
         trackFirebaseAnalytics.trackInfluenceOpenEvent();

      OSSyncService.getInstance().cancelSyncTask(appContext);
   }

   static void addNetType(JSONObject jsonObj) {
      try {
         jsonObj.put("net_type", osUtils.getNetType());
      } catch (Throwable t) {}
   }

   private static int getTimeZoneOffset() {
      TimeZone timezone = Calendar.getInstance().getTimeZone();
      int offset = timezone.getRawOffset();

      if (timezone.inDaylightTime(new Date()))
          offset = offset + timezone.getDSTSavings();

      return offset / 1000;
   }

   private static String getTimeZoneId() {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
         return ZoneId.systemDefault().getId();
      } else {
         return TimeZone.getDefault().getID();
      }
   }

   private static void registerUser() {
      logger.debug(
         "registerUser:" +
         "registerForPushFired:" + registerForPushFired +
         ", locationFired: " + locationFired +
         ", remoteParams: " + getRemoteParams() +
         ", appId: " + appId
      );

      if (!registerForPushFired || !locationFired || getRemoteParams() == null || appId == null) {
         logger.debug("registerUser not possible");
         return;
      }

      Thread thread = new Thread(new Runnable() {
         public void run() {
            try {
               registerUserTask();
            } catch(JSONException t) {
               Log(LOG_LEVEL.FATAL, "FATAL Error registering device!", t);
            }
         }
      }, "OS_REG_USER");
      startThreadWithRetry(thread);
   }

   private static void registerUserTask() throws JSONException {
      String packageName = appContext.getPackageName();

      JSONObject deviceInfo = new JSONObject();

      deviceInfo.put("app_id", getSavedAppId());

      deviceInfo.put("device_os", Build.VERSION.RELEASE);
      deviceInfo.put("timezone", getTimeZoneOffset());
      deviceInfo.put("timezone_id", getTimeZoneId());
      deviceInfo.put("language", languageContext.getLanguage());
      deviceInfo.put("sdk", VERSION);
      deviceInfo.put("sdk_type", sdkType);
      deviceInfo.put("android_package", packageName);
      deviceInfo.put("device_model", Build.MODEL);
      Integer appVersion = getAppVersion();
      if (appVersion != null) {
         deviceInfo.put("game_version", appVersion);
      }
      deviceInfo.put("net_type", osUtils.getNetType());
      deviceInfo.put("carrier", osUtils.getCarrierName());
      deviceInfo.put("rooted", RootToolsInternalMethods.isRooted());

      OneSignalStateSynchronizer.updateDeviceInfo(deviceInfo, null);

      JSONObject pushState = new JSONObject();
      pushState.put("identifier", lastRegistrationId);
      pushState.put("subscribableStatus", subscribableStatus);
      pushState.put("androidPermission", areNotificationsEnabledForSubscribedState());
      pushState.put("device_type", osUtils.getDeviceType());
      OneSignalStateSynchronizer.updatePushState(pushState);

      if (isLocationShared() && lastLocationPoint != null)
         OneSignalStateSynchronizer.updateLocation(lastLocationPoint);

      logger.debug("registerUserTask calling readyToUpdate");
      OneSignalStateSynchronizer.readyToUpdate(true);

      waitingToPostStateSync = false;
   }

   private static Integer getAppVersion() {
      String packageName = appContext.getPackageName();
      GetPackageInfoResult result =
           PackageInfoHelper.Companion.getInfo(
                OneSignal.appContext,
                packageName,
                0
           );
      if (!result.getSuccessful() || result.getPackageInfo() == null) {
         return null;
      }

      return result.getPackageInfo().versionCode;
   }

   public static void setSMSNumber(@NonNull final String smsNumber, OSSMSUpdateHandler callback) {
      setSMSNumber(smsNumber, null, callback);
   }

   public static void setSMSNumber(@NonNull final String smsNumber) {
      setSMSNumber(smsNumber, null, null);
   }

   public static void setSMSNumber(@NonNull final String smsNumber, @Nullable final String smsAuthHash) {
      setSMSNumber(smsNumber, smsAuthHash, null);
   }

   /**
    * Set an sms number for the device to later send sms to this number
    * @param smsNumber The sms number that you want subscribe and associate with the device
    * @param smsAuthHash Generated auth hash from your server to authorize. (Recommended)
    *                      Create and send this hash from your backend to your app after
    *                          the user logs into your app.
    *                      DO NOT generate this from your app!
    *                      Omit this value if you do not have a backend to authenticate the user.
    * @param callback Fire onSuccess or onFailure depending if the update successes or fails
    */
   public static void setSMSNumber(@NonNull final String smsNumber, final String smsAuthHash, final OSSMSUpdateHandler callback) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SET_SMS_NUMBER)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SET_SMS_NUMBER + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SET_SMS_NUMBER + " operation from a pending task queue.");
               setSMSNumber(smsNumber, smsAuthHash, callback);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.SET_SMS_NUMBER))
         return;

      if (TextUtils.isEmpty(smsNumber)) {
         String errorMessage = "SMS number is invalid";
         if (callback != null)
            callback.onFailure(new OSSMSUpdateError(SMSErrorType.VALIDATION, errorMessage));
         logger.error(errorMessage);
         return;
      }

      if (getRemoteParams().useSMSAuth && (smsAuthHash == null || smsAuthHash.length() == 0)) {
         String errorMessage = "SMS authentication (auth token) is set to REQUIRED for this application. Please provide an auth token from your backend server or change the setting in the OneSignal dashboard.";
         if (callback != null)
            callback.onFailure(new OSSMSUpdateError(SMSErrorType.REQUIRES_SMS_AUTH, errorMessage));
         logger.error(errorMessage);
         return;
      }

      smsUpdateHandler = callback;

      getCurrentSMSSubscriptionState(appContext).setSMSNumber(smsNumber);
      OneSignalStateSynchronizer.setSMSNumber(smsNumber, smsAuthHash);
   }

   /**
    * Call when user logs out of their account.
    * This dissociates the device from the email address.
    * This does not effect the subscription status of the email address itself.
    */
   public static void logoutSMSNumber() {
      logoutSMSNumber(null);
   }

   public static void logoutSMSNumber(@Nullable final OSSMSUpdateHandler callback) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.LOGOUT_SMS_NUMBER)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.LOGOUT_SMS_NUMBER + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running  " + OSTaskRemoteController.LOGOUT_SMS_NUMBER + " operation from pending task queue.");
               logoutSMSNumber(callback);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.LOGOUT_SMS_NUMBER))
         return;

      if (getSMSId() == null) {
         final String message = OSTaskRemoteController.LOGOUT_SMS_NUMBER + " not valid as sms number was not set or already logged out!";
         if (callback != null)
            callback.onFailure(new OSSMSUpdateError(SMSErrorType.INVALID_OPERATION, message));
         logger.error(message);
         return;
      }

      smsLogoutHandler = callback;
      OneSignalStateSynchronizer.logoutSMS();
   }

   public static void setEmail(@NonNull final String email, EmailUpdateHandler callback) {
      setEmail(email, null, callback);
   }

   public static void setEmail(@NonNull final String email) {
      setEmail(email, null, null);
   }

   public static void setEmail(@NonNull final String email, @Nullable final String emailAuthHash) {
      setEmail(email, emailAuthHash, null);
   }

   /**
    * Set an email for the device to later send emails to this address
    * @param email The email that you want subscribe and associate with the device
    * @param emailAuthHash Generated auth hash from your server to authorize. (Recommended)
    *                      Create and send this hash from your backend to your app after
    *                          the user logs into your app.
    *                      DO NOT generate this from your app!
    *                      Omit this value if you do not have a backend to authenticate the user.
    * @param callback Fire onSuccess or onFailure depending if the update successes or fails
    */
   public static void setEmail(@NonNull final String email, @Nullable final String emailAuthHash, @Nullable final EmailUpdateHandler callback) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SET_EMAIL)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SET_EMAIL + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SET_EMAIL + " operation from a pending task queue.");
               setEmail(email, emailAuthHash, callback);
            }
         });
         return;
      }
      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.SET_EMAIL))
         return;

      if (!OSUtils.isValidEmail(email)) {
         String errorMessage = "Email is invalid";
         if (callback != null)
            callback.onFailure(new EmailUpdateError(EmailErrorType.VALIDATION, errorMessage));
         logger.error(errorMessage);
         return;
      }

      if (getRemoteParams().useEmailAuth && (emailAuthHash == null || emailAuthHash.length() == 0)) {
         String errorMessage = "Email authentication (auth token) is set to REQUIRED for this application. Please provide an auth token from your backend server or change the setting in the OneSignal dashboard.";
         if (callback != null)
            callback.onFailure(new EmailUpdateError(EmailErrorType.REQUIRES_EMAIL_AUTH, errorMessage));
         logger.error(errorMessage);
         return;
      }

      emailUpdateHandler = callback;

      String trimmedEmail = email.trim();

      String internalEmailAuthHash = emailAuthHash;
      if (internalEmailAuthHash != null)
         internalEmailAuthHash = internalEmailAuthHash.toLowerCase();

      getCurrentEmailSubscriptionState(appContext).setEmailAddress(trimmedEmail);
      OneSignalStateSynchronizer.setEmail(trimmedEmail.toLowerCase(), internalEmailAuthHash);
   }

   /**
    * Call when user logs out of their account.
    * This dissociates the device from the email address.
    * This does not effect the subscription status of the email address itself.
    */
   public static void logoutEmail() {
      logoutEmail(null);
   }

   public static void logoutEmail(@Nullable final EmailUpdateHandler callback) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.LOGOUT_EMAIL)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.LOGOUT_EMAIL + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running  " + OSTaskRemoteController.LOGOUT_EMAIL + " operation from pending task queue.");
               logoutEmail(callback);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.LOGOUT_EMAIL))
         return;

      if (getEmailId() == null) {
         final String message = "logoutEmail not valid as email was not set or already logged out!";
         if (callback != null)
            callback.onFailure(new EmailUpdateError(EmailErrorType.INVALID_OPERATION, message));
         logger.error(message);
         return;
      }

      emailLogoutHandler = callback;
      OneSignalStateSynchronizer.logoutEmail();
   }

   public static void setLanguage(@NonNull final String language) {
      setLanguage(language, null);
   }

   public static void setLanguage(@NonNull final String language, @Nullable final OSSetLanguageCompletionHandler completionCallback) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SET_LANGUAGE)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SET_LANGUAGE + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SET_LANGUAGE + " operation from pending task queue.");
               setLanguage(language, completionCallback);
            }
         });
         return;
      }

      OSDeviceInfoCompletionHandler deviceInfoCompletionHandler = null;

      if(completionCallback != null) {
         deviceInfoCompletionHandler = new OSDeviceInfoCompletionHandler() {
            @Override
            public void onSuccess(String results) {
               completionCallback.onSuccess(results);
            }

            @Override
            public void onFailure(OSDeviceInfoError error) {
               OSLanguageError languageError = new OSLanguageError(error.errorCode, error.message);
               completionCallback.onFailure(languageError);
            }
         };
      }

      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.SET_LANGUAGE))
         return;

      LanguageProviderAppDefined languageProviderAppDefined = new LanguageProviderAppDefined(preferences);
      languageProviderAppDefined.setLanguage(language);
      languageContext.setStrategy(languageProviderAppDefined);

      try {
         JSONObject deviceInfo = new JSONObject();
         deviceInfo.put("language", languageContext.getLanguage());
         OneSignalStateSynchronizer.updateDeviceInfo(deviceInfo, deviceInfoCompletionHandler);
      } catch (JSONException exception) {
         exception.printStackTrace();
      }
   }

   public static void setExternalUserId(@NonNull final String externalId) {
      setExternalUserId(externalId, null, null);
   }

   public static void setExternalUserId(@NonNull final String externalId, @Nullable final OSExternalUserIdUpdateCompletionHandler completionCallback) {
      setExternalUserId(externalId, null, completionCallback);
   }

   public static void setExternalUserId(@NonNull final String externalId,  @Nullable final String externalIdAuthHash) {
      setExternalUserId(externalId, externalIdAuthHash, null);
   }

   public static void setExternalUserId(@NonNull final String externalId, @Nullable final String externalIdAuthHash, @Nullable final OSExternalUserIdUpdateCompletionHandler completionCallback) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SET_EXTERNAL_USER_ID)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SET_EXTERNAL_USER_ID + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SET_EXTERNAL_USER_ID + " operation from pending task queue.");
               setExternalUserId(externalId, externalIdAuthHash, completionCallback);
            }
         });
         return;
      }

      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("setExternalUserId()"))
         return;

      if (externalId == null) {
         logger.warning("External id can't be null, set an empty string to remove an external id");
         return;
      }

      // Empty external Id is used for remove external id, in this case auth hash will be pop from saved UserState
      if (!externalId.isEmpty() && getRemoteParams() != null && getRemoteParams().useUserIdAuth && (externalIdAuthHash == null || externalIdAuthHash.length() == 0)) {
         String errorMessage = "External Id authentication (auth token) is set to REQUIRED for this application. Please provide an auth token from your backend server or change the setting in the OneSignal dashboard.";
         if (completionCallback != null)
            completionCallback.onFailure(new ExternalIdError(ExternalIdErrorType.REQUIRES_EXTERNAL_ID_AUTH, errorMessage));
         logger.error(errorMessage);
         return;
      }

      String lowerCaseIdAuthHash = externalIdAuthHash;
      if (lowerCaseIdAuthHash != null)
         lowerCaseIdAuthHash = externalIdAuthHash.toLowerCase();

      try {
         OneSignalStateSynchronizer.setExternalUserId(externalId, lowerCaseIdAuthHash, completionCallback);
      } catch (JSONException exception) {
         String operation = externalId.equals("") ? "remove" : "set";
         logger.error("Attempted to " + operation + " external ID but encountered a JSON exception");
         exception.printStackTrace();
      }
   }

   public static void removeExternalUserId() {
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("removeExternalUserId()"))
         return;

      removeExternalUserId(null);
   }

   public static void removeExternalUserId(final OSExternalUserIdUpdateCompletionHandler completionHandler) {
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("removeExternalUserId()"))
         return;

      // to remove the external user ID, the API requires an empty string
      setExternalUserId("", completionHandler);
   }

   /**
    * Tag a user based on an app event of your choosing so later you can create
    * <a href="https://documentation.onesignal.com/docs/segmentation">OneSignal Segments</a>
    * to target these users.
    *
    * @see OneSignal#sendTags to set more than one tag on a user at a time.
    *
    * @param key Key of your chossing to create or update
    * @param value Value to set on the key. <b>Note:</b> Passing in a blank {@code String} deletes
    *              the key.
    * @see OneSignal#deleteTag
    * @see OneSignal#deleteTags
    */
   public static void sendTag(final String key, final String value) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SEND_TAG)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SEND_TAG + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SEND_TAG + " operation from pending task queue.");
               sendTag(key, value);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.SEND_TAG))
         return;

      try {
         sendTags(new JSONObject().put(key, value));
      } catch (JSONException t) {
         t.printStackTrace();
      }
   }

   public static void sendTags(String jsonString) {
      try {
         sendTags(new JSONObject(jsonString));
      } catch (JSONException t) {
         Log(LOG_LEVEL.ERROR, "Generating JSONObject for sendTags failed!", t);
      }
   }

   /**
    * Tag a user based on an app event of your choosing so later you can create
    * <a href="https://documentation.onesignal.com/docs/segmentation">OneSignal Segments</a>
    *  to target these users.
    * @param keyValues Key value pairs of your choosing to create or update. <b>Note:</b>
    *                  Passing in a blank String as a value deletes a key.
    * @see OneSignal#deleteTag
    * @see OneSignal#deleteTags
    */
   public static void sendTags(final JSONObject keyValues) {
      sendTags(keyValues, null);
   }

   /**
    * Tag a user based on an app event of your choosing so later you can create
    * <a href="https://documentation.onesignal.com/docs/segmentation">OneSignal Segments</a>
    *  to target these users.
    *
    *  NOTE: The ChangeTagsUpdateHandler will not be called under all circumstances. It can also take
    *  more than 5 seconds in some cases to be called, so please do not block any user action
    *  based on this callback.
    * @param keyValues Key value pairs of your choosing to create or update. <b>Note:</b>
    *                  Passing in a blank String as a value deletes a key.
    * @see OneSignal#deleteTag
    * @see OneSignal#deleteTags
    *
    */
   public static void sendTags(final JSONObject keyValues, final ChangeTagsUpdateHandler changeTagsUpdateHandler) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SEND_TAGS)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SEND_TAGS + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SEND_TAGS + " operation from pending task queue.");
               sendTags(keyValues, changeTagsUpdateHandler);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.SEND_TAGS))
         return;

      Runnable sendTagsRunnable = new Runnable() {
         @Override
         public void run() {
            if (keyValues == null) {
               logger.error("Attempted to send null tags");
               if (changeTagsUpdateHandler != null)
                  changeTagsUpdateHandler.onFailure(new SendTagsError(-1, "Attempted to send null tags"));
               return;
            }

            JSONObject existingKeys = OneSignalStateSynchronizer.getTags(false).result;
            JSONObject toSend = new JSONObject();

            Iterator<String> keys = keyValues.keys();
            String key;
            Object value;

            while (keys.hasNext()) {
               key = keys.next();
               try {
                  value = keyValues.opt(key);
                  if (value instanceof JSONArray || value instanceof JSONObject)
                     Log(LOG_LEVEL.ERROR, "Omitting key '" + key  + "'! sendTags DO NOT supported nested values!");
                  else if (keyValues.isNull(key) || "".equals(value)) {
                     if (existingKeys != null && existingKeys.has(key))
                        toSend.put(key, "");
                  }
                  else
                     toSend.put(key, value.toString());
               }
               catch (Throwable t) {}
            }

            if (!toSend.toString().equals("{}")) {
               logger.debug("Available tags to send: " + toSend.toString());
               OneSignalStateSynchronizer.sendTags(toSend, changeTagsUpdateHandler);
            } else {
               logger.debug("Send tags ended successfully");
               if (changeTagsUpdateHandler != null)
                  changeTagsUpdateHandler.onSuccess(existingKeys);
            }
         }
      };

      // If pendingTaskExecutor is running, there might be sendTags tasks running, use it to run sendTagsRunnable to keep order call
      if (taskRemoteController.shouldRunTaskThroughQueue()) {
         logger.debug("Sending " + OSTaskRemoteController.SEND_TAGS + " operation to pending task queue.");
         taskRemoteController.addTaskToQueue(sendTagsRunnable);
         return;
      }

      sendTagsRunnable.run();
   }

   public static void postNotification(String json, final PostNotificationResponseHandler handler) {
      try {
         postNotification(new JSONObject(json), handler);
      } catch (JSONException e) {
         Log(LOG_LEVEL.ERROR, "Invalid postNotification JSON format: " + json);
      }
   }

   /**
    * Allows you to send notifications from user to user or schedule ones in the future to be delivered
    * to the current device.
    * <br/><br/>
    * <b>Note:</b> You can only use {@code include_player_ids} as a targeting parameter from your app.
    * Other target options such as {@code tags} and {@code included_segments} require your OneSignal
    * App REST API key which can only be used from your server.
    *
    * @param json Contains notification options, see <a href="https://documentation.onesignal.com/reference#create-notification">OneSignal | Create Notification</a>
    *              POST call for all options.
    * @param handler a {@link PostNotificationResponseHandler} object to receive the request result
    */
   public static void postNotification(JSONObject json, final PostNotificationResponseHandler handler) {

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("postNotification()"))
         return;

      try {
         if (!json.has("app_id"))
            json.put("app_id", getSavedAppId());

         // app_id will not be set if init was never called.
         if (!json.has("app_id")) {
            if (handler != null)
               handler.onFailure(new JSONObject().put("error", "Missing app_id"));
            return;
         }

         OneSignalRestClient.post("notifications/", json, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
               logger.debug("HTTP create notification success: " + (response != null ? response : "null"));
               if (handler != null) {
                  try {
                     JSONObject jsonObject = new JSONObject(response);
                     if (jsonObject.has("errors"))
                        handler.onFailure(jsonObject);
                     else
                        handler.onSuccess(new JSONObject(response));
                  } catch (Throwable t) {
                     t.printStackTrace();
                  }
               }
            }

            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
               logHttpError("create notification failed", statusCode, throwable, response);
               if (handler != null) {
                  try {
                     if (statusCode == 0)
                        response = "{\"error\": \"HTTP no response error\"}";

                     handler.onFailure(new JSONObject(response));
                  } catch (Throwable t) {
                     try {
                        handler.onFailure(new JSONObject("{\"error\": \"Unknown response!\"}"));
                     } catch (JSONException e) {
                        e.printStackTrace();
                     }
                  }
               }
            }
         });
      } catch (JSONException e) {
         logger.error("HTTP create notification json exception!", e);
         if (handler != null) {
            try {
               handler.onFailure(new JSONObject("{'error': 'HTTP create notification json exception!'}"));
            } catch (JSONException e1) {
               e1.printStackTrace();
            }
         }
      }
   }

   /**
    * Retrieve a list of tags that have been set on the user frm the OneSignal server.
    * @param getTagsHandler an instance of {@link OSGetTagsHandler}.
    *                       <br/>
    *                       Calls {@link OSGetTagsHandler#tagsAvailable(JSONObject) tagsAvailable} once the tags are available
    */
   public static void getTags(final OSGetTagsHandler getTagsHandler) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.GET_TAGS)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.GET_TAGS + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.GET_TAGS + " operation from pending queue.");
               getTags(getTagsHandler);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.GET_TAGS))
         return;

      if (getTagsHandler == null) {
         logger.error("getTags called with null GetTagsHandler!");
         return;
      }

      new Thread(new Runnable() {
         @Override
         public void run() {
            synchronized (pendingGetTagsHandlers) {
               pendingGetTagsHandlers.add(getTagsHandler);

               // If there is an existing in-flight request, we should return
               // since there's no point in making a duplicate runnable
               if (pendingGetTagsHandlers.size() > 1) return;
            }

            runGetTags();
         }
      }, "OS_GETTAGS").start();
   }

   private static void runGetTags() {
      if (getUserId() == null) {
         logger.warning("getTags called under a null user!");
         return;
      }

      internalFireGetTagsCallbacks();
   }

   private static void internalFireGetTagsCallbacks() {
      synchronized (pendingGetTagsHandlers) {
         if (pendingGetTagsHandlers.size() == 0) return;
      }

      new Thread(new Runnable() {
         @Override
         public void run() {
            final UserStateSynchronizer.GetTagsResult tags = OneSignalStateSynchronizer.getTags(!getTagsCall);
            if (tags.serverSuccess) getTagsCall = true;

            synchronized (pendingGetTagsHandlers) {
               for (OSGetTagsHandler handler : pendingGetTagsHandlers) {
                  handler.tagsAvailable(tags.result == null || tags.toString().equals("{}") ? null : tags.result);
               }

               pendingGetTagsHandlers.clear();
            }
         }
      }, "OS_GETTAGS_CALLBACK").start();
   }

   /**
    * Deletes a single tag that was previously set on a user with
    * @see OneSignal#sendTag or {@link #sendTags(JSONObject)}.
    * @see OneSignal#deleteTags if you need to delete
    * more than one.
    * @param key Key to remove.
    */
   public static void deleteTag(String key) {
      deleteTag(key, null);
   }

   public static void deleteTag(String key, ChangeTagsUpdateHandler handler) {
      //if applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("deleteTag()"))
         return;

      Collection<String> tempList = new ArrayList<>(1);
      tempList.add(key);
      deleteTags(tempList, handler);
   }

   /**
    * Deletes one or more tags that were previously set on a user with
    * @see OneSignal#sendTag or {@link #sendTags(JSONObject)}.
    * @param keys Keys to remove.
    */
   public static void deleteTags(Collection<String> keys) {
      deleteTags(keys, null);
   }

   public static void deleteTags(Collection<String> keys, ChangeTagsUpdateHandler handler) {
      //if applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("deleteTags()"))
         return;

      try {
         JSONObject jsonTags = new JSONObject();
         for (String key : keys)
            jsonTags.put(key, "");

         sendTags(jsonTags, handler);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for deleteTags.", t);
      }
   }

   public static void deleteTags(String jsonArrayString) {
      deleteTags(jsonArrayString, null);
   }

   public static void deleteTags(String jsonArrayString, ChangeTagsUpdateHandler handler) {
      try {
         deleteTags(new JSONArray(jsonArrayString), handler);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for deleteTags.", t);
      }
   }

   public static void deleteTags(JSONArray jsonArray, ChangeTagsUpdateHandler handler) {
      //if applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("deleteTags()"))
         return;

      try {
         JSONObject jsonTags = new JSONObject();

         for (int i = 0; i < jsonArray.length(); i++)
            jsonTags.put(jsonArray.getString(i), "");

         sendTags(jsonTags, handler);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for deleteTags.", t);
      }
   }

   static void sendPurchases(JSONArray purchases, boolean newAsExisting, OneSignalRestClient.ResponseHandler responseHandler) {

      //if applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("sendPurchases()"))
         return;

      if (getUserId() == null) {
         iapUpdateJob = new IAPUpdateJob(purchases);
         iapUpdateJob.newAsExisting = newAsExisting;
         iapUpdateJob.restResponseHandler = responseHandler;

         return;
      }

      try {
         JSONObject jsonBody = new JSONObject();
         jsonBody.put("app_id", getSavedAppId());
         if (newAsExisting)
            jsonBody.put("existing", true);
         jsonBody.put("purchases", purchases);

         OneSignalStateSynchronizer.sendPurchases(jsonBody, responseHandler);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for sendPurchases.", t);
      }
   }

   private static void runNotificationOpenedCallback(final JSONArray dataArray) {
      if (notificationOpenedHandler == null) {
         unprocessedOpenedNotifs.add(dataArray);
         return;
      }

      OSNotificationOpenedResult openedResult = generateNotificationOpenedResult(dataArray);
      addEntryStateListener(openedResult, appEntryState);
      fireNotificationOpenedHandler(openedResult);
   }

   // Also called for received but OSNotification is extracted from it.
   @NonNull
   private static OSNotificationOpenedResult generateNotificationOpenedResult(JSONArray jsonArray) {
      int jsonArraySize = jsonArray.length();

      boolean firstMessage = true;
      int androidNotificationId = jsonArray.optJSONObject(0).optInt(BUNDLE_KEY_ANDROID_NOTIFICATION_ID);

      List<OSNotification> groupedNotifications = new ArrayList<>();
      String actionSelected = null;
      JSONObject payload = null;

      for (int i = 0; i < jsonArraySize; i++) {
         try {
            payload = jsonArray.getJSONObject(i);

            if (actionSelected == null && payload.has(BUNDLE_KEY_ACTION_ID))
               actionSelected = payload.optString(BUNDLE_KEY_ACTION_ID, null);

            if (firstMessage)
               firstMessage = false;
            else {
               groupedNotifications.add(new OSNotification(payload));
            }
         } catch (Throwable t) {
            Log(LOG_LEVEL.ERROR, "Error parsing JSON item " + i + "/" + jsonArraySize + " for callback.", t);
         }
      }

      OSNotificationAction.ActionType actionType = actionSelected != null ? OSNotificationAction.ActionType.ActionTaken : OSNotificationAction.ActionType.Opened;
      OSNotificationAction notificationAction = new OSNotificationAction(actionType, actionSelected);

      OSNotification notification = new OSNotification(groupedNotifications, payload, androidNotificationId);
      return new OSNotificationOpenedResult(notification, notificationAction);
   }

   private static void fireNotificationOpenedHandler(final OSNotificationOpenedResult openedResult) {
      // TODO: Is there a reason we need the opened handler to be fired from main thread?

      // TODO: Once the NotificationOpenedHandler gets a Worker, we should make sure we add a catch
      //    like we have implemented for the OSRemoteNotificationReceivedHandler and NotificationWillShowInForegroundHandlers
      CallbackThreadManager.Companion.runOnPreferred(new Runnable() {
         @Override
         public void run() {
            notificationOpenedHandler.notificationOpened(openedResult);
         }
      });
   }

   /**
    * Called when receiving FCM/ADM message after it has been displayed.
    * Or right when it is received if it is a silent one
    *   If a NotificationExtenderService is present in the developers app this will not fire for silent notifications.
    */
   static void handleNotificationReceived(OSNotificationGenerationJob notificationJob) {
      try {
         JSONObject jsonObject = new JSONObject(notificationJob.getJsonPayload().toString());
         jsonObject.put(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notificationJob.getAndroidId());

         OSNotificationOpenedResult openResult = generateNotificationOpenedResult(newJsonArray(jsonObject));
         if (trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled())
            trackFirebaseAnalytics.trackReceivedEvent(openResult);

      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   /**
    * Checks if the app is in the background
    * Checks if notificationWillShowInForegroundHandler is setup
    * <br/><br/>
    * @see OSNotificationWillShowInForegroundHandler
    */
   static boolean shouldFireForegroundHandlers(OSNotificationGenerationJob notificationJob) {
      if (!isInForeground()) {
         OneSignal.onesignalLog(LOG_LEVEL.INFO, "App is in background, show notification");
         return false;
      }

      if (notificationWillShowInForegroundHandler == null) {
         OneSignal.onesignalLog(LOG_LEVEL.INFO, "No NotificationWillShowInForegroundHandler setup, show notification");
         return false;
      }

      // Notification is restored. Don't fire for restored notifications.
      if (notificationJob.isRestoring()) {
         OneSignal.onesignalLog(LOG_LEVEL.INFO, "Not firing notificationWillShowInForegroundHandler for restored notifications");
         return false;
      }

      return true;
   }

   /**
    * Responsible for firing the notificationWillShowInForegroundHandler
    * <br/><br/>
    * @see OSNotificationWillShowInForegroundHandler
    */
   static void fireForegroundHandlers(OSNotificationController notificationController) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Fire notificationWillShowInForegroundHandler");

      OSNotificationReceivedEvent notificationReceivedEvent = notificationController.getNotificationReceivedEvent();
      try {
         OneSignal.notificationWillShowInForegroundHandler.notificationWillShowInForeground(notificationReceivedEvent);
      } catch (Throwable t) {
         OneSignal.onesignalLog(LOG_LEVEL.ERROR, "Exception thrown while notification was being processed for display by notificationWillShowInForegroundHandler, showing notification in foreground!");
         notificationReceivedEvent.complete(notificationReceivedEvent.getNotification());
         throw t;
      }
   }

   /**
    * Method called when opening a notification
    */
   static void handleNotificationOpen(final Activity context, final JSONArray data, @Nullable final String notificationId) {
      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(null))
         return;

      notificationOpenedRESTCall(context, data);

      if (trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled())
         trackFirebaseAnalytics.trackOpenedEvent(generateNotificationOpenedResult(data));

      if (shouldInitDirectSessionFromNotificationOpen(context, data)) {
         applicationOpenedByNotification(notificationId);
      }

      openDestinationActivity(context, data);

      runNotificationOpenedCallback(data);
   }

   static void openDestinationActivity(
      @NonNull final Activity activity,
      @NonNull final JSONArray pushPayloads
   ) {
      try {
         // Always use the top most notification if user tapped on the summary notification
         JSONObject firstPayloadItem = pushPayloads.getJSONObject(0);
         GenerateNotificationOpenIntent intentGenerator = GenerateNotificationOpenIntentFromPushPayload.INSTANCE.create(
            activity,
            firstPayloadItem
         );

         Intent intent = intentGenerator.getIntentVisible();
         if (intent != null) {
            logger.info("SDK running startActivity with Intent: " + intent);
            activity.startActivity(intent);
         }
         else {
            logger.info("SDK not showing an Activity automatically due to it's settings.");
         }
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   private static boolean shouldInitDirectSessionFromNotificationOpen(Activity context, final JSONArray data) {
      if (inForeground) {
         return false;
      }

      try {
         JSONObject interactedNotificationData = data.getJSONObject(0);
         return new OSNotificationOpenBehaviorFromPushPayload(
            context,
            interactedNotificationData
         ).getShouldOpenApp();
      } catch (JSONException e) {
         e.printStackTrace();
      }
      return true;
   }

   static void applicationOpenedByNotification(@Nullable final String notificationId) {
      // We want to set the app entry state to NOTIFICATION_CLICK when coming from background
      appEntryState = AppEntryAction.NOTIFICATION_CLICK;
      getOSSessionManager().onDirectInfluenceFromNotificationOpen(appEntryState, notificationId);
   }

   private static void notificationOpenedRESTCall(Context inContext, JSONArray dataArray) {
      for (int i = 0; i < dataArray.length(); i++) {
         try {
            JSONObject data = dataArray.getJSONObject(i);
            JSONObject customJson = new JSONObject(data.optString("custom", null));

            String notificationId = customJson.optString("i", null);
            // Prevent duplicate calls from summary notifications.
            //  Also needed if developer overrides setAutoCancel.
            if (postedOpenedNotifIds.contains(notificationId))
               continue;
            postedOpenedNotifIds.add(notificationId);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("app_id", getSavedAppId(inContext));
            jsonBody.put("player_id", getSavedUserId(inContext));
            jsonBody.put("opened", true);
            jsonBody.put("device_type", osUtils.getDeviceType());

            OneSignalRestClient.put("notifications/" + notificationId, jsonBody, new OneSignalRestClient.ResponseHandler() {
               @Override
               void  onFailure(int statusCode, String response, Throwable throwable) {
                  logHttpError("sending Notification Opened Failed", statusCode, throwable, response);
               }
            });
         }
         catch(Throwable t){ // JSONException and UnsupportedEncodingException
            Log(LOG_LEVEL.ERROR, "Failed to generate JSON to send notification opened.", t);
         }
      }
   }

   private static void saveAppId(String appId) {
      if (appContext == null)
         return;

      OneSignalPrefs.saveString(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_APP_ID,
              appId);
   }

   static String getSavedAppId() {
      return getSavedAppId(appContext);
   }

   private static String getSavedAppId(Context inContext) {
      if (inContext == null)
         return null;

      return OneSignalPrefs.getString(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_APP_ID,
              null);
   }

   private static String getSavedUserId(Context inContext) {
      if (inContext == null)
         return null;

      return OneSignalPrefs.getString(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_PLAYER_ID,
              null);
   }

   static boolean hasUserId() {
      return getUserId() != null;
   }

   static String getUserId() {
      if (userId == null && appContext != null)
         userId = getSavedUserId(appContext);

      return userId;
   }

   static void saveUserId(String id) {
      userId = id;
      if (appContext == null)
         return;

      OneSignalPrefs.saveString(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_PLAYER_ID,
              userId);
   }

   static boolean hasEmailId() {
      return !TextUtils.isEmpty(emailId);
   }

   static String getEmailId() {
      if (emailId == null && appContext != null) {
         emailId = OneSignalPrefs.getString(
                 OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_OS_EMAIL_ID,
                 null);
      }

      if (TextUtils.isEmpty(emailId))
         return null;

      return emailId;
   }

   static void saveEmailId(String id) {
      emailId = id;
      if (appContext == null)
         return;

      OneSignalPrefs.saveString(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_EMAIL_ID,
              "".equals(emailId) ? null : emailId);
   }

   static boolean hasSMSlId() {
      return !TextUtils.isEmpty(smsId);
   }

   static String getSMSId() {
      if (smsId == null && appContext != null) {
         smsId = OneSignalPrefs.getString(
                 OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_OS_SMS_ID,
                 null);
      }

      if (TextUtils.isEmpty(smsId))
         return null;

      return smsId;
   }

   static void saveSMSId(String id) {
      smsId = id;
      if (appContext == null)
         return;

      OneSignalPrefs.saveString(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_SMS_ID,
              "".equals(smsId) ? null : smsId);
   }

   // Called when a player id is returned from OneSignal
   // Updates anything else that might have been waiting for this id.
   static void updateUserIdDependents(String userId) {
      saveUserId(userId);
      internalFireGetTagsCallbacks();

      getCurrentSubscriptionState(appContext).setUserId(userId);

      if (iapUpdateJob != null) {
         sendPurchases(iapUpdateJob.toReport, iapUpdateJob.newAsExisting, iapUpdateJob.restResponseHandler);
         iapUpdateJob = null;
      }

      OneSignalStateSynchronizer.refreshSecondaryChannelState();
   }

   static void updateEmailIdDependents(String emailId) {
      saveEmailId(emailId);
      getCurrentEmailSubscriptionState(appContext).setEmailUserId(emailId);
      try {
         JSONObject updateJson = new JSONObject().put("parent_player_id", emailId);
         OneSignalStateSynchronizer.updatePushState(updateJson);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static void updateSMSIdDependents(String smsId) {
      saveSMSId(smsId);
      getCurrentSMSSubscriptionState(appContext).setSMSUserId(smsId);
   }

   // Start Remote params getters
   static boolean getFirebaseAnalyticsEnabled() {
      return remoteParamController.getFirebaseAnalyticsEnabled();
   }

   static boolean getClearGroupSummaryClick() {
      return remoteParamController.getClearGroupSummaryClick();
   }

   static boolean getDisableGMSMissingPrompt() {
      return remoteParamController.isGMSMissingPromptDisable();
   }

   public static boolean isLocationShared() {
      return remoteParamController.isLocationShared();
   }

   static boolean isUserPrivacyConsentRequired() {
      return remoteParamController.isPrivacyConsentRequired();
   }
   // End Remote params getters

   static void setLastSessionTime(long time) {
      logger.debug("Last session time set to: " + time);
      OneSignalPrefs.saveLong(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_LAST_SESSION_TIME,
              time);
   }

   private static long getLastSessionTime() {
      return OneSignalPrefs.getLong(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_LAST_SESSION_TIME,
              -31 * 1000L);
   }

   /**
    * You can call this method with {@code true} to opt users out of receiving all notifications through
    * OneSignal. You can pass {@code false} later to opt users back into notifications.
    * @param disable whether to subscribe the user to notifications or not
    */
   public static void disablePush(final boolean disable) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SET_SUBSCRIPTION)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SET_SUBSCRIPTION + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SET_SUBSCRIPTION + " operation from pending queue.");
               disablePush(disable);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.SET_SUBSCRIPTION))
         return;

      getCurrentSubscriptionState(appContext).setPushDisabled(disable);
      OneSignalStateSynchronizer.setSubscription(!disable);
   }


   /**
    * This method will be replaced by remote params set
    */
   public static void disableGMSMissingPrompt(final boolean promptDisable) {
      // Already set by remote params
      if (getRemoteParamController().hasDisableGMSMissingPromptKey())
         return;

      getRemoteParamController().saveGMSMissingPromptDisable(promptDisable);
   }

   /**
    * This method will be replaced by remote params set
    */
   public static void setLocationShared(final boolean enable) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SET_LOCATION_SHARED)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SET_LOCATION_SHARED + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SET_LOCATION_SHARED + " operation from pending task queue.");
               setLocationShared(enable);
            }
         });
         return;
      }

      // Already set by remote params
      if (getRemoteParamController().hasLocationKey())
         return;

      startLocationShared(enable);
   }

   static void startLocationShared(boolean enable) {
      logger.debug("OneSignal startLocationShared: " + enable);
      getRemoteParamController().saveLocationShared(enable);

      if (!enable) {
         logger.debug("OneSignal is shareLocation set false, clearing last location!");
         OneSignalStateSynchronizer.clearLocation();
      }
   }

   /**
    * Use this method to manually prompt the user for location permissions.
    * This allows for geotagging so you send notifications to users based on location.
    *<br/><br/>
    * Make sure you have one of the following permission in your {@code AndroidManifest.xml} as well.
    * <br/>
    * {@code <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>}
    * <br/>
    * {@code <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>}
    *
    * <br/><br/>Be aware of best practices regarding asking permissions on Android:
    * <a href="https://developer.android.com/guide/topics/permissions/requesting.html">
    *     Requesting Permissions | Android Developers
    * </a>
    *
    * @see <a href="https://documentation.onesignal.com/docs/permission-requests">Permission Requests | OneSignal Docs</a>
    */
   public static void promptLocation() {
      promptLocation(null, false);
   }

   static void promptLocation(@Nullable final OSPromptActionCompletionCallback callback, final boolean fallbackToSettings) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.PROMPT_LOCATION)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.PROMPT_LOCATION + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.PROMPT_LOCATION + " operation from pending queue.");
               promptLocation(callback, fallbackToSettings);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.PROMPT_LOCATION))
         return;

      LocationController.LocationHandler locationHandler = new LocationController.LocationPromptCompletionHandler() {
         @Override
         public LocationController.PermissionType getType() {
            return LocationController.PermissionType.PROMPT_LOCATION;
         }

         @Override
         public void onComplete(LocationController.LocationPoint point) {
            //if applicable, check if the user provided privacy consent
            if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.PROMPT_LOCATION))
               return;

            if (point != null)
               OneSignalStateSynchronizer.updateLocation(point);
         }

         @Override
         void onAnswered(OneSignal.PromptActionResult result) {
            super.onAnswered(result);
            if (callback != null)
               callback.onCompleted(result);
         }
      };

      LocationController.getLocation(appContext, true, fallbackToSettings, locationHandler);
   }

   /**
    * On Android 13 shows the system notification permission prompt to enable displaying
    * notifications. This is required for apps that target Android API level 33 / "Tiramisu"
    * to subscribe the device for push notifications.
    */
   public static void promptForPushNotifications() {
      promptForPushNotifications(false);
   }

   /**
    * On Android 13 shows the system notification permission prompt to enable displaying
    * notifications. This is required for apps that target Android API level 33 / "Tiramisu"
    * to subscribe the device for push notifications.
    *
    * @param fallbackToSettings whether to show a Dialog to direct users to the App's notification
    *                           settings if they have declined before.
    */
   public static void promptForPushNotifications(boolean fallbackToSettings) {
      promptForPushNotifications(fallbackToSettings, null);
   }

   /**
    * On Android 13 shows the system notification permission prompt to enable displaying
    * notifications. This is required for apps that target Android API level 33 / "Tiramisu"
    * to subscribe the device for push notifications.
    *
    * @param fallbackToSettings whether to show a Dialog to direct users to the App's notification
    *                           settings if they have declined before.
    * @param handler fires when the user declines ore accepts the notification permission prompt.
    */
   public static void promptForPushNotifications(
      boolean fallbackToSettings,
      @Nullable PromptForPushNotificationPermissionResponseHandler handler
   ) {
      NotificationPermissionController.INSTANCE.prompt(fallbackToSettings, handler);
   }

   /**
    * Removes all OneSignal notifications from the Notification Shade. If you just use
    * {@link NotificationManager#cancelAll()}, OneSignal notifications will be restored when
    * your app is restarted.
    */
   public static void clearOneSignalNotifications() {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.CLEAR_NOTIFICATIONS) || notificationDataController == null) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.CLEAR_NOTIFICATIONS + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.CLEAR_NOTIFICATIONS + " operation from pending queue.");
               clearOneSignalNotifications();
            }
         });
         return;
      }

      notificationDataController.clearOneSignalNotifications(new WeakReference<>(appContext));
   }

   /**
    * Cancels a single OneSignal notification based on its Android notification integer ID. Use
    * instead of Android's {@link NotificationManager#cancel(int)}, otherwise the notification will be restored
    * when your app is restarted.
    * @param id
    */
   public static void removeNotification(final int id) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.REMOVE_NOTIFICATION) || notificationDataController == null) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.REMOVE_NOTIFICATION + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.REMOVE_NOTIFICATION + " operation from pending queue.");
               removeNotification(id);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.REMOVE_NOTIFICATION))
         return;

      notificationDataController.removeNotification(id, new WeakReference<>(appContext));
   }

   public static void removeGroupedNotifications(final String group) {
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.REMOVE_GROUPED_NOTIFICATIONS) || notificationDataController == null) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.REMOVE_GROUPED_NOTIFICATIONS + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.REMOVE_GROUPED_NOTIFICATIONS + " operation from pending queue.");
               removeGroupedNotifications(group);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskRemoteController.REMOVE_GROUPED_NOTIFICATIONS))
         return;

      notificationDataController.removeGroupedNotifications(group, new WeakReference<>(appContext));
   }

   /**
    * The {@link OSPermissionObserver#onOSPermissionChanged(OSPermissionStateChanges)}
    * method will be fired on the passed-in object when a notification permission setting changes.
    * This happens when the user enables or disables notifications for your app from the system
    * settings outside of your app. Disable detection is supported on Android 4.4+
    * <br/><br/>
    * <b>Keep a reference</b> - Make sure to hold a reference to your observable at the class level,
    * otherwise it may not fire
    * <br/>
    * <b>Leak Safe</b> - OneSignal holds a weak reference to your observer so it's guaranteed not to
    * leak your {@code Activity}
    *
    * @param observer the instance of {@link OSPermissionObserver} that you want to process the permission
    *                 changes within
    */
   public static void addPermissionObserver(OSPermissionObserver observer) {

      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not add permission observer");
         return;
      }

      getPermissionStateChangesObserver().addObserver(observer);

      if (getCurrentPermissionState(appContext).compare(getLastPermissionState(appContext)))
         OSPermissionChangedInternalObserver.fireChangesToPublicObserver(getCurrentPermissionState(appContext));
   }

   public static void removePermissionObserver(OSPermissionObserver observer) {
      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not modify permission observer");
         return;
      }

      getPermissionStateChangesObserver().removeObserver(observer);
   }

   /**
    * The {@link OSSubscriptionObserver#onOSSubscriptionChanged(OSSubscriptionStateChanges)}
    * method will be fired on the passed-in object when a notification subscription property changes.
    *<br/><br/>
    * This includes the following events:
    * <br/>
    * - Getting a Registration ID (push token) from Google
    * <br/>
    * - Getting a player/user ID from OneSignal
    * <br/>
    * - {@link OneSignal#disablePush(boolean)} is called
    * <br/>
    * - User disables or enables notifications
    * @param observer the instance of {@link OSSubscriptionObserver} that acts as the observer
    */
   public static void addSubscriptionObserver(OSSubscriptionObserver observer) {
      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not add subscription observer");
         return;
      }

      getSubscriptionStateChangesObserver().addObserver(observer);

      if (getCurrentSubscriptionState(appContext).compare(getLastSubscriptionState(appContext)))
         OSSubscriptionChangedInternalObserver.fireChangesToPublicObserver(getCurrentSubscriptionState(appContext));
   }

   public static void removeSubscriptionObserver(OSSubscriptionObserver observer) {
      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not modify subscription observer");
         return;
      }

      getSubscriptionStateChangesObserver().removeObserver(observer);
   }

   /**
    * The {@link OSEmailSubscriptionObserver#onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges)}
    * method will be fired on the passed-in object when a email subscription property changes.
    *<br/><br/>
    * This includes the following events:
    * <br/>
    * - Email address set
    * <br/>
    * - Getting a player/user ID from OneSignal
    * @param observer the instance of {@link OSSubscriptionObserver} that acts as the observer
    */
   public static void addEmailSubscriptionObserver(@NonNull OSEmailSubscriptionObserver observer) {

      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not add email subscription observer");
         return;
      }

      getEmailSubscriptionStateChangesObserver().addObserver(observer);

      if (getCurrentEmailSubscriptionState(appContext).compare(getLastEmailSubscriptionState(appContext)))
         OSEmailSubscriptionChangedInternalObserver.fireChangesToPublicObserver(getCurrentEmailSubscriptionState(appContext));
   }

   public static void removeEmailSubscriptionObserver(@NonNull OSEmailSubscriptionObserver observer) {
      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not modify email subscription observer");
         return;
      }

      getEmailSubscriptionStateChangesObserver().removeObserver(observer);
   }

   /**
    * The {@link OSSMSSubscriptionObserver#onSMSSubscriptionChanged(OSSMSSubscriptionStateChanges)}
    * method will be fired on the passed-in object when a sms subscription property changes.
    *<br/><br/>
    * This includes the following events:
    * <br/>
    * - SMS number set
    * <br/>
    * - Getting a player/user ID from OneSignal
    * @param observer the instance of {@link OSSubscriptionObserver} that acts as the observer
    */
   public static void addSMSSubscriptionObserver(@NonNull OSSMSSubscriptionObserver observer) {
      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not add sms subscription observer");
         return;
      }

      getSMSSubscriptionStateChangesObserver().addObserver(observer);

      if (getCurrentSMSSubscriptionState(appContext).compare(getLastSMSSubscriptionState(appContext)))
         OSSMSSubscriptionChangedInternalObserver.fireChangesToPublicObserver(getCurrentSMSSubscriptionState(appContext));
   }

   public static void removeSMSSubscriptionObserver(@NonNull OSSMSSubscriptionObserver observer) {
      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not modify sms subscription observer");
         return;
      }

      getSMSSubscriptionStateChangesObserver().removeObserver(observer);
   }

   /** In-App Message Triggers */

   /**
    * Allows you to set multiple trigger key/value pairs simultaneously with a Map
    * Triggers are used for targeting in-app messages.
    */
   public static void addTriggers(Map<String, Object> triggers) {
      getInAppMessageController().addTriggers(triggers);
   }

   /**
    * Allows you to set an individual trigger key/value pair for in-app message targeting
    */
   public static void addTrigger(String key, Object object) {
      HashMap<String, Object> triggerMap = new HashMap<>();
      triggerMap.put(key, object);

      getInAppMessageController().addTriggers(triggerMap);
   }

   /** Removes a list/collection of triggers from their keys with a Collection of Strings */
   public static void removeTriggersForKeys(Collection<String> keys) {
      getInAppMessageController().removeTriggersForKeys(keys);
   }

   /** Removes a single trigger for the given key */
   public static void removeTriggerForKey(String key) {
      ArrayList<String> triggerKeys = new ArrayList<>();
      triggerKeys.add(key);

      getInAppMessageController().removeTriggersForKeys(triggerKeys);
   }

   /** Returns a single trigger value for the given key (if it exists, otherwise returns null) */
   @Nullable
   public static Object getTriggerValueForKey(String key) {
      if (appContext == null) {
         logger.error("Before calling getTriggerValueForKey, Make sure OneSignal initWithContext and setAppId is called first");
         return null;
      }

      return getInAppMessageController().getTriggerValue(key);
   }

   /** Returns all trigger key-value for the current user */
   public static Map<String, Object> getTriggers() {
      if (appContext == null) {
         logger.error("Before calling getTriggers, Make sure OneSignal initWithContext and setAppId is called first");
         return new HashMap<>();
      }

      return getInAppMessageController().getTriggers();
   }

   /***
    * Can temporarily pause in-app messaging on this device.
    * Useful if you don't want to interrupt a user while playing a match in a game.
    *
    * @param pause The boolean that pauses/resumes in-app messages
    */
   public static void pauseInAppMessages(final boolean pause) {
      if (appContext == null) {
         logger.error("Waiting initWithContext. " +
                 "Moving " + OSTaskRemoteController.PAUSE_IN_APP_MESSAGES + " operation to a pending task queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.PAUSE_IN_APP_MESSAGES + " operation from pending queue.");
               pauseInAppMessages(pause);
            }
         });
         return;
      }

      getInAppMessageController().setInAppMessagingEnabled(!pause);
   }

   public static boolean isInAppMessagingPaused() {
      if (appContext == null) {
         logger.error("Before calling isInAppMessagingPaused, Make sure OneSignal initWithContext and setAppId is called first");
         return false;
      }

      return !getInAppMessageController().inAppMessagingEnabled();
   }

   /**
    * Method that checks if notification is valid or duplicated.
    *
    * Method called from Notification processing. This might be called from a flow that doesn't call initWithContext yet.
    * Check if notificationDataController is not init before processing notValidOrDuplicated
    *
    * @param context the context from where the notification was received
    * @param jsonPayload the notification payload in a JSON format
    * @param callback the callback to return the result since this method does DB access in another thread
    * */
   static void notValidOrDuplicated(Context context, JSONObject jsonPayload, @NonNull OSNotificationDataController.InvalidOrDuplicateNotificationCallback callback) {
      if (notificationDataController == null) {
          OneSignalDbHelper helper = OneSignal.getDBHelperInstance(context);
          notificationDataController = new OSNotificationDataController(helper, logger);
      }
      notificationDataController.notValidOrDuplicated(jsonPayload, callback);
   }

   static String getNotificationIdFromFCMJson(@Nullable JSONObject fcmJson) {
      if (fcmJson == null)
         return null;
      try {
         JSONObject customJSON = new JSONObject(fcmJson.getString("custom"));

         if (customJSON.has("i"))
            return customJSON.optString("i", null);
         else
            logger.debug("Not a OneSignal formatted FCM message. No 'i' field in custom.");
      } catch (JSONException e) {
         logger.debug("Not a OneSignal formatted FCM message. No 'custom' field in the JSONObject.");
      }

      return null;
   }

   static boolean isAppActive() {
      return initDone && isInForeground();
   }

   private static boolean shouldStartNewSession() {
      if (!isInForeground())
         return false;

      if (!isPastOnSessionTime())
         return false;

      return true;
   }

   private static boolean isPastOnSessionTime() {
      long currentTimeMillis = OneSignal.getTime().getCurrentTimeMillis();
      long lastSessionTime = getLastSessionTime();
      long difference = currentTimeMillis - lastSessionTime;
      logger.debug("isPastOnSessionTime currentTimeMillis: " + currentTimeMillis + " lastSessionTime: " + lastSessionTime + " difference: " + difference);
      return difference >= MIN_ON_SESSION_TIME_MILLIS;
   }

   // Extra check to make sure we don't unsubscribe devices that rely on silent background notifications.
   static boolean areNotificationsEnabledForSubscribedState() {
      if (remoteParamController.unsubscribeWhenNotificationsAreDisabled())
         return OSUtils.areNotificationsEnabled(appContext);
      return true;
   }

   static void handleSuccessfulEmailLogout() {
      if (emailLogoutHandler != null) {
         emailLogoutHandler.onSuccess();
         emailLogoutHandler = null;
      }
   }

   static void handleFailedEmailLogout() {
      if (emailLogoutHandler != null) {
         emailLogoutHandler.onFailure(new EmailUpdateError(EmailErrorType.NETWORK, "Failed due to network failure. Will retry on next sync."));
         emailLogoutHandler = null;
      }
   }

   static void fireEmailUpdateSuccess() {
      if (emailUpdateHandler != null) {
        emailUpdateHandler.onSuccess();
        emailUpdateHandler = null;
      }
   }

   static void fireEmailUpdateFailure() {
      if (emailUpdateHandler != null) {
         emailUpdateHandler.onFailure(new EmailUpdateError(EmailErrorType.NETWORK, "Failed due to network failure. Will retry on next sync."));
         emailUpdateHandler = null;
      }
   }

   static void handleSuccessfulSMSlLogout(JSONObject result) {
      if (smsLogoutHandler != null) {
         smsLogoutHandler.onSuccess(result);
         smsLogoutHandler = null;
      }
   }

   static void handleFailedSMSLogout() {
      if (smsLogoutHandler != null) {
         smsLogoutHandler.onFailure(new OSSMSUpdateError(SMSErrorType.NETWORK, "Failed due to network failure. Will retry on next sync."));
         smsLogoutHandler = null;
      }
   }

   static void fireSMSUpdateSuccess(JSONObject result) {
      if (smsUpdateHandler != null) {
         smsUpdateHandler.onSuccess(result);
         smsUpdateHandler = null;
      }
   }

   static void fireSMSUpdateFailure() {
      if (smsUpdateHandler != null) {
         smsUpdateHandler.onFailure(new OSSMSUpdateError(SMSErrorType.NETWORK, "Failed due to network failure. Will retry on next sync."));
         smsUpdateHandler = null;
      }
   }

   @NonNull
   static OSTime getTime() {
      return time;
   }

   /*
    * Start Mock Injection module
    */
   static void setTime(OSTime time) {
      OneSignal.time = time;
   }

   static void setTrackerFactory(OSTrackerFactory trackerFactory) {
      OneSignal.mTrackerFactory = trackerFactory;
   }

   static void setSessionManager(OSSessionManager sessionManager) {
      OneSignal.mSessionManager = sessionManager;
   }

   static void setSharedPreferences(OSSharedPreferences preferences) {
      OneSignal.preferences = preferences;
   }

   static OSSessionManager.SessionListener getSessionListener() {
      return sessionListener;
   }

   static OSRemoteParamController getRemoteParamController() {
      return remoteParamController;
   }

   static OneSignalDbHelper getDBHelperInstance() {
      return OneSignalDbHelper.getInstance(appContext);
   }

   static OneSignalDbHelper getDBHelperInstance(Context context) {
       return OneSignalDbHelper.getInstance(context);
   }

   static OSTaskController getTaskRemoteController() {
      return taskRemoteController;
   }

   static OSTaskController getTaskController() {
      return taskController;
   }

   static FocusTimeController getFocusTimeController() {
      if (focusTimeController == null) {
         focusTimeController = new FocusTimeController(new OSFocusTimeProcessorFactory(), logger);
      }

      return focusTimeController;
   }
   /*
    * End Mock Injection module
    */

   /*
    * Start OneSignalOutcome module
    */
   static OSSessionManager getSessionManager() {
      return getOSSessionManager();
   }

   static void sendClickActionOutcomes(@NonNull List<OSInAppMessageOutcome> outcomes) {
      // This is called from IAM shouldn't need this check
      if (outcomeEventsController == null || appId == null) {
         OneSignal.Log(LOG_LEVEL.ERROR, "Make sure OneSignal.init is called first");
         return;
      }

      outcomeEventsController.sendClickActionOutcomes(outcomes);
   }

   public static void sendOutcome(@NonNull String name) {
      sendOutcome(name, null);
   }

   public static void sendOutcome(@NonNull final String name, final OutcomeCallback callback) {
      if (!isValidOutcomeEntry(name)) {
         logger.error("Make sure OneSignal initWithContext and setAppId is called first");
         return;
      }

      // Outcomes needs app id, delay until init is not done
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SEND_OUTCOME) || outcomeEventsController == null) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SEND_OUTCOME + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SEND_OUTCOME + " operation from pending queue.");
               sendOutcome(name, callback);
            }
         });
         return;
      }

      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("sendOutcome()")) {
         return;
      }

      outcomeEventsController.sendOutcomeEvent(name, callback);
   }

   public static void sendUniqueOutcome(@NonNull String name) {
      sendUniqueOutcome(name, null);
   }

   public static void sendUniqueOutcome(@NonNull final String name, final OutcomeCallback callback) {
      if (!isValidOutcomeEntry(name))
         return;

      // Outcomes needs app id, delay until init is not done
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SEND_UNIQUE_OUTCOME) || outcomeEventsController == null) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SEND_UNIQUE_OUTCOME + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SEND_UNIQUE_OUTCOME + " operation from pending queue.");
               sendUniqueOutcome(name, callback);
            }
         });
         return;
      }

      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("sendUniqueOutcome()")) {
         return;
      }

      outcomeEventsController.sendUniqueOutcomeEvent(name, callback);
   }

   public static void sendOutcomeWithValue(@NonNull String name, float value) {
      sendOutcomeWithValue(name, value, null);
   }

   public static void sendOutcomeWithValue(@NonNull final String name, final float value, final OutcomeCallback callback) {
      if (!isValidOutcomeEntry(name) || !isValidOutcomeValue(value))
         return;

      // Outcomes needs app id, delay until init is not done
      if (taskRemoteController.shouldQueueTaskForInit(OSTaskRemoteController.SEND_OUTCOME_WITH_VALUE) || outcomeEventsController == null) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskRemoteController.SEND_OUTCOME_WITH_VALUE + " operation to a pending queue.");
         taskRemoteController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskRemoteController.SEND_OUTCOME_WITH_VALUE + " operation from pending queue.");
               sendOutcomeWithValue(name, value, callback);
            }
         });
         return;
      }

      outcomeEventsController.sendOutcomeEventWithValue(name, value, callback);
   }

   private static boolean isValidOutcomeEntry(String name) {
      if (name == null || name.isEmpty()) {
         OneSignal.Log(LOG_LEVEL.ERROR, "Outcome name must not be empty");
         return false;
      }

      return true;
   }

   private static boolean isValidOutcomeValue(float value) {
      if (value <= 0) {
         OneSignal.Log(LOG_LEVEL.ERROR, "Outcome value must be greater than 0");
         return false;
      }

      return true;
   }

   /**
    * OutcomeEvent will be null in cases where the request was not sent:
    *    1. OutcomeEventParams cached already for re-attempt in future
    *    2. Unique OutcomeEventParams already sent for ATTRIBUTED session and notification(s)
    *    3. Unique OutcomeEventParams already sent for UNATTRIBUTED session during session
    *    4. Outcomes disabled
    */
   public interface OutcomeCallback {
      void onSuccess(@Nullable OSOutcomeEvent outcomeEvent);
   }
   /*
    * End OneSignalOutcome module
    */

   interface OSPromptActionCompletionCallback {
      void onCompleted(PromptActionResult result);
   }

   enum PromptActionResult {
      PERMISSION_GRANTED,
      PERMISSION_DENIED,
      LOCATION_PERMISSIONS_MISSING_MANIFEST,
      ERROR;
   }
}
