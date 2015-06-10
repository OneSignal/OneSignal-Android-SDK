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

import com.onesignal.OneSignal;

import android.content.Context;
import android.os.Bundle;

public class NotificationBundleProcessor {

   static final String DEFAULT_ACTION = "__DEFAULT__";

   public static void Process(Context context, Bundle bundle, Class<?> notificationOpenedActivityClass) {
      if (OneSignal.isValidAndNotDuplicated(context, bundle)) {
         boolean showAsAlert = OneSignal.getInAppAlertNotificationEnabled(context);
         boolean isActive = OneSignal.initDone && OneSignal.isForeground();
         boolean display = OneSignal.getNotificationsWhenActiveEnabled(context)
                           || showAsAlert
                           || !isActive;
         
         prepareBundle(bundle);

         BackgroundBroadcaster.Invoke(context, bundle, isActive);

         if (!bundle.containsKey("alert") || bundle.getString("alert") == null || bundle.getString("alert").equals(""))
            return;

         if (display)// Build notification from the Bundle
            GenerateNotification.fromBundle(context, bundle, notificationOpenedActivityClass, showAsAlert && isActive);
         else {
            final Bundle finalBundle = bundle;
            // Current thread is meant to be short lived. Make a new thread to do our OneSignal work on.
            new Thread(new Runnable() {
               public void run() {
                  OneSignal.handleNotificationOpened(finalBundle);
               }
            }).start();
         }
      }
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