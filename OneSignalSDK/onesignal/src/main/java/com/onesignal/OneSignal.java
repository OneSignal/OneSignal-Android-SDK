/**
 * Modified MIT License
 * 
 * Copyright 2015 OneSignal
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
import java.io.UnsupportedEncodingException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.http.Header;
import org.json.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;

import com.loopj.android.http.*;
import com.stericson.RootTools.internal.RootToolsInternalMethods;

public class OneSignal {

   public enum LOG_LEVEL {
      NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.TYPE)
   public @interface TiedToCurrentActivity {}

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

   /**
    * Tag used on log messages.
    */
   static final String TAG = "OneSignal";

   private static String appId;
   static Activity appContext;
   
   private static LOG_LEVEL visualLogLevel = LOG_LEVEL.NONE;
   private static LOG_LEVEL logCatLevel = LOG_LEVEL.WARN;

   private static String registrationId, userId = null;
   private static JSONObject pendingTags;
   private static int savedSubscription;
   private static int currentSubscription = 1;
   private static final int UNSUBSCRIBE_VALUE = -2;

   private static NotificationOpenedHandler notificationOpenedHandler;

   static boolean initDone;
   private static boolean foreground = true;

   private static IdsAvailableHandler idsAvailableHandler;

   private static long lastTrackedTime, unSentActiveTime = -1;

   private static String lastNotificationIdOpened;
   private static TrackGooglePurchase trackGooglePurchase;
   private static TrackAmazonPurchase trackAmazonPurchase;

   public static final String VERSION = "010902";

   private static PushRegistrator pushRegistrator;
   private static AdvertisingIdentifierProvider mainAdIdProvider = new AdvertisingIdProviderGPS();

   private static int deviceType;
   public static String sdkType = "native";

   private static JSONObject nextInitAdditionalDataJSON = null;
   private static String nextInitMessage = null;

   public static void init(Activity context, String googleProjectNumber, String oneSignalAppId) {
      init(context, googleProjectNumber, oneSignalAppId, null);
   }

   public static void init(Activity context, String googleProjectNumber, String oneSignalAppId, NotificationOpenedHandler inNotificationOpenedHandler) {

      try {
         Class.forName("com.amazon.device.messaging.ADM");
         pushRegistrator = new PushRegistratorADM();
         deviceType = 2;
      } catch (ClassNotFoundException e) {
         pushRegistrator = new PushRegistratorGPS();
         deviceType = 1;
      }

      // START: Init validation
      try {
         UUID.fromString(oneSignalAppId);
      } catch (Throwable t) {
         Log(LOG_LEVEL.FATAL, "OneSignal AppId format is invalid.\nExample: 'b2f7f966-d8cc-11eg-bed1-df8f05be55ba'\n", t, context);
         return;
      }

      if ("b2f7f966-d8cc-11eg-bed1-df8f05be55ba".equals(oneSignalAppId) || "5eb5a37e-b458-11e3-ac11-000c2940e62c".equals(oneSignalAppId))
         Log(LOG_LEVEL.WARN, "OneSignal Example AppID detected, please update to your app's id found on OneSignal.com");

      if (deviceType == 1) {
         try {
            Double.parseDouble(googleProjectNumber);
            if (googleProjectNumber.length() < 8 || googleProjectNumber.length() > 16)
               throw new IllegalArgumentException("Google Project number (Sender_ID) should be a 10 to 14 digit number in length.");
         } catch (Throwable t) {
            Log(LOG_LEVEL.FATAL, "Google Project number (Sender_ID) format is invalid. Please use the 10 to 14 digit number found in the Google Developer Console for your project.\nExample: '703322744261'\n", t, context);
            currentSubscription = -6;
         }
         
         try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
         } catch (ClassNotFoundException e) {
            Log(LOG_LEVEL.FATAL, "The Google Play services client library was not found. Please make sure to include it in your project.", e, context);
            currentSubscription = -4;
         }
      }

      try {
         Class.forName("android.support.v4.view.MenuCompat");
         try {
            Class.forName("android.support.v4.content.WakefulBroadcastReceiver");
         } catch (ClassNotFoundException e) {
            Log(LOG_LEVEL.FATAL, "The included Android Support Library v4 is to old. Please update your project's android-support-v4.jar to the latest revision.", e, context);
            currentSubscription = -5;
         }
      } catch (ClassNotFoundException e) {
         Log(LOG_LEVEL.FATAL, "Could not find the Android Support Library v4. Please make sure android-support-v4.jar has been correctly added to your project.", e, context);
         currentSubscription = -3;
      }

      if (initDone) {
         if (context != null)
            appContext = context;
         if (inNotificationOpenedHandler != null)
            notificationOpenedHandler = inNotificationOpenedHandler;

         onResumed();

         if (nextInitMessage != null && notificationOpenedHandler != null) {
            fireNotificationOpenedHandler(nextInitMessage, nextInitAdditionalDataJSON, false);

            nextInitMessage = null;
            nextInitAdditionalDataJSON = null;
         }

         return;
      }

      // END: Init validation

      savedSubscription = getSubscription(context);

      appId = oneSignalAppId;
      appContext = context;
      notificationOpenedHandler = inNotificationOpenedHandler;
      lastTrackedTime = SystemClock.elapsedRealtime();

      try {
         Class.forName("com.amazon.device.iap.PurchasingListener");
         trackAmazonPurchase = new TrackAmazonPurchase(appContext);
      } catch (ClassNotFoundException e) {}

      // Re-register user if the app id changed, this might happen when a dev is testing.
      String oldAppId = getSavedAppId();
      if (oldAppId != null) {
         if (!oldAppId.equals(appId)) {
            Log(LOG_LEVEL.DEBUG, "APP ID changed, clearing user id as it is no longer valid.");
            saveUserId(null);
            SaveAppId(appId);
         }
      }
      else
         SaveAppId(appId);

      pushRegistrator.registerForPush(appContext, googleProjectNumber, new PushRegistrator.RegisteredHandler() {
         private boolean firstRun = true;
         @Override
         public void complete(String id) {
            if (firstRun)
               registerUser(id);
            else
               updateRegistrationId(id);
            firstRun = false;
         }
      });

      // Called from tapping on a Notification from the status bar when the activity is completely dead and not open in any state.
      if (appContext.getIntent() != null && appContext.getIntent().getBundleExtra("data") != null)
         runNotificationOpenedCallback(appContext.getIntent().getBundleExtra("data"), false);

      if (TrackGooglePurchase.CanTrack(appContext))
         trackGooglePurchase = new TrackGooglePurchase(appContext);

      initDone = true;

      // In the future on Android 4.0 (API 14)+ devices use registerActivityLifecycleCallbacks
      //    instead of requiring developers to call onPause and onResume in each activity.
      // Might be able to use registerOnActivityPausedListener in Android 2.3.3 (API 10) to 3.2 (API 13) for backwards compatibility
   }

   private static void updateRegistrationId(String id) {
      String orgRegId = GetRegistrationId();
      if (!id.equals(orgRegId)) {
         SaveRegistrationId(id);
         fireIdsAvailableCallback();
         try {
            JSONObject jsonBody = playerUpdateBaseJSON();
            jsonBody.put("identifier", registrationId);
            postPlayerUpdate(jsonBody);
         } catch (JSONException e) {}
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
   
   public static void Log(LOG_LEVEL level, String message) {
      Log(level, message, null, appContext);
   }

   public static void Log(final LOG_LEVEL level, String message, Throwable throwable) {
      Log(level, message, throwable, appContext);
   }
   
   public static void Log(final LOG_LEVEL level, String message, Throwable throwable, final Context context) {
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
      
      if (context != null && level.compareTo(visualLogLevel) < 1) {
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

            ((Activity)context).runOnUiThread(new Runnable() {
               @Override
               public void run() {
                  new AlertDialog.Builder(context)
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

   private static void logHttpError(String errorString, int statusCode, Throwable throwable, JSONObject errorResponse) {
      String jsonError = "";
      if (errorResponse != null && atLogLevel(LOG_LEVEL.INFO))
         jsonError = "\n" + errorResponse.toString() + "\n";
      Log(LOG_LEVEL.WARN, "HTTP code: " + statusCode + " " + errorString + jsonError, throwable);
   }

   public static void onPaused() {
      foreground = false;

      if (trackAmazonPurchase != null)
         trackAmazonPurchase.checkListener();

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

      if (totalTimeActive < 30) {
         SaveUnsentActiveTime(totalTimeActive);
         return;
      }

      if (getUserId() == null)
         return;

      JSONObject jsonBody = new JSONObject();
      try {
         jsonBody.put("app_id", appId);
         jsonBody.put("state", "ping");
         jsonBody.put("active_time", totalTimeActive);
         addNetType(jsonBody);

         OneSignalRestClient.post(appContext, "players/" + getUserId() + "/on_focus", jsonBody, new JsonHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
               logHttpError("sending on_focus Failed", statusCode, throwable, errorResponse);
            }
         });

         SaveUnsentActiveTime(0);
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Generating on_focus:JSON Failed.", t);
      }
   }

   public static void onResumed() {
      foreground = true;
      lastTrackedTime = SystemClock.elapsedRealtime();

      if (trackGooglePurchase != null)
         trackGooglePurchase.trackIAP();
   }

   static boolean isForeground() {
      return foreground;
   }

   private static void addNetType(JSONObject jsonObj) {
      try {
         ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
         NetworkInfo netInfo = cm.getActiveNetworkInfo();

         int networkType = netInfo.getType();
         int netType = 1;
         if (networkType == ConnectivityManager.TYPE_WIFI || networkType == ConnectivityManager.TYPE_ETHERNET)
            netType = 0;
         jsonObj.put("net_type", netType);
      } catch (Throwable t) {}
   }
   
   private static int getTimeZoneOffset() {
      TimeZone timzone = Calendar.getInstance().getTimeZone();
      int offset = timzone.getRawOffset();
      
      if (timzone.inDaylightTime(new Date()))
          offset = offset + timzone.getDSTSavings();
      
      return offset / 1000;
   }

   private static void registerUser(String id) {
      if (id != null)
         SaveRegistrationId(id);

      // Must run in its own thread due to the use of getAdvertisingId
      new Thread(new Runnable() {
         public void run() {
            try {
               String packageName = appContext.getPackageName();
               PackageManager packageManager = appContext.getPackageManager();

               JSONObject jsonBody = new JSONObject();
               jsonBody.put("app_id", appId);
               if (registrationId != null)
                  jsonBody.put("identifier", registrationId);
               
               String adId = mainAdIdProvider.getIdentifier(appContext);
               // "... must use the advertising ID (when available on a device) in lieu of any other device identifiers ..."
               // https://play.google.com/about/developer-content-policy.html
               if (adId != null)
                  jsonBody.put("ad_id", adId);
               else {
                  adId = new AdvertisingIdProviderFallback().getIdentifier(appContext);
                  if (adId != null)
                     jsonBody.put("ad_id", adId);
               }

               jsonBody.put("device_os", Build.VERSION.RELEASE);
               jsonBody.put("timezone", getTimeZoneOffset());
               jsonBody.put("language", Locale.getDefault().getLanguage());
               jsonBody.put("sdk", VERSION);

               if (getUserId() == null) {
                  jsonBody.put("android_package", packageName);
                  jsonBody.put("sdk_type", sdkType);
               }

               // These values would never change as well but need to send them in case the user has been deleted via the dashboard.
               jsonBody.put("device_model", Build.MODEL);
               jsonBody.put("device_type", deviceType);

               if (savedSubscription != currentSubscription)
                  jsonBody.put("notification_types", currentSubscription);

               try {
                  jsonBody.put("game_version", "" + packageManager.getPackageInfo(packageName, 0).versionCode);
               } catch (PackageManager.NameNotFoundException e) {}
               List<PackageInfo> packList = packageManager.getInstalledPackages(0);
               int count = -1;
               for(int i = 0; i < packList.size(); i++)
                  count += ((packList.get(i).applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) ? 1 : 0;
               jsonBody.put("pkgc", count);

               addNetType(jsonBody);

               if (RootToolsInternalMethods.isRooted())
                  jsonBody.put("rooted", true);

               try {
                  Field[] fields = Class.forName(packageName + ".R$raw").getFields();
                  JSONArray soundList = new JSONArray();
                  TypedValue fileType = new TypedValue();
                  String fileName;

                  for (int i = 0; i < fields.length; i++) {
                     appContext.getResources().getValue(fields[i].getInt(null), fileType, true);
                     fileName = fileType.string.toString().toLowerCase();

                     if (fileName.endsWith(".wav") || fileName.endsWith(".mp3"))
                        soundList.put(fields[i].getName());
                  }

                  if (soundList.length() > 0)
                     jsonBody.put("sounds", soundList);
               } catch (Throwable t) {}

               String urlStr;
               if (getUserId() == null)
                  urlStr = "players";
               else
                  urlStr = "players/" + getUserId() + "/on_session";

               JsonHttpResponseHandler jsonHandler = new JsonHttpResponseHandler() {
                  @Override
                  public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                     saveSubscription(currentSubscription);

                     try {
                        if (response.has("id")) {
                           saveUserId(response.getString("id"));
                           sendPendingIdData();

                           fireIdsAvailableCallback();
                           
                           Log(LOG_LEVEL.INFO, "Device registered with OneSignal, UserId = " + response.getString("id"));
                        }
                        else
                           Log(LOG_LEVEL.INFO, "Device session registered with OneSignal, UserId = " + getUserId());
                     } catch (Throwable t) {
                        Log(LOG_LEVEL.ERROR, "ERROR parsing on_session or create JSON Response.", t);
                     }
                  }

                  @Override
                  public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                     logHttpError("Create or on_session for user failed to send!", statusCode, throwable, errorResponse);
                  }
               };
               OneSignalRestClient.postSync(appContext, urlStr, jsonBody, jsonHandler);

            } catch (Throwable t) { // JSONException and UnsupportedEncodingException
               Log(LOG_LEVEL.ERROR, "Generating JSON create or on_session for user failed!", t);
            }
         }
      }).start();
   }

   private static void sendPendingIdData() {
      if (pendingTags != null || currentSubscription != 1) {
         try {
            JSONObject json = playerUpdateBaseJSON();
            if (pendingTags != null) {
               json.put("tags", pendingTags);
               pendingTags = null;
            }

            if (currentSubscription != 1)
               json.put("notification_types", currentSubscription);
            postPlayerUpdate(json);
         } catch (JSONException e) {
            e.printStackTrace();
         }
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
      try {
         if (getUserId() == null) {
            if (pendingTags == null)
               pendingTags = new JSONObject();
            Iterator<String> keys = keyValues.keys();
            String key;
            while (keys.hasNext()) {
               key = keys.next();
               pendingTags.put(key, keyValues.get(key));
            }
         } else {
            JSONObject jsonBody = playerUpdateBaseJSON();
            if (keyValues != null)
               jsonBody.put("tags", keyValues);

            postPlayerUpdate(jsonBody);
         }
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
         Log(LOG_LEVEL.ERROR, "Generating JSON sendTags failed!", t);
      }
   }

   private static JSONObject playerUpdateBaseJSON() {
      JSONObject jsonBody = new JSONObject();
      try {
         jsonBody.put("app_id", appId);
         addNetType(jsonBody);
      } catch (JSONException e) {
         Log(LOG_LEVEL.ERROR, "Generating player update base JSON failed!", e);
      }

      return jsonBody;
   }

   private static void postPlayerUpdate(JSONObject postBody) {
      try {
         OneSignalRestClient.put(appContext, "players/" + getUserId(), postBody, new JsonHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
               logHttpError("player update failed!", statusCode, throwable, errorResponse);
            }
         });
      } catch (UnsupportedEncodingException e) {
         Log(LOG_LEVEL.ERROR, "HTTP player update encoding exception!", e);
      }
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

         OneSignalRestClient.post(appContext, "notifications/", json, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
               Log(LOG_LEVEL.DEBUG, "HTTP create notification success: " + (response != null ? response : "null"));
               if (handler != null)
                  handler.onSuccess(response);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject response) {
               logHttpError("create notification failed", statusCode, throwable, response);

               if (statusCode == 0) {
                  try {
                     response = new JSONObject("{'error': 'HTTP no response error'}");
                  } catch (JSONException e1) {
                     if (atLogLevel(LOG_LEVEL.INFO))
                        e1.printStackTrace();
                  }
               }

               if (handler != null)
                  handler.onFailure(response);
            }
         });
      } catch (UnsupportedEncodingException e) {
         Log(LOG_LEVEL.ERROR, "HTTP create notification encoding exception!", e);
         if (handler != null) {
            try {
               handler.onFailure(new JSONObject("{'error': 'HTTP create notification encoding exception!'}"));
            } catch (JSONException e1) {
               e1.printStackTrace();
            }
         }
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
      OneSignalRestClient.get(appContext, "players/" + getUserId(), new JsonHttpResponseHandler() {
         @Override
         public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
            appContext.runOnUiThread(new Runnable() {
               @Override
               public void run() {
                  try {
                     getTagsHandler.tagsAvailable(response.getJSONObject("tags"));
                  } catch (Throwable t) {
                     Log(LOG_LEVEL.ERROR, "Failed to Parse getTags.", t);
                  }
               }
            });
         }

         @Override
         public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
            logHttpError("failed to getTags.", statusCode, throwable, errorResponse);
         }
      });
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
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
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
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for deleteTags.", t);
      }
   }

   public static void idsAvailable(IdsAvailableHandler inIdsAvailableHandler) {
      if (getUserId() != null)
         inIdsAvailableHandler.idsAvailable(getUserId(), GetRegistrationId());
      else
         idsAvailableHandler = inIdsAvailableHandler;
   }

   private static void fireIdsAvailableCallback() {
      if (idsAvailableHandler != null) {
         appContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
               String regId = GetRegistrationId();
               idsAvailableHandler.idsAvailable(getUserId(), regId);
               if (regId != null)
                  idsAvailableHandler = null;
            }
         });
      }
   }

   static void sendPurchases(JSONArray purchases, boolean newAsExisting, ResponseHandlerInterface httpHandler) {
      if (getUserId() == null)
         return;

      try {
         JSONObject jsonBody = new JSONObject();
         jsonBody.put("app_id", appId);
         if (newAsExisting)
            jsonBody.put("existing", true);
         jsonBody.put("purchases", purchases);

         if (httpHandler == null)
            httpHandler = new JsonHttpResponseHandler();
         
         OneSignalRestClient.post(appContext, "players/" + getUserId() + "/on_purchase", jsonBody, httpHandler);
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON for sendPurchases.", t);
      }
   }

   private static void runNotificationOpenedCallback(final Bundle data, final boolean isActive) {
      try {
         JSONObject customJSON = new JSONObject(data.getString("custom"));

         if (!isActive && customJSON.has("u")) {
            String url = customJSON.getString("u");
            if (!url.startsWith("http://") && !url.startsWith("https://"))
               url = "http://" + url;
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            appContext.startActivity(browserIntent);
         }

         if (notificationOpenedHandler != null) {
            JSONObject additionalDataJSON = new JSONObject();

            if (customJSON.has("a"))
               additionalDataJSON = customJSON.getJSONObject("a");

            if (data.containsKey("title"))
               additionalDataJSON.put("title", data.getString("title"));

            if (customJSON.has("u"))
               additionalDataJSON.put("launchURL", customJSON.getString("u"));

            if (data.containsKey("sound"))
               additionalDataJSON.put("sound", data.getString("sound"));

            if (data.containsKey("sicon"))
               additionalDataJSON.put("smallIcon", data.getString("sicon"));

            if (data.containsKey("licon"))
               additionalDataJSON.put("largeIcon", data.getString("licon"));

            if (data.containsKey("bicon"))
               additionalDataJSON.put("bigPicture", data.getString("bicon"));

            if (additionalDataJSON.equals(new JSONObject()))
               additionalDataJSON = null;

            if (appContext.isFinishing()
               &&  (notificationOpenedHandler.getClass().isAnnotationPresent(TiedToCurrentActivity.class)
                 || notificationOpenedHandler instanceof Activity )) {

               // Activity is finished or isFinishing, run callback later when OneSignal.init is called again from anther Activity.
               nextInitAdditionalDataJSON = additionalDataJSON;
               nextInitMessage = data.getString("alert");
               return;
            }

            fireNotificationOpenedHandler(data.getString("alert"), additionalDataJSON, isActive);
         }
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to run callback from notification opened.", t);
      }
   }

   private static void fireNotificationOpenedHandler(final String message, final JSONObject additionalDataJSON, final boolean isActive) {
      if (Looper.getMainLooper().getThread() == Thread.currentThread()) // isUIThread
         notificationOpenedHandler.notificationOpened(message, additionalDataJSON, isActive);
      else {
         appContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
               notificationOpenedHandler.notificationOpened(message, additionalDataJSON, isActive);
            }
         });
      }
   }

   // Called when receiving GCM message when app is open and in focus.
   static void handleNotificationOpened(Bundle data) {
      sendNotificationOpened(appContext, data);
      runNotificationOpenedCallback(data, true);
   }

   // Called when opening a notification when the app is suspended in the background or when it is dead
   public static void handleNotificationOpened(Context inContext, Bundle data) {
      sendNotificationOpened(inContext, data);

      // Open/Resume app when opening the notification.

      PackageManager packageManager = inContext.getPackageManager();

      boolean isCustom = false;

      Intent intent = new Intent();
      intent.setAction("com.onesignal.NotificationOpened.RECEIVE");
      intent.setPackage(inContext.getPackageName());

      List<ResolveInfo> resolveInfo = packageManager.queryBroadcastReceivers(intent, PackageManager.GET_INTENT_FILTERS);
      if (resolveInfo.size() > 0) {
         intent.putExtra("data", data);
         inContext.sendBroadcast(intent);
         isCustom = true;
      }

      resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
      if (resolveInfo.size() > 0) {
         isCustom = true;
         if (!isCustom)
            intent.putExtra("data", data);
         intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
         inContext.startActivity(intent);
      }

      if (!isCustom) {
         Log(LOG_LEVEL.DEBUG, "normal start");
         Intent launchIntent = inContext.getPackageManager().getLaunchIntentForPackage(inContext.getPackageName()).putExtra("data", data);
         launchIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
         inContext.startActivity(launchIntent);
      }

      if (initDone)
         runNotificationOpenedCallback(data, false);
   }

   private static void sendNotificationOpened(Context inContext, Bundle data) {
      try {
         JSONObject customJson = new JSONObject(data.getString("custom"));
         String notificationId = customJson.getString("i");

         // In some rare cases this can double fire, preventing that here.
         if (notificationId.equals(lastNotificationIdOpened))
            return;

         lastNotificationIdOpened = notificationId;

         JSONObject jsonBody = new JSONObject();
         jsonBody.put("app_id", getSavedAppId(inContext));
         jsonBody.put("player_id", getSavedUserId(inContext));
         jsonBody.put("opened", true);

         OneSignalRestClient.put(inContext, "notifications/" + customJson.getString("i"), jsonBody, new JsonHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
               logHttpError("sending Notification Opened Failed", statusCode, throwable, errorResponse);
            }
         });
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON to send notification opened.", t);
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

   private static String getSavedAppId() {
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

   private static void saveUserId(String inUserId) {
      userId = inUserId;
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_PLAYER_ID", userId);
      editor.commit();
   }

   static String GetRegistrationId() {
      if (registrationId == null && appContext != null) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         registrationId = prefs.getString("GT_REGISTRATION_ID", null);
      }
      return registrationId;
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

      currentSubscription = currentSubscription < UNSUBSCRIBE_VALUE ? currentSubscription : (enable ? 1 : UNSUBSCRIBE_VALUE);
      if (savedSubscription == currentSubscription)
         return;
      saveSubscription(currentSubscription);

      try {
         if (getUserId() != null) {
            JSONObject jsonBody = playerUpdateBaseJSON();
            jsonBody.put("notification_types", currentSubscription);
            postPlayerUpdate(jsonBody);
         }
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
         Log(LOG_LEVEL.ERROR, "Generating JSON setSubscription failed!", t);
      }
   }

   private static void saveSubscription(int value) {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putInt("ONESIGNAL_SUBSCRIPTION", value);
      editor.commit();
   }

   static int getSubscription(Context context) {
      final SharedPreferences prefs = getGcmPreferences(context);
      return prefs.getInt("ONESIGNAL_SUBSCRIPTION", 1);
   }

   private static void SaveRegistrationId(String inRegistrationId) {
      registrationId = inRegistrationId;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_REGISTRATION_ID", registrationId);
      editor.commit();
   }

   private static long GetUnsentActiveTime() {
      if (unSentActiveTime == -1 && appContext != null) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         unSentActiveTime = prefs.getLong("GT_UNSENT_ACTIVE_TIME", 0);
      }

      return unSentActiveTime;
   }

   private static void SaveUnsentActiveTime(long time) {
      unSentActiveTime = time;
      if (appContext == null)
         return;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putLong("GT_UNSENT_ACTIVE_TIME", time);
      editor.commit();
   }

   static SharedPreferences getGcmPreferences(Context context) {
      return context.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
   }

   private static LinkedList<String> notificationsReceivedStack;

   private static void GetNotificationsReceived(Context context) {
      if (notificationsReceivedStack == null && context != null ) {
         notificationsReceivedStack = new LinkedList<String>();

         final SharedPreferences prefs = getGcmPreferences(context);
         String jsonListStr = prefs.getString("GT_RECEIVED_NOTIFICATION_LIST", null);

         if (jsonListStr != null) {
            try {
               JSONArray notificationsReceivedList = new JSONArray(jsonListStr);
               for (int i = 0; i < notificationsReceivedList.length(); i++)
                  notificationsReceivedStack.push(notificationsReceivedList.getString(i));
            } catch (Throwable t) {
               Log(LOG_LEVEL.ERROR, "Failed to get notification received list.", t);
            }
         }
      }
   }

   private static void AddNotificationIdToList(String id, Context context) {
      GetNotificationsReceived(context);
      if (notificationsReceivedStack == null)
         return;

      if (notificationsReceivedStack.size() >= 10)
         notificationsReceivedStack.removeLast();

      notificationsReceivedStack.addFirst(id);

      JSONArray jsonArray = new JSONArray();
      String notificationId;
      for (int i = notificationsReceivedStack.size() - 1; i > -1; i--) {
         notificationId = notificationsReceivedStack.get(i);
         if (notificationId == null)
            continue;
         jsonArray.put(notificationsReceivedStack.get(i));
      }

      final SharedPreferences prefs = getGcmPreferences(context);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_RECEIVED_NOTIFICATION_LIST", jsonArray.toString());
      editor.commit();
   }

   static boolean isDuplicateNotification(String id, Context context) {
      GetNotificationsReceived(context);
      if (notificationsReceivedStack == null || id == null || "".equals(id))
         return false;

      if (notificationsReceivedStack.contains(id)) {
         Log(LOG_LEVEL.DEBUG, "Duplicate GCM message received, skipping processing. " + id);
         return true;
      }
      
      AddNotificationIdToList(id, context);
      return false;
   }
   
   static boolean isValidAndNotDuplicated(Context context, Bundle bundle) {
      if (bundle.isEmpty())
         return false;

      try {
         if (bundle.containsKey("custom")) {
            JSONObject customJSON = new JSONObject(bundle.getString("custom"));
           
            if (customJSON.has("i"))
               return !OneSignal.isDuplicateNotification(customJSON.getString("i"), context);
            else
               Log(LOG_LEVEL.DEBUG, "Not a OneSignal formated GCM message. No 'i' field in custom.");
         }
         else
            Log(LOG_LEVEL.DEBUG, "Not a OneSignal formated GCM message. No 'custom' field in the bundle.");
      } catch (Throwable t) {
         Log(LOG_LEVEL.INFO, "Could not parse bundle for duplicate.", t);
      }

      return false;
   }
}
