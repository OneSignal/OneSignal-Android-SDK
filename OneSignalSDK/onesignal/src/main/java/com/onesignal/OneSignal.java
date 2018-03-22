/**
 * Modified MIT License
 * 
 * Copyright 2018 OneSignal
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.json.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Base64;
import android.util.Log;

import com.onesignal.OneSignalDbContract.NotificationTable;

/**
 * The main OneSignal class - this is where you will interface with the OneSignal SDK
 * <br/><br/>
 * <b>Reminder:</b> Add your {@code onesignal_app_id} to your build.gradle config in <i>android</i> > <i>defaultConfig</i>
 * <br/>
 * @see <a href="https://documentation.onesignal.com/docs/android-sdk-setup#section-1-gradle-setup">OneSignal Gradle Setup</a>
 */
public class OneSignal {

   public enum LOG_LEVEL {
      NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
   }

   public enum OSInFocusDisplayOption {
      None, InAppAlert, Notification
   }

   static final long MIN_ON_FOCUS_TIME = 60;
   private static final long MIN_ON_SESSION_TIME = 30;

   /**
    * An interface used to process a OneSignal notification the user just tapped on.
    * <br/>
    * Set this during OneSignal init in
    * {@link OneSignal.Builder#setNotificationOpenedHandler(NotificationOpenedHandler) setNotificationOpenedHandler}
    *<br/><br/>
    * @see <a href="https://documentation.onesignal.com/docs/android-native-sdk#section--notificationopenedhandler-">NotificationOpenedHandler | OneSignal Docs</a>
    */
   public interface NotificationOpenedHandler {
      /**
       * Fires when a user taps on a notification.
       * @param result a {@link OSNotificationOpenResult} with the user's response and properties of this notification
       */
      void notificationOpened(OSNotificationOpenResult result);
   }

   /**
    * An interface used to handle notifications that are received.
    * <br/>
    * Set this during OneSignal init in
    * {@link OneSignal.Builder#setNotificationReceivedHandler(NotificationReceivedHandler) setNotificationReceivedHandler}
    *<br/><br/>
    * @see <a href="https://documentation.onesignal.com/docs/android-native-sdk#section--notificationreceivedhandler-">NotificationReceivedHandler | OneSignal Docs</a>
    */
   public interface NotificationReceivedHandler {
      /**
       * Fires when a notification is received. It will be fired when your app is in focus or
       * in the background.
       * @param notification Contains both the user's response and properties of the notification
       */
      void notificationReceived(OSNotification notification);
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

   public static class Builder {
      Context mContext;
      NotificationOpenedHandler mNotificationOpenedHandler;
      NotificationReceivedHandler mNotificationReceivedHandler;
      boolean mPromptLocation;
      boolean mDisableGmsMissingPrompt;
      // Default true in 4.0.0 release.
      boolean mUnsubscribeWhenNotificationsAreDisabled;
      boolean mFilterOtherGCMReceivers;

      // Exists to make wrapper SDKs simpler so they don't need to store their own variable before
      //  calling startInit().init()
      // mDisplayOptionCarryOver is used if setInFocusDisplaying is called but inFocusDisplaying wasn't
      boolean mDisplayOptionCarryOver;
      // Default Notification in 4.0.0 release.
      OSInFocusDisplayOption mDisplayOption = OSInFocusDisplayOption.InAppAlert;
   
      private Builder() {}

      private Builder(Context context) {
         mContext = context;
      }

      private void setDisplayOptionCarryOver(boolean carryOver) {
         mDisplayOptionCarryOver = carryOver;
      }

      /**
       * Sets a notification opened handler. The instance will be called when a notification
       * is tapped on from the notification shade or when closing an Alert notification shown in the app.
       * <br/><br/>
       * See the
       * <a href="https://documentation.onesignal.com/docs/android-native-sdk#section--notificationopenedhandler-">
       *     NotificationOpenedHandler
       * </a> documentation for an example of the {@code ExampleNotificationOpenedHandler} class.
       *
       *
       * @param handler Instance of a class implementing the {@link NotificationOpenedHandler} interface
       * @return the builder on which you called this method
       */
      public Builder setNotificationOpenedHandler(NotificationOpenedHandler handler) {
         mNotificationOpenedHandler = handler;
         return this;
      }

      /**
       * Sets a notification received handler that will fire when a notification is received. It will
       * be fired when your app is in focus or in the background.
       * <br/><br/>
       * See the
       * <a href="https://documentation.onesignal.com/docs/android-native-sdk#section--notificationreceivedhandler-">
       *     NotificationReceivedHandler
       * </a> documentation for an example of the {@code ExampleNotificationReceivedHandler} class.
       *
       * @param handler Instance of a class implementing the {@link NotificationReceivedHandler} interface
       * @return the builder on which you called this method
       */
      public Builder setNotificationReceivedHandler(NotificationReceivedHandler handler) {
         mNotificationReceivedHandler = handler;
         return this;
      }

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
      public Builder autoPromptLocation(boolean enable) {
         mPromptLocation = enable;
         return this;
      }

      /**
       * Prompts the user to update/enable Google Play Services if it's disabled on the device.
       *
       * @param disable if {@code false}, prompt users. if {@code true}, never show the out of date prompt.
       *                Default is {@code false}
       * @return
       */
      public Builder disableGmsMissingPrompt(boolean disable) {
         mDisableGmsMissingPrompt = disable;
         return this;
      }

      public Builder inFocusDisplaying(OSInFocusDisplayOption displayOption) {
         getCurrentOrNewInitBuilder().mDisplayOptionCarryOver = false;
         mDisplayOption = displayOption;
         return this;
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
      public Builder unsubscribeWhenNotificationsAreDisabled(boolean set) {
         mUnsubscribeWhenNotificationsAreDisabled = set;
         return this;
      }

      /**
       * Enable to prevent other broadcast receivers from receiving OneSignal FCM/GCM payloads.
       * Prevent thrown exceptions or double notifications from other libraries/SDKs that implement
       * notifications. Other non-OneSignal payloads will still be passed through so your app can
       * handle FCM/GCM payloads from other back-ends.
       * <br/><br/>
       * <b>Note:</b> You can't use multiple
       * Google Project numbers/Sender IDs. They must be the same if you are using multiple providers,
       * otherwise there will be unexpected subscribes.
       * @param set
       * @return
       */
      public Builder filterOtherGCMReceivers(boolean set) {
         mFilterOtherGCMReceivers = set;
         return this;
      }

      public void init() {
         OneSignal.init(this);
      }
   }

   static String appId;
   private static String mGoogleProjectNumber;
   private static boolean mGoogleProjectNumberIsRemote;
   static Context appContext;
   
   private static LOG_LEVEL visualLogLevel = LOG_LEVEL.NONE;
   private static LOG_LEVEL logCatLevel = LOG_LEVEL.WARN;

   private static String userId = null, emailId = null;
   private static int subscribableStatus;

   static boolean initDone;
   private static boolean foreground;

   // the concurrent queue in which we pin pending tasks upon finishing initialization
   static ExecutorService pendingTaskExecutor;
   public static ConcurrentLinkedQueue<Runnable> taskQueueWaitingForInit = new ConcurrentLinkedQueue<>();
   static AtomicLong lastTaskId = new AtomicLong();

   private static IdsAvailableHandler idsAvailableHandler;

   private static long lastTrackedFocusTime = 1, unSentActiveTime = -1;

   private static TrackGooglePurchase trackGooglePurchase;
   private static TrackAmazonPurchase trackAmazonPurchase;
   private static TrackFirebaseAnalytics trackFirebaseAnalytics;

   public static final String VERSION = "030803";

   private static AdvertisingIdentifierProvider mainAdIdProvider = new AdvertisingIdProviderGPS();

   private static int deviceType;
   @SuppressWarnings("WeakerAccess")
   public static String sdkType = "native";

   private static OSUtils osUtils;

   private static String lastRegistrationId;
   private static boolean registerForPushFired, locationFired, awlFired, promptedLocation;
   
   private static LocationGMS.LocationPoint lastLocationPoint;
   
   static boolean shareLocation = true;
   static OneSignal.Builder mInitBuilder;

   private static Collection<JSONArray> unprocessedOpenedNotifis = new ArrayList<>();
   private static HashSet<String> postedOpenedNotifIds = new HashSet<>();

   private static GetTagsHandler pendingGetTagsHandler;
   private static boolean getTagsCall;

   private static boolean waitingToPostStateSync;
   private static boolean sendAsSession;

   private static JSONObject awl;
   static boolean mEnterp;
   private static boolean useEmailAuth;
   
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
   
   
   private static class IAPUpdateJob {
      JSONArray toReport;
      boolean newAsExisting;
      OneSignalRestClient.ResponseHandler restResponseHandler;
   
      IAPUpdateJob(JSONArray toReport) {
         this.toReport = toReport;
      }
   }
   private static IAPUpdateJob iapUpdateJob;
   
   public static OneSignal.Builder getCurrentOrNewInitBuilder() {
      if (mInitBuilder == null)
         mInitBuilder = new OneSignal.Builder();
      return mInitBuilder;
   }

   /**
    * Initializes OneSignal to register the device for push notifications.
    *<br/>
    * Call this first from your application class' {@code onCreate} method
    *<br/><br/>
    * <i>Don't have a class that extends Application in your project?</i>
    * <br/>Follow <a href="https://www.mobomo.com/2011/05/how-to-use-application-object-of-android/">this tutorial</a> to create one.
    * @see <a href="https://documentation.onesignal.com/docs/android-sdk-setup#section-2-add-required-code">Initializing OneSignal</a>
    * @param context The application context
    * @return a {@link OneSignal.Builder} instance to begin building the OneSignal instance
    */
   public static OneSignal.Builder startInit(Context context) {
      return new OneSignal.Builder(context);
   }

   /**
    * Initializes Onesignal to register the device for push notifications.
    * Should be called upon a {@link OneSignal.Builder} instance after you've defined options on it.
    * <br/><br/>
    * Refer to {@link #startInit(Context)}
    * @param inBuilder
    */
   private static void init(OneSignal.Builder inBuilder) {
      if (getCurrentOrNewInitBuilder().mDisplayOptionCarryOver)
         inBuilder.mDisplayOption = getCurrentOrNewInitBuilder().mDisplayOption;
      mInitBuilder = inBuilder;

      Context context = mInitBuilder.mContext;
      mInitBuilder.mContext = null; // Clear to prevent leaks.

      try {
         ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
         Bundle bundle = ai.metaData;

         String sender_id = bundle.getString("onesignal_google_project_number");
         if (sender_id != null && sender_id.length() > 4)
            sender_id = sender_id.substring(4);

         OneSignal.init(context, sender_id, bundle.getString("onesignal_app_id"), mInitBuilder.mNotificationOpenedHandler, mInitBuilder.mNotificationReceivedHandler);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   public static void init(Context context, String googleProjectNumber, String oneSignalAppId) {
      init(context, googleProjectNumber, oneSignalAppId, null, null);
   }

   public static void init(Context context, String googleProjectNumber, String oneSignalAppId, NotificationOpenedHandler notificationOpenedHandler) {
      init(context, googleProjectNumber, oneSignalAppId, notificationOpenedHandler, null);
   }

   public static void init(Context context, String googleProjectNumber, String oneSignalAppId, NotificationOpenedHandler notificationOpenedHandler, NotificationReceivedHandler notificationReceivedHandler) {
      mInitBuilder = getCurrentOrNewInitBuilder();
      mInitBuilder.mDisplayOptionCarryOver = false;
      mInitBuilder.mNotificationOpenedHandler = notificationOpenedHandler;
      mInitBuilder.mNotificationReceivedHandler = notificationReceivedHandler;
      if (!mGoogleProjectNumberIsRemote)
         mGoogleProjectNumber = googleProjectNumber;

      osUtils = new OSUtils();
      deviceType = osUtils.getDeviceType();
      subscribableStatus = osUtils.initializationChecker(context, deviceType, oneSignalAppId);
      if (subscribableStatus == OSUtils.UNINITIALIZABLE_STATUS)
         return;

      if (initDone) {
         if (context != null)
            appContext = context.getApplicationContext();

         if (mInitBuilder.mNotificationOpenedHandler != null)
            fireCallbackForOpenedNotifications();

         return;
      }

      boolean contextIsActivity = (context instanceof Activity);

      foreground = contextIsActivity;
      appId = oneSignalAppId;
      appContext = context.getApplicationContext();
   
      saveFilterOtherGCMReceivers(mInitBuilder.mFilterOtherGCMReceivers);

      if (contextIsActivity) {
         ActivityLifecycleHandler.curActivity = (Activity) context;
         NotificationRestorer.asyncRestore(appContext);
      }
      else
         ActivityLifecycleHandler.nextResumeIsFirstActivity = true;

      lastTrackedFocusTime = SystemClock.elapsedRealtime();

      OneSignalStateSynchronizer.initUserState();
      
      ((Application)appContext).registerActivityLifecycleCallbacks(new ActivityLifecycleListener());

      try {
         Class.forName("com.amazon.device.iap.PurchasingListener");
         trackAmazonPurchase = new TrackAmazonPurchase(appContext);
      } catch (ClassNotFoundException e) {}

      // Re-register user if the app id changed, this might happen when a dev is testing.
      String oldAppId = getSavedAppId();
      if (oldAppId != null) {
         if (!oldAppId.equals(appId)) {
            Log(LOG_LEVEL.DEBUG, "APP ID changed, clearing user id as it is no longer valid.");
            SaveAppId(appId);
            OneSignalStateSynchronizer.resetCurrentState();
         }
      }
      else {
         BadgeCountUpdater.updateCount(0, appContext);
         SaveAppId(appId);
      }
   
      OSPermissionChangedInternalObserver.handleInternalChanges(getCurrentPermissionState(appContext));

      if (foreground || getUserId() == null) {
         sendAsSession = isPastOnSessionTime();
         setLastSessionTime(System.currentTimeMillis());
         startRegistrationOrOnSession();
      }

      if (mInitBuilder.mNotificationOpenedHandler != null)
         fireCallbackForOpenedNotifications();

      if (TrackGooglePurchase.CanTrack(appContext))
         trackGooglePurchase = new TrackGooglePurchase(appContext);

      if (TrackFirebaseAnalytics.CanTrack())
         trackFirebaseAnalytics = new TrackFirebaseAnalytics(appContext);
      
      initDone = true;

      //clean up any pending tasks that were queued up before initialization
      startPendingTasks();
   }

   private static void onTaskRan(long taskId) {
      if(lastTaskId.get() == taskId) {
         OneSignal.Log(LOG_LEVEL.INFO,"Last Pending Task has ran, shutting down");
         pendingTaskExecutor.shutdown();
      }
   }

   private static class PendingTaskRunnable implements Runnable {
      private Runnable innerTask;
      private long taskId;

      PendingTaskRunnable(Runnable innerTask) {
         this.innerTask = innerTask;
      }

      @Override
      public void run() {
         innerTask.run();
         onTaskRan(taskId);
      }
   }

   private static void startPendingTasks() {
      if(!taskQueueWaitingForInit.isEmpty()) {
         pendingTaskExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable runnable) {
               Thread newThread = new Thread(runnable);
               newThread.setName("OS_PENDING_EXECUTOR_" + newThread.getId());
               return newThread;
            }
         });

         while(!taskQueueWaitingForInit.isEmpty()) {
            pendingTaskExecutor.submit(taskQueueWaitingForInit.poll());
         }
      }
   }

   private static void addTaskToQueue(PendingTaskRunnable task) {
      task.taskId = lastTaskId.incrementAndGet();

      if(pendingTaskExecutor == null) {
         OneSignal.Log(LOG_LEVEL.INFO,"Adding a task to the pending queue with ID: " + task.taskId);
         //the tasks haven't been executed yet...add them to the waiting queue
         taskQueueWaitingForInit.add(task);
      }
      else if(!pendingTaskExecutor.isShutdown()) {
         OneSignal.Log(LOG_LEVEL.INFO,"Executor is still running, add to the executor with ID: " + task.taskId);
         //if the executor isn't done with tasks, submit the task to the executor
         pendingTaskExecutor.submit(task);
      }

   }

   private static boolean shouldRunTaskThroughQueue() {
      if(initDone && pendingTaskExecutor == null) // there never were any waiting tasks
         return false;

      //if init isn't finished and the pending executor hasn't been defined yet...
      if(!initDone && pendingTaskExecutor == null)
         return true;

      //or if the pending executor is alive and hasn't been shutdown yet...
      if(pendingTaskExecutor != null && !pendingTaskExecutor.isShutdown())
         return true;

      return false;
   }

   private static void startRegistrationOrOnSession() {
      if (waitingToPostStateSync)
         return;

      waitingToPostStateSync = true;

      registerForPushFired = false;
      if (sendAsSession)
         locationFired = false;

      startLocationUpdate();
      makeAndroidParamsRequest();

      promptedLocation = promptedLocation || mInitBuilder.mPromptLocation;
   }

   private static void startLocationUpdate() {
      LocationGMS.LocationHandler locationHandler = new LocationGMS.LocationHandler() {
         @Override
         public LocationGMS.CALLBACK_TYPE getType() {
            return LocationGMS.CALLBACK_TYPE.STARTUP;
         }
         @Override
         public void complete(LocationGMS.LocationPoint point) {
            lastLocationPoint = point;
            locationFired = true;
            registerUser();
         }
      };
      boolean doPrompt = mInitBuilder.mPromptLocation && !promptedLocation;
      LocationGMS.getLocation(appContext, doPrompt, locationHandler);
   }

   private static void registerForPushToken() {
      PushRegistrator pushRegistrator;
      if (deviceType == 2)
         pushRegistrator = new PushRegistratorADM();
      else
         pushRegistrator = new PushRegistratorGPS();

      pushRegistrator.registerForPush(appContext, mGoogleProjectNumber, new PushRegistrator.RegisteredHandler() {
         @Override
         public void complete(String id, int status) {
            if (status < 1) {
               // Only allow errored subscribableStatuses if we have never gotten a token.
               //   This ensures the device will not later be marked unsubscribed due to a
               //   any inconsistencies returned by Google Play services.
               // Also do not override other types of errors status ( > -6).
               if (OneSignalStateSynchronizer.getRegistrationId() == null &&
                   (subscribableStatus == 1 || subscribableStatus < -6))
                  subscribableStatus = status;
            }
            else if (subscribableStatus < -6)
               subscribableStatus = status;

            lastRegistrationId = id;
            registerForPushFired = true;
            getCurrentSubscriptionState(appContext).setPushToken(id);
            registerUser();
         }
      });
   }

   private static int androidParamsReties = 0;

   private static void makeAndroidParamsRequest() {
      if (awlFired) {
         // Only ever call android_params endpoint once per cold start.
         //   Re-register for push token to be safe.
         registerForPushToken();
         return;
      }

      OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
         @Override
         void onFailure(int statusCode, String response, Throwable throwable) {
            new Thread(new Runnable() {
               public void run() {
                  try {
                     int sleepTime = 30000 + androidParamsReties * 10000;
                     
                     if (sleepTime > 90000)
                        sleepTime = 90000;
                     
                     OneSignal.Log(LOG_LEVEL.INFO, "Failed to get Android parameters, trying again in " + (sleepTime / 1000) +  " seconds.");
                     Thread.sleep(sleepTime);
                  } catch (Throwable t) {}
                  androidParamsReties++;
                  makeAndroidParamsRequest();
               }
            }, "OS_PARAMS_REQUEST").start();
         }

         @Override
         void onSuccess(String response) {
            try {
               JSONObject responseJson = new JSONObject(response);
               if (responseJson.has("android_sender_id")) {
                  mGoogleProjectNumberIsRemote = true;
                  mGoogleProjectNumber = responseJson.getString("android_sender_id");
               }
               
               mEnterp = responseJson.optBoolean("enterp", false);

               useEmailAuth = responseJson.optBoolean("use_email_auth", false);
               
               awl = responseJson.getJSONObject("awl_list");
   
               boolean firebaseAnalytics = responseJson.optBoolean("fba", false);
               OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
                   OneSignalPrefs.PREFS_GT_FIREBASE_TRACKING_ENABLED, firebaseAnalytics);
   
               NotificationChannelManager.processChannelList(appContext, responseJson);
            } catch (Throwable t) {
               t.printStackTrace();
            }
            awlFired = true;
            registerForPushToken();
         }
      };

      String awl_url = "apps/" + appId + "/android_params.js";
      String userId = getUserId();
      if (userId != null)
         awl_url += "?player_id=" + userId;
   
      OneSignal.Log(LOG_LEVEL.DEBUG, "Starting request to get Android parameters.");
      OneSignalRestClient.get(awl_url, responseHandler);
   }

   private static void fireCallbackForOpenedNotifications() {
      for(JSONArray dataArray : unprocessedOpenedNotifis)
         runNotificationOpenedCallback(dataArray, true, false);

      unprocessedOpenedNotifis.clear();
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

   private static boolean atLogLevel(LOG_LEVEL level) {
      return level.compareTo(visualLogLevel) < 1 || level.compareTo(logCatLevel) < 1;
   }

   static void Log(LOG_LEVEL level, String message) {
      Log(level, message, null);
   }

   static void Log(final LOG_LEVEL level, String message, Throwable throwable) {
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

   private static void logHttpError(String errorString, int statusCode, Throwable throwable, String errorResponse) {
      String jsonError = "";
      if (errorResponse != null && atLogLevel(LOG_LEVEL.INFO))
         jsonError = "\n" + errorResponse + "\n";
      Log(LOG_LEVEL.WARN, "HTTP code: " + statusCode + " " + errorString + jsonError, throwable);
   }

   // Returns true if there is active time that is unsynced.
   @WorkerThread
   static boolean onAppLostFocus() {
      foreground = false;

      if (!initDone) return false;

      if (trackAmazonPurchase != null)
         trackAmazonPurchase.checkListener();

      if (lastTrackedFocusTime == -1)
         return false;

      long time_elapsed = (long)(((SystemClock.elapsedRealtime() - lastTrackedFocusTime) / 1_000d) + 0.5d);
      lastTrackedFocusTime = SystemClock.elapsedRealtime();
      if (time_elapsed < 0 || time_elapsed > 86_400)
         return false;

      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "Android Context not found, please call OneSignal.init when your app starts.");
         return false;
      }

      boolean scheduleSyncService = scheduleSyncService();

      setLastSessionTime(System.currentTimeMillis());

      long unSentActiveTime = GetUnsentActiveTime();
      long totalTimeActive = unSentActiveTime + time_elapsed;

      SaveUnsentActiveTime(totalTimeActive);

      if (totalTimeActive < MIN_ON_FOCUS_TIME || getUserId() == null)
         return totalTimeActive >= MIN_ON_FOCUS_TIME;

      // Schedule this sync in case app is killed before completing
      if (!scheduleSyncService)
         OneSignalSyncServiceUtils.scheduleSyncTask(appContext);

      OneSignalSyncServiceUtils.syncOnFocusTime();
      
      return false;
   }

   static boolean scheduleSyncService() {
      boolean unsyncedChanges = OneSignalStateSynchronizer.persist();
      if (unsyncedChanges)
         OneSignalSyncServiceUtils.scheduleSyncTask(appContext);

      boolean locationScheduled = LocationGMS.scheduleUpdate(appContext);
      return locationScheduled || unsyncedChanges;
   }

   static void sendOnFocus(long totalTimeActive, boolean synchronous) {
      try {
         JSONObject jsonBody = new JSONObject()
            .put("app_id", appId)
            .put("type", 1)
            .put("state", "ping")
            .put("active_time", totalTimeActive);
         addNetType(jsonBody);

         sendOnFocusToPlayer(getUserId(), jsonBody, synchronous);
         String emailId = getEmailId();
         if (emailId != null)
            sendOnFocusToPlayer(emailId, jsonBody, synchronous);

      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Generating on_focus:JSON Failed.", t);
      }
   }

   private static void sendOnFocusToPlayer(String userId, JSONObject jsonBody, boolean synchronous) {
      String url = "players/" + userId + "/on_focus";
      OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
         @Override
         void onFailure(int statusCode, String response, Throwable throwable) {
            logHttpError("sending on_focus Failed", statusCode, throwable, response);
         }

         @Override
         void onSuccess(String response) {
            SaveUnsentActiveTime(0);
         }
      };

      if (synchronous)
         OneSignalRestClient.postSync(url, jsonBody, responseHandler);
      else
         OneSignalRestClient.post(url, jsonBody, responseHandler);
   }


   static void onAppFocus() {
      foreground = true;
      lastTrackedFocusTime = SystemClock.elapsedRealtime();

      sendAsSession = isPastOnSessionTime();
      setLastSessionTime(System.currentTimeMillis());

      startRegistrationOrOnSession();

      if (trackGooglePurchase != null)
         trackGooglePurchase.trackIAP();

      NotificationRestorer.asyncRestore(appContext);
      
      getCurrentPermissionState(appContext).refreshAsTo();

      if (trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled(appContext))
         trackFirebaseAnalytics.trackInfluenceOpenEvent();

      OneSignalSyncServiceUtils.cancelSyncTask(appContext);
   }

   static boolean isForeground() {
      return foreground;
   }

   private static void addNetType(JSONObject jsonObj) {
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
      Log(LOG_LEVEL.DEBUG, "registerUser: registerForPushFired:" + registerForPushFired + ", locationFired: " + locationFired + ", awlFired: " + awlFired);

      if (!registerForPushFired || !locationFired || !awlFired)
         return;

      new Thread(new Runnable() {
         public void run() {
            try {
               registerUserTask();
               OneSignalChromeTab.setup(appContext, appId, userId, AdvertisingIdProviderGPS.getLastValue());
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

      deviceInfo.put("app_id", appId);

      String adId = mainAdIdProvider.getIdentifier(appContext);
      if (adId != null)
         deviceInfo.put("ad_id", adId);
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

      try {
         List<PackageInfo> packList = packageManager.getInstalledPackages(0);
         JSONArray pkgs = new JSONArray();
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         for (int i = 0; i < packList.size(); i++) {
            md.update(packList.get(i).packageName.getBytes());
            String pck = Base64.encodeToString(md.digest(), Base64.NO_WRAP);
            if (awl.has(pck))
               pkgs.put(pck);
         }
         deviceInfo.put("pkgs", pkgs);
      } catch (Throwable t) {}

      deviceInfo.put("net_type", osUtils.getNetType());
      deviceInfo.put("carrier", osUtils.getCarrierName());
      deviceInfo.put("rooted", RootToolsInternalMethods.isRooted());

      OneSignalStateSynchronizer.updateDeviceInfo(deviceInfo);

      JSONObject pushState = new JSONObject();
      pushState.put("identifier", lastRegistrationId);
      pushState.put("subscribableStatus", subscribableStatus);
      pushState.put("androidPermission", areNotificationsEnabledForSubscribedState());
      pushState.put("device_type", deviceType);
      OneSignalStateSynchronizer.updatePushState(pushState);

      if (shareLocation && lastLocationPoint != null)
         OneSignalStateSynchronizer.updateLocation(lastLocationPoint);

      if (sendAsSession)
         OneSignalStateSynchronizer.setSyncAsNewSession();

      waitingToPostStateSync = false;
   }

   /**
    * @deprecated Please migrate to setEmail. This will be removed in next major release
    */
   @Deprecated
   public static void syncHashedEmail(final String email) {
      if (!OSUtils.isValidEmail(email))
         return;

      Runnable runSyncHashedEmail = new Runnable() {
         @Override
         public void run() {
            String trimmedEmail = email.trim();
            OneSignalStateSynchronizer.syncHashedEmail(trimmedEmail.toLowerCase());
         }
      };

      //If either the app context is null or the waiting queue isn't done (to preserve operation order)
      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "You should initialize OneSignal before calling syncHashedEmail! " +
                 "Moving this operation to a pending task queue.");
         addTaskToQueue(new PendingTaskRunnable(runSyncHashedEmail));
         return;
      }
      runSyncHashedEmail.run();
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
   public static void setEmail(@NonNull final String email, @Nullable final String emailAuthHash, @Nullable EmailUpdateHandler callback) {
      if (!OSUtils.isValidEmail(email)) {
         String errorMessage = "Email is invalid";
         if (callback != null)
            callback.onFailure(new EmailUpdateError(EmailErrorType.VALIDATION, errorMessage));
         Log(LOG_LEVEL.ERROR, errorMessage);
         return;
      }

      if (useEmailAuth && emailAuthHash == null) {
         String errorMessage = "Email authentication (auth token) is set to REQUIRED for this application. Please provide an auth token from your backend server or change the setting in the OneSignal dashboard.";
         if (callback != null)
            callback.onFailure(new EmailUpdateError(EmailErrorType.REQUIRES_EMAIL_AUTH, errorMessage));
         Log(LOG_LEVEL.ERROR, errorMessage);
         return;
      }

      emailUpdateHandler = callback;

      Runnable runSetEmail = new Runnable() {
         @Override
         public void run() {
            String trimmedEmail = email.trim();

            String internalEmailAuthHash = emailAuthHash;
            if (internalEmailAuthHash != null)
               internalEmailAuthHash.toLowerCase();

            getCurrentEmailSubscriptionState(appContext).setEmailAddress(trimmedEmail);
            OneSignalStateSynchronizer.setEmail(trimmedEmail.toLowerCase(), internalEmailAuthHash);
         }
      };

      // If either the app context is null or the waiting queue isn't done (to preserve operation order)
      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "You should initialize OneSignal before calling setEmail! " +
                 "Moving this operation to a pending task queue.");
         addTaskToQueue(new PendingTaskRunnable(runSetEmail));
         return;
      }
      runSetEmail.run();
   }

   /**
    * Call when user logs out of their account.
    * This dissociates the device from the email address.
    * This does not effect the subscription status of the email address itself.
    */
   public static void logoutEmail() {
      logoutEmail(null);
   }

   public static void logoutEmail(@Nullable EmailUpdateHandler callback) {
      if (getEmailId() == null) {
         final String message = "logoutEmail not valid as email was not set or already logged out!";
         if (callback != null)
            callback.onFailure(new EmailUpdateError(EmailErrorType.INVALID_OPERATION, message));
         Log(LOG_LEVEL.ERROR, message);
         return;
      }

      emailLogoutHandler = callback;

      Runnable emailLogout = new Runnable() {
         @Override
         public void run() {
            OneSignalStateSynchronizer.logoutEmail();
         }
      };

      // If either the app context is null or the waiting queue isn't done (to preserve operation order)
      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "You should initialize OneSignal before calling logoutEmail! " +
                 "Moving this operation to a pending task queue.");
         addTaskToQueue(new PendingTaskRunnable(emailLogout));
         return;
      }
      emailLogout.run();
   }

   /**
    * Tag a user based on an app event of your choosing so later you can create
    * <a href="https://documentation.onesignal.com/docs/segmentation">OneSignal Segments</a>
    * to target these users. Use {@link #sendTags(String)} to set more than one tag on a user at
    * a time.
    * @param key Key of your chossing to create or update
    * @param value Value to set on the key. <b>Note:</b> Passing in a blank {@code String} deletes
    *              the key. You can also call {@link #deleteTag(String)} or {@link #deleteTags(String)}.
    */
   public static void sendTag(String key, String value) {
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
    *                  You can also call {@link #deleteTag(String)} or {@link #deleteTags(String)}.
    *
    */
   public static void sendTags(final JSONObject keyValues) {
      Runnable sendTagsRunnable = new Runnable() {
         @Override
         public void run() {
            if (keyValues == null) return;

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

            if (!toSend.toString().equals("{}"))
               OneSignalStateSynchronizer.sendTags(toSend);
         }
      };


      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "You must initialize OneSignal before modifying tags!" +
                 "Moving this operation to a pending task queue.");
         addTaskToQueue(new PendingTaskRunnable(sendTagsRunnable));
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
      try {
         if (!json.has("app_id"))
            json.put("app_id", getSavedAppId());

         OneSignalRestClient.post("notifications/", json, new OneSignalRestClient.ResponseHandler() {
            @Override
            public void onSuccess(String response) {
               Log(LOG_LEVEL.DEBUG, "HTTP create notification success: " + (response != null ? response : "null"));
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
         Log(LOG_LEVEL.ERROR, "HTTP create notification json exception!", e);
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
      pendingGetTagsHandler = getTagsHandler;

      Runnable getTagsRunnable = new Runnable() {
         @Override
         public void run() {
            if (getTagsHandler == null) {
               Log(LOG_LEVEL.ERROR, "getTagsHandler is null!");
               return;
            }

            if (getUserId() == null) {
               return;
            }
            internalFireGetTagsCallback(pendingGetTagsHandler);
         }
      };

      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "You must initialize OneSignal before getting tags! " +
                 "Moving this tag operation to a pending queue.");
         taskQueueWaitingForInit.add(getTagsRunnable);
         return;
      }

      getTagsRunnable.run();
   }

   private static void internalFireGetTagsCallback(final GetTagsHandler getTagsHandler) {
      if (getTagsHandler == null) return;

      new Thread(new Runnable() {
         @Override
         public void run() {
            final UserStateSynchronizer.GetTagsResult tags = OneSignalStateSynchronizer.getTags(!getTagsCall);
            if (tags.serverSuccess) getTagsCall = true;
            if (tags.result == null || tags.toString().equals("{}"))
               getTagsHandler.tagsAvailable(null);
            else
               getTagsHandler.tagsAvailable(tags.result);
         }
      }, "OS_GETTAGS_CALLBACK").start();
   }

   /**
    * Deletes a single tag that was previously set on a user with {@link #sendTag(String, String)}
    * or {@link #sendTags(JSONObject)}. Use {@link #deleteTags(String)} if you need to delete
    * more than one.
    * @param key Key to remove.
    */
   public static void deleteTag(String key) {
      Collection<String> tempList = new ArrayList<>(1);
      tempList.add(key);
      deleteTags(tempList);
   }

   /**
    * Deletes one or more tags that were previously set on a user with {@link #sendTag(String, String)}
    * or {@link #sendTags(JSONObject)}.
    * @param keys Keys to remove.
    */
   public static void deleteTags(Collection<String> keys) {
      try {
         JSONObject jsonTags = new JSONObject();
         for (String key : keys)
            jsonTags.put(key, "");

         sendTags(jsonTags);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for deleteTags.", t);
      }
   }

   public static void deleteTags(String jsonArrayString) {
      try {
         JSONObject jsonTags = new JSONObject();
         JSONArray jsonArray = new JSONArray(jsonArrayString);

         for (int i = 0; i < jsonArray.length(); i++)
            jsonTags.put(jsonArray.getString(i), "");

         sendTags(jsonTags);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for deleteTags.", t);
      }
   }

   public static void idsAvailable(IdsAvailableHandler inIdsAvailableHandler) {
      idsAvailableHandler = inIdsAvailableHandler;

      Runnable runIdsAvailable = new Runnable() {
         @Override
         public void run() {
            if (getUserId() != null)
               OSUtils.runOnMainUIThread(new Runnable() {
                  @Override
                  public void run() {
                     internalFireIdsAvailableCallback();
                  }
               });
         }
      };

      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "You must initialize OneSignal before getting tags! " +
                 "Moving this tag operation to a pending queue.");
         addTaskToQueue(new PendingTaskRunnable(runIdsAvailable));
         return;
      }

      runIdsAvailable.run();
   }

   private static void fireIdsAvailableCallback() {
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
      if (getUserId() == null) {
         iapUpdateJob = new IAPUpdateJob(purchases);
         iapUpdateJob.newAsExisting = newAsExisting;
         iapUpdateJob.restResponseHandler = responseHandler;
         
         return;
      }

      try {
         JSONObject jsonBody = new JSONObject();
         jsonBody.put("app_id", appId);
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
               if (!url.contains("://"))
                  url = "http://" + url;

               Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
               intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET |Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
               context.startActivity(intent);
               urlOpened = true;
            }
         } catch (Throwable t) {
            Log(LOG_LEVEL.ERROR, "Error parsing JSON item " + i + "/" + jsonArraySize + " for launching a web URL.", t);
         }
      }

      return urlOpened;
   }

   private static void runNotificationOpenedCallback(final JSONArray dataArray, final boolean shown, boolean fromAlert) {
      if (mInitBuilder == null || mInitBuilder.mNotificationOpenedHandler == null) {
         unprocessedOpenedNotifis.add(dataArray);
         return;
      }

      fireNotificationOpenedHandler(generateOsNotificationOpenResult(dataArray, shown, fromAlert));
   }

   // Also called for received but OSNotification is extracted from it.
   @NonNull
   private static OSNotificationOpenResult generateOsNotificationOpenResult(JSONArray dataArray, boolean shown, boolean fromAlert) {
      int jsonArraySize = dataArray.length();

      boolean firstMessage = true;

      OSNotificationOpenResult openResult = new OSNotificationOpenResult();
      OSNotification notification = new OSNotification();
      notification.isAppInFocus = isAppActive();
      notification.shown = shown;
      notification.androidNotificationId = dataArray.optJSONObject(0).optInt("notificationId");

      String actionSelected = null;

      for (int i = 0; i < jsonArraySize; i++) {
         try {
            JSONObject data = dataArray.getJSONObject(i);
            
            notification.payload = NotificationBundleProcessor.OSNotificationPayloadFrom(data);
            if (actionSelected == null && data.has("actionSelected"))
               actionSelected = data.optString("actionSelected", null);
            
            if (firstMessage)
               firstMessage = false;
            else {
               if (notification.groupedNotifications == null)
                  notification.groupedNotifications = new ArrayList<>();
               notification.groupedNotifications.add(notification.payload);
            }
         } catch (Throwable t) {
            Log(LOG_LEVEL.ERROR, "Error parsing JSON item " + i + "/" + jsonArraySize + " for callback.", t);
         }
      }

      openResult.notification = notification;
      openResult.action = new OSNotificationAction();
      openResult.action.actionID = actionSelected;
      openResult.action.type = actionSelected != null ? OSNotificationAction.ActionType.ActionTaken : OSNotificationAction.ActionType.Opened;
      if (fromAlert)
         openResult.notification.displayType = OSNotification.DisplayType.InAppAlert;
      else
         openResult.notification.displayType = OSNotification.DisplayType.Notification;

      return openResult;
   }

   private static void fireNotificationOpenedHandler(final OSNotificationOpenResult openedResult) {
      OSUtils.runOnMainUIThread(new Runnable() {
         @Override
         public void run() {
            mInitBuilder.mNotificationOpenedHandler.notificationOpened(openedResult);
         }
      });
   }

   // Called when receiving GCM/ADM message after it has been displayed.
   // Or right when it is received if it is a silent one
   //   If a NotificationExtenderService is present in the developers app this will not fire for silent notifications.
   static void handleNotificationReceived(JSONArray data, boolean displayed, boolean fromAlert) {
      OSNotificationOpenResult openResult = generateOsNotificationOpenResult(data, displayed, fromAlert);
      if(trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled(appContext))
         trackFirebaseAnalytics.trackReceivedEvent(openResult);

      if (mInitBuilder == null || mInitBuilder.mNotificationReceivedHandler == null)
         return;

      mInitBuilder.mNotificationReceivedHandler.notificationReceived(openResult.notification);
   }

   // Called when opening a notification
   public static void handleNotificationOpen(Context inContext, JSONArray data, boolean fromAlert) {
      notificationOpenedRESTCall(inContext, data);

      if (trackFirebaseAnalytics != null && getFirebaseAnalyticsEnabled(appContext))
         trackFirebaseAnalytics.trackOpenedEvent(generateOsNotificationOpenResult(data, true, fromAlert));

      boolean urlOpened = false;
      boolean defaultOpenActionDisabled = "DISABLE".equals(OSUtils.getManifestMeta(inContext, "com.onesignal.NotificationOpened.DEFAULT"));

      if (!defaultOpenActionDisabled)
         urlOpened = openURLFromNotification(inContext, data);

      runNotificationOpenedCallback(data, true, fromAlert);

      // Open/Resume app when opening the notification.
      if (!fromAlert && !urlOpened && !defaultOpenActionDisabled)
         fireIntentFromNotificationOpen(inContext);
   }

   private static void fireIntentFromNotificationOpen(Context inContext) {
      Intent launchIntent = inContext.getPackageManager().getLaunchIntentForPackage(inContext.getPackageName());
      // Make sure we have a launcher intent.
      if (launchIntent != null) {
         launchIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
         inContext.startActivity(launchIntent);
      }
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

   private static void SaveAppId(String appId) {
      if (appContext == null)
         return;
      OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_APP_ID, appId);
   }

   static String getSavedAppId() {
      return getSavedAppId(appContext);
   }

   private static String getSavedAppId(Context inContext) {
      if (inContext == null)
         return "";

      return OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_APP_ID,null);
   }

   private static String getSavedUserId(Context inContext) {
      if (inContext == null)
         return "";

      return OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_PLAYER_ID,null);
   }

   static String getUserId() {
      if (userId == null && appContext != null) {
         userId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_GT_PLAYER_ID,null);
      }
      return userId;
   }

   static void saveUserId(String id) {
      userId = id;
      if (appContext == null)
         return;

      OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_PLAYER_ID, userId);
   }

   static String getEmailId() {
      if ("".equals(emailId))
         return null;

      if (emailId == null && appContext != null) {
         emailId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_OS_EMAIL_ID,null);
      }
      return emailId;
   }

   static void saveEmailId(String id) {
      emailId = id;
      if (appContext == null)
         return;

      OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_EMAIL_ID, "".equals(emailId) ? null : emailId);
   }
   
   static boolean getFilterOtherGCMReceivers(Context context) {
      return OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_FILTER_OTHER_GCM_RECEIVERS,false);
   }
   
   static void saveFilterOtherGCMReceivers(boolean set) {
      if (appContext == null)
         return;

      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,"OS_FILTER_OTHER_GCM_RECEIVERS",set);
   }

   // Called when a player id is returned from OneSignal
   // Updates anything else that might have been waiting for this id.
   static void updateUserIdDependents(String userId) {
      saveUserId(userId);
      fireIdsAvailableCallback();
      internalFireGetTagsCallback(pendingGetTagsHandler);
   
      getCurrentSubscriptionState(appContext).setUserId(userId);
      
      if (iapUpdateJob != null) {
         sendPurchases(iapUpdateJob.toReport, iapUpdateJob.newAsExisting, iapUpdateJob.restResponseHandler);
         iapUpdateJob = null;
      }

      OneSignalStateSynchronizer.refreshEmailState();
      
      OneSignalChromeTab.setup(appContext, appId, userId, AdvertisingIdProviderGPS.getLastValue());
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

   static boolean getFirebaseAnalyticsEnabled(Context context) {
      return OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_FIREBASE_TRACKING_ENABLED,false);
   }

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
      if (appContext == null)
         return;

      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_VIBRATE_ENABLED,enable);
   }

   static boolean getVibrate(Context context) {
      return OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_VIBRATE_ENABLED,true);
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
      if (appContext == null)
         return;

      OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_SOUND_ENABLED,enable);
   }

   static boolean getSoundEnabled(Context context) {
      return OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_SOUND_ENABLED,true);
   }

   static void setLastSessionTime(long time) {
      OneSignalPrefs.saveLong(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_LAST_SESSION_TIME,time);
   }

   private static long getLastSessionTime(Context context) {
      return OneSignalPrefs.getLong(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_OS_LAST_SESSION_TIME,-31*1000);
   }

   /**
    * Setting to control how OneSignal notifications will be shown when one is received while your app
    * is in focus.
    * <br/><br/>
    * {@link OneSignal.OSInFocusDisplayOption#Notification Notification} - native notification display while user has app in focus (can be distracting).
    * <br/>
    * {@link OneSignal.OSInFocusDisplayOption#InAppAlert In-App Alert (Default)} - native alert dialog display, which can be helpful during development.
    * <br/>
    * {@link OneSignal.OSInFocusDisplayOption#None None} - notification is silent.
    *
    * @param displayOption the {@link OneSignal.OSInFocusDisplayOption OSInFocusDisplayOption} to set
    */
   public static void setInFocusDisplaying(OSInFocusDisplayOption displayOption) {
      getCurrentOrNewInitBuilder().mDisplayOptionCarryOver = true;
      getCurrentOrNewInitBuilder().mDisplayOption = displayOption;
   }
   public static void setInFocusDisplaying(int displayOption) {
      setInFocusDisplaying(getInFocusDisplaying(displayOption));
   }

   private static OSInFocusDisplayOption getInFocusDisplaying(int displayOption) {
      switch(displayOption) {
         case 0:
            return OSInFocusDisplayOption.None;
         case 1:
            return OSInFocusDisplayOption.InAppAlert;
         case 2:
            return OSInFocusDisplayOption.Notification;
      }

      if (displayOption < 0)
         return OSInFocusDisplayOption.None;
      return OSInFocusDisplayOption.Notification;
   }

   static boolean getNotificationsWhenActiveEnabled() {
      // If OneSignal hasn't been initialized yet it is best to display a normal notification.
      if (mInitBuilder == null) return true;
      return mInitBuilder.mDisplayOption == OSInFocusDisplayOption.Notification;
   }

   static boolean getInAppAlertNotificationEnabled() {
      if (mInitBuilder == null) return false;
      return mInitBuilder.mDisplayOption == OSInFocusDisplayOption.InAppAlert;
   }

   /**
    * You can call this method with {@code false} to opt users out of receiving all notifications through
    * OneSignal. You can pass {@code true} later to opt users back into notifications.
    * @param enable whether to subscribe the user to notifications or not
    */
   public static void setSubscription(final boolean enable) {
      Runnable runSetSubscription = new Runnable() {
         @Override
         public void run() {
            getCurrentSubscriptionState(appContext).setUserSubscriptionSetting(enable);
            OneSignalStateSynchronizer.setSubscription(enable);
         }
      };

      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. " +
                 "Moving subscription action to a waiting task queue.");
         addTaskToQueue(new PendingTaskRunnable(runSetSubscription));
         return;
      }

      runSetSubscription.run();
   }

   public static void setLocationShared(boolean enable) {
      shareLocation = enable;
      if (!enable)
         OneSignalStateSynchronizer.clearLocation();
      Log(LOG_LEVEL.DEBUG, "shareLocation:" + shareLocation);
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
      Runnable runPromptLocation = new Runnable() {
         @Override
         public void run() {
            LocationGMS.LocationHandler locationHandler = new LocationGMS.LocationHandler() {
               @Override
               public LocationGMS.CALLBACK_TYPE getType() {
                  return LocationGMS.CALLBACK_TYPE.PROMPT_LOCATION;
               }
               @Override
               public void complete(LocationGMS.LocationPoint point) {
                  if (point != null)
                     OneSignalStateSynchronizer.updateLocation(point);
               }
            };

            LocationGMS.getLocation(appContext, true, locationHandler);
            promptedLocation = true;
         }
      };

      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. " +
                 "Could not prompt for location at this time - moving this operation to a" +
                 "waiting queue.");
         addTaskToQueue(new PendingTaskRunnable(runPromptLocation));
         return;
      }

      runPromptLocation.run();
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
            NotificationManager notificationManager = (NotificationManager)appContext.getSystemService(Context.NOTIFICATION_SERVICE);

            OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(appContext);
            Cursor cursor = null;
            try {
               SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();

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
                  writableDb = dbHelper.getWritableDbWithRetries();
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

      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. " +
                 "Could not clear notifications at this time - moving this operation to" +
                 "a waiting task queue.");
         addTaskToQueue(new PendingTaskRunnable(runClearOneSignalNotifications));
         return;
      }

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
               writableDb = dbHelper.getWritableDbWithRetries();
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
         }
      };

      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. " +
                 "Could not clear notification id: " + id + " at this time - moving" +
                 "this operation to a waiting task queue. The notification will still be canceled" +
                 "from NotificationManager at this time.");
         taskQueueWaitingForInit.add(runCancelNotification);
         return;
      }

      runCancelNotification.run();

      NotificationManager notificationManager = (NotificationManager)appContext.getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.cancel(id);
   }
   
   
   public static void cancelGroupedNotifications(final String group) {
      Runnable runCancelGroupedNotifications = new Runnable() {
         @Override
         public void run() {
            NotificationManager notificationManager = (NotificationManager)appContext.getSystemService(Context.NOTIFICATION_SERVICE);

            OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(appContext);
            Cursor cursor = null;

            try {
               SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();

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
               writableDb = dbHelper.getWritableDbWithRetries();
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

      if (appContext == null || shouldRunTaskThroughQueue()) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. " +
                 "Could not clear notifications part of group " + group + " - moving" +
                 "this operation to a waiting task queue.");
         addTaskToQueue(new PendingTaskRunnable(runCancelGroupedNotifications));
         return;
      }

      runCancelGroupedNotifications.run();
   }

   public static void removeNotificationOpenedHandler() {
      getCurrentOrNewInitBuilder().mNotificationOpenedHandler = null;
   }

   public static void removeNotificationReceivedHandler() {
      getCurrentOrNewInitBuilder().mNotificationReceivedHandler = null;
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
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not add permission observer");
         return;
      }
   
      getPermissionStateChangesObserver().addObserver(observer);
   
      if (getCurrentPermissionState(appContext).compare(getLastPermissionState(appContext)))
         OSPermissionChangedInternalObserver.fireChangesToPublicObserver(getCurrentPermissionState(appContext));
   }
   
   public static void removePermissionObserver(OSPermissionObserver observer) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not modify permission observer");
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
    * - {@link OneSignal#setSubscription(boolean)} is called
    * <br/>
    * - User disables or enables notifications
    * @param observer the instance of {@link OSSubscriptionObserver} that acts as the observer
    */
   public static void addSubscriptionObserver(OSSubscriptionObserver observer) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not add subscription observer");
         return;
      }
      
      getSubscriptionStateChangesObserver().addObserver(observer);
      
      if (getCurrentSubscriptionState(appContext).compare(getLastSubscriptionState(appContext)))
         OSSubscriptionChangedInternalObserver.fireChangesToPublicObserver(getCurrentSubscriptionState(appContext));
   }
   
   public static void removeSubscriptionObserver(OSSubscriptionObserver observer) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not modify subscription observer");
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
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not add email subscription observer");
         return;
      }

      getEmailSubscriptionStateChangesObserver().addObserver(observer);

      if (getCurrentEmailSubscriptionState(appContext).compare(getLastEmailSubscriptionState(appContext)))
         OSEmailSubscriptionChangedInternalObserver.fireChangesToPublicObserver(getCurrentEmailSubscriptionState(appContext));
   }

   public static void removeEmailSubscriptionObserver(@NonNull OSEmailSubscriptionObserver observer) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not modify email subscription observer");
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
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not get OSPermissionSubscriptionState");
         return null;
      }
      
      OSPermissionSubscriptionState status = new OSPermissionSubscriptionState();
      status.subscriptionStatus = getCurrentSubscriptionState(appContext);
      status.permissionStatus = getCurrentPermissionState(appContext);
      status.emailSubscriptionStatus = getCurrentEmailSubscriptionState(appContext);
   
      return status;
   }

   static long GetUnsentActiveTime() {
      if (unSentActiveTime == -1 && appContext != null) {
         unSentActiveTime = OneSignalPrefs.getLong(OneSignalPrefs.PREFS_ONESIGNAL,
                 OneSignalPrefs.PREFS_GT_UNSENT_ACTIVE_TIME,0);
      }

      Log(LOG_LEVEL.INFO, "GetUnsentActiveTime: " + unSentActiveTime);

      return unSentActiveTime;
   }

   private static void SaveUnsentActiveTime(long time) {
      unSentActiveTime = time;
      if (appContext == null)
         return;

      Log(LOG_LEVEL.INFO, "SaveUnsentActiveTime: " + unSentActiveTime);

      OneSignalPrefs.saveLong(OneSignalPrefs.PREFS_ONESIGNAL,
              OneSignalPrefs.PREFS_GT_UNSENT_ACTIVE_TIME, time);
   }

   private static boolean isDuplicateNotification(String id, Context context) {
      if (id == null || "".equals(id))
         return false;
   
      boolean exists = false;
      
      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(context);
      Cursor cursor = null;
      
      try {
         SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
   
         String[] retColumn = {NotificationTable.COLUMN_NAME_NOTIFICATION_ID};
         String[] whereArgs = {id};
   
         cursor = readableDb.query(
             NotificationTable.TABLE_NAME,
             retColumn,
             NotificationTable.COLUMN_NAME_NOTIFICATION_ID + " = ?",   // Where String
             whereArgs,
             null, null, null);
   
         exists = cursor.moveToFirst();
      }
      catch (Throwable t) {
         OneSignal.Log(LOG_LEVEL.ERROR, "Could not check for duplicate, assuming unique.", t);
      }
      finally {
         if (cursor != null)
            cursor.close();
      }

      if (exists) {
         Log(LOG_LEVEL.DEBUG, "Duplicate GCM message received, skip processing of " + id);
         return true;
      }

      return false;
   }
   
   static boolean notValidOrDuplicated(Context context, JSONObject jsonPayload) {
      String id = getNotificationIdFromGCMJsonPayload(jsonPayload);
      return id == null || OneSignal.isDuplicateNotification(id, context);
   }

   static String getNotificationIdFromGCMBundle(Bundle bundle) {
      if (bundle.isEmpty())
         return null;

      try {
         if (bundle.containsKey("custom")) {
            JSONObject customJSON = new JSONObject(bundle.getString("custom"));

            if (customJSON.has("i"))
               return customJSON.optString("i", null);
            else
               Log(LOG_LEVEL.DEBUG, "Not a OneSignal formatted GCM message. No 'i' field in custom.");
         }
         else
            Log(LOG_LEVEL.DEBUG, "Not a OneSignal formatted GCM message. No 'custom' field in the bundle.");
      } catch (Throwable t) {
         Log(LOG_LEVEL.DEBUG, "Could not parse bundle, probably not a OneSignal notification.", t);
      }

      return null;
   }

   private static String getNotificationIdFromGCMJsonPayload(JSONObject jsonPayload) {
      try {
         JSONObject customJSON = new JSONObject(jsonPayload.optString("custom"));
         return customJSON.optString("i", null);
      } catch(Throwable t) {}
      return null;
   }

   static boolean isAppActive() {
      return initDone && isForeground();
   }

   static void updateOnSessionDependents() {
      sendAsSession = false;
      setLastSessionTime(System.currentTimeMillis());
   }

   private static boolean isPastOnSessionTime() {
      if (sendAsSession)
         return true;

      return (System.currentTimeMillis() - getLastSessionTime(appContext)) / 1000 >= MIN_ON_SESSION_TIME;
   }
   
   // Extra check to make sure we don't unsubscribe devices that rely on silent background notifications.
   static boolean areNotificationsEnabledForSubscribedState() {
      if (mInitBuilder.mUnsubscribeWhenNotificationsAreDisabled)
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
}
