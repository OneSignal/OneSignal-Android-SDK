/**
 * Modified MIT License
 * 
 * Copyright 2016 OneSignal
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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;

import org.json.*;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;

import com.onesignal.OneSignalDbContract.NotificationTable;

public class OneSignal {

   public enum LOG_LEVEL {
      NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
   }

   static final long MIN_ON_FOCUS_TIME = 60;

   public interface NotificationOpenedHandler {
      /**
       * Callback to implement in your app to handle when a notification is
       * opened from the Android status bar or a new one comes in while the app is running.
       *
       * @param message
       *           The message string the user seen/should see in the Android status bar.
       * @param additionalData
       *           The additionalData key value pair section you entered in on onesignal.com.
       * @param isActive
       *           Was the app in the foreground when the notification was received.
       */
      void notificationOpened(String message, JSONObject additionalData, boolean isActive);
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
      boolean mPromptLocation;

      private Builder() {}

      private Builder(Context context) {
         mContext = context;
      }

      public Builder setNotificationOpenedHandler(NotificationOpenedHandler handler) {
         mNotificationOpenedHandler = handler;
         return this;
      }

      public Builder setAutoPromptLocation(boolean enable) {
         mPromptLocation = enable;
         return this;
      }

      public void init() {
         OneSignal.init(this);
      }
   }

   /**
    * Tag used on log messages.
    */
   static final String TAG = "OneSignal";

   static String appId, mGoogleProjectNumber;
   static Context appContext;

   private static boolean startedRegistration;
   
   private static LOG_LEVEL visualLogLevel = LOG_LEVEL.NONE;
   private static LOG_LEVEL logCatLevel = LOG_LEVEL.WARN;

   private static String userId = null;
   private static int subscribableStatus = 1;

   private static NotificationOpenedHandler notificationOpenedHandler;

   static boolean initDone;
   private static boolean foreground;

   private static IdsAvailableHandler idsAvailableHandler;

   private static long lastTrackedTime = 1, unSentActiveTime = -1;

   private static TrackGooglePurchase trackGooglePurchase;
   private static TrackAmazonPurchase trackAmazonPurchase;

   public static final String VERSION = "020403";

   private static AdvertisingIdentifierProvider mainAdIdProvider = new AdvertisingIdProviderGPS();

   private static int deviceType;
   public static String sdkType = "native";

   private static OSUtils osUtils;

   private static boolean ranSessionInitThread;

   private static String lastRegistrationId;
   private static boolean registerForPushFired, locationFired;
   private static Double lastLocLat, lastLocLong;
   private static Float lastLocAcc;
   private static Integer lastLocType;
   private static OneSignal.Builder mInitBuilder;

   static Collection<JSONArray> unprocessedOpenedNotifis = new ArrayList<JSONArray>();

   private static GetTagsHandler pendingGetTagsHandler;
   private static boolean getTagsCall;

   public static OneSignal.Builder startInit(Context context) {
      return new OneSignal.Builder(context);
   }

   private static void init(OneSignal.Builder inBuilder) {
      mInitBuilder = inBuilder;

      Context context = mInitBuilder.mContext;
      mInitBuilder.mContext = null; // Clear to prevent leaks.

      try {
         ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
         Bundle bundle = ai.metaData;
         OneSignal.init(context, bundle.getString("onesignal_google_project_number").substring(4), bundle.getString("onesignal_app_id"), mInitBuilder.mNotificationOpenedHandler);
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   public static void init(Context context, String googleProjectNumber, String oneSignalAppId) {
      init(context, googleProjectNumber, oneSignalAppId, null);
   }

   public static void init(Context context, String googleProjectNumber, String oneSignalAppId, NotificationOpenedHandler inNotificationOpenedHandler) {
      if (mInitBuilder == null)
         mInitBuilder = new OneSignal.Builder();

      osUtils = new OSUtils();

      deviceType = osUtils.getDeviceType();

      // START: Init validation
      try {
         UUID.fromString(oneSignalAppId);
      } catch (Throwable t) {
         Log(LOG_LEVEL.FATAL, "OneSignal AppId format is invalid.\nExample: 'b2f7f966-d8cc-11e4-bed1-df8f05be55ba'\n", t);
         return;
      }

      if ("b2f7f966-d8cc-11e4-bed1-df8f05be55ba".equals(oneSignalAppId) || "5eb5a37e-b458-11e3-ac11-000c2940e62c".equals(oneSignalAppId))
         Log(LOG_LEVEL.WARN, "OneSignal Example AppID detected, please update to your app's id found on OneSignal.com");

      if (deviceType == 1) {
         try {
            Double.parseDouble(googleProjectNumber);
            if (googleProjectNumber.length() < 8 || googleProjectNumber.length() > 16)
               throw new IllegalArgumentException("Google Project number (Sender_ID) should be a 10 to 14 digit number in length.");
         } catch (Throwable t) {
            Log(LOG_LEVEL.FATAL, "Google Project number (Sender_ID) format is invalid. Please use the 10 to 14 digit number found in the Google Developer Console for your project.\nExample: '703322744261'\n", t);
            subscribableStatus = -6;
         }
         
         try {
            Class.forName("com.google.android.gms.gcm.GoogleCloudMessaging");
         } catch (ClassNotFoundException e) {
            Log(LOG_LEVEL.FATAL, "The GCM Google Play services client library was not found. Please make sure to include it in your project.", e);
            subscribableStatus = -4;
         }
      }

      mGoogleProjectNumber = googleProjectNumber;

      try {
         Class.forName("android.support.v4.view.MenuCompat");
         try {
            Class.forName("android.support.v4.content.WakefulBroadcastReceiver");
            Class.forName("android.support.v4.app.NotificationManagerCompat");
         } catch (ClassNotFoundException e) {
            Log(LOG_LEVEL.FATAL, "The included Android Support Library v4 is to old or incomplete. Please update your project's android-support-v4.jar to the latest revision.", e);
            subscribableStatus = -5;
         }
      } catch (ClassNotFoundException e) {
         Log(LOG_LEVEL.FATAL, "Could not find the Android Support Library v4. Please make sure android-support-v4.jar has been correctly added to your project.", e);
         subscribableStatus = -3;
      }

      if (initDone) {
         if (context != null)
            appContext = context.getApplicationContext();

         if (inNotificationOpenedHandler != null)
            notificationOpenedHandler = inNotificationOpenedHandler;

         if (notificationOpenedHandler != null)
            fireCallbackForOpenedNotifications();

         return;
      }

      // END: Init validation
      boolean contextIsActivity = (context instanceof Activity);

      foreground = contextIsActivity;
      appId = oneSignalAppId;
      appContext = context.getApplicationContext();
      if (contextIsActivity)
         ActivityLifecycleHandler.curActivity = (Activity)context;
      else
         ActivityLifecycleHandler.nextResumeIsFirstActivity = true;
      notificationOpenedHandler = inNotificationOpenedHandler;
      lastTrackedTime = SystemClock.elapsedRealtime();

      OneSignalStateSynchronizer.initUserState(appContext);
      appContext.startService(new Intent(appContext, SyncService.class));

      if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2)
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
      else
         SaveAppId(appId);

      if (foreground || getUserId() == null)
         startRegistrationOrOnSession();

      if (notificationOpenedHandler != null)
         fireCallbackForOpenedNotifications();

      if (TrackGooglePurchase.CanTrack(appContext))
         trackGooglePurchase = new TrackGooglePurchase(appContext);

      initDone = true;
   }

   private static void startRegistrationOrOnSession() {
      if (startedRegistration)
         return;

      startedRegistration = true;

      PushRegistrator pushRegistrator;
      if (deviceType == 2)
         pushRegistrator = new PushRegistratorADM();
      else
         pushRegistrator = new PushRegistratorGPS();

      pushRegistrator.registerForPush(appContext, mGoogleProjectNumber, new PushRegistrator.RegisteredHandler() {
         @Override
         public void complete(String id) {
            lastRegistrationId = id;
            registerForPushFired = true;
            registerUser();
         }
      });

      LocationGMS.getLocation(appContext, mInitBuilder.mPromptLocation, new LocationGMS.LocationHandler() {
         @Override
         public void complete(Double lat, Double log, Float accuracy, Integer type) {
            lastLocLat = lat;
            lastLocLong = log;
            lastLocAcc = accuracy;
            lastLocType = type;
            locationFired = true;
            registerUser();
         }
      });
   }

   private static void fireCallbackForOpenedNotifications() {
      for(JSONArray dataArray : unprocessedOpenedNotifis)
         runNotificationOpenedCallback(dataArray, false);

      unprocessedOpenedNotifis.clear();
   }

   private static void updateRegistrationId() {
      String orgRegId = OneSignalStateSynchronizer.getRegistrationId();
      if (lastRegistrationId != null && !lastRegistrationId.equals(orgRegId)) {
         OneSignalStateSynchronizer.updateIdentifier(lastRegistrationId);
         fireIdsAvailableCallback();
      }
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
            runOnUiThread(new Runnable() {
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

   /**
    * Now automatically tracked, remove from your Activities.
    *
    * @deprecated Automatically tracked.
    * @Deprecated Automatically tracked.
    */
   public static void onPaused() {
      Log(LOG_LEVEL.INFO, "Deprecated! onPaused is now tracked automatically, please remove calls to OneSignal.onPaused() and OneSignal.onResume().");
   }

   static void onAppLostFocus(boolean onlySave) {
      foreground = false;

      if (!initDone) return;

      if (trackAmazonPurchase != null)
         trackAmazonPurchase.checkListener();

      if (lastTrackedTime  == -1)
         return;

      long time_elapsed = (long) (((SystemClock.elapsedRealtime() - lastTrackedTime) / 1000d) + 0.5d);
      lastTrackedTime = SystemClock.elapsedRealtime();
      if (time_elapsed < 0 || time_elapsed > 604800)
         return;
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "Android Context not found, please call OneSignal.init when your app starts.");
         return;
      }

      long unSentActiveTime = GetUnsentActiveTime();
      long totalTimeActive = unSentActiveTime + time_elapsed;

      if (onlySave || totalTimeActive < MIN_ON_FOCUS_TIME || getUserId() == null) {
         SaveUnsentActiveTime(totalTimeActive);
         return;
      }

      sendOnFocus(totalTimeActive, true);
   }

   static void sendOnFocus(long totalTimeActive, boolean synchronous) {
      JSONObject jsonBody = new JSONObject();
      try {
         jsonBody.put("app_id", appId);
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

   /**
    * Now automatically tracked, remove from your Activities.
    *
    * @deprecated Automatically tracked.
    * @Deprecated Automatically tracked.
    */
   public static void onResumed() {
      Log(LOG_LEVEL.INFO, "Deprecated! onResumed is now tracked automatically, please remove calls to OneSignal.onPaused() and OneSignal.onResume().");
   }

   static void onAppFocus() {
      foreground = true;
      lastTrackedTime = SystemClock.elapsedRealtime();

      startRegistrationOrOnSession();

      if (trackGooglePurchase != null)
         trackGooglePurchase.trackIAP();
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
      Log(LOG_LEVEL.DEBUG, "registerUser: registerForPushFired:" + registerForPushFired + ", locationFired: " + locationFired);

      if (!registerForPushFired || !locationFired)
         return;

      if (ranSessionInitThread) {
         updateRegistrationId();
         return;
      }

      ranSessionInitThread = true;

      new Thread(new Runnable() {
         public void run() {
            OneSignalStateSynchronizer.UserState userState = OneSignalStateSynchronizer.getNewUserState();

            String packageName = appContext.getPackageName();
            PackageManager packageManager = appContext.getPackageManager();

            userState.set("app_id", appId);
            userState.set("identifier", lastRegistrationId);

            String adId = mainAdIdProvider.getIdentifier(appContext);
            if (adId == null)
               adId = new AdvertisingIdProviderFallback().getIdentifier(appContext);
            userState.set("ad_id", adId);
            userState.set("device_os", Build.VERSION.RELEASE);
            userState.set("timezone", getTimeZoneOffset());
            userState.set("language", Locale.getDefault().getLanguage());
            userState.set("sdk", VERSION);
            userState.set("sdk_type", sdkType);
            userState.set("android_package", packageName);
            userState.set("device_model", Build.MODEL);
            userState.set("device_type", deviceType);
            userState.setState("subscribableStatus", subscribableStatus);

            try {
               userState.set("game_version", packageManager.getPackageInfo(packageName, 0).versionCode);
            } catch (PackageManager.NameNotFoundException e) {}

            try {
               List<PackageInfo> packList = packageManager.getInstalledPackages(0);
               JSONArray pkgs = new JSONArray();
               MessageDigest md = MessageDigest.getInstance("SHA-256");
               for (int i = 0; i < packList.size(); i++) {
                  if ((packList.get(i).applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !packageName.equals(packList.get(i).packageName)) {
                     md.update(packList.get(i).packageName.getBytes());
                     pkgs.put(Base64.encodeToString(md.digest(), Base64.NO_WRAP));
                  }
               }
               userState.set("pkgs", pkgs);
            } catch (Throwable t) {}

            final SharedPreferences prefs = getGcmPreferences(appContext);
            String email = prefs.getString("OS_USER_EMAIL", null);
            if (email != null)
               userState.set("email", email);
            else if (AndroidSupportV4Compat.ContextCompat.checkSelfPermission(appContext, "android.permission.GET_ACCOUNTS") == PackageManager.PERMISSION_GRANTED) {
               Account[] accounts = AccountManager.get(appContext).getAccounts();
               for (Account account : accounts) {
                  if (Patterns.EMAIL_ADDRESS.matcher(account.name).matches()) {
                     userState.set("email", account.name);
                     break;
                  }
               }
            }

            userState.set("net_type", osUtils.getNetType());
            userState.set("carrier", osUtils.getCarrierName());
            userState.set("rooted", RootToolsInternalMethods.isRooted());

            userState.set("lat", lastLocLat); userState.set("long", lastLocLong);
            userState.set("loc_acc", lastLocAcc); userState.set("loc_type", lastLocType);

            OneSignalStateSynchronizer.postSession(userState);
         }
      }).start();
   }

   public static void setEmail(String email) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "You must initialize OneSignal before setting email! Omitting this operation.");
         return;
      }

      if (email != null && !"".equals(email)) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         SharedPreferences.Editor editor = prefs.edit();
         editor.putString("OS_USER_EMAIL", email);
         editor.commit();
         OneSignalStateSynchronizer.setEmail(email);
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
            value = keyValues.get(key);
            if (value instanceof JSONArray || value instanceof JSONObject)
               Log(LOG_LEVEL.ERROR, "Omitting key '" + key  + "'! sendTags DO NOT supported nested values!");
            else if (keyValues.isNull(key) || "".equals(value)) {
               if (existingKeys.has(key))
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

               if (statusCode == 0)
                  response = "{'error': 'HTTP no response error'}";

               if (handler != null) {
                  try {
                     handler.onFailure(new JSONObject(response));
                  } catch (Throwable t) {
                     handler.onFailure(null);
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
      }).start();
   }

   public static void deleteTag(String key) {
      Collection<String> tempList = new ArrayList<String>(1);
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

   static void fireIdsAvailableCallback() {
      if (idsAvailableHandler != null) {
         runOnUiThread(new Runnable() {
            @Override
            public void run() {
               internalFireIdsAvailableCallback();
            }
         });
      }
   }

   private static void internalFireIdsAvailableCallback() {
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
      if (getUserId() == null)
         return;

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

            JSONObject customJSON = new JSONObject(data.getString("custom"));

            if (customJSON.has("u")) {
               String url = customJSON.getString("u");
               if (!url.contains("://"))
                  url = "http://" + url;

               Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
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

   private static void runNotificationOpenedCallback(final JSONArray dataArray, final boolean isActive) {
      if (notificationOpenedHandler == null) {
         unprocessedOpenedNotifis.add(dataArray);
         return;
      }

      int jsonArraySize = dataArray.length();

      JSONObject completeAdditionalData = null;
      String firstMessage = null;

      for (int i = 0; i < jsonArraySize; i++) {
         try {
            JSONObject data = dataArray.getJSONObject(i);

            JSONObject additionalDataJSON = null;

            // Summary notifications will not have custom set
            if (data.has("custom")) {
               JSONObject customJSON = new JSONObject(data.getString("custom"));
               additionalDataJSON = new JSONObject();

               if (customJSON.has("a"))
                  additionalDataJSON = customJSON.getJSONObject("a");

               if (data.has("title"))
                  additionalDataJSON.put("title", data.getString("title"));

               if (customJSON.has("u"))
                  additionalDataJSON.put("launchURL", customJSON.getString("u"));

               if (data.has("sound"))
                  additionalDataJSON.put("sound", data.getString("sound"));

               if (data.has("sicon"))
                  additionalDataJSON.put("smallIcon", data.getString("sicon"));

               if (data.has("licon"))
                  additionalDataJSON.put("largeIcon", data.getString("licon"));

               if (data.has("bicon"))
                  additionalDataJSON.put("bigPicture", data.getString("bicon"));

               if (additionalDataJSON.equals(new JSONObject()))
                  additionalDataJSON = null;
            }

            if (firstMessage == null) {
               completeAdditionalData = additionalDataJSON;
               firstMessage = data.getString("alert");
            }
            else {
               if (completeAdditionalData == null)
                  completeAdditionalData = new JSONObject();
               if (!completeAdditionalData.has("stacked_notifications"))
                  completeAdditionalData.put("stacked_notifications", new JSONArray());

               additionalDataJSON.put("message", data.getString("alert"));

               completeAdditionalData.getJSONArray("stacked_notifications").put(additionalDataJSON);
            }
         } catch (Throwable t) {
            Log(LOG_LEVEL.ERROR, "Error parsing JSON item " + i + "/" + jsonArraySize + " for callback.", t);
         }
      }

      fireNotificationOpenedHandler(firstMessage, completeAdditionalData, isActive);
   }

   private static void fireNotificationOpenedHandler(final String message, final JSONObject additionalDataJSON, final boolean isActive) {
      if (Looper.getMainLooper().getThread() == Thread.currentThread()) // isUIThread
         notificationOpenedHandler.notificationOpened(message, additionalDataJSON, isActive);
      else {
         runOnUiThread(new Runnable() {
            @Override
            public void run() {
               notificationOpenedHandler.notificationOpened(message, additionalDataJSON, isActive);
            }
         });
      }
   }

   // Called when receiving GCM message when app is open, in focus, and is not set to display when active.
   static void handleNotificationOpened(JSONArray data) {
      sendNotificationOpened(appContext, data);
      runNotificationOpenedCallback(data, true);
   }

   // Called when opening a notification when the app is suspended in the background, from alert type notification, or when it is dead
   public static void handleNotificationOpened(Context inContext, JSONArray data, boolean fromAlert) {
      sendNotificationOpened(inContext, data);

      boolean urlOpened = false;
      boolean defaultOpenActionDisabled = "DISABLE".equals(OSUtils.getManifestMeta(inContext, "com.onesignal.NotificationOpened.DEFAULT"));

      if (!defaultOpenActionDisabled)
         urlOpened = openURLFromNotification(inContext, data);

      runNotificationOpenedCallback(data, false);

      // Open/Resume app when opening the notification.
      if (!fromAlert && !urlOpened)
         fireIntentFromNotificationOpen(inContext, data, defaultOpenActionDisabled);
   }

   private static void fireIntentFromNotificationOpen(Context inContext, JSONArray data, boolean defaultOpenActionDisabled) {
      PackageManager packageManager = inContext.getPackageManager();

      boolean isCustom = false;

      Intent intent = new Intent().setAction("com.onesignal.NotificationOpened.RECEIVE").setPackage(inContext.getPackageName());

      List<ResolveInfo> resolveInfo = packageManager.queryBroadcastReceivers(intent, PackageManager.GET_INTENT_FILTERS);
      if (resolveInfo.size() > 0) {
         intent.putExtra("onesignal_data", data.toString());
         inContext.sendBroadcast(intent);
         isCustom = true;
      }

      // Calling startActivity() from outside of an Activity  context requires the FLAG_ACTIVITY_NEW_TASK flag.
      resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
      if (resolveInfo.size() > 0) {
         if (!isCustom)
            intent.putExtra("onesignal_data", data.toString());
         isCustom = true;
         intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
         inContext.startActivity(intent);
      }

      if (!isCustom && !defaultOpenActionDisabled) {
         Intent launchIntent = inContext.getPackageManager().getLaunchIntentForPackage(inContext.getPackageName());

         if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            inContext.startActivity(launchIntent);
         }
      }
   }

   private static void sendNotificationOpened(Context inContext, JSONArray dataArray) {
      for (int i = 0; i < dataArray.length(); i++) {
         try {
            JSONObject data = dataArray.getJSONObject(i);

            // Summary notifications do not always have a custom field.
            if (!data.has("custom"))
               continue;

            JSONObject customJson = new JSONObject(data.getString("custom"));

            // ... they also never have a OneSignal notification id.
            if (!customJson.has("i"))
               continue;

            String notificationId = customJson.getString("i");

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

   static void updateUserIdDependents(String userId) {
      saveUserId(userId);
      fireIdsAvailableCallback();
      internalFireGetTagsCallback(pendingGetTagsHandler);
   }

   // If true(default) - Device will always vibrate unless the device is in silent mode.
   // If false - Device will only vibrate when the device is set on it's vibrate only mode.
   public static void enableVibrate(boolean enable) {
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("GT_VIBRATE_ENABLED", enable);
      editor.commit();
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
      editor.commit();
   }

   static boolean getSoundEnabled(Context context) {
      final SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getBoolean("GT_SOUND_ENABLED", true);
   }

   public static void enableNotificationsWhenActive(boolean enable) {
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("ONESIGNAL_ALWAYS_SHOW_NOTIF", enable);
      editor.commit();
   }

   static boolean getNotificationsWhenActiveEnabled(Context context) {
      final SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getBoolean("ONESIGNAL_ALWAYS_SHOW_NOTIF", false);
   }

   public static void enableInAppAlertNotification(boolean enable) {
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("ONESIGNAL_INAPP_ALERT", enable);
      editor.commit();
   }

   static boolean getInAppAlertNotificationEnabled(Context context) {
      final SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getBoolean("ONESIGNAL_INAPP_ALERT", false);
   }

   public static void setSubscription(boolean enable) {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not set subscription.");
         return;
      }

      OneSignalStateSynchronizer.setSubscription(enable);
   }

   public static void promptLocation() {
      if (appContext == null) {
         Log(LOG_LEVEL.ERROR, "OneSignal.init has not been called. Could not prompt for location.");
         return;
      }

      LocationGMS.getLocation(appContext, true, new LocationGMS.LocationHandler() {
         @Override
         public void complete(Double lat, Double log, Float accuracy, Integer type) {
            if (lat != null && log != null)
               OneSignalStateSynchronizer.updateLocation(lat, log, accuracy, type);
         }
      });
   }

   public static void removeNotificationOpenedHandler() {
      notificationOpenedHandler = null;
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

   static boolean isDuplicateNotification(String id, Context context) {
      if (id == null || "".equals(id))
         return false;

      OneSignalDbHelper dbHelper = new OneSignalDbHelper(context);
      SQLiteDatabase readableDb = dbHelper.getReadableDatabase();

      String[] retColumn = { NotificationTable.COLUMN_NAME_NOTIFICATION_ID };
      String[] whereArgs = { id };

      Cursor cursor = readableDb.query(
            NotificationTable.TABLE_NAME,
            retColumn,
            NotificationTable.COLUMN_NAME_NOTIFICATION_ID + " = ?",   // Where String
            whereArgs,
            null, null, null);

      boolean exists = cursor.moveToFirst();
      cursor.close();
      readableDb.close();

      if (exists) {
         Log(LOG_LEVEL.DEBUG, "Duplicate GCM message received, skipping processing. " + id);
         return true;
      }

      return false;
   }

   static void runOnUiThread(Runnable action) {
      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(action);
   }
   
   static boolean notValidOrDuplicated(Context context, Bundle bundle) {
      String id = getNotificationIdFromGCMBundle(bundle);
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

   static boolean isAppActive() {
      return initDone && isForeground();
   }
}
