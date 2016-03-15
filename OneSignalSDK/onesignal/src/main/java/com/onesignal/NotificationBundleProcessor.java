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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.onesignal.OneSignalDbContract.NotificationTable;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import java.util.Set;

public class NotificationBundleProcessor {

   static final String DEFAULT_ACTION = "__DEFAULT__";

   public static void Process(Context context, Bundle bundle) {
      if (OneSignal.isValidAndNotDuplicated(context, bundle)) {
         boolean showAsAlert = OneSignal.getInAppAlertNotificationEnabled(context);
         boolean isActive = OneSignal.initDone && OneSignal.isForeground();
         boolean display = OneSignal.getNotificationsWhenActiveEnabled(context)
                           || showAsAlert
                           || !isActive;
         
         prepareBundle(bundle);

         BackgroundBroadcaster.Invoke(context, bundle, isActive);

         if (OneSignal.getPreventDisplayingNotificationEnabled(context)) return;

         if (!bundle.containsKey("alert") || bundle.getString("alert") == null || bundle.getString("alert").equals(""))
            return;

         int notificationId = -1;

         if (display)// Build notification from the Bundle
            notificationId = GenerateNotification.fromBundle(context, bundle, showAsAlert && isActive);
         else {
            final Bundle finalBundle = bundle;
            // Current thread is meant to be short lived. Make a new thread to do our OneSignal work on.
            new Thread(new Runnable() {
               public void run() {
                  OneSignal.handleNotificationOpened(NotificationBundleProcessor.bundleAsJsonArray(finalBundle));
               }
            }).start();
         }

         saveNotification(context, bundle, !display, notificationId);
      }
   }

   private static void saveNotification(Context context, Bundle bundle, boolean opened, int notificationId) {
      try {
         JSONObject customJSON = new JSONObject(bundle.getString("custom"));

         OneSignalDbHelper dbHelper = new OneSignalDbHelper(context);
         SQLiteDatabase writableDb = dbHelper.getWritableDatabase();

         ContentValues values = new ContentValues();
         values.put(NotificationTable.COLUMN_NAME_NOTIFICATION_ID, customJSON.getString("i"));
         if (bundle.containsKey("grp"))
            values.put(NotificationTable.COLUMN_NAME_GROUP_ID, bundle.getString("grp"));

         values.put(NotificationTable.COLUMN_NAME_OPENED, opened ? 1 : 0);
         if (!opened)
            values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, notificationId);

         if (bundle.containsKey("title"))
            values.put(NotificationTable.COLUMN_NAME_TITLE, bundle.getString("title"));
         values.put(NotificationTable.COLUMN_NAME_MESSAGE, bundle.getString("alert"));

         values.put(NotificationTable.COLUMN_NAME_FULL_DATA, bundleAsJSONObject(bundle).toString());

         writableDb.insert(NotificationTable.TABLE_NAME, null, values);

         // Clean up old records that have been dismissed or opened already after 1 week.
         writableDb.delete(NotificationTable.TABLE_NAME,
               NotificationTable.COLUMN_NAME_CREATED_TIME + " < " + ((System.currentTimeMillis() / 1000) - 604800) + " AND " +
                     "(" + NotificationTable.COLUMN_NAME_DISMISSED + " = 1 OR " + NotificationTable.COLUMN_NAME_OPENED + " = 1" + ")",
               null);

         writableDb.close();
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   public static JSONArray newJsonArray(JSONObject jsonObject) {
      JSONArray jsonArray = new JSONArray();
      jsonArray.put(jsonObject);
      return jsonArray;
   }

   public static JSONArray bundleAsJsonArray(Bundle bundle) {
      JSONArray jsonArray = new JSONArray();
      jsonArray.put(bundleAsJSONObject(bundle));
      return jsonArray;
   }

   public static JSONObject bundleAsJSONObject(Bundle bundle) {
      JSONObject json = new JSONObject();
      Set<String> keys = bundle.keySet();

      for (String key : keys) {
         try {
            json.put(key, bundle.get(key));
         } catch (JSONException e) {}
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
}