package com.onesignal.example;

import android.app.Activity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.onesignal.OneSignal;
import com.onesignal.OneSignal.NotificationOpenedHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.HashMap;


public class MainActivity extends ActionBarActivity {

    private static Activity currentActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentActivity = this;

        // Enable Logging below to debug issues. (LogCat level, Visual level);
        // OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.DEBUG);

        // Pass in your app's Context, Google Project number, OneSignal App ID, and a NotificationOpenedHandler
        OneSignal.init(this, "703322744261", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", new ExampleNotificationOpenedHandler());
        //OneSignal.init(this, "703322744261", "5eb5a37e-b458-11e3-ac11-000c2940e62c", new ExampleNotificationOpenedHandler());
        OneSignal.enableInAppAlertNotification(false);
        OneSignal.enableNotificationsWhenActive(true);
        //OneSignal.setSubscription(false);

        OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
            @Override
            public void idsAvailable(String userId, String registrationId) {
                Log.i("OneSignal Example:", "UserID: " + userId + ", RegId: " + (registrationId != null ? registrationId : "null"));

                try {
                    OneSignal.postNotification(new JSONObject("{'contents': {'en':'Test Message'}, 'include_player_ids': ['" + userId + "']}"), null);
                    //OneSignal.postNotification(new JSONObject("{'contents': {'en':'Test Message'}, 'include_player_ids': ['" + "86480bb0-ef9a-11e4-8cf1-000c29917011', '2def6d7a-4395-11e4-890a-000c2940e62c" + "']}"), null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // NotificationOpenedHandler is implemented in its own class instead of adding implements to MainActivity so we don't hold on to a reference of our first activity if it gets recreated.
    @OneSignal.TiedToCurrentActivity
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
            String messageTitle = "OneSignal Example:" + isActive, messageBody = message;

            try {
                if (additionalData != null) {
                    if (additionalData.has("title"))
                        messageTitle = additionalData.getString("title");
                    if (additionalData.has("actionSelected"))
                        messageBody += "\nPressed ButtonID: " + additionalData.getString("actionSelected");

                    messageBody = message + "\n\nFull additionalData:\n" + additionalData.toString();
                }
            } catch (JSONException e) {}

            new AlertDialog.Builder(MainActivity.currentActivity)
                    .setTitle(messageTitle)
                    .setMessage(messageBody)
                    .setCancelable(true)
                    .setPositiveButton("OK", null)
                    .create().show();
        }
    }
}
