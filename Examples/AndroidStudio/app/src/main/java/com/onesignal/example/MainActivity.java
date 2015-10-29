package com.onesignal.example;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.onesignal.OneSignal;
import com.onesignal.OneSignal.NotificationOpenedHandler;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

   private static Activity currentActivity;
   
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      currentActivity = this;

      // NOTE: Please update your Google Project number and OneSignal id to yours below.
      // Pass in your app's Context, Google Project number, your OneSignal App ID, and NotificationOpenedHandler
      OneSignal.init(this, "703322744261", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", new ExampleNotificationOpenedHandler());

      OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            String text = "OneSignal UserID:\n" + userId + "\n\n";

            if (registrationId != null)
               text += "Google Registration Id:\n" + registrationId;
            else
               text += "Google Registration Id:\nCould not subscribe for push";

            TextView textView = (TextView)findViewById(R.id.debug_view);
            textView.setText(text);
         }
      });
   }

   // NotificationOpenedHandler is implemented in its own class instead of adding implements to MainActivity so we don't hold on to a reference of our first activity if it gets recreated.
   private class ExampleNotificationOpenedHandler implements NotificationOpenedHandler {
      /**
       * Callback to implement in your app to handle when a notification is opened from the Android status bar or
       * a new one comes in while the app is running.
       * This method is located in this activity as an example, you may have any class you wish implement NotificationOpenedHandler and define this method.
       *
       * @param message        The message string the user seen/should see in the Android status bar.
       * @param additionalData The additionalData key value pair section you entered in on onesignal.com.
       * @param isActive       Was the app in the foreground when the notification was received.
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

         new AlertDialog.Builder(MainActivity.currentActivity)
             .setTitle(messageTitle)
             .setMessage(messageBody)
             .setCancelable(true)
             .setPositiveButton("OK", null)
             .create().show();
      }
   }

   @Override
   protected void onPause() {
      super.onPause();
      OneSignal.onPaused();
   }
   @Override
   protected void onResume() {
      super.onResume();
      OneSignal.onResumed();
   }

}
