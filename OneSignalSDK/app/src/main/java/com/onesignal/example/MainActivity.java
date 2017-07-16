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

package com.onesignal.example;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.onesignal.*;
import com.onesignal.OneSignal.NotificationOpenedHandler;

import com.onesignal.example.iap.IabHelper;
import com.onesignal.example.iap.IabResult;

import java.util.Locale;


public class MainActivity extends Activity {

   IabHelper mHelper;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      Log.e("tet", Locale.getDefault().getLanguage());

      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.InAppAlert);
      OneSignal.sendTag("test3", "test7");
      //OneSignal.setSubscription(false);

      OneSignal.syncHashedEmail("test@onesignal.com");

//        OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
//            @Override
//            public void idsAvailable(String userId, String registrationId) {
//                Log.i("OneSignal Example:", "UserID: " + userId + ", RegId: " + (registrationId != null ? registrationId : "null"));
//
//                try {
//                    OneSignal.postNotification(new JSONObject("{'contents': {'en':'Test Message'}, 'include_player_ids': ['" + userId + "']}"), null);
//                    //OneSignal.postNotification(new JSONObject("{'contents': {'en':'Test Message'}, 'include_player_ids': ['" + "86480bb0-ef9a-11e4-8cf1-000c29917011', '2def6d7a-4395-11e4-890a-000c2940e62c" + "']}"), null);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
//        });


      // compute your public key and store it in base64EncodedPublicKey
      mHelper = new IabHelper(this, "sdafsfds");
      mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
         public void onIabSetupFinished(IabResult result) {
            if (!result.isSuccess()) {
               // Oh noes, there was a problem.
               Log.d("OneSignalExample", "Problem setting up In-app Billing: " + result);
            }
            // Hooray, IAB is fully set up!
         }
      });
   }

   public void onSubscribeClicked(View v) {
      OneSignal.setSubscription(true);
      OneSignal.promptLocation();

      String ns = Context.NOTIFICATION_SERVICE;
      NotificationManager nMgr = (NotificationManager) getApplicationContext().getSystemService(ns);
      nMgr.cancelAll();
   }

   public void onUnsubscribeClicked(View v) {
      OneSignal.setSubscription(false);
   }

//   @Override
//   protected void onPause() {
//      super.onPause();
//      if (mHelper != null)
//         mHelper.dispose();
//   }
//
//   @Override
//   public void onDestroy() {
//      super.onDestroy();
//      if (mHelper != null) mHelper.dispose();
//      mHelper = null;
//   }

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
      if (id == R.id.action_settings)
         return true;

      return super.onOptionsItemSelected(item);
   }

   // NotificationOpenedHandler is implemented in its own class instead of adding implements to MainActivity so we don't hold on to a reference of our first activity if it gets recreated.
   private class ExampleNotificationOpenedHandler implements NotificationOpenedHandler {
      /**
       * Callback to implement in your app to handle when a notification is opened from the Android status bar or
       * a new one comes in while the app is running.
       * This method is located in this activity as an example, you may have any class you wish implement NotificationOpenedHandler and define this method.
       *
       * @param openedResult The message string the user seen/should see in the Android status bar.
       */
      @Override
      public void notificationOpened(OSNotificationOpenResult openedResult) {
         Log.e("OneSignalExample", "body: " + openedResult.notification.payload.body);
         Log.e("OneSignalExample", "additional data: " + openedResult.notification.payload.additionalData);
         //Log.e("OneSignalExample", "additionalData: " + additionalData.toString());
      }
   }
}
