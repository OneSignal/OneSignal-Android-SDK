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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.onesignal.OneSignalDbContract.NotificationTable;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

class NotificationBundleProcessor {

   static final String DEFAULT_ACTION = "__DEFAULT__";

   static void ProcessFromGCMIntentService(Context context, Bundle bundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      try {
         String jsonStrPayload = bundle.getString("json_payload");
         if (jsonStrPayload == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "json_payload key is nonexistent from bundle passed to ProcessFromGCMIntentService: " + bundle);
            return;
         }
   
         NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
         notifJob.restoring = bundle.getBoolean("restoring", false);
         notifJob.shownTimeStamp = bundle.getLong("timestamp");
         notifJob.jsonPayload = new JSONObject(jsonStrPayload);
         
         if (!notifJob.restoring && OneSignal.notValidOrDuplicated(context, notifJob.jsonPayload))
            return;

         if (bundle.containsKey("android_notif_id")) {
            if (overrideSettings == null)
               overrideSettings = new NotificationExtenderService.OverrideSettings();
            overrideSettings.androidNotificationId = bundle.getInt("android_notif_id");
         }
         
         notifJob.overrideSettings = overrideSettings;
         Process(notifJob);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static int Process(NotificationGenerationJob notifJob) {
      notifJob.showAsAlert = OneSignal.getInAppAlertNotificationEnabled() && OneSignal.isAppActive();
      processCollapseKey(notifJob);
      
      GenerateNotification.fromJsonPayload(notifJob);

      if (!notifJob.restoring) {
         saveNotification(notifJob, false);
         try {
            JSONObject jsonObject = new JSONObject(notifJob.jsonPayload.toString());
            jsonObject.put("notificationId", notifJob.getAndroidId());
            OneSignal.handleNotificationReceived(newJsonArray(jsonObject), true, notifJob.showAsAlert);
         } catch(Throwable t) {}
      }

      return notifJob.getAndroidId();
   }

   static JSONArray bundleAsJsonArray(Bundle bundle) {
      JSONArray jsonArray = new JSONArray();
      jsonArray.put(bundleAsJSONObject(bundle));
      return jsonArray;
   }


   static void saveNotification(Context context, Bundle bundle, boolean opened, int notificationId) {
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = bundleAsJSONObject(bundle);
      notifJob.overrideSettings = new NotificationExtenderService.OverrideSettings();
      notifJob.overrideSettings.androidNotificationId = notificationId;
      
      saveNotification(notifJob, opened);
   }
   
   // Saving the notification provides the following:
   //   * Prevent duplicates
   //   * Build summary notifications
   //   * Collapse key / id support - Used to lookup the android notification id later
   //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
   //   * Future - Public API to get a list of notifications
   static void saveNotification(NotificationGenerationJob notifiJob, boolean opened) {
      Context context = notifiJob.context;
      JSONObject jsonPayload = notifiJob.jsonPayload;
      
      try {
         JSONObject customJSON = new JSONObject(notifiJob.jsonPayload.optString("custom"));
   
         OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notifiJob.context);
         SQLiteDatabase writableDb = null;

         try {
            writableDb = dbHelper.getWritableDbWithRetries();
   
            writableDb.beginTransaction();
            
            deleteOldNotifications(writableDb);
            
            // Count any notifications with duplicated android notification ids as dismissed.
            // -1 is used to note never displayed
            if (notifiJob.overrideSettings.androidNotificationId != -1) {
               String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notifiJob.overrideSettings.androidNotificationId;
   
               ContentValues values = new ContentValues();
               values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);
   
               writableDb.update(NotificationTable.TABLE_NAME, values, whereStr, null);
               BadgeCountUpdater.update(writableDb, context);
            }

            // Save just received notification to DB
            ContentValues values = new ContentValues();
            values.put(NotificationTable.COLUMN_NAME_NOTIFICATION_ID, customJSON.optString("i"));
            if (jsonPayload.has("grp"))
               values.put(NotificationTable.COLUMN_NAME_GROUP_ID, jsonPayload.optString("grp"));
            if (jsonPayload.has("collapse_key") && !"do_not_collapse".equals(jsonPayload.optString("collapse_key")))
               values.put(NotificationTable.COLUMN_NAME_COLLAPSE_ID, jsonPayload.optString("collapse_key"));

            values.put(NotificationTable.COLUMN_NAME_OPENED, opened ? 1 : 0);
            if (!opened)
               values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, notifiJob.overrideSettings.androidNotificationId);
            
            if (notifiJob.getTitle() != null)
               values.put(NotificationTable.COLUMN_NAME_TITLE, notifiJob.getTitle().toString());
            values.put(NotificationTable.COLUMN_NAME_MESSAGE, notifiJob.getBody().toString());

            values.put(NotificationTable.COLUMN_NAME_FULL_DATA, jsonPayload.toString());

            writableDb.insertOrThrow(NotificationTable.TABLE_NAME, null, values);

            if (!opened)
               BadgeCountUpdater.update(writableDb, context);
            writableDb.setTransactionSuccessful();
         } catch (Exception e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error saving notification record! ", e);
         } finally {
            if (writableDb != null) {
               try {
                  writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
               } catch (Throwable t) {
                  OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
               }
            }
         }
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   // Clean up old records after 1 week.
   static void deleteOldNotifications(SQLiteDatabase writableDb) {
      writableDb.delete(NotificationTable.TABLE_NAME,
          NotificationTable.COLUMN_NAME_CREATED_TIME + " < " + ((System.currentTimeMillis() / 1000L) - 604800L),
          null);
   }

   static JSONObject bundleAsJSONObject(Bundle bundle) {
      JSONObject json = new JSONObject();
      Set<String> keys = bundle.keySet();

      for (String key : keys) {
         try {
            json.put(key, bundle.get(key));
         } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "bundleAsJSONObject error for key: " + key, e);
         }
      }

      return json;
   }

   // Format our short keys into more readable ones.
   private static void prepareBundle(Bundle gcmBundle) {
      if (gcmBundle.containsKey("o")) {
         try {
            JSONObject customJSON = new JSONObject(gcmBundle.getString("custom"));
            JSONObject additionalDataJSON;

            if (customJSON.has("a"))
               additionalDataJSON = customJSON.getJSONObject("a");
            else
               additionalDataJSON = new JSONObject();

            JSONArray buttons = new JSONArray(gcmBundle.getString("o"));
            gcmBundle.remove("o");
            for (int i = 0; i < buttons.length(); i++) {
               JSONObject button = buttons.getJSONObject(i);

               String buttonText = button.getString("n");
               button.remove("n");
               String buttonId;
               if (button.has("i")) {
                  buttonId = button.getString("i");
                  button.remove("i");
               } else
                  buttonId = buttonText;

               button.put("id", buttonId);
               button.put("text", buttonText);

               if (button.has("p")) {
                  button.put("icon", button.getString("p"));
                  button.remove("p");
               }
            }

            additionalDataJSON.put("actionButtons", buttons);
            additionalDataJSON.put("actionSelected", DEFAULT_ACTION);
            if (!customJSON.has("a"))
               customJSON.put("a", additionalDataJSON);

            gcmBundle.putString("custom", customJSON.toString());
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }
   }

   static OSNotificationPayload OSNotificationPayloadFrom(JSONObject currentJsonPayload) {
      OSNotificationPayload notification = new OSNotificationPayload();
      try {
         JSONObject customJson = new JSONObject(currentJsonPayload.optString("custom"));
         notification.notificationID = customJson.optString("i");
         notification.rawPayload = currentJsonPayload.toString();
         notification.additionalData = customJson.optJSONObject("a");
         notification.launchURL = customJson.optString("u", null);

         notification.body = currentJsonPayload.optString("alert", null);
         notification.title = currentJsonPayload.optString("title", null);
         notification.smallIcon = currentJsonPayload.optString("sicon", null);
         notification.bigPicture = currentJsonPayload.optString("bicon", null);
         notification.largeIcon = currentJsonPayload.optString("licon", null);
         notification.sound = currentJsonPayload.optString("sound", null);
         notification.groupKey = currentJsonPayload.optString("grp", null);
         notification.groupMessage = currentJsonPayload.optString("grp_msg", null);
         notification.smallIconAccentColor = currentJsonPayload.optString("bgac", null);
         notification.ledColor = currentJsonPayload.optString("ledc", null);
         String visibility = currentJsonPayload.optString("vis", null);
         if (visibility != null)
            notification.lockScreenVisibility = Integer.parseInt(visibility);
         notification.fromProjectNumber = currentJsonPayload.optString("from", null);
         notification.priority = currentJsonPayload.optInt("pri", 0);
         String collapseKey = currentJsonPayload.optString("collapse_key", null);
         if (!"do_not_collapse".equals(collapseKey))
            notification.collapseId = collapseKey;

         try {
            setActionButtons(notification);
         } catch (Throwable t) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationPayload.actionButtons values!", t);
         }

         try {
            setBackgroundImageLayout(notification, currentJsonPayload);
         } catch (Throwable t) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationPayload.backgroundImageLayout values!", t);
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationPayload values!", t);
      }

      return notification;
   }


   private static void setActionButtons(OSNotificationPayload notification) throws Throwable {
      if (notification.additionalData != null && notification.additionalData.has("actionButtons")) {
         JSONArray jsonActionButtons = notification.additionalData.getJSONArray("actionButtons");
         notification.actionButtons = new ArrayList<>();

         for (int i = 0; i < jsonActionButtons.length(); i++) {
            JSONObject jsonActionButton = jsonActionButtons.getJSONObject(i);
            OSNotificationPayload.ActionButton actionButton = new OSNotificationPayload.ActionButton();
            actionButton.id = jsonActionButton.optString("id", null);
            actionButton.text = jsonActionButton.optString("text", null);
            actionButton.icon = jsonActionButton.optString("icon", null);
            notification.actionButtons.add(actionButton);
         }
         notification.additionalData.remove("actionSelected");
         notification.additionalData.remove("actionButtons");
      }
   }

   private static void setBackgroundImageLayout(OSNotificationPayload notification, JSONObject currentJsonPayload) throws Throwable {
      String jsonStrBgImage = currentJsonPayload.optString("bg_img", null);
      if (jsonStrBgImage != null) {
         JSONObject jsonBgImage = new JSONObject(jsonStrBgImage);
         notification.backgroundImageLayout = new OSNotificationPayload.BackgroundImageLayout();
         notification.backgroundImageLayout.image = jsonBgImage.optString("img");
         notification.backgroundImageLayout.titleTextColor = jsonBgImage.optString("tc");
         notification.backgroundImageLayout.bodyTextColor = jsonBgImage.optString("bc");
      }
   }

   private static void processCollapseKey(NotificationGenerationJob notifJob) {
      if (notifJob.restoring)
         return;
      if (!notifJob.jsonPayload.has("collapse_key") || "do_not_collapse".equals(notifJob.jsonPayload.optString("collapse_key")))
         return;
      String collapse_id = notifJob.jsonPayload.optString("collapse_key");


      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notifJob.context);
      Cursor cursor = null;

      try {
         SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
         cursor = readableDb.query(
               NotificationTable.TABLE_NAME,
               new String[]{NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID}, // retColumn
               NotificationTable.COLUMN_NAME_COLLAPSE_ID + " = ? AND " +
                     NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                     NotificationTable.COLUMN_NAME_OPENED + " = 0 ",
               new String[] {collapse_id},
               null, null, null);

         if (cursor.moveToFirst()) {
            int androidNotificationId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
            notifJob.setAndroidIdWithOutOverriding(androidNotificationId);
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not read DB to find existing collapse_key!", t);
      }
      finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
   }

   // Return true to count the payload as processed.
   static ProcessedBundleResult processBundle(Context context, final Bundle bundle) {
      ProcessedBundleResult result = new ProcessedBundleResult();
      
      // Not a OneSignal GCM message
      if (OneSignal.getNotificationIdFromGCMBundle(bundle) == null)
         return result;
      result.isOneSignalPayload = true;

      prepareBundle(bundle);
      
      // NotificationExtenderService still makes additional checks such as notValidOrDuplicated
      Intent overrideIntent = NotificationExtenderService.getIntent(context);
      if (overrideIntent != null) {
         overrideIntent.putExtra("json_payload", bundleAsJSONObject(bundle).toString());
         overrideIntent.putExtra("timestamp", System.currentTimeMillis() / 1000L);
         WakefulBroadcastReceiver.startWakefulService(context, overrideIntent);
         result.hasExtenderService = true;
         return result;
      }
   
      // We already ran a getNotificationIdFromGCMBundle == null check above so this will only be true for dups
      result.isDup = OneSignal.notValidOrDuplicated(context, bundleAsJSONObject(bundle));
      if (result.isDup)
         return result;

      String alert = bundle.getString("alert");
      boolean display = shouldDisplay(alert != null && !"".equals(alert));

      // Save as a opened notification to prevent duplicates.
      if (!display) {
         saveNotification(context, bundle, true, -1);
         // Current thread is meant to be short lived.
         //    Make a new thread to do our OneSignal work on.
         new Thread(new Runnable() {
            public void run() {
               OneSignal.handleNotificationReceived(bundleAsJsonArray(bundle), false, false);
            }
         }, "OS_PROC_BUNDLE").start();
      }
      
      return result;
   }

   static boolean shouldDisplay(boolean hasBody) {
      boolean showAsAlert = OneSignal.getInAppAlertNotificationEnabled();
      boolean isActive = OneSignal.isAppActive();
      return hasBody &&
                (OneSignal.getNotificationsWhenActiveEnabled()
              || showAsAlert
              || !isActive);
   }

   static JSONArray newJsonArray(JSONObject jsonObject) {
      JSONArray jsonArray = new JSONArray();
      jsonArray.put(jsonObject);
      return jsonArray;
   }
   
   
   static class ProcessedBundleResult {
      boolean isOneSignalPayload;
      boolean hasExtenderService;
      boolean isDup;
      
      boolean processed() {
         return !isOneSignalPayload || hasExtenderService || isDup;
      }
   }
}