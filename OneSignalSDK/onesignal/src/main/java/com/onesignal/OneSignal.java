/**
 * Modified MIT License
 * 
 * Copyright 2017 OneSignal
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

import org.json.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.util.Base64;
import android.util.Log;

import com.onesignal.OneSignalDbContract.NotificationTable;

public class OneSignal {
   
   public enum LOG_LEVEL {
      NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
   }

   public enum OSInFocusDisplayOption {
      None, InAppAlert, Notification
   }

   static final long MIN_ON_FOCUS_TIME = 60;
   private static final long MIN_ON_SESSION_TIME = 30;

   public interface NotificationOpenedHandler {
      void notificationOpened(OSNotificationOpenResult result);
   }

   public interface NotificationReceivedHandler {
      void notificationReceived(OSNotification notification);
   }

   public interface IdsAvailableHandler {
      void idsAvailable(String userId, String registrationId);
   }

   public interface GetTagsHandler {
      void tagsAvailable(JSONObject tags);
   }

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

      public Builder setNotificationOpenedHandler(NotificationOpenedHandler handler) {
         mNotificationOpenedHandler = handler;
         return this;
      }

      public Builder setNotificationReceivedHandler(NotificationReceivedHandler handler) {
         mNotificationReceivedHandler = handler;
         return this;
      }

      public Builder autoPromptLocation(boolean enable) {
         mPromptLocation = enable;
         return this;
      }

      public Builder disableGmsMissingPrompt(boolean disable) {
         mDisableGmsMissingPrompt = disable;
         return this;
      }

      public Builder inFocusDisplaying(OSInFocusDisplayOption displayOption) {
         getCurrentOrNewInitBuilder().mDisplayOptionCarryOver = false;
         mDisplayOption = displayOption;
         return this;
      }
      
      public Builder unsubscribeWhenNotificationsAreDisabled(boolean set) {
         mUnsubscribeWhenNotificationsAreDisabled = set;
         return this;
      }
      
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

   private static String userId = null;
   private static int subscribableStatus;

   static boolean initDone;
   private static boolean foreground;

   private static IdsAvailableHandler idsAvailableHandler;

   private static long lastTrackedFocusTime = 1, unSentActiveTime = -1;

   private static TrackGooglePurchase trackGooglePurchase;
   private static TrackAmazonPurchase trackAmazonPurchase;

   public static final String VERSION = "030508";

   private static AdvertisingIdentifierProvider mainAdIdProvider = new AdvertisingIdProviderGPS();

   private static int deviceType;
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

   public static OneSignal.Builder startInit(Context context) {
      return new OneSignal.Builder(context);
   }

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
      subscribableStatus = osUtils.initializationChecker(deviceType, oneSignalAppId);
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
         startSyncService();
      }
      else
         ActivityLifecycleHandler.nextResumeIsFirstActivity = true;

      lastTrackedFocusTime = SystemClock.elapsedRealtime();

      OneSignalStateSynchronizer.initUserState(appContext);

      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2)
         ((Application)appContext).registerActivityLifecycleCallbacks(new ActivityLifecycleListener());
      else
         ActivityLifecycleListenerCompat.startListener();

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
      
      initDone = true;
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
      LocationGMS.getLocation(appContext, mInitBuilder.mPromptLocation && !promptedLocation, new LocationGMS.LocationHandler() {
         @Override
         public void complete(LocationGMS.LocationPoint point) {
            lastLocationPoint = point;
            locationFired = true;
            registerUser();
         }
      });
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
               
               awl = responseJson.getJSONObject("awl_list");
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
   static boolean onAppLostFocus(boolean onlySave) {
      foreground = false;

      if (!initDone) return false;

      if (trackAmazonPurchase != null)
         trackAmazonPurchase.checkListener();

      if (lastTrackedFocusTime == -1)
         return false;

      long time_elapsed = (long) (((SystemClock.elapsedRealtime() - lastTrackedFocusTime) / 1000d) + 0.5d);
      lastTrackedFocusTime = SystemClock.elapsedRealtime();
      if (time_elapsed < 0 || time_elapsed > 86400)
         return false;
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "Android Context not found, please call OneSignal.init when your app starts.");
         return false;
      }

      setLastSessionTime(System.currentTimeMillis());

      long unSentActiveTime = GetUnsentActiveTime();
      long totalTimeActive = unSentActiveTime + time_elapsed;

      if (onlySave || totalTimeActive < MIN_ON_FOCUS_TIME || getUserId() == null) {
         SaveUnsentActiveTime(totalTimeActive);
         return totalTimeActive >= MIN_ON_FOCUS_TIME;
      }

      sendOnFocus(totalTimeActive, true);
      
      return false;
   }

   static void sendOnFocus(long totalTimeActive, boolean synchronous) {
      JSONObject jsonBody = new JSONObject();
      try {
         jsonBody.put("app_id", appId);
         jsonBody.put("type", 1);
         jsonBody.put("state", "ping");
         jsonBody.put("active_time", totalTimeActive);
         addNetType(jsonBody);

         String url = "players/" + getUserId() + "/on_focus";
         OneSignalRestClient.ResponseHandler responseHandler =  new OneSignalRestClient.ResponseHandler() {
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
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Generating on_focus:JSON Failed.", t);
      }
   }

   static void onAppFocus() {
      startSyncService();
      foreground = true;
      lastTrackedFocusTime = SystemClock.elapsedRealtime();

      sendAsSession = isPastOnSessionTime();
      setLastSessionTime(System.currentTimeMillis());

      startRegistrationOrOnSession();

      if (trackGooglePurchase != null)
         trackGooglePurchase.trackIAP();

      NotificationRestorer.asyncRestore(appContext);
      
      getCurrentPermissionState(appContext).refreshAsTo();
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
            OneSignalStateSynchronizer.UserState userState = OneSignalStateSynchronizer.getNewUserState();

            String packageName = appContext.getPackageName();
            PackageManager packageManager = appContext.getPackageManager();

            userState.set("app_id", appId);
            userState.set("identifier", lastRegistrationId);

            String adId = mainAdIdProvider.getIdentifier(appContext);
            if (adId != null)
               userState.set("ad_id", adId);
            userState.set("device_os", Build.VERSION.RELEASE);
            userState.set("timezone", getTimeZoneOffset());
            userState.set("language", OSUtils.getCorrectedLanguage());
            userState.set("sdk", VERSION);
            userState.set("sdk_type", sdkType);
            userState.set("android_package", packageName);
            userState.set("device_model", Build.MODEL);
            userState.set("device_type", deviceType);
            userState.setState("subscribableStatus", subscribableStatus);
            userState.setState("androidPermission", areNotificationsEnabledForSubscribedState());

            try {
               userState.set("game_version", packageManager.getPackageInfo(packageName, 0).versionCode);
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
               userState.set("pkgs", pkgs);
            } catch (Throwable t) {}

            userState.set("net_type", osUtils.getNetType());
            userState.set("carrier", osUtils.getCarrierName());
            userState.set("rooted", RootToolsInternalMethods.isRooted());

            if (lastLocationPoint != null)
               userState.setLocation(lastLocationPoint);

            OneSignalStateSynchronizer.postUpdate(userState, sendAsSession);
            waitingToPostStateSync = false;
            
            OneSignalChromeTab.setup(appContext, appId, userId, AdvertisingIdProviderGPS.getLastValue());
         }
      }, "OS_REG_USER").start();
   }

   public static void syncHashedEmail(String email) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "You must initialize OneSignal before calling syncHashedEmail! Omitting this operation.");
         return;
      }

      if (OSUtils.isValidEmail(email)) {
         email = email.trim();
         OneSignalStateSynchronizer.syncHashedEmail(email.toLowerCase());
      }
   }

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

   public static void sendTags(JSONObject keyValues) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "You must initialize OneSignal before modifying tags! Omitting this tag operation.");
         return;
      }

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

   public static void postNotification(String json, final PostNotificationResponseHandler handler) {
      try {
         postNotification(new JSONObject(json), handler);
      } catch (JSONException e) {
         Log(LOG_LEVEL.ERROR, "Invalid postNotification JSON format: " + json);
      }
   }

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

   public static void getTags(final GetTagsHandler getTagsHandler) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "You must initialize OneSignal before getting tags! Omitting this tag operation.");
         return;
      }
      if (getTagsHandler == null) {
         Log(LOG_LEVEL.ERROR, "getTagsHandler is null!");
         return;
      }

      if (getUserId() == null) {
         pendingGetTagsHandler = getTagsHandler;
         return;
      }
      internalFireGetTagsCallback(getTagsHandler);
   }

   private static void internalFireGetTagsCallback(final GetTagsHandler getTagsHandler) {
      if (getTagsHandler == null) return;

      new Thread(new Runnable() {
         @Override
         public void run() {
            final OneSignalStateSynchronizer.GetTagsResult tags = OneSignalStateSynchronizer.getTags(!getTagsCall);
            if (tags.serverSuccess) getTagsCall = true;
            if (tags.result == null || tags.toString().equals("{}"))
               getTagsHandler.tagsAvailable(null);
            else
               getTagsHandler.tagsAvailable(tags.result);
         }
      }, "OS_GETTAGS_CALLBACK").start();
   }

   public static void deleteTag(String key) {
      Collection<String> tempList = new ArrayList<>(1);
      tempList.add(key);
      deleteTags(tempList);
   }

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

      if (getUserId() != null)
         internalFireIdsAvailableCallback();
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
      if (mInitBuilder == null || mInitBuilder.mNotificationReceivedHandler == null)
         return;

      OSNotificationOpenResult openResult = generateOsNotificationOpenResult(data, displayed, fromAlert);
      mInitBuilder.mNotificationReceivedHandler.notificationReceived(openResult.notification);
   }

   // Called when opening a notification
   public static void handleNotificationOpen(Context inContext, JSONArray data, boolean fromAlert) {
      notificationOpenedRESTCall(inContext, data);

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
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_APP_ID", appId);
      editor.commit();
   }

   static String getSavedAppId() {
      return getSavedAppId(appContext);
   }

   private static String getSavedAppId(Context inContext) {
      if (inContext == null)
         return "";

      final SharedPreferences prefs = getGcmPreferences(inContext);
      return prefs.getString("GT_APP_ID", null);
   }

   private static String getSavedUserId(Context inContext) {
      if (inContext == null)
         return "";
      final SharedPreferences prefs = getGcmPreferences(inContext);
      return prefs.getString("GT_PLAYER_ID", null);
   }

   static String getUserId() {
      if (userId == null && appContext != null) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         userId = prefs.getString("GT_PLAYER_ID", null);
      }
      return userId;
   }

   static void saveUserId(String inUserId) {
      userId = inUserId;
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_PLAYER_ID", userId);
      editor.commit();
   }
   
   static boolean getFilterOtherGCMReceivers(Context context) {
      SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getBoolean("OS_FILTER_OTHER_GCM_RECEIVERS", false);
   }
   
   static void saveFilterOtherGCMReceivers(boolean set) {
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("OS_FILTER_OTHER_GCM_RECEIVERS", set);
      editor.commit();
   }
   
   static void updateUserIdDependents(String userId) {
      saveUserId(userId);
      fireIdsAvailableCallback();
      internalFireGetTagsCallback(pendingGetTagsHandler);
   
      getCurrentSubscriptionState(appContext).setUserId(userId);
      
      if (iapUpdateJob != null) {
         sendPurchases(iapUpdateJob.toReport, iapUpdateJob.newAsExisting, iapUpdateJob.restResponseHandler);
         iapUpdateJob = null;
      }
      
      OneSignalChromeTab.setup(appContext, appId, userId, AdvertisingIdProviderGPS.getLastValue());
   }

   // If true(default) - Device will always vibrate unless the device is in silent mode.
   // If false - Device will only vibrate when the device is set on it's vibrate only mode.
   public static void enableVibrate(boolean enable) {
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("GT_VIBRATE_ENABLED", enable);
      editor.apply();
   }

   static boolean getVibrate(Context context) {
      final SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getBoolean("GT_VIBRATE_ENABLED", true);
   }

   // If true(default) - Sound plays when receiving notification. Vibrates when device is on vibrate only mode.
   // If false - Only vibrates unless EnableVibrate(false) was set.
   public static void enableSound(boolean enable) {
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("GT_SOUND_ENABLED", enable);
      editor.apply();
   }

   static boolean getSoundEnabled(Context context) {
      final SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getBoolean("GT_SOUND_ENABLED", true);
   }

   static void setLastSessionTime(long time) {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putLong("OS_LAST_SESSION_TIME", time);
      editor.apply();
   }

   private static long getLastSessionTime(Context context) {
      final SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getLong("OS_LAST_SESSION_TIME", -31 * 1000);
   }

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

   public static void setSubscription(boolean enable) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not set subscription.");
         return;
      }
      
      getCurrentSubscriptionState(appContext).setUserSubscriptionSetting(enable);
      OneSignalStateSynchronizer.setSubscription(enable);
   }

   public static void setLocationShared(boolean enable) {
      shareLocation = enable;
      if (!enable)
         OneSignalStateSynchronizer.clearLocation();
      Log(LOG_LEVEL.DEBUG, "shareLocation:" + shareLocation);
   }

   public static void promptLocation() {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not prompt for location.");
         return;
      }

      LocationGMS.getLocation(appContext, true, new LocationGMS.LocationHandler() {
         @Override
         public void complete(LocationGMS.LocationPoint point) {
            if (point != null)
               OneSignalStateSynchronizer.updateLocation(point);
         }
      });
  
      promptedLocation = true;
   }

   public static void clearOneSignalNotifications() {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not clear notifications.");
         return;
      }

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

   public static void cancelNotification(int id) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not clear notification id: " + id);
         return;
      }

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

      NotificationManager notificationManager = (NotificationManager)appContext.getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.cancel(id);
   }
   
   
   public static void cancelGroupedNotifications(String group) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not clear notifications part of group " + group);
         return;
      }
   
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

   public static void removeNotificationOpenedHandler() {
      getCurrentOrNewInitBuilder().mNotificationOpenedHandler = null;
   }

   public static void removeNotificationReceivedHandler() {
      getCurrentOrNewInitBuilder().mNotificationReceivedHandler = null;
   }
   
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
   
   public static OSPermissionSubscriptionState getPermissionSubscriptionState() {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not get OSPermissionSubscriptionState");
         return null;
      }
      
      OSPermissionSubscriptionState status = new OSPermissionSubscriptionState();
      status.subscriptionStatus = getCurrentSubscriptionState(appContext);
      status.permissionStatus = getCurrentPermissionState(appContext);
   
      return status;
   }

   static long GetUnsentActiveTime() {
      if (unSentActiveTime == -1 && appContext != null) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         unSentActiveTime = prefs.getLong("GT_UNSENT_ACTIVE_TIME", 0);
      }

      Log(LOG_LEVEL.INFO, "GetUnsentActiveTime: " + unSentActiveTime);

      return unSentActiveTime;
   }

   private static void SaveUnsentActiveTime(long time) {
      unSentActiveTime = time;
      if (appContext == null)
         return;

      Log(LOG_LEVEL.INFO, "SaveUnsentActiveTime: " + unSentActiveTime);

      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putLong("GT_UNSENT_ACTIVE_TIME", time);
      editor.commit();
   }

   static SharedPreferences getGcmPreferences(Context context) {
      return context.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
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
      return (System.currentTimeMillis() - getLastSessionTime(appContext)) / 1000 >= MIN_ON_SESSION_TIME;
   }
   
   private static void startSyncService() {
      Intent intent = new Intent(appContext, SyncService.class);
      intent.putExtra("task", SyncService.TASK_APP_STARTUP);
      appContext.startService(intent);
   }
   
   // Extra check to make sure we don't unsubscribe devices that rely on silent background notifications.
   static boolean areNotificationsEnabledForSubscribedState() {
      if (mInitBuilder.mUnsubscribeWhenNotificationsAreDisabled)
         return OSUtils.areNotificationsEnabled(appContext);
      return true;
   }
}
