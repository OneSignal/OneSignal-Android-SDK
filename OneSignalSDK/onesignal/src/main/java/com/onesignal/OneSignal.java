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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.onesignal.OSNotificationGenerationJob.AppNotificationGenerationJob;
import com.onesignal.OneSignalDbContract.NotificationTable;
import com.onesignal.influence.data.OSTrackerFactory;
import com.onesignal.influence.domain.OSInfluence;
import com.onesignal.outcomes.data.OSOutcomeEventsFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
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

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID;
import static com.onesignal.NotificationBundleProcessor.newJsonArray;

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
    * A display type enum for notifications when the app receives and tries shows them.
    * <br/><br/>
    * Used with the {@link OSNotificationGenerationJob#setNotificationDisplayOption(OSNotificationDisplay)}
    * Determines how the notification will show to the user when inside of the
    *  {@link NotificationWillShowInForegroundHandler#notificationWillShowInForeground(AppNotificationGenerationJob)}
    * <br/><br/>
    * Default for {@link OSNotificationGenerationJob#displayOption} is {@link OSNotificationDisplay#NOTIFICATION}
    * <br/><br/>
    * @see OSNotificationGenerationJob
    * @see NotificationWillShowInForegroundHandler
    */
   public enum OSNotificationDisplay {

      /**
       * Notification will be a background notification (will not be shown on the device) and
       *  offers a good option for performing work when receiving specific notifications, but not
       *  caring for showing this notif to the user.
       */
      SILENT,

      /**
       * Notification will show in shade of notification tray.
       */
      NOTIFICATION;

      boolean isSilent() {
         return this.equals(SILENT);
      }

      boolean isNotification() {
         return this.equals(NOTIFICATION);
      }
   }

   /**
    * An app entry type enum for knowing how the user foregrounded or backgrounded the app.
    * <br/><br/>
    * The enum also helps decide the type of session the user is in an is tracked in {@link OneSignal#sessionManager}
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
    * Meant to be implemented within a custom made NotificationExtensionService
    * Naming can be whatever makes the most sense to the developer, but to actually activate any
    *    implemented interfaces, a metadata tag with a specific OneSignal key and then file path value
    *    must be added to the apps AndroidManifest.xml
    *       ex. <meta-data android:name="com.onesignal.NotificationExtensionServiceClass" android:value="com.company.ExtensionService" />
    * <br/><br/>
    * Allows for modification of a notifications payload by creating a {@link com.onesignal.OSNotificationExtender.OverrideSettings}
    *    instance and passing it into {@link OSNotificationReceived#setModifiedContent(OSNotificationExtender.OverrideSettings)}
    * To display the notification, call {@link OSNotificationReceived#display()}
    * Finally, to notify the SDK that the processing work is done, call {@link OSNotificationReceived#complete()}
    * <br/><br/>
    * TODO: Update docs with new NotificationProcessingHandler, this would be replacing the old NotificationExtenderService
    */
   public interface NotificationProcessingHandler {

      void notificationProcessing(Context context, OSNotificationReceived notification);
   }

   /**
    * Meant to be implemented with {@link OneSignal#setNotificationWillShowInForegroundHandler(NotificationWillShowInForegroundHandler)}
    * <br/><br/>
    * Call setters from the {@link AppNotificationGenerationJob} to modify the {@link OSNotificationGenerationJob}
    *    before the notification is shown on the device
    * After modification is done {@link AppNotificationGenerationJob#complete()} should be called,
    *    but if it is not called a default of bubbling will be decided after 30 second timeout
    * <br/><br/>
    * TODO: Update docs with new NotificationReceivedHandler
    * @see <a href="https://documentation.onesignal.com/docs/android-native-sdk#notificationreceivedhandler">NotificationReceivedHandler | OneSignal Docs</a>
    */
   public interface NotificationWillShowInForegroundHandler {

      void notificationWillShowInForeground(AppNotificationGenerationJob notificationJob);
   }

   /**
    * An interface used to process a OneSignal notification the user just tapped on.
    * <br/>
    * Set this during OneSignal init in
    * {@link OneSignal#setNotificationOpenedHandler(NotificationOpenedHandler)}
    * <br/><br/>
    * @see <a href="https://documentation.onesignal.com/docs/android-native-sdk#notificationopenedhandler">NotificationOpenedHandler | OneSignal Docs</a>
    */
   public interface NotificationOpenedHandler {
      /**
       * Fires when a user taps on a notification.
       * @param result a {@link OSNotificationOpenResult} with the user's response and properties of this notification
       */
      void notificationOpened(OSNotificationOpenResult result);
   }

   /**
    * An interface used to process a OneSignal In-App Message the user just tapped on.
    * <br/>
    * Set this during OneSignal init in
    * {@link OneSignal#setInAppMessageClickHandler(InAppMessageClickHandler)}
    */
   public interface InAppMessageClickHandler {
      /**
       * Fires when a user taps on a clickable element in the notification such as a button or image
       * @param result a {@link OSInAppMessageAction}
       **/
      void inAppMessageClicked(OSInAppMessageAction result);
   }

   public interface IdsAvailableHandler {
      void idsAvailable(String userId, String registrationId);
   }

   /**
    * Interface which you can implement and pass to {@link OneSignal#getTags(GetTagsHandler)} to
    * get all the tags set on a user
    * <br/><br/>
    * <b>Note:</b> the {@link #tagsAvailable(JSONObject)} callback does not run on the Main(UI)
    * Thread, so be aware when modifying UI in this method.
    */
   public interface GetTagsHandler {
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

   public interface OSExternalUserIdUpdateCompletionHandler {
      void onComplete(JSONObject results);
   }

   interface OSInternalExternalUserIdUpdateCompletionHandler {
      void onComplete(String channel, boolean success);
   }

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

   static Context appContext;
   static String appId;
   static String googleProjectNumber;

   private static LOG_LEVEL visualLogLevel = LOG_LEVEL.NONE;
   private static LOG_LEVEL logCatLevel = LOG_LEVEL.WARN;

   private static String userId = null;
   private static String emailId = null;
   private static int subscribableStatus = Integer.MAX_VALUE;

   static NotificationProcessingHandler notificationProcessingHandler;
   static NotificationWillShowInForegroundHandler notificationWillShowInForegroundHandler;

   // TODO: Start of old mInitBuilder params
   //    These should be cleaned up and managed else where maybe?
   //    These have been ripped out of mInitBuilder since it was deleted and placed here for now
   static NotificationOpenedHandler notificationOpenedHandler;
   static InAppMessageClickHandler inAppMessageClickHandler;

   static boolean mAutoPromptLocation;
   // TODO: End of old mInitBuilder params

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

   // Tells the action taken to enter the app
   @NonNull private static AppEntryAction appEntryState = AppEntryAction.APP_CLOSE;
   static @NonNull AppEntryAction getAppEntryState() {
      return appEntryState;
   }

   private static IdsAvailableHandler idsAvailableHandler;

   private static TrackGooglePurchase trackGooglePurchase;
   private static TrackAmazonPurchase trackAmazonPurchase;
   private static TrackFirebaseAnalytics trackFirebaseAnalytics;

   public static final String VERSION = "031501";

   private static OSSessionManager.SessionListener sessionListener = new OSSessionManager.SessionListener() {
         @Override
         public void onSessionEnding(@NonNull List<OSInfluence> lastInfluences) {
            if (outcomeEventsController == null)
               OneSignal.Log(LOG_LEVEL.WARN, "OneSignal onSessionEnding called before init!");
            if (outcomeEventsController != null)
               outcomeEventsController.cleanOutcomes();
            FocusTimeController.getInstance().onSessionEnded(lastInfluences);
         }
      };

   private static OSTime time = new OSTimeImpl();
   private static OSLogger logger = new OSLogWrapper();
   private static OSRemoteParamController remoteParamController = new OSRemoteParamController();
   private static OSTaskController taskController = new OSTaskController(remoteParamController, logger);
   private static OneSignalAPIClient apiClient = new OneSignalRestClientWrapper();
   private static OSSharedPreferences preferences = new OSSharedPreferencesWrapper();
   private static OSTrackerFactory trackerFactory = new OSTrackerFactory(preferences, logger, time);
   private static OSSessionManager sessionManager = new OSSessionManager(sessionListener, trackerFactory, logger);
   @Nullable private static OSOutcomeEventsController outcomeEventsController;
   @Nullable private static OSOutcomeEventsFactory outcomeEventsFactory;

   @Nullable private static AdvertisingIdentifierProvider adIdProvider;
   private static synchronized @Nullable AdvertisingIdentifierProvider getAdIdProvider() {
      if (adIdProvider == null) {
         if (OSUtils.isAndroidDeviceType())
            adIdProvider = new AdvertisingIdProviderGPS();
      }

      return adIdProvider;
   }

   @SuppressWarnings("WeakerAccess")
   public static String sdkType = "native";
   private static String lastRegistrationId;

   @NonNull private static OSUtils osUtils = new OSUtils();

   private static boolean registerForPushFired, locationFired, promptedLocation, getTagsCall, waitingToPostStateSync;

   private static LocationController.LocationPoint lastLocationPoint;

   private static Collection<JSONArray> unprocessedOpenedNotifs = new ArrayList<>();
   private static HashSet<String> postedOpenedNotifIds = new HashSet<>();
   private static final ArrayList<GetTagsHandler> pendingGetTagsHandlers = new ArrayList<>();

   static DelayedConsentInitializationParameters delayedInitParams;

   // Start PermissionState
   private static OSPermissionState currentPermissionState;
   private static OSPermissionState getCurrentPermissionState(Context context) {
      if (context == null)
         return null;

      if (currentPermissionState == null) {
         currentPermissionState = new OSPermissionState(false);
         currentPermissionState.observable.addObserverStrong(new OSPermissionChangedInternalObserver());
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
         currentSubscriptionState = new OSSubscriptionState(false, getCurrentPermissionState(context).getEnabled());
         getCurrentPermissionState(context).observable.addObserver(currentSubscriptionState);
         currentSubscriptionState.observable.addObserverStrong(new OSSubscriptionChangedInternalObserver());
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
         currentEmailSubscriptionState.observable.addObserverStrong(new OSEmailSubscriptionChangedInternalObserver());
      }

      return currentEmailSubscriptionState;
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

   @Nullable
   public static OSDeviceState getDeviceState() {
      if (getPermissionSubscriptionState() == null)
         return null;
      return new OSDeviceState(getPermissionSubscriptionState());
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
    * Prompts the user for location permissions.
    * This allows for geotagging so you can send notifications to users based on location.
    * This does not accommodate any rationale-gating that is encouraged before requesting
    * permissions from the user.
    * <br/><br/>
    * See {@link #promptLocation()} for more details on how to manually prompt location permissions.
    *
    * @param enable If set to {@code false}, OneSignal will not prompt for location.
    *               If set to {@code true}, OneSignal will prompt users for location permissions
    *               when your app starts
    * @return the builder object you called this method on
    */
   public static void autoPromptLocation(boolean enable) {
      mAutoPromptLocation = enable;
   }

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
      if (taskController.shouldQueueTaskForInit(OSTaskController.UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED + " operation from pending task queue.");
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
      logger.verbose("setAppId(id) called with app_id: " + newAppId + "!");

      if (newAppId == null || newAppId.isEmpty()) {
         logger.warning("newAppId is null or empty, ignoring!");
         return;
      } else if (!newAppId.equals(appId)) {
         // Pre-check on app id to make sure init of SDK is performed properly
         //     Usually when the app id is changed during runtime so that SDK is reinitialized properly
         initDone = false;
      }

      appId = newAppId;

      logger.verbose("setAppId(id) finished, checking if appContext has been set before proceeding...");
      if (appContext == null) {
         logger.warning("appId set, but please call initWithContext(appContext) with Application context to complete OneSignal init!");
         return;
      }

      logger.verbose("setAppId(id) successful and appContext is set, continuing OneSignal init...");
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
      logger.verbose("initWithContext(context) called!");

      if (context == null) {
         Log(LOG_LEVEL.WARN, "context is null, ignoring!");
         return;
      }

      boolean wasAppContextNull = (appContext == null);
      appContext = context.getApplicationContext();
      setupActivityLifecycleListener(wasAppContextNull);
      setupPrivacyConsent(appContext);

      logger.verbose("initWithContext(context) finished, checking if appId has been set before proceeding...");
      if (appId == null) {
         // Get the cached app id, if it exists
         String oldAppId = getSavedAppId();
         if (oldAppId == null) {
            logger.warning("appContext set, but please call setAppId(appId) with a valid appId to complete OneSignal init!");
         } else {
            logger.verbose("appContext set and an old appId was found, attempting to call setAppId(oldAppId)");
            setAppId(oldAppId);
         }
         return;
      }
      logger.verbose("initWithContext(context) successful and appId is set, continuing OneSignal init...");
      init(context);
   }

   static void setNotificationProcessingHandler(NotificationProcessingHandler callback) {
      if (notificationProcessingHandler == null)
         notificationProcessingHandler = callback;
   }

   public static void setNotificationWillShowInForegroundHandler(NotificationWillShowInForegroundHandler callback) {
      notificationWillShowInForegroundHandler = callback;
   }

   public static void setNotificationOpenedHandler(NotificationOpenedHandler callback) {
      notificationOpenedHandler = callback;

      if (initDone && notificationOpenedHandler != null)
         fireCallbackForOpenedNotifications();
   }

   public static void setInAppMessageClickHandler(InAppMessageClickHandler callback) {
      inAppMessageClickHandler = callback;
   }

   /**
    * Called after setAppId and initWithContext, depending on which one is called last (order does not matter)
    */
   synchronized private static void init(Context context) {
      OSNotificationExtender.setupNotificationExtensionServiceClass();

      if (requiresUserPrivacyConsent() || !remoteParamController.isRemoteParamsCallDone()) {
         if (!remoteParamController.isRemoteParamsCallDone())
            logger.verbose("OneSignal SDK initialization delayed, " +
                    "waiting for remote params.");
         else
            logger.verbose("OneSignal SDK initialization delayed, " +
                    "waiting for privacy consent to be set.");

         delayedInitParams = new DelayedConsentInitializationParameters(context, appId);
         String lastAppId = appId;
         // Set app id null since OneSignal was not init fully
         appId = null;
         // Wrapper SDK's call init twice and pass null as the appId on the first call
         //  the app ID is required to download parameters, so do not download params until the appID is provided
         if (lastAppId != null && context != null)
            makeAndroidParamsRequest(lastAppId, getUserId());
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

      outcomeEventsController.sendSavedOutcomes();

      // Clean up any pending tasks that were queued up before initialization
      taskController.startPendingTasks();
   }

   static void onRemoteParamSet() {
      if (delayedInitParams != null) // Remote Params called from init
         reassignDelayedInitParams();
      else if (inForeground) // Remote Params called from onAppFocus
         onAppFocusLogic();
   }

   private static void setupActivityLifecycleListener(boolean wasAppContextNull) {
      // Register the lifecycle listener of the app for state changes in activities with proper context
      ActivityLifecycleListener.registerActivityLifecycleCallbacks((Application) appContext);

      // Do work here that should only happen once or at the start of a new lifecycle
      if (wasAppContextNull) {
         if (outcomeEventsFactory == null)
            outcomeEventsFactory = new OSOutcomeEventsFactory(logger, apiClient, OneSignalDbHelper.getInstance(appContext), preferences);

         sessionManager.initSessionFromCache();
         outcomeEventsController = new OSOutcomeEventsController(sessionManager, outcomeEventsFactory);
         // Prefs require a context to save
         // If the previous state of appContext was null, kick off write in-case it was waiting
         OneSignalPrefs.startDelayedWrite();
         // Cleans out old cached data to prevent over using the storage on devices
         OneSignalCacheCleaner.cleanOldCachedData(appContext);
      }
   }

   private static void setupPrivacyConsent(Context context) {
      try {
         ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
         Bundle bundle = ai.metaData;

         // Read the current privacy consent setting from AndroidManifest.xml
         String requireSetting = bundle.getString("com.onesignal.PrivacyConsent");
         setRequiresUserPrivacyConsent("ENABLE".equalsIgnoreCase(requireSetting));
      } catch (Throwable t) {
         t.printStackTrace();
      }
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

   private static boolean isGoogleProjectNumberRemote() {
      return getRemoteParams() != null &&
              getRemoteParams().googleProjectNumber != null;
   }

   private static boolean isSubscriptionStatusUninitializable() {
      return subscribableStatus == OSUtils.UNINITIALIZABLE_STATUS;
   }

   private static void handleActivityLifecycleHandler(Context context) {
      inForeground = isContextActivity(context);
      if (inForeground) {
         ActivityLifecycleHandler.curActivity = (Activity) context;
         OSNotificationRestoreWorkManager.beginEnqueueingWork(context, false);
         FocusTimeController.getInstance().appForegrounded();
      }
      else
         ActivityLifecycleHandler.nextResumeIsFirstActivity = true;
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
      if (isPastOnSessionTime()) {
          OneSignalStateSynchronizer.setNewSession();
         if (inForeground) {
            outcomeEventsController.cleanOutcomes();
            sessionManager.restartSessionIfNeeded(getAppEntryState());
         }
      } else if (inForeground) {
         OSInAppMessageController.getController(OneSignal.getLogger()).initWithCachedInAppMessages();
         sessionManager.attemptSessionUpgrade(getAppEntryState());
      }

      // We still want register the user to OneSignal if the SDK was initialized
      //   in the background for the first time.
      if (!inForeground && hasUserId())
         return;

      setLastSessionTime(time.getCurrentTimeMillis());
      startRegistrationOrOnSession();
   }

   private static boolean isContextActivity(Context context) {
      return context instanceof Activity;
   }

   private static void startRegistrationOrOnSession() {
      if (waitingToPostStateSync)
         return;
      waitingToPostStateSync = true;

      if (OneSignalStateSynchronizer.getSyncAsNewSession())
         locationFired = false;

      startLocationUpdate();

      registerForPushFired = false;

      if (getRemoteParams() != null)
         registerForPushToken();
      else
         makeAndroidParamsRequest(appId, getUserId());
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
      boolean doPrompt = mAutoPromptLocation && !promptedLocation;
      // Prompted so we don't ask for permissions more than once
      promptedLocation = promptedLocation || mAutoPromptLocation;

      LocationController.getLocation(appContext, doPrompt, false, locationHandler);
   }
   private static PushRegistrator mPushRegistrator;

   private static PushRegistrator getPushRegistrator() {
      if (mPushRegistrator != null)
         return mPushRegistrator;

      if (OSUtils.isFireOSDeviceType())
         mPushRegistrator = new PushRegistratorADM();
      else if (OSUtils.isAndroidDeviceType()) {
         if (OSUtils.hasFCMLibrary())
            mPushRegistrator = new PushRegistratorFCM();
      } else
         mPushRegistrator = new PushRegistratorHMS();

      return mPushRegistrator;
   }

   private static void registerForPushToken() {
      getPushRegistrator().registerForPush(appContext, googleProjectNumber, new PushRegistrator.RegisteredHandler() {
         @Override
         public void complete(String id, int status) {
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

   private static void makeAndroidParamsRequest(String appId, String userId) {
      if (getRemoteParams() != null)
         return;

      OneSignalRemoteParams.makeAndroidParamsRequest(appId, userId, new OneSignalRemoteParams.Callback() {
         @Override
         public void complete(OneSignalRemoteParams.Params params) {
            if (params.googleProjectNumber != null)
               googleProjectNumber = params.googleProjectNumber;

            remoteParamController.saveRemoteParams(params, trackerFactory, preferences, logger);
            onRemoteParamSet();

            NotificationChannelManager.processChannelList(
               OneSignal.appContext,
               params.notificationChannels
            );
         }
      });
   }

   private static void fireCallbackForOpenedNotifications() {
      for (JSONArray dataArray : unprocessedOpenedNotifs)
         runNotificationOpenedCallback(dataArray, true);

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

   private static void reassignDelayedInitParams() {
      Context delayedContext = delayedInitParams.context;
      String delayedAppId = delayedInitParams.appId;

      delayedInitParams = null;

      setAppId(delayedAppId);
      initWithContext(delayedContext);
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
      if (taskController.shouldQueueTaskForInit(OSTaskController.SET_REQUIRES_USER_PRIVACY_CONSENT)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.SET_REQUIRES_USER_PRIVACY_CONSENT + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.SET_REQUIRES_USER_PRIVACY_CONSENT + " with " + required + " operation from pending task queue.");
               setRequiresUserPrivacyConsent(required);
            }
         });
         return;
      }

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

      if (level.compareTo(visualLogLevel) < 1 && ActivityLifecycleHandler.curActivity != null) {
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
                  if (ActivityLifecycleHandler.curActivity != null)
                     new AlertDialog.Builder(ActivityLifecycleHandler.curActivity)
                         .setTitle(level.toString())
                         .setMessage(finalFullMessage)
                         .show();
               }
            });
         } catch(Throwable t) {
            Log.e(TAG, "Error showing logging message.", t);
         }
      }
   }

   static void logHttpError(String errorString, int statusCode, Throwable throwable, String errorResponse) {
      String jsonError = "";
      if (errorResponse != null && atLogLevel(LOG_LEVEL.INFO))
         jsonError = "\n" + errorResponse + "\n";
      Log(LOG_LEVEL.WARN, "HTTP code: " + statusCode + " " + errorString + jsonError, throwable);
   }

   static OSLogger getLogger() {
      return logger;
   }

   // Returns true if there is active time that is unsynced.
   @WorkerThread
   static void onAppLostFocus() {
      inForeground = false;
      appEntryState = AppEntryAction.APP_CLOSE;

      setLastSessionTime(OneSignal.getTime().getCurrentTimeMillis());
      LocationController.onFocusChange();

      if (!initDone)
         return;

      if (trackAmazonPurchase != null)
         trackAmazonPurchase.checkListener();

      FocusTimeController.getInstance().appBackgrounded();

      scheduleSyncService();
   }

   // Schedules location update or a player update if there are any unsynced changes
   private static boolean scheduleSyncService() {
      boolean unsyncedChanges = OneSignalStateSynchronizer.persist();
      if (unsyncedChanges)
         OneSignalSyncServiceUtils.scheduleSyncTask(appContext);

      boolean locationScheduled = LocationController.scheduleUpdate(appContext);
      return locationScheduled || unsyncedChanges;
   }

   static void onAppFocus() {
      inForeground = true;

      // If the app gains focus and has not been set to NOTIFICATION_CLICK yet we can assume this is a normal app open
      if (!appEntryState.equals(AppEntryAction.NOTIFICATION_CLICK))
         appEntryState = AppEntryAction.APP_OPEN;

      LocationController.onFocusChange();

      if (OSUtils.shouldLogMissingAppIdError(appId))
         return;
      // Make sure remote param call has finish in order to know if privacyConsent is required
      if (!remoteParamController.isRemoteParamsCallDone()) {
         Log(LOG_LEVEL.DEBUG, "Delay onAppFocus logic due to missing remote params");
         makeAndroidParamsRequest(appId, getUserId());
         return;
      }

      onAppFocusLogic();
   }

   private static void onAppFocusLogic() {
      // Make sure without privacy consent, onAppFocus returns early
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("onAppFocus"))
         return;

      FocusTimeController.getInstance().appForegrounded();

      doSessionInit();

      if (trackGooglePurchase != null)
         trackGooglePurchase.trackIAP();

      OSNotificationRestoreWorkManager.beginEnqueueingWork(appContext, false);

      getCurrentPermissionState(appContext).refreshAsTo();

      if (trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled())
         trackFirebaseAnalytics.trackInfluenceOpenEvent();

      OneSignalSyncServiceUtils.cancelSyncTask(appContext);
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

   private static void registerUser() {
      Log(LOG_LEVEL.DEBUG,
         "registerUser:" +
         "registerForPushFired:" + registerForPushFired +
         ", locationFired: " + locationFired +
         ", remoteParams: " + getRemoteParams() +
         ", appId: " + appId
      );

      if (!registerForPushFired || !locationFired || getRemoteParams() == null || appId == null)
         return;

      new Thread(new Runnable() {
         public void run() {
            try {
               registerUserTask();
               OneSignalChromeTabAndroidFrame.setup(appId, userId, AdvertisingIdProviderGPS.getLastValue(), getRemoteParams());
            } catch(JSONException t) {
               Log(LOG_LEVEL.FATAL, "FATAL Error registering device!", t);
            }
         }
      }, "OS_REG_USER").start();
   }

   private static void registerUserTask() throws JSONException {
      String packageName = appContext.getPackageName();
      PackageManager packageManager = appContext.getPackageManager();

      JSONObject deviceInfo = new JSONObject();

      deviceInfo.put("app_id", getSavedAppId());

      if (getAdIdProvider() != null) {
         String adId = getAdIdProvider().getIdentifier(appContext);
         if (adId != null)
            deviceInfo.put("ad_id", adId);
      }
      deviceInfo.put("device_os", Build.VERSION.RELEASE);
      deviceInfo.put("timezone", getTimeZoneOffset());
      deviceInfo.put("language", OSUtils.getCorrectedLanguage());
      deviceInfo.put("sdk", VERSION);
      deviceInfo.put("sdk_type", sdkType);
      deviceInfo.put("android_package", packageName);
      deviceInfo.put("device_model", Build.MODEL);

      try {
         deviceInfo.put("game_version", packageManager.getPackageInfo(packageName, 0).versionCode);
      } catch (PackageManager.NameNotFoundException e) {}

      deviceInfo.put("net_type", osUtils.getNetType());
      deviceInfo.put("carrier", osUtils.getCarrierName());
      deviceInfo.put("rooted", RootToolsInternalMethods.isRooted());

      OneSignalStateSynchronizer.updateDeviceInfo(deviceInfo);

      JSONObject pushState = new JSONObject();
      pushState.put("identifier", lastRegistrationId);
      pushState.put("subscribableStatus", subscribableStatus);
      pushState.put("androidPermission", areNotificationsEnabledForSubscribedState());
      pushState.put("device_type", osUtils.getDeviceType());
      OneSignalStateSynchronizer.updatePushState(pushState);

      if (isLocationShared() && lastLocationPoint != null)
         OneSignalStateSynchronizer.updateLocation(lastLocationPoint);

      OneSignalStateSynchronizer.readyToUpdate(true);

      waitingToPostStateSync = false;
   }

   /**
    * @deprecated Please migrate to setEmail. This will be removed in next major release
    */
   @Deprecated
   public static void syncHashedEmail(final String email) {
      if (taskController.shouldQueueTaskForInit(OSTaskController.SYNC_HASHED_EMAIL)) {
         logger.error( "Waiting for remote params. " +
                 "Moving " + OSTaskController.SYNC_HASHED_EMAIL + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug( "Running " + OSTaskController.SYNC_HASHED_EMAIL + " operation from pending task queue.");
               syncHashedEmail(email);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.SYNC_HASHED_EMAIL))
         return;

      if (!OSUtils.isValidEmail(email)) {
         logger.error("syncHashedEmail called with an invalid email!, ignoring");
         return;
      }

      String trimmedEmail = email.trim();
      OneSignalStateSynchronizer.syncHashedEmail(trimmedEmail.toLowerCase());
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
      if (taskController.shouldQueueTaskForInit(OSTaskController.SET_EMAIL)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.SET_EMAIL + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.SET_EMAIL + " operation from a pending task queue.");
               setEmail(email, emailAuthHash, callback);
            }
         });
      }
      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.SET_EMAIL))
         return;

      if (!OSUtils.isValidEmail(email)) {
         String errorMessage = "Email is invalid";
         if (callback != null)
            callback.onFailure(new EmailUpdateError(EmailErrorType.VALIDATION, errorMessage));
         logger.error(errorMessage);
         return;
      }

      if (getRemoteParams().useEmailAuth && emailAuthHash == null) {
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
      if (taskController.shouldQueueTaskForInit(OSTaskController.LOGOUT_EMAIL)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.LOGOUT_EMAIL + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running  " + OSTaskController.LOGOUT_EMAIL + " operation from pending task queue.");
               logoutEmail(callback);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.LOGOUT_EMAIL))
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

   public static void setExternalUserId(@NonNull final String externalId) {
      setExternalUserId(externalId, null);
   }

   public static void setExternalUserId(@NonNull final String externalId, @Nullable final OSExternalUserIdUpdateCompletionHandler completionCallback) {
      if (taskController.shouldQueueTaskForInit(OSTaskController.SET_EXTERNAL_USER_ID)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.SET_EXTERNAL_USER_ID + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.SET_EXTERNAL_USER_ID + " operation from pending task queue.");
               setExternalUserId(externalId, completionCallback);
            }
         });
         return;
      }

      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.SET_EXTERNAL_USER_ID))
         return;

      if (externalId == null) {
         logger.warning("External id can't be null, set an empty string to remove an external id");
         return;
      }

      try {
         OneSignalStateSynchronizer.setExternalUserId(externalId, completionCallback);
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
      if (taskController.shouldQueueTaskForInit(OSTaskController.SEND_TAG)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.SEND_TAG + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.SEND_TAG + " operation from pending task queue.");
               sendTag(key, value);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.SEND_TAG))
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
      if (taskController.shouldQueueTaskForInit(OSTaskController.SEND_TAGS)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.SEND_TAGS + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.SEND_TAGS + " operation from pending task queue.");
               sendTags(keyValues, changeTagsUpdateHandler);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.SEND_TAGS))
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
      if (taskController.shouldRunTaskThroughQueue()) {
         logger.debug("Sending " + OSTaskController.SEND_TAGS + " operation to pending task queue.");
         taskController.addTaskToQueue(sendTagsRunnable);
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
    * @param getTagsHandler an instance of {@link GetTagsHandler}.
    *                       <br/>
    *                       Calls {@link GetTagsHandler#tagsAvailable(JSONObject) tagsAvailable} once the tags are available
    */
   public static void getTags(final GetTagsHandler getTagsHandler) {
      if (taskController.shouldQueueTaskForInit(OSTaskController.GET_TAGS)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.GET_TAGS + " operation to a pending queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.GET_TAGS + " operation from pending queue.");
               getTags(getTagsHandler);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.GET_TAGS))
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
               for (GetTagsHandler handler : pendingGetTagsHandlers) {
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

   public static void idsAvailable(final IdsAvailableHandler inIdsAvailableHandler) {
      if (taskController.shouldQueueTaskForInit(OSTaskController.IDS_AVAILABLE)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.IDS_AVAILABLE + " operation to a pending queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.IDS_AVAILABLE + " operation from pending queue.");
               idsAvailable(inIdsAvailableHandler);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.IDS_AVAILABLE))
         return;

      idsAvailableHandler = inIdsAvailableHandler;

      if (getUserId() != null) {
         OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
               internalFireIdsAvailableCallback();
            }
         });
      }
   }

   static void fireIdsAvailableCallback() {
      if (idsAvailableHandler != null) {
         OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
               internalFireIdsAvailableCallback();
            }
         });
      }
   }

   private synchronized static void internalFireIdsAvailableCallback() {
      if (idsAvailableHandler == null)
         return;

      String regId = OneSignalStateSynchronizer.getRegistrationId();
      if (!OneSignalStateSynchronizer.getSubscribed())
         regId = null;

      String userId = getUserId();
      if (userId == null)
         return;

      idsAvailableHandler.idsAvailable(userId, regId);

      if (regId != null)
         idsAvailableHandler = null;
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

         OneSignalRestClient.post("players/" + getUserId() + "/on_purchase", jsonBody, responseHandler);
         if (getEmailId() != null)
            OneSignalRestClient.post("players/" + getEmailId() + "/on_purchase", jsonBody, null);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for sendPurchases.", t);
      }
   }

   private static boolean openURLFromNotification(Context context, JSONArray dataArray) {

      //if applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(null))
         return false;

      int jsonArraySize = dataArray.length();

      boolean urlOpened = false;

      for (int i = 0; i < jsonArraySize; i++) {
         try {
            JSONObject data = dataArray.getJSONObject(i);
            if (!data.has("custom"))
               continue;

            JSONObject customJSON = new JSONObject(data.optString("custom"));

            if (customJSON.has("u")) {
               String url = customJSON.optString("u", null);
               if (url != null) {
                  OSUtils.openURLInBrowser(url);
                  urlOpened = true;
               }
            }
         } catch (Throwable t) {
            Log(LOG_LEVEL.ERROR, "Error parsing JSON item " + i + "/" + jsonArraySize + " for launching a web URL.", t);
         }
      }

      return urlOpened;
   }

   private static void runNotificationOpenedCallback(final JSONArray dataArray, final boolean shown) {
      if (notificationOpenedHandler == null) {
         unprocessedOpenedNotifs.add(dataArray);
         return;
      }

      fireNotificationOpenedHandler(generateOsNotificationOpenResult(dataArray, shown));
   }

   // Also called for received but OSNotification is extracted from it.
   @NonNull
   private static OSNotificationOpenResult generateOsNotificationOpenResult(JSONArray jsonArray, boolean shown) {
      int jsonArraySize = jsonArray.length();

      boolean firstMessage = true;
      boolean isAppInFocus = isAppActive();
      int androidNotificationId = jsonArray.optJSONObject(0).optInt(BUNDLE_KEY_ANDROID_NOTIFICATION_ID);
      OSNotificationDisplay displayOption = OSNotificationDisplay.NOTIFICATION;

      List<OSNotificationPayload> groupedNotifications = new ArrayList<>();
      String actionSelected = null;
      OSNotificationPayload payload = null;

      for (int i = 0; i < jsonArraySize; i++) {
         try {
            JSONObject data = jsonArray.getJSONObject(i);

            payload = NotificationBundleProcessor.OSNotificationPayloadFrom(data);
            if (actionSelected == null && data.has(BUNDLE_KEY_ACTION_ID))
               actionSelected = data.optString(BUNDLE_KEY_ACTION_ID, null);

            if (firstMessage)
               firstMessage = false;
            else {
               groupedNotifications.add(payload);
            }
         } catch (Throwable t) {
            Log(LOG_LEVEL.ERROR, "Error parsing JSON item " + i + "/" + jsonArraySize + " for callback.", t);
         }
      }

      OSNotificationAction.ActionType actionType = actionSelected != null ? OSNotificationAction.ActionType.ActionTaken : OSNotificationAction.ActionType.Opened;
      OSNotificationAction notificationAction = new OSNotificationAction(actionType, actionSelected);

      OSNotification notification = new OSNotification(groupedNotifications, payload, displayOption, isAppInFocus, shown, androidNotificationId);
      return new OSNotificationOpenResult(notification, notificationAction);
   }

   private static void fireNotificationOpenedHandler(final OSNotificationOpenResult openedResult) {
      // TODO: Is there a reason we need the opened handler to be fired from main thread?

      // TODO: Once the NotificationOpenedHandler gets a Worker, we should make sure we add a catch
      //    like we have implemented for the NotificationProcessingHandler and NotificationWillShowInForegroundHandlers
      OSUtils.runOnMainUIThread(new Runnable() {
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
   static void handleNotificationReceived(OSNotificationGenerationJob notificationJob, boolean displayed) {
      try {
         JSONObject jsonObject = new JSONObject(notificationJob.jsonPayload.toString());
         jsonObject.put(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notificationJob.getAndroidId());

         OSNotificationOpenResult openResult = generateOsNotificationOpenResult(newJsonArray(jsonObject), displayed);
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
    * @see NotificationWillShowInForegroundHandler
    */
   static boolean shouldFireForegroundHandlers() {
      if (!isInForeground()) {
         OneSignal.onesignalLog(LOG_LEVEL.INFO, "App is in background, show notification");
         return false;
      }

      if (notificationWillShowInForegroundHandler == null) {
         OneSignal.onesignalLog(LOG_LEVEL.INFO, "No NotificationWillShowInForegroundHandler setup, show notification");
         return false;
      }

      return true;
   }

   /**
    * Responsible for firing the notificationWillShowInForegroundHandler
    * <br/><br/>
    * @see NotificationWillShowInForegroundHandler
    */
   static void fireForegroundHandlers(OSNotificationGenerationJob notificationJob) {
      OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Fire notificationWillShowInForegroundHandler");
      AppNotificationGenerationJob appnotificationJob = notificationJob.toAppNotificationGenerationJob();
      try {
         OneSignal.notificationWillShowInForegroundHandler.notificationWillShowInForeground(appnotificationJob);
      } catch (Throwable t) {
         OneSignal.onesignalLog(LOG_LEVEL.ERROR, "Exception thrown while notification was being processed for display by notificationWillShowInForegroundHandler, showing notification in foreground!");
         appnotificationJob.complete();
         throw t;
      }
   }

   /**
    * Method called when opening a notification
    */
   public static void handleNotificationOpen(Context inContext, final JSONArray data, final boolean fromAlert, @Nullable final String notificationId) {
      // Delay call until remote params are set
      if (taskController.shouldQueueTaskForInit(OSTaskController.HANDLE_NOTIFICATION_OPEN)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.HANDLE_NOTIFICATION_OPEN + " operation to a pending queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               if (appContext != null) {
                  logger.debug("Running " + OSTaskController.HANDLE_NOTIFICATION_OPEN + " operation from pending queue.");
                  handleNotificationOpen(appContext, data, fromAlert, notificationId);
               }
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(null))
         return;

      notificationOpenedRESTCall(inContext, data);

      if (trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled())
         trackFirebaseAnalytics.trackOpenedEvent(generateOsNotificationOpenResult(data, true));

      boolean urlOpened = false;
      boolean defaultOpenActionDisabled = "DISABLE".equals(OSUtils.getManifestMeta(inContext, "com.onesignal.NotificationOpened.DEFAULT"));

      if (!defaultOpenActionDisabled)
         urlOpened = openURLFromNotification(inContext, data);

      // Check if the notification click should lead to a DIRECT session
      if (shouldInitDirectSessionFromNotificationOpen(inContext, fromAlert, urlOpened, defaultOpenActionDisabled)) {
         // We want to set the app entry state to NOTIFICATION_CLICK when coming from background
         appEntryState = AppEntryAction.NOTIFICATION_CLICK;
         sessionManager.onDirectInfluenceFromNotificationOpen(appEntryState, notificationId);
      }

      runNotificationOpenedCallback(data, true);
   }

   static boolean startOrResumeApp(Context inContext) {
      Intent launchIntent = inContext.getPackageManager().getLaunchIntentForPackage(inContext.getPackageName());
      // Make sure we have a launcher intent.
      if (launchIntent != null) {
         launchIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
         inContext.startActivity(launchIntent);
         return true;
      }
      return false;
   }

   /**
    * 1. App is not an alert
    * 2. Not a URL open
    * 3. Manifest setting for com.onesignal.NotificationOpened.DEFAULT is not disabled
    * 4. App is coming from the background
    * 5. App open/resume intent exists
    */
   private static boolean shouldInitDirectSessionFromNotificationOpen(Context context, boolean fromAlert, boolean urlOpened, boolean defaultOpenActionDisabled) {
      return !fromAlert
              && !urlOpened
              && !defaultOpenActionDisabled
              && !inForeground
              && startOrResumeApp(context);
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
      if (TextUtils.isEmpty(emailId) && appContext != null) {
         emailId = OneSignalPrefs.getString(
                 OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_OS_EMAIL_ID,
                 null);
      }
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

   // Called when a player id is returned from OneSignal
   // Updates anything else that might have been waiting for this id.
   static void updateUserIdDependents(String userId) {
      saveUserId(userId);
      fireIdsAvailableCallback();
      internalFireGetTagsCallbacks();

      getCurrentSubscriptionState(appContext).setUserId(userId);

      if (iapUpdateJob != null) {
         sendPurchases(iapUpdateJob.toReport, iapUpdateJob.newAsExisting, iapUpdateJob.restResponseHandler);
         iapUpdateJob = null;
      }

      OneSignalStateSynchronizer.refreshEmailState();

      OneSignalChromeTabAndroidFrame.setup(appId, userId, AdvertisingIdProviderGPS.getLastValue(), getRemoteParams());
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

   static boolean isLocationShared() {
      return remoteParamController.isLocationShared();
   }

   static boolean isUserPrivacyConsentRequired() {
      return remoteParamController.isPrivacyConsentRequired();
   }
   // End Remote params getters

   // If true(default) - Device will always vibrate unless the device is in silent mode.
   // If false - Device will only vibrate when the device is set on it's vibrate only mode.
   /**
    * By default OneSignal always vibrates the device when a notification is displayed unless the
    * device is in a total silent mode.
    * <br/><br/>
    * <i>You can link this action to a UI button to give your user a vibration option for your notifications.</i>
    * @param enable Passing {@code false} means that the device will only vibrate lightly when the device is in it's vibrate only mode.
    */
   public static void enableVibrate(boolean enable) {
      if (appContext == null) {
         logger.error("enableVibrate called before initWithContext!");
         return;
      }

      OneSignalPrefs.saveBool(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_VIBRATE_ENABLED,
              enable);
   }

   static boolean getVibrate() {
      return OneSignalPrefs.getBool(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_VIBRATE_ENABLED,
              true);
   }

   // If true(default) - Sound plays when receiving notification. Vibrates when device is on vibrate only mode.
   // If false - Only vibrates unless EnableVibrate(false) was set.
   /**
    * By default OneSignal plays the system's default notification sound when the
    * device's notification system volume is turned on.
    * <br/><br/>
    * <i>You can link this action to a UI button to give your user a different sound option for your notifications.</i>
    * @param enable Passing {@code false} means that the device will only vibrate unless the device is set to a total silent mode.
    */
   public static void enableSound(boolean enable) {
      if (appContext == null) {
         logger.error("enableSound called before initWithContext!");
         return;
      }

      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_SOUND_ENABLED,enable);
   }

   static boolean getSoundEnabled() {
      return OneSignalPrefs.getBool(
              OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_SOUND_ENABLED,
              true);
   }

   static void setLastSessionTime(long time) {
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
    * You can call this method with {@code false} to opt users out of receiving all notifications through
    * OneSignal. You can pass {@code true} later to opt users back into notifications.
    * @param disable whether to subscribe the user to notifications or not
    */
   public static void disablePush(final boolean disable) {
      if (taskController.shouldQueueTaskForInit(OSTaskController.SET_SUBSCRIPTION)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.SET_SUBSCRIPTION + " operation to a pending queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.SET_SUBSCRIPTION + " operation from pending queue.");
               disablePush(disable);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.SET_SUBSCRIPTION))
         return;

      getCurrentSubscriptionState(appContext).setUserSubscriptionSetting(disable);
      OneSignalStateSynchronizer.setSubscription(disable);
   }

   /**
    * This method will be replaced by remote params set
    */
   public static void setLocationShared(final boolean enable) {
      if (taskController.shouldQueueTaskForInit(OSTaskController.SET_LOCATION_SHARED)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.SET_LOCATION_SHARED + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.SET_LOCATION_SHARED + " operation from pending task queue.");
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
      logger.debug("OneSignal is shareLocation enabled: " + enable);
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
      if (taskController.shouldQueueTaskForInit(OSTaskController.PROMPT_LOCATION)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.PROMPT_LOCATION + " operation to a pending queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.PROMPT_LOCATION + " operation from pending queue.");
               promptLocation(callback, fallbackToSettings);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.PROMPT_LOCATION))
         return;

      LocationController.LocationHandler locationHandler = new LocationController.LocationPromptCompletionHandler() {
         @Override
         public LocationController.PermissionType getType() {
            return LocationController.PermissionType.PROMPT_LOCATION;
         }

         @Override
         public void onComplete(LocationController.LocationPoint point) {
            //if applicable, check if the user provided privacy consent
            if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.PROMPT_LOCATION))
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
      promptedLocation = true;
   }

   /**
    * Removes all OneSignal notifications from the Notification Shade. If you just use
    * {@link NotificationManager#cancelAll()}, OneSignal notifications will be restored when
    * your app is restarted.
    */
   public static void clearOneSignalNotifications() {
      Runnable runClearOneSignalNotifications = new Runnable() {
         @Override
         public void run() {
            NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(appContext);

            OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(appContext);
            Cursor cursor = null;
            try {
               SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

               String[] retColumn = {OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID};

               cursor = readableDb.query(
                       OneSignalDbContract.NotificationTable.TABLE_NAME,
                       retColumn,
                       OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                               OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0",
                       null,
                       null,                                                    // group by
                       null,                                                    // filter by row groups
                       null                                                     // sort order
               );

               if (cursor.moveToFirst()) {
                  do {
                     int existingId = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
                     notificationManager.cancel(existingId);
                  } while (cursor.moveToNext());
               }


               // Mark all notifications as dismissed unless they were already opened.
               SQLiteDatabase writableDb = null;
               try {
                  writableDb = dbHelper.getSQLiteDatabaseWithRetries();
                  writableDb.beginTransaction();

                  String whereStr = NotificationTable.COLUMN_NAME_OPENED + " = 0";
                  ContentValues values = new ContentValues();
                  values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);
                  writableDb.update(NotificationTable.TABLE_NAME, values, whereStr, null);
                  writableDb.setTransactionSuccessful();
               } catch (Throwable t) {
                  OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error marking all notifications as dismissed! ", t);
               } finally {
                  if (writableDb != null) {
                     try {
                        writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                     } catch (Throwable t) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
                     }
                  }
               }

               BadgeCountUpdater.updateCount(0, appContext);
            } catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error canceling all notifications! ", t);
            } finally {
               if (cursor != null)
                  cursor.close();
            }
         }
      };

      // DB access is a heavy task, dispatch to a thread if running on main thread
      if (OSUtils.isRunningOnMainThread())
         new Thread(runClearOneSignalNotifications, "OS_NOTIFICATIONS").start();
      else
         runClearOneSignalNotifications.run();
   }

   /**
    * Cancels a single OneSignal notification based on its Android notification integer ID. Use
    * instead of Android's {@link NotificationManager#cancel(int)}, otherwise the notification will be restored
    * when your app is restarted.
    * @param id
    */
   public static void cancelNotification(final int id) {
      Runnable runCancelNotification = new Runnable() {
         @Override
         public void run() {
            OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(appContext);
            SQLiteDatabase writableDb = null;
            try {
               writableDb = dbHelper.getSQLiteDatabaseWithRetries();
               writableDb.beginTransaction();

               String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + id + " AND " +
                       NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                       NotificationTable.COLUMN_NAME_DISMISSED + " = 0";

               ContentValues values = new ContentValues();
               values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

               int records = writableDb.update(NotificationTable.TABLE_NAME, values, whereStr, null);

               if (records > 0)
                  NotificationSummaryManager.updatePossibleDependentSummaryOnDismiss(appContext, writableDb, id);
               BadgeCountUpdater.update(writableDb, appContext);

               writableDb.setTransactionSuccessful();
            } catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error marking a notification id " + id + " as dismissed! ", t);
            } finally {
               if (writableDb != null) {
                  try {
                     writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                  } catch (Throwable t) {
                     OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
                  }
               }
            }

            NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(appContext);
            notificationManager.cancel(id);
         }
      };

      // DB access is a heavy task, dispatch to a thread if running on main thread
      if (OSUtils.isRunningOnMainThread())
         new Thread(runCancelNotification, "OS_NOTIFICATIONS").start();
      else
         runCancelNotification.run();
   }


   public static void cancelGroupedNotifications(final String group) {
      if (taskController.shouldQueueTaskForInit(OSTaskController.CANCEL_GROUPED_NOTIFICATIONS)) {
         logger.error("Waiting for remote params. " +
                 "Moving " + OSTaskController.CANCEL_GROUPED_NOTIFICATIONS + " operation to a pending queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.CANCEL_GROUPED_NOTIFICATIONS + " operation from pending queue.");
               cancelGroupedNotifications(group);
            }
         });
         return;
      }

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName(OSTaskController.CANCEL_GROUPED_NOTIFICATIONS))
         return;

      Runnable runCancelGroupedNotifications = new Runnable() {
         @Override
         public void run() {
            NotificationManager notificationManager = OneSignalNotificationManager.getNotificationManager(appContext);

            OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(appContext);
            Cursor cursor = null;

            try {
               SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

               String[] retColumn = { NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID };

               String whereStr =  NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
                       NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                       NotificationTable.COLUMN_NAME_OPENED + " = 0";
               String[] whereArgs = { group };

               cursor = readableDb.query(
                       NotificationTable.TABLE_NAME,
                       retColumn,
                       whereStr,
                       whereArgs,
                       null, null, null);

               while (cursor.moveToNext()) {
                  int notifId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
                  if (notifId != -1)
                     notificationManager.cancel(notifId);
               }
            }
            catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error getting android notifications part of group: " + group, t);
            }
            finally {
               if (cursor != null && !cursor.isClosed())
                  cursor.close();
            }

            SQLiteDatabase writableDb = null;
            try {
               writableDb = dbHelper.getSQLiteDatabaseWithRetries();
               writableDb.beginTransaction();

               String whereStr = NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
                       NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                       NotificationTable.COLUMN_NAME_DISMISSED + " = 0";
               String[] whereArgs = { group };

               ContentValues values = new ContentValues();
               values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

               writableDb.update(NotificationTable.TABLE_NAME, values, whereStr, whereArgs);
               BadgeCountUpdater.update(writableDb, appContext);

               writableDb.setTransactionSuccessful();
            } catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error marking a notifications with group " + group + " as dismissed! ", t);
            } finally {
               if (writableDb != null) {
                  try {
                     writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                  } catch (Throwable t) {
                     OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
                  }
               }
            }
         }
      };

      // DB access is a heavy task, dispatch to a thread if running on main thread
      if (OSUtils.isRunningOnMainThread())
         new Thread(runCancelGroupedNotifications, "OS_NOTIFICATIONS").start();
      else
         runCancelGroupedNotifications.run();
   }

   public static void removeNotificationWillShowInForegroundHandler() {
      OneSignal.notificationWillShowInForegroundHandler = null;
   }

   public static void removeNotificationOpenedHandler() {
      notificationOpenedHandler = null;
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
    * Get the current notification and permission state.
    *<br/><br/>
    * {@code permissionStatus} - {@link OSPermissionState} - Android Notification Permissions State
    * <br/>
    * {@code subscriptionStatus} - {@link OSSubscriptionState} - Google and OneSignal subscription state
    * <br/>
    * {@code emailSubscriptionStatus} - {@link OSEmailSubscriptionState} - Email subscription state
    * @return a {@link OSPermissionSubscriptionState} as described above
    */
   public static OSPermissionSubscriptionState getPermissionSubscriptionState() {

      // If applicable, check if the user provided privacy consent
      if (shouldLogUserPrivacyConsentErrorMessageForMethodName("getPermissionSubscriptionState()"))
         return null;

      if (appContext == null) {
         logger.error("OneSignal.initWithContext has not been called. Could not get OSPermissionSubscriptionState");
         return null;
      }

      OSPermissionSubscriptionState status = new OSPermissionSubscriptionState();
      status.subscriptionStatus = getCurrentSubscriptionState(appContext);
      status.permissionStatus = getCurrentPermissionState(appContext);
      status.emailSubscriptionStatus = getCurrentEmailSubscriptionState(appContext);

      return status;
   }

   /** In-App Message Triggers */

   /**
    * Allows you to set multiple trigger key/value pairs simultaneously with a Map
    * Triggers are used for targeting in-app messages.
    */
   public static void addTriggers(Map<String, Object> triggers) {
      OSInAppMessageController.getController(logger).addTriggers(triggers);
   }

   /**
    * Allows you to set multiple trigger key/value pairs simultaneously with a JSON String
    * Triggers are used for targeting in-app messages.
    */
   public static void addTriggersFromJsonString(String triggersJsonString) {
      try {
         JSONObject jsonObject = new JSONObject(triggersJsonString);
         addTriggers(JSONUtils.jsonObjectToMap(jsonObject));
      } catch (JSONException e) {
         logger.error("addTriggersFromJsonString, invalid json", e);
      }
   }

   /**
    * Allows you to set an individual trigger key/value pair for in-app message targeting
    */
   public static void addTrigger(String key, Object object) {
      HashMap<String, Object> triggerMap = new HashMap<>();
      triggerMap.put(key, object);

      OSInAppMessageController.getController(logger).addTriggers(triggerMap);
   }

   /** Removes a list/collection of triggers from their keys with a Collection of Strings */
   public static void removeTriggersForKeys(Collection<String> keys) {
      OSInAppMessageController.getController(logger).removeTriggersForKeys(keys);
   }

   /** Removes a list/collection of triggers from their keys with a JSONArray String.
    *  Only String types are used, other types in the array will be ignored. */
   public static void removeTriggersForKeysFromJsonArrayString(@NonNull String keys) {
      try {
         JSONArray jsonArray = new JSONArray(keys);
         Collection<String> keysCollection = OSUtils.extractStringsFromCollection(
            JSONUtils.jsonArrayToList(jsonArray)
         );
         // Some keys were filtered, log as warning
         if (jsonArray.length() != keysCollection.size())
            logger.warning("removeTriggersForKeysFromJsonArrayString: Skipped removing non-String type keys ");
         OSInAppMessageController.getController(logger).removeTriggersForKeys(keysCollection);
      } catch (JSONException e) {
         logger.error("removeTriggersForKeysFromJsonArrayString, invalid json", e);
      }
   }

   /** Removes a single trigger for the given key */
   public static void removeTriggerForKey(String key) {
      ArrayList<String> triggerKeys = new ArrayList<>();
      triggerKeys.add(key);

      OSInAppMessageController.getController(logger).removeTriggersForKeys(triggerKeys);
   }

   /** Returns a single trigger value for the given key (if it exists, otherwise returns null) */
   @Nullable
   public static Object getTriggerValueForKey(String key) {
      return OSInAppMessageController.getController(logger).getTriggerValue(key);
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
                 "Moving " + OSTaskController.PAUSE_IN_APP_MESSAGES + " operation to a pending task queue.");
         taskController.addTaskToQueue(new Runnable() {
            @Override
            public void run() {
               logger.debug("Running " + OSTaskController.PAUSE_IN_APP_MESSAGES + " operation from pending queue.");
               pauseInAppMessages(pause);
            }
         });
         return;
      }

      OSInAppMessageController.getController(logger).setInAppMessagingEnabled(!pause);
   }

   private static boolean isDuplicateNotification(Context context, String id) {
      if (id == null || "".equals(id))
         return false;

      boolean exists = false;

      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
      Cursor cursor = null;

      try {
         SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();

         String[] retColumn = {NotificationTable.COLUMN_NAME_NOTIFICATION_ID};
         String[] whereArgs = {id};

         cursor = readableDb.query(
                 NotificationTable.TABLE_NAME,
                 retColumn,
                 NotificationTable.COLUMN_NAME_NOTIFICATION_ID + " = ?",
                 whereArgs,
                 null,
                 null,
                 null);

         exists = cursor.moveToFirst();
      }
      catch (Throwable t) {
         logger.error("Could not check for duplicate, assuming unique.", t);
      }
      finally {
         if (cursor != null)
            cursor.close();
      }

      if (exists) {
         logger.debug("Duplicate FCM message received, skip processing of " + id);
         return true;
      }

      return false;
   }

   static boolean notValidOrDuplicated(Context context, JSONObject jsonPayload) {
      String id = OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload);
      return id == null || OneSignal.isDuplicateNotification(context, id);
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

    static String getNotificationIdFromFCMBundle(@Nullable Bundle fcmBundle) {
        if (fcmBundle == null || fcmBundle.isEmpty())
            return null;

      try {
         if (fcmBundle.containsKey("custom")) {
            JSONObject customJSON = new JSONObject(fcmBundle.getString("custom"));

            if (customJSON.has("i"))
               return customJSON.optString("i", null);
            else
               Log(LOG_LEVEL.DEBUG, "Not a OneSignal formatted FCM message. No 'i' field in custom.");
         }
         else
            Log(LOG_LEVEL.DEBUG, "Not a OneSignal formatted FCM message. No 'custom' field in the bundle.");
      } catch (Throwable t) {
         Log(LOG_LEVEL.DEBUG, "Could not parse bundle, probably not a OneSignal notification.", t);
      }

      return null;
   }

   private static String getNotificationIdFromFCMJsonPayload(JSONObject fcmJson) {
      try {
         JSONObject customJSON = new JSONObject(fcmJson.optString("custom"));
         return customJSON.optString("i", null);
      } catch(Throwable t) {}
      return null;
   }

   static boolean isAppActive() {
      return initDone && isInForeground();
   }

   private static boolean isPastOnSessionTime() {
      return (OneSignal.getTime().getCurrentTimeMillis() - getLastSessionTime()) >= MIN_ON_SESSION_TIME_MILLIS;
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
      OneSignal.trackerFactory = trackerFactory;
   }

   static void setSessionManager(OSSessionManager sessionManager) {
      OneSignal.sessionManager = sessionManager;
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

   static OSTaskController getTaskController() {
      return taskController;
   }
   /*
    * End Mock Injection module
    */

   /*
    * Start OneSignalOutcome module
    */
   static OSSessionManager getSessionManager() {
      return sessionManager;
   }

   static void sendClickActionOutcomes(@NonNull List<OSInAppMessageOutcome> outcomes) {
      if (outcomeEventsController == null) {
         OneSignal.Log(LOG_LEVEL.ERROR, "Make sure OneSignal.init is called first");
         return;
      }

      outcomeEventsController.sendClickActionOutcomes(outcomes);
   }

   public static void sendOutcome(@NonNull String name) {
      sendOutcome(name, null);
   }

   public static void sendOutcome(@NonNull String name, OutcomeCallback callback) {
      if (!isValidOutcomeEntry(name)) {
         logger.error("Make sure OneSignal initWithContext and setAppId is called first");
         return;
      }

      if (outcomeEventsController == null) {
         logger.error("Make sure OneSignal initWithContext and setAppId is called first");
         return;
      }

      outcomeEventsController.sendOutcomeEvent(name, callback);
   }

   public static void sendUniqueOutcome(@NonNull String name) {
      sendUniqueOutcome(name, null);
   }

   public static void sendUniqueOutcome(@NonNull String name, OutcomeCallback callback) {
      if (!isValidOutcomeEntry(name))
         return;

      if (outcomeEventsController == null) {
         logger.error("Make sure OneSignal initWithContext and setAppId is called first");
         return;
      }

      outcomeEventsController.sendUniqueOutcomeEvent(name, callback);
   }

   public static void sendOutcomeWithValue(@NonNull String name, float value) {
      sendOutcomeWithValue(name, value, null);
   }

   public static void sendOutcomeWithValue(@NonNull String name, float value, OutcomeCallback callback) {
      if (!isValidOutcomeEntry(name) || !isValidOutcomeValue(value))
         return;

      if (outcomeEventsController == null) {
         logger.error("Make sure OneSignal.init is called first");
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
    */
   public interface OutcomeCallback {
      void onSuccess(@Nullable OutcomeEvent outcomeEvent);
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
