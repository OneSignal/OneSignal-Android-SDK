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

package com.onesignal.example;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.onesignal.OneSignal;
import com.onesignal.OneSignal.NotificationOpenedHandler;
import com.onesignal.OneSignal.IdsAvailableHandler;

public class MainActivity extends Activity {

   static Activity currentActivity;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);
      currentActivity = this;

      // Enable Logging below to debug issues. (LogCat level, Visual level);
      // OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.DEBUG);

      // Pass in your app's Context
      OneSignal.startInit(this).setNotificationOpenedHandler(new ExampleNotificationOpenedHandler()).init();
   }

   // activity_main.xml defines the link to this method from the button.
   public void sendTag(View view) {
      OneSignal.sendTag("key", "valueAndroid");
   }

   // activity_main.xml defines the link to this method from the button.
   public void getIds(View view) {
      OneSignal.idsAvailable(new IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            TextView textView = (TextView) findViewById(R.id.textViewIds);
            String labelStr = "UserId: " + userId + "\n\nRegistrationId: ";
            if (registrationId != null)
               labelStr += registrationId;
            else
               labelStr += "Did not register. See Android LogCat for errors.";
            textView.setText(labelStr);
            Log.i("OneSignalExample", labelStr + "\n");
         }
      });
   }

   // NotificationOpenedHandler is implemented in its own class instead of adding implements to MainActivity so we don't hold on to a reference of our first activity if it gets recreated.
   private class ExampleNotificationOpenedHandler implements NotificationOpenedHandler {
      /**
       * Called when a notification is opened from the Android status bar or a new one comes in while the app is in focus.
       *
       * @param message
       *           The message string the user seen/should see in the Android status bar.
       * @param additionalData
       *           The additionalData key value pair section you entered in on onesignal.com.
       * @param isActive
       *           Was the app in the foreground when the notification was received.
       */
      @Override
      public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
         String messageTitle = "OneSignal Example", messageBody = message;

         try {
            if (additionalData != null) {
               if (additionalData.has("title"))
                  messageTitle = additionalData.getString("title");
               if (additionalData.has("actionSelected"))
                  messageBody += "\nPressed ButtonID: " + additionalData.getString("actionSelected");

               messageBody = message + "\n\nFull additionalData:\n" + additionalData.toString();
            }
         } catch (JSONException e) {
         }

         SafeAlertDialog(messageTitle, messageBody);
      }
   }

   // AlertDialogs do not show on Android 2.3 if they are trying to be displayed while the activity is pause.
   // We sleep for 500ms to wait for the activity to be ready before displaying.
   private static void SafeAlertDialog(final String msgTitle, final String msgBody) {
      new Thread(new Runnable() {
         public void run() {
				try {Thread.sleep(500);} catch(Throwable t) {}

            MainActivity.currentActivity.runOnUiThread(new Runnable() {
               public void run() {
                  new AlertDialog.Builder(MainActivity.currentActivity)
                     .setTitle(msgTitle)
                     .setMessage(msgBody)
                     .setCancelable(true)
                     .setPositiveButton("OK", null)
                     .create().show();
               }
            });
         }
      }).start();
   }
}