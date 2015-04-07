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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

import org.apache.http.Header;
import org.json.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;

import com.loopj.android.http.*;
import com.stericson.RootTools.internal.RootToolsInternalMethods;

public class OneSignal {
   
   public enum LOG_LEVEL {
      NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
   }

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

   /**
    * Tag used on log messages.
    */
   static final String TAG = "OneSignal";

   private static String appId;
   private static Activity appContext;
   
   private static LOG_LEVEL visualogLevel = LOG_LEVEL.NONE;
   private static LOG_LEVEL logCatLevel = LOG_LEVEL.INFO;

   private static String registrationId, userId = null;
   private static JSONObject pendingTags;

   private static NotificationOpenedHandler notificationOpenedHandler;

   static boolean initDone;
   private static boolean foreground = true;

   private static IdsAvailableHandler idsAvailableHandler;

   private static long lastTrackedTime, unSentActiveTime = -1;

   private static String lastNotificationIdOpenned;
   private static TrackGooglePurchase trackGooglePurchase;
   private static TrackAmazonPurchase trackAmazonPurchase;

   public static final String VERSION = "010800";

   private static PushRegistrator pushRegistrator;
   private static AdvertisingIdentifierProvider mainAdIdProvider = new AdvertisingIdProviderGPS();

   private static int deviceType;

   public static void init(Activity context, String googleProjectNumber, String oneSignalAppId) {
      init(context, googleProjectNumber, oneSignalAppId, null);
   }

   public static void init(Activity context, String googleProjectNumber, String oneSignalAppId, NotificationOpenedHandler inNnotificationOpenedHandler) {
      if (initDone)
         return;

      appId = oneSignalAppId;
      appContext = context;
      notificationOpenedHandler = inNnotificationOpenedHandler;
      lastTrackedTime = SystemClock.elapsedRealtime();

      try {
         Class.forName("com.amazon.device.iap.PurchasingListener");
         trackAmazonPurchase = new TrackAmazonPurchase(appContext);
      } catch (ClassNotFoundException e) {
      }

      try {
         Class.forName("com.amazon.device.messaging.ADM");
         pushRegistrator = new PushRegistratorADM();
         deviceType = 2;
      } catch (ClassNotFoundException e) {
         pushRegistrator = new PushRegistratorGPS();
         deviceType = 1;
      }

      // Re-register user if the app id changed, this might happen when a dev is testing.
      String oldAppId = GetSavedAppId();
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
         @Override
         public void complete(String id) {
            registerUser(id);
         }
      });

      // Called from tapping on a Notification from the status bar when the activity is completely dead and not open in any state.
      if (appContext.getIntent() != null && appContext.getIntent().getBundleExtra("data") != null)
         runNotificationOpenedCallback(appContext.getIntent().getBundleExtra("data"), false, true);

      if (TrackGooglePurchase.CanTrack(appContext))
         trackGooglePurchase = new TrackGooglePurchase(appContext);

      initDone = true;
   }
   
   public static void setLogLevel(LOG_LEVEL inLogCatLevel, LOG_LEVEL inVisualLogLevel) {
      logCatLevel = inLogCatLevel; visualogLevel = inVisualLogLevel;
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
   
   
   public static void Log(LOG_LEVEL level, String message) {
      Log(level, message, null);
   }
   
   public static void Log(final LOG_LEVEL level, String message, Throwable throwable) {
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
      
      if (appContext != null && level.compareTo(visualogLevel) < 1) {
         try {
            String fullMessgae = message + "\n";
            if (throwable != null) {
               fullMessgae += throwable.getMessage();
               StringWriter sw = new StringWriter();
               PrintWriter pw = new PrintWriter(sw);
               throwable.printStackTrace(pw);
               fullMessgae += sw.toString();
            }
            
            final String finalFullMessage = fullMessgae;
            
            appContext.runOnUiThread(new Runnable() {
               @Override
               public void run() {
                  new AlertDialog.Builder(appContext)
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

   public static void onPaused() {
      foreground = false;

      if (trackAmazonPurchase != null)
         trackAmazonPurchase.checkListener();

      long time_elapsed = (long) (((SystemClock.elapsedRealtime() - lastTrackedTime) / 1000d) + 0.5d);
      if (time_elapsed < 0 || time_elapsed > 604800)
         return;

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
               Log(LOG_LEVEL.WARN, "HTTP sending on_focus Failed.", throwable);
            }
         });

         SaveUnsentActiveTime(0);
         lastTrackedTime = SystemClock.elapsedRealtime();
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

   private static void registerUser(String id) {
      if (id != null)
         SaveRegistractionId(id);

      // Must run in its own thread due to the use of getAdvertisingId
      new Thread(new Runnable() {
         public void run() {
            try {
               JSONObject jsonBody = new JSONObject();
               jsonBody.put("app_id", appId);
               jsonBody.put("device_type", deviceType);
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

               jsonBody.put("device_os", android.os.Build.VERSION.RELEASE);
               jsonBody.put("device_model", android.os.Build.MODEL);
               jsonBody.put("timezone", Calendar.getInstance().getTimeZone().getRawOffset() / 1000); // converting from milliseconds to seconds
               jsonBody.put("language", Locale.getDefault().getLanguage());
               jsonBody.put("sdk", VERSION);
               try {
                  jsonBody.put("game_version", "" + appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0).versionCode);
               } catch (PackageManager.NameNotFoundException e) {
               }

               addNetType(jsonBody);

               if (RootToolsInternalMethods.isRooted())
                  jsonBody.put("rooted", true);

               try {
                  Field[] fields = Class.forName(appContext.getPackageName() + ".R$raw").getFields();
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
                     try {
                        if (response.has("id")) {
                           saveUserId(response.getString("id"));
                           if (pendingTags != null) {
                              sendTags(pendingTags);
                              pendingTags = null;
                           }

                           if (idsAvailableHandler != null) {
                              appContext.runOnUiThread(new Runnable() {
                                 @Override
                                 public void run() {
                                    idsAvailableHandler.idsAvailable(getUserId(), GetRegistrationId());
                                    idsAvailableHandler = null;
                                 }
                              });
                           }
                           
                           Log(LOG_LEVEL.INFO, "Device registerd with OneSignal, UserId=" + response.getString("id"));
                        }
                     } catch (Throwable t) {
                        Log(LOG_LEVEL.ERROR, "ERROR parsing on_session or create JSON Response.", t);
                     }
                  }

                  @Override
                  public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                     Log(LOG_LEVEL.WARN, "HTTP Create or on_session for user failed to send!", throwable);
                  }
               };
               OneSignalRestClient.postSync(appContext, urlStr, jsonBody, jsonHandler);

            } catch (Throwable t) { // JSONException and UnsupportedEncodingException
               Log(LOG_LEVEL.ERROR, "Generating JSON create or on_session for user failed!", t);
            }
         }
      }).start();
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
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("app_id", appId);
            jsonBody.put("tags", keyValues);
            addNetType(jsonBody);

            OneSignalRestClient.put(appContext, "players/" + getUserId(), jsonBody, new JsonHttpResponseHandler() {
               @Override
               public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                  Log(LOG_LEVEL.WARN, "HTTP sendTags failed!", throwable);
               }
            });
         }
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
         Log(LOG_LEVEL.ERROR, "Generating JSON sendTags failed!", t);
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
            Log(LOG_LEVEL.WARN, "HTTP failed to getTags.", throwable);
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

   private static void runNotificationOpenedCallback(final Bundle data, final boolean isActive, boolean isUiThread) {
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

            final JSONObject finalAdditionalDataJSON = additionalDataJSON;
            Runnable callBack = new Runnable() {
               @Override
               public void run() {
                  notificationOpenedHandler.notificationOpened(data.getString("alert"), finalAdditionalDataJSON, isActive);
               }
            };

            if (isUiThread)
               callBack.run();
            else
               appContext.runOnUiThread(callBack);
         }
      } catch (Throwable t) {
         Log(LOG_LEVEL.ERROR, "Failed to run callback from notifiation opened.", t);
      }
   }

   // Called when receiving GCM message when app is open and in focus.
   static void handleNotificationOpened(Bundle data) {
      sendNotificationOpened(appContext, data);
      runNotificationOpenedCallback(data, true, false);
   }

   // Called when opening a notification when the app is suspended in the background.
   public static void handleNotificationOpened(Context inContext, Bundle data) {
      sendNotificationOpened(inContext, data);

      // Open/Resume app when opening the notification.
      Intent launchIntent = inContext.getPackageManager().getLaunchIntentForPackage(inContext.getPackageName()).putExtra("data", data);
      launchIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      inContext.startActivity(launchIntent);
      if (initDone)
         runNotificationOpenedCallback(data, false, false);
   }

   private static void sendNotificationOpened(Context inContext, Bundle data) {
      try {
         JSONObject customJson = new JSONObject(data.getString("custom"));
         String notificationId = customJson.getString("i");

         // In some rare cases this can double fire, preventing that here.
         if (notificationId.equals(lastNotificationIdOpenned))
            return;

         lastNotificationIdOpenned = notificationId;

         JSONObject jsonBody = new JSONObject();
         jsonBody.put("app_id", getSavedAppId(inContext));
         jsonBody.put("player_id", getSavedUserId(inContext));
         jsonBody.put("opened", true);

         OneSignalRestClient.put(inContext, "notifications/" + customJson.getString("i"), jsonBody, new JsonHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
               Log(LOG_LEVEL.WARN, "HTTP sending Notification Opened Failed", throwable);
            }
         });
      } catch (Throwable t) { // JSONException and UnsupportedEncodingException
         Log(LOG_LEVEL.ERROR, "Failed to generate JSON to send notification opened.", t);
      }
   }

   private static void SaveAppId(String appId) {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_APP_ID", appId);
      editor.commit();
   }

   private static String GetSavedAppId() {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      return prefs.getString("GT_APP_ID", null);
   }

   private static String getSavedAppId(Context inContext) {
      final SharedPreferences prefs = getGcmPreferences(inContext);
      return prefs.getString("GT_APP_ID", null);
   }

   private static String getSavedUserId(Context inContext) {
      final SharedPreferences prefs = getGcmPreferences(inContext);
      return prefs.getString("GT_PLAYER_ID", null);
   }

   static String getUserId() {
      if (userId == null) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         userId = prefs.getString("GT_PLAYER_ID", null);
      }
      return userId;
   }

   private static void saveUserId(String inUserId) {
      userId = inUserId;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_PLAYER_ID", userId);
      editor.commit();
   }

   static String GetRegistrationId() {
      if (registrationId == null) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         registrationId = prefs.getString("GT_REGISTRATION_ID", null);
      }
      return registrationId;
   }

   // If true(default) - Device will always vibrate unless the device is in silent mode.
   // If false - Device will only vibrate when the device is set on it's vibrate only mode.
   public static void enableVibrate(boolean enable) {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("GT_VIBRATE_ENABLED", enable);
      editor.commit();
   }

   static boolean getVibrate(Context appContext) {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      return prefs.getBoolean("GT_VIBRATE_ENABLED", true);
   }

   // If true(default) - Sound plays when receiving notification. Vibrates when device is on vibrate only mode.
   // If false - Only vibrates unless EnableVibrate(false) was set.
   public static void enableSound(boolean enable) {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putBoolean("GT_SOUND_ENABLED", enable);
      editor.commit();
   }

   static boolean getSoundEnabled(Context appContext) {
      final SharedPreferences prefs = getGcmPreferences(appContext);
      return prefs.getBoolean("GT_SOUND_ENABLED", true);
   }

   private static void SaveRegistractionId(String inRegistrationId) {
      registrationId = inRegistrationId;
      final SharedPreferences prefs = getGcmPreferences(appContext);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("GT_REGISTRATION_ID", registrationId);
      editor.commit();
   }

   private static long GetUnsentActiveTime() {
      if (unSentActiveTime == -1) {
         final SharedPreferences prefs = getGcmPreferences(appContext);
         unSentActiveTime = prefs.getLong("GT_UNSENT_ACTIVE_TIME", 0);
      }

      return unSentActiveTime;
   }

   private static void SaveUnsentActiveTime(long time) {
      unSentActiveTime = time;
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
      if (notificationsReceivedStack == null) {
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
