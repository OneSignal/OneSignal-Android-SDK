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

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.onesignal.OneSignal.NotificationOpenedHandler;
import com.onesignal.example.OneSignalExampleApp;
import com.onesignal.example.R;
import com.onesignal.example.iap.IabHelper;
import com.onesignal.example.iap.IabResult;
import com.onesignal.outcomes.model.OSOutcomeEventParams;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends Activity implements OSEmailSubscriptionObserver, OSPermissionObserver, OSSubscriptionObserver, NotificationOpenedHandler, OneSignal.NotificationReceivedHandler {

   IabHelper mHelper;

   private int[] interactiveViewIds = new int[]{
           R.id.subscribe,
           R.id.unsubscribe,
           R.id.emailEditText,
           R.id.setEmailButton,
           R.id.logoutEmailButton,
           R.id.externalUserIdEditText,
           R.id.setExternalUserId,
           R.id.removeExternalUserId,
           R.id.sendTags,
           R.id.getTags,
           R.id.sendEvent,
           R.id.postNotification,
           R.id.postNotificationAsync,
           R.id.postNotificationGroupCheckBox,
           R.id.postNotificationAsyncGroupCheckBox};

   private EditText externalUserIdEditText;
   private TextView debugTextView;
   private TextView outcomeName;
   private TextView outcomeValueName;
   private TextView outcomeValue;
   private TextView outcomeUnique;
   private Button consentButton;
   private EditText emailEditText;
   private Button setEmailButton;
   private Button logoutEmailButton;
   private Button sendEvent;
   private Button postNotifButton;
   private Button postNotifAsyncButton;
   private CheckBox postNotifGroupCheckBox;
   private CheckBox postNotifAsyncGroupCheckBox;

   // Session and Focus Tracking Debug Fields
   private TextView sessionFocusTitleTextView;
   private ProgressBar sessionFocusProgressBar;
   private LinearLayout sessionFocusLinearLayout;
   private TextView onSessionForegroundTimeTextView;
   private TextView onFocusBackgroundTimeTextView;

   // Outcome V2 Debug Fields
   private TextView outcomeV2TitleTextView;
   private ProgressBar outcomeV2ProgressBar;
   private LinearLayout outcomeV2LinearLayout;

   // IAM V2 Debug Fields
   private TextView iamV2TitleTextView;
   private ProgressBar iamV2ProgressBar;
   private LinearLayout iamV2LinearLayout;
   private EditText iamV2TagEditText;
   private EditText iamV2OutcomeEditText;

   private int sendTagsCounter = 1;
   private boolean addedObservers = false;
   private TextView iamHost;
   private TextView triggerKeyTextView;
   private TextView triggerValueTextView;

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      // Inflate the menu; this adds items to the action bar if it is present.
      getMenuInflater().inflate(com.onesignal.example.R.menu.menu_main, menu);
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

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(com.onesignal.example.R.layout.activity_main);

      this.consentButton = this.findViewById(R.id.consentButton);
      this.emailEditText = findViewById(R.id.emailEditText);
      this.setEmailButton = findViewById(R.id.setEmailButton);
      this.logoutEmailButton = this.findViewById(R.id.logoutEmailButton);
      this.externalUserIdEditText = this.findViewById(R.id.externalUserIdEditText);
      this.postNotifButton = this.findViewById(R.id.postNotification);
      this.postNotifAsyncButton = this.findViewById(R.id.postNotificationAsync);
      this.postNotifGroupCheckBox = this.findViewById(R.id.postNotificationGroupCheckBox);
      this.postNotifAsyncGroupCheckBox = this.findViewById(R.id.postNotificationAsyncGroupCheckBox);
      this.consentButton = this.findViewById(R.id.consentButton);
      this.sendEvent = this.findViewById(R.id.sendEvent);
      this.iamHost = this.findViewById(R.id.iamHost);
      this.triggerKeyTextView = this.findViewById(R.id.triggerKey);
      this.triggerValueTextView = this.findViewById(R.id.triggerValue);
      this.outcomeName = this.findViewById(R.id.outcomeName);
      this.outcomeValueName = this.findViewById(R.id.outcomeNameValue);
      this.outcomeValue = this.findViewById(R.id.outcomeValue);
      this.outcomeUnique = this.findViewById(R.id.outcomeUniqueName);

      // Session and Focus Tracking Debug Fields
      this.sessionFocusTitleTextView = this.findViewById(R.id.session_and_focus_text_view);
      this.sessionFocusProgressBar = this.findViewById(R.id.session_and_focus_progress_bar);
      this.sessionFocusLinearLayout = this.findViewById(R.id.session_and_focus_test_area_linear_layout);
      this.onSessionForegroundTimeTextView = this.findViewById(R.id.on_session_foreground_time_value_text_view);
      this.onFocusBackgroundTimeTextView = this.findViewById(R.id.on_focus_background_time_value_text_view);

      // Outcome V2 Debug Fields
      this.outcomeV2TitleTextView = this.findViewById(R.id.outcome_v2_text_view);
      this.outcomeV2ProgressBar = this.findViewById(R.id.outcome_v2_progress_bar);
      this.outcomeV2LinearLayout = this.findViewById(R.id.outcome_v2_test_area_linear_layout);

      // IAM V2 Debug Fields
      this.iamV2TitleTextView = this.findViewById(R.id.iam_v2_text_view);
      this.iamV2ProgressBar = this.findViewById(R.id.iam_v2_progress_bar);
      this.iamV2LinearLayout = this.findViewById(R.id.iam_v2_test_area_linear_layout);
      this.iamV2TagEditText = this.findViewById(R.id.iam_v2_tag_edit_text);
      this.iamV2OutcomeEditText = this.findViewById(R.id.iam_v2_outcome_edit_text);

      this.iamHost.setText(OneSignalExampleApp.getOneSignalAppId(this));

      if (OneSignal.requiresUserPrivacyConsent()) {
         //disable all interactive views except consent button
         this.changeInteractiveViewsEnabled(false);
         consentButton.setText("Provide Consent");
      } else {
         consentButton.setText("Revoke Consent");

         this.addObservers();

         OSPermissionSubscriptionState state = OneSignal.getPermissionSubscriptionState();

         this.didGetEmailStatus(state.getEmailSubscriptionStatus().getSubscribed());
      }

      this.debugTextView = this.findViewById(com.onesignal.example.R.id.debugTextView);

//      OSPermissionSubscriptionState state = OneSignal.getPermissionSubscriptionState();
//
//      this.didGetEmailStatus(state.getEmailSubscriptionStatus().getSubscribed());

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

      setupGroupingNotificationCheckBoxes();

      updateSessionAndFocusData();
      updateOutcomeData();
      updateIamData();
   }

   private void updateIamhost() {
      String appId = this.iamHost.getText().toString();
      OneSignalExampleApp.setOneSignalAppId(this, appId);
   }

   private void updateTextView(final String newText) {
      //ensure that we only update the text view from the main thread.

      Handler mainHandler = new Handler(Looper.getMainLooper());

      Runnable runnable = new Runnable() {
         @Override
         public void run() {
            if (debugTextView != null)
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
      if (setEmailButton != null) {
         this.setEmailButton.setEnabled(!hasEmailUserId);
         this.logoutEmailButton.setEnabled(hasEmailUserId);
      }
   }

   public void onSubscribeClicked(View v) {
      OneSignal.setSubscription(true);
      OneSignal.promptLocation();

      String ns = Context.NOTIFICATION_SERVICE;
      NotificationManager nMgr = (NotificationManager) getApplicationContext().getSystemService(ns);
      nMgr.cancelAll();
   }

   public void onSetExternalUserId(View view) {
      String externalUserId = this.externalUserIdEditText.getText().toString().trim();

      OneSignal.setExternalUserId(externalUserId, new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
         @Override
         public void onComplete(JSONObject results) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Set external user id done with results: " + results.toString());
         }
      });
   }

   public void onRemoveExternalUserId(View view) {
      OneSignal.removeExternalUserId(new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
         @Override
         public void onComplete(JSONObject results) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Remove external user id done with results: " + results.toString());
         }
      });
   }

   public void onSetTrigger(View v) {
      String triggerKey = this.triggerKeyTextView.getText().toString();
      String triggerValue = this.triggerValueTextView.getText().toString();
      OneSignal.addTrigger(triggerKey, triggerValue);
   }

   private void changeInteractiveViewsEnabled(boolean enabled) {
      for (int viewId : this.interactiveViewIds) {
         View view = this.findViewById(viewId);
         if (view != null)
            this.findViewById(viewId).setEnabled(enabled);
      }
   }

   public void onUnsubscribeClicked(View v) {
      OneSignal.setSubscription(false);

      this.debugTextView.setText("Unsubscribed");
   }

   public void onSendTagsClicked(View v) {
      try {
         OneSignal.sendTags(new JSONObject("{\"counter\" : " + sendTagsCounter + ", \"test_value\" : \"test_key\"}"), new OneSignal.ChangeTagsUpdateHandler() {
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
      String email = emailEditText.getText().toString();

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

   public void onSendOutcomeClicked(View view) {
      OneSignal.sendOutcome(outcomeName.getText().toString(), new OneSignal.OutcomeCallback() {
         @Override
         public void onSuccess(@Nullable OutcomeEvent outcomeEvent) {
            if (outcomeEvent != null)
               updateTextView(outcomeEvent.toString());
         }
      });
   }

   public void onSendUniqueOutcomeClicked(View view) {
      OneSignal.sendUniqueOutcome(outcomeUnique.getText().toString(), new OneSignal.OutcomeCallback() {
         @Override
         public void onSuccess(@Nullable OutcomeEvent outcomeEvent) {
            if (outcomeEvent != null)
               updateTextView(outcomeEvent.toString());
         }
      });
   }

   public void onSendOutcomeWithValueClicked(View view) {
      if (outcomeValue.getText().toString().isEmpty())
         return;

      OneSignal.sendOutcomeWithValue(outcomeValueName.getText().toString(), Float.parseFloat(outcomeValue.getText().toString()), new OneSignal.OutcomeCallback() {
         @Override
         public void onSuccess(@Nullable OutcomeEvent outcomeEvent) {
            if (outcomeEvent != null)
               updateTextView(outcomeEvent.toString());
         }
      });
   }

   public void onFullScreenClicked(View v) {
      updateIamhost();
   }

   public void onPostNotifClicked(View v) {

         String userId = OneSignal.getUserId();

         JSONObject notifPayload = null;
         try {

            notifPayload = new JSONObject("{'contents': " + "{'en':'Test Message'}, 'include_player_ids': ['" + userId + "']}");

            if (postNotifGroupCheckBox.isChecked())
               notifPayload.put("android_group", "group_1");

         } catch (JSONException e) {
            e.printStackTrace();
         }

         OneSignal.postNotification(notifPayload, new OneSignal.PostNotificationResponseHandler() {
            @Override
            public void onSuccess(JSONObject response) {
               OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, response.toString());
            }

            @Override
            public void onFailure(JSONObject response) {
               OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, response.toString());
            }
         });
   }

   public void onPostNotifAsyncClicked(View v) {
      new AsyncTaskRunner().execute(
              postNotifAsyncGroupCheckBox.isChecked()
      );
   }

   private class AsyncTaskRunner extends AsyncTask<Object, Void, Void> {

      @Override
      protected Void doInBackground(Object... params) {

        try {
           Thread.sleep(1000);
        } catch (InterruptedException e) {
           e.printStackTrace();
        }

        String userId = OneSignal.getUserId();

        JSONObject notifPayload = null;
        try {

           notifPayload = new JSONObject("{'contents': " + "{'en':'Test Message'}, 'include_player_ids': ['" + userId + "']}");

           Boolean addGroup = (Boolean) params[0];
           if (addGroup)
              notifPayload.put("android_group", "group_1");

        } catch (JSONException e) {
           e.printStackTrace();
        }

        OneSignal.postNotification(notifPayload, new OneSignal.PostNotificationResponseHandler() {
           @Override
           public void onSuccess(JSONObject response) {
              OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, response.toString());
           }

           @Override
           public void onFailure(JSONObject response) {
              OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, response.toString());
           }
        });

         return null;
      }


      @Override
      protected void onPostExecute(Void result) {

      }


      @Override
      protected void onPreExecute() {

      }


      @Override
      protected void onProgressUpdate(Void... entry) {

      }
   }

   private void setupGroupingNotificationCheckBoxes() {
      this.postNotifGroupCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
         @Override
         public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Toaster.makeToast(MainActivity.this, "Main thread Notifications will be grouped: " + isChecked, Toast.LENGTH_SHORT);
         }
      });

      this.postNotifAsyncGroupCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
         @Override
         public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Toaster.makeToast(MainActivity.this, "Async notifications will be grouped: " + isChecked, Toast.LENGTH_SHORT);
         }
      });
   }

   /*
    * ===========================================================================
    * EVERYTHING BELOW THIS TEMPORARY AND INTENDED FOR TESTING PURPOSES ONLY
    */

   // Handles the visual representation of foreground time and accumulated foreground time
   public void updateSessionAndFocusData() {
      OneSignal.handlerForSessionAndFocusTracking(new OneSignal.Debug.Completion() {
         @Override
         public void onComplete(JSONObject data) {
            // Session and Focus V2
            sessionFocusTitleTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            sessionFocusProgressBar.setVisibility(View.INVISIBLE);
            sessionFocusLinearLayout.setVisibility(View.VISIBLE);
            try {
               onSessionForegroundTimeTextView.setText(String.valueOf(data.getInt("sum_foreground_time")));
               onFocusBackgroundTimeTextView.setText(String.valueOf(data.getInt("foreground_time")));
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      });
   }

   // Handles the visual representation for outcomes being tracked in the notification channel and iam channel
   public static boolean directIamSent = false;
   public void updateOutcomeData() {
      OneSignal.handlerForOutcomeTracking(new OneSignal.Debug.Completion() {
         @Override
         public void onComplete(JSONObject data) {
            // Outcome V2
            outcomeV2TitleTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            outcomeV2ProgressBar.setVisibility(View.INVISIBLE);
            outcomeV2LinearLayout.setVisibility(View.VISIBLE);

            try {
               JSONArray directNotifIds = new JSONArray();
               if (data.has("direct_notif_ids"))
                  directNotifIds = data.getJSONArray("direct_notif_ids");

               JSONArray indirectNotifIds = new JSONArray();
               if (data.has("indirect_notif_ids"))
                  indirectNotifIds = data.getJSONArray("indirect_notif_ids");

               addNewStringRecyclerViewWithTitle("Direct Notifs Ids", directNotifIds, outcomeV2LinearLayout);
               addNewStringRecyclerViewWithTitle("Indirect Notifs Ids", indirectNotifIds, outcomeV2LinearLayout);

               JSONArray directIamIds = new JSONArray();
               if (data.has("direct_iam_ids"))
                  directIamIds = data.getJSONArray("direct_iam_ids");

               JSONArray indirectIamIds = new JSONArray();
               if (data.has("indirect_iam_ids"))
                  indirectIamIds = data.getJSONArray("indirect_iam_ids");

               addNewStringRecyclerViewWithTitle("Direct Iam Ids", directIamIds, outcomeV2LinearLayout);
               addNewStringRecyclerViewWithTitle("Indirect Iam Ids", indirectIamIds, outcomeV2LinearLayout);

            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      }, new OneSignal.Debug.Completion() {
         @Override
         public void onComplete(JSONObject data) {
            try {
               JSONArray directIamIds = new JSONArray();
               if (data.has("direct")) {
                  JSONObject directSource = data.getJSONObject("direct");
                  if (directSource.has("in_app_message_ids"))
                     directIamIds = directSource.getJSONArray("in_app_message_ids");

               }

               JSONArray indirectIamIds = new JSONArray();
               if (!data.has("direct") && data.has("indirect")) {
                  JSONObject indirectSource = data.getJSONObject("indirect");
                  if (indirectSource.has("in_app_message_ids"))
                     indirectIamIds = indirectSource.getJSONArray("in_app_message_ids");
               }

               addNewStringRecyclerViewWithTitle("Direct Iam Ids", directIamIds, outcomeV2LinearLayout);
               addNewStringRecyclerViewWithTitle("Indirect Iam Ids", indirectIamIds, outcomeV2LinearLayout);

            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      });
   }

   // Handles the hijacking of the IAM data and this method being called signifies that IAM data
   // has now been successfully pulled down or from cache
   // A new session (30+ secs in background) will pull latest IAMs down and reentering the app by
   // force quitting and reopening it will use the cached IAMs form most recent new session
   public void onAttachIamV2Data(View v) {
      OneSignal.iamV2Tag = iamV2TagEditText.getText().toString().trim();
      OneSignal.iamV2Outcome = iamV2OutcomeEditText.getText().toString().trim();

      OneSignal.pauseInAppMessages(false);

      if (OneSignal.iamV2Tag == null)
         OneSignal.iamV2Tag = "";
      if (OneSignal.iamV2Outcome == null)
         OneSignal.iamV2Outcome = "";

      OneSignal.Debug.receiveInAppMessages();
   }

   private void addNewStringRecyclerViewWithTitle(String title, final JSONArray ids, LinearLayout parent) {
      boolean isNew = parent.findViewWithTag(title) == null;

      View view = this.getLayoutInflater().inflate(R.layout.string_recycler_view_layout, null, false);
      if (!isNew)
         view = parent.findViewWithTag(title);

      view.setTag(title);

      TextView titleTextView = view.findViewById(R.id.string_recycler_view_title_text_view);
      final RecyclerView recyclerView = view.findViewById(R.id.string_recycler_view_recycler_view);

      // Setup titleTextView layout
      String titleText = title + ": " + ids.length();
      titleTextView.setText(titleText);

      if (isNew || recyclerView.getAdapter() == null) {
         // Setup recyclerView layout only in event that this is new
         buildRecyclerViewClasses(recyclerView, ids);
         parent.addView(view);
      } else {
         if (recyclerView.getAdapter() != null) {
            final View finalView = view;
            runOnUiThread(new Runnable() {
               @Override
               public void run() {
                  ((StringRecyclerViewAdapter) recyclerView.getAdapter()).setIds(ids);
                  int vis = ids.length() == 0 ? View.GONE : View.VISIBLE;
                  finalView.setVisibility(vis);
               }
            });
         }
      }
   }

   private void buildRecyclerViewClasses(RecyclerView recyclerView, JSONArray ids) {
      DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();

      int orientation = true ? DividerItemDecoration.VERTICAL : DividerItemDecoration.HORIZONTAL;
      DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, orientation);

      int divider = false ? R.drawable.divider : R.drawable.no_divider;
      dividerItemDecoration.setDrawable(getResources().getDrawable(divider));

      recyclerView.setItemAnimator(defaultItemAnimator);
      recyclerView.addItemDecoration(dividerItemDecoration);
      recyclerView.setHasFixedSize(false);

      LinearLayoutManager layoutManager = new LinearLayoutManager(this, orientation, false);
      recyclerView.setLayoutManager(layoutManager);

      StringRecyclerViewAdapter adapter = new StringRecyclerViewAdapter(this, ids);
      recyclerView.setAdapter(adapter);
   }

   public void updateIamData() {
      OneSignal.handlerForIamTracking(new OneSignal.Debug.Completion() {
         @Override
         public void onComplete(JSONObject data) {
            // IAM V2
            iamV2TitleTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            iamV2ProgressBar.setVisibility(View.INVISIBLE);
            iamV2LinearLayout.setVisibility(View.VISIBLE);
         }
      });
   }

   /*
    * DON'T PLACE ANYTHING UNDER HERE, THIS IS END OF TEMP DEBUG TESTING CODE
    * ===========================================================================
    */
}
