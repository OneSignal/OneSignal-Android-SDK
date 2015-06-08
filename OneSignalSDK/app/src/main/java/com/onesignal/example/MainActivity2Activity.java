package com.onesignal.example;

import android.app.Activity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;

import com.onesignal.OneSignal;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity2Activity extends ActionBarActivity {

    private static Activity currentActivity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_activity2);

        currentActivity = this;

        OneSignal.init(this, "703322744261", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", new ExampleNotificationOpenedHandler());
        OneSignal.enableNotificationsWhenActive(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_activity2, menu);
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

    private class ExampleNotificationOpenedHandler implements OneSignal.NotificationOpenedHandler {
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
            String messageTitle = "OneSignal Example2:" + isActive, messageBody = message;

            try {
                if (additionalData != null) {
                    if (additionalData.has("title"))
                        messageTitle = additionalData.getString("title");
                    if (additionalData.has("actionSelected"))
                        messageBody += "\nPressed ButtonID: " + additionalData.getString("actionSelected");

                    messageBody = message + "\n\nFull additionalData:\n" + additionalData.toString();
                }
            } catch (JSONException e) {}

            new AlertDialog.Builder(MainActivity2Activity.currentActivity)
                    .setTitle(messageTitle)
                    .setMessage(messageBody)
                    .setCancelable(true)
                    .setPositiveButton("OK", null)
                    .create().show();
        }
    }
}
