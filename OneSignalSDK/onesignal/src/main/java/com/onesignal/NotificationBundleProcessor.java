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
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;

import java.util.Random;
import java.util.Set;

class NotificationBundleProcessor {

   static final String DEFAULT_ACTION = "__DEFAULT__";

   static void ProcessFromGCMIntentService(Context context, Bundle bundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      try {
         boolean restoring = bundle.getBoolean("restoring", false);
         JSONObject jsonPayload = new JSONObject(bundle.getString("json_payload"));
         if (!restoring && OneSignal.notValidOrDuplicated(context, jsonPayload))
            return;

         if (bundle.containsKey("android_notif_id"))
            overrideSettings.androidNotificationId = bundle.getInt("android_notif_id");

         Process(context, restoring, jsonPayload, overrideSettings);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static int Process(Context context, boolean restoring, JSONObject jsonPayload, NotificationExtenderService.OverrideSettings overrideSettings) {
      boolean showAsAlert = OneSignal.getInAppAlertNotificationEnabled(context);

      int notificationId;
      if (overrideSettings != null && overrideSettings.androidNotificationId != null)
         notificationId = overrideSettings.androidNotificationId;
      else
         notificationId = new Random().nextInt();

      GenerateNotification.fromJsonPayload(context, restoring, notificationId, jsonPayload, showAsAlert && OneSignal.isAppActive(), overrideSettings);
      if (!restoring)
         saveNotification(context, jsonPayload, false, notificationId);
      return notificationId;
   }

   static JSONArray bundleAsJsonArray(Bundle bundle) {
      JSONArray jsonArray = new JSONArray();
      jsonArray.put(bundleAsJSONObject(bundle));
      return jsonArray;
   }


   static void saveNotification(Context context, Bundle bundle, boolean opened, int notificationId) {
      saveNotification(context, bundleAsJSONObject(bundle), opened, notificationId);
   }

   // Saving the notification provides the following:
   //   * Prevent duplicates.
   //   * Build summary notifications
   //   * Future - Re-display notifications after reboot and upgrade of app.
   //   * Future - Developer API to get a list of notifications.
   static void saveNotification(Context context, JSONObject jsonPayload, boolean opened, int notificationId) {
      try {
         JSONObject customJSON = new JSONObject(jsonPayload.optString("custom"));

         OneSignalDbHelper dbHelper = new OneSignalDbHelper(context);
         SQLiteDatabase writableDb = dbHelper.getWritableDatabase();

         ContentValues values = new ContentValues();
         values.put(NotificationTable.COLUMN_NAME_NOTIFICATION_ID, customJSON.optString("i"));
         if (jsonPayload.has("grp"))
            values.put(NotificationTable.COLUMN_NAME_GROUP_ID, jsonPayload.optString("grp"));

         values.put(NotificationTable.COLUMN_NAME_OPENED, opened ? 1 : 0);
         if (!opened)
            values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, notificationId);

         if (jsonPayload.has("title"))
            values.put(NotificationTable.COLUMN_NAME_TITLE, jsonPayload.optString("title"));
         values.put(NotificationTable.COLUMN_NAME_MESSAGE, jsonPayload.optString("alert"));

         values.put(NotificationTable.COLUMN_NAME_FULL_DATA, jsonPayload.toString());

         writableDb.insert(NotificationTable.TABLE_NAME, null, values);

         deleteOldNotifications(writableDb);

         if (!opened)
            BadgeCountUpdater.update(writableDb, context);

         writableDb.close();
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   // Clean up old records that have been dismissed or opened already after 1 week.
   static void deleteOldNotifications(SQLiteDatabase writableDb) {
      writableDb.delete(NotificationTable.TABLE_NAME,
          NotificationTable.COLUMN_NAME_CREATED_TIME + " < " + ((System.currentTimeMillis() / 1000) - 604800) + " AND " +
              "(" + NotificationTable.COLUMN_NAME_DISMISSED + " = 1 OR " + NotificationTable.COLUMN_NAME_OPENED + " = 1" + ")",
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
   static void prepareBundle(Bundle gcmBundle) {
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
}