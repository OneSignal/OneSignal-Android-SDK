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
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;

import com.onesignal.*;
import com.onesignal.OneSignal.NotificationOpenedHandler;

import com.onesignal.example.iap.IabHelper;
import com.onesignal.example.iap.IabResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.ArrayList;


public class MainActivity extends Activity implements OSEmailSubscriptionObserver, OSPermissionObserver, OSSubscriptionObserver, NotificationOpenedHandler, OneSignal.NotificationReceivedHandler {

   IabHelper mHelper;

   private int[] interactiveViewIds = new int[] {R.id.subscribe, R.id.unsubscribe, R.id.sendTags, R.id.getTags, R.id.logoutEmail, R.id.setEmail, R.id.emailTextView};

   private TextView debugTextView;
   private TextView emailTextView;
   private Button consentButton;
   private Button setEmailButton;
   private Button logoutEmailButton;

   private int sendTagsCounter = 1;
   private boolean addedObservers = false;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      this.consentButton = (Button)this.findViewById(R.id.consentButton);
      this.setEmailButton = (Button)this.findViewById(R.id.setEmail);
      this.logoutEmailButton = (Button)this.findViewById(R.id.logoutEmail);

      if (OneSignal.requiresUserPrivacyConsent()) {
         //disable all interactive views except consent button
         this.changeInteractiveViewsEnabled(false);
         consentButton.setText("Provide Consent");
      } else {
         consentButton.setText("Revoke Consent");

         this.addObservers();
      }

      this.debugTextView = this.findViewById(R.id.debugTextView);
      this.emailTextView = this.findViewById(R.id.emailTextView);

      OSPermissionSubscriptionState state = OneSignal.getPermissionSubscriptionState();

      this.didGetEmailStatus(state.getEmailSubscriptionStatus().getSubscribed());

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

   private void updateTextView(final String newText) {
      //ensure that we only update the text view from the main thread.

      Handler mainHandler = new Handler(Looper.getMainLooper());

      Runnable runnable = new Runnable() {
         @Override
         public void run() {
            debugTextView.setText(newText);
         }
      };

      mainHandler.post(runnable);
   }

   private void addObservers() {
      this.addedObservers = true;

      Log.d("onesignal", "adding observers");

      OneSignal.addEmailSubscriptionObserver(this);

      OneSignal.addSubscriptionObserver(this);

      OneSignal.addPermissionObserver(this);
   }

   @Override
   public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {
      updateTextView("Email Subscription State Changed: " + stateChanges.toString());

      didGetEmailStatus(stateChanges.getTo().getSubscribed());
   }

   @Override
   public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
      updateTextView("Push Subscriuption State Changed: " + stateChanges.toString());
   }

   @Override
   public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
      updateTextView("Permission State Changed: " + stateChanges.toString());
   }

   @Override
   public void notificationOpened(OSNotificationOpenResult result) {
      updateTextView("Opened Notification: " + result.toString());
   }

   @Override
   public void notificationReceived(OSNotification notification) {
      updateTextView("Received Notification: " + notification.toString());
   }

   public void didGetEmailStatus(boolean hasEmailUserId) {
      this.setEmailButton.setEnabled(!hasEmailUserId);
      this.logoutEmailButton.setEnabled(hasEmailUserId);
   }

   public void onSubscribeClicked(View v) {
      OneSignal.setSubscription(true);
      OneSignal.promptLocation();

      String ns = Context.NOTIFICATION_SERVICE;
      NotificationManager nMgr = (NotificationManager) getApplicationContext().getSystemService(ns);
      nMgr.cancelAll();
   }

   private void changeInteractiveViewsEnabled(boolean enabled) {
      for (int viewId : this.interactiveViewIds) {
         this.findViewById(viewId).setEnabled(enabled);
      }
   }

   public void onUnsubscribeClicked(View v) {
      OneSignal.setSubscription(false);

      this.debugTextView.setText("Unsubscribed");
   }


   public void onSendTagsClicked(View v) {
      try {
         OneSignal.sendTags(new JSONObject("{\"counter\" : " + String.valueOf(sendTagsCounter) + ", \"test_value\" : \"test_key\"}"), new OneSignal.ChangeTagsUpdateHandler() {
            @Override
            public void onSuccess(JSONObject tags) {
               updateTextView("Successfully sent tags: " + tags.toString());
            }

            @Override
            public void onFailure(OneSignal.SendTagsError error) {
               updateTextView("Failed to send tags: " + error.toString());
            }
         });

         sendTagsCounter++;
      } catch (JSONException exception) {
         updateTextView("Failed to serialize tags into JSON: " + exception.getMessage());
      }
   }

   public void onConsentButtonClicked(View v) {
      boolean consentRequired = OneSignal.requiresUserPrivacyConsent();

      //flips consent
      OneSignal.provideUserConsent(consentRequired);
      this.changeInteractiveViewsEnabled(consentRequired);
      this.consentButton.setText(consentRequired ? "Revoke Consent" : "Provide Consent");

      if (consentRequired && this.addedObservers == false)
         addObservers();
   }

   public void onGetTagsClicked(View v) {
      OneSignal.getTags(new OneSignal.GetTagsHandler() {
         @Override
         public void tagsAvailable(final JSONObject tags) {
            updateTextView("Got Tags: " + tags.toString());
         }
      });
   }

   public void onSetEmailClicked(View v) {
      String email = emailTextView.getText().toString();

      if (email.length() == 0) {
         Log.d("onesingal", "email not set");
         return;
      }

      OneSignal.setEmail(email, new OneSignal.EmailUpdateHandler() {
         @Override
         public void onSuccess() {
            updateTextView("Successfully set email");
         }

         @Override
         public void onFailure(OneSignal.EmailUpdateError error) {
            updateTextView("Failed to set email with error: " + error.getMessage());
         }
      });
   }

   public void onLogoutEmailClicked(View v) {
      OneSignal.logoutEmail(new OneSignal.EmailUpdateHandler() {
         @Override
         public void onSuccess() {
            updateTextView("Successfully logged out of email");
         }

         @Override
         public void onFailure(OneSignal.EmailUpdateError error) {
            updateTextView("Failed to logout of email with error: " + error.getMessage());
         }
      });
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
