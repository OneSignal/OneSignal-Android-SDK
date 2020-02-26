package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

class OneSignalRemoteParams {

   static class FCMParams {
      @Nullable String projectId;
      @Nullable String appId;
      @Nullable String apiKey;
   }

   static class OutcomesParams {
      //in minutes
      int indirectAttributionWindow = DEFAULT_INDIRECT_ATTRIBUTION_WINDOW;
      int notificationLimit = DEFAULT_NOTIFICATION_LIMIT;
      boolean directEnabled = false;
      boolean indirectEnabled = false;
      boolean unattributedEnabled = false;
   }

   static class Params {
      String googleProjectNumber;
      boolean enterprise;
      boolean useEmailAuth;
      JSONArray notificationChannels;
      boolean firebaseAnalytics;
      boolean restoreTTLFilter;
      boolean clearGroupOnSummaryClick;
      boolean receiveReceiptEnabled;
      OutcomesParams outcomesParams;
      FCMParams fcmParams;
   }

   interface CallBack {
      void complete(Params params);
   }

   private static int androidParamsRetries = 0;

   private static final String OUTCOME_PARAM = "outcomes";
   private static final String ENABLED_PARAM = "enabled";
   private static final String DIRECT_PARAM = "direct";
   private static final String INDIRECT_PARAM = "indirect";
   private static final String NOTIFICATION_ATTRIBUTION_PARAM = "notification_attribution";
   private static final String UNATTRIBUTED_PARAM = "unattributed";

   private static final String FCM_PARENT_PARAM = "fcm";
   private static final String FCM_PROJECT_ID = "project_id";
   private static final String FCM_APP_ID = "app_id";
   private static final String FCM_API_KEY = "api_key";

   private static final int INCREASE_BETWEEN_RETRIES = 10_000;
   private static final int MIN_WAIT_BETWEEN_RETRIES = 30_000;
   private static final int MAX_WAIT_BETWEEN_RETRIES = 90_000;

   static final int DEFAULT_INDIRECT_ATTRIBUTION_WINDOW = 24 * 60;
   static final int DEFAULT_NOTIFICATION_LIMIT = 10;

   static void makeAndroidParamsRequest(final @NonNull CallBack callBack) {
      OneSignalRestClient.ResponseHandler responseHandler = new OneSignalRestClient.ResponseHandler() {
         @Override
         void onFailure(int statusCode, String response, Throwable throwable) {
            if (statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
               OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "403 error getting OneSignal params, omitting further retries!");
               return;
            }

            new Thread(new Runnable() {
               public void run() {
                  int sleepTime = MIN_WAIT_BETWEEN_RETRIES + androidParamsRetries * INCREASE_BETWEEN_RETRIES;
                  if (sleepTime > MAX_WAIT_BETWEEN_RETRIES)
                     sleepTime = MAX_WAIT_BETWEEN_RETRIES;

                  OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Failed to get Android parameters, trying again in " + (sleepTime / 1_000) +  " seconds.");
                  OSUtils.sleep(sleepTime);
                  androidParamsRetries++;
                  makeAndroidParamsRequest(callBack);
               }
            }, "OS_PARAMS_REQUEST").start();
         }

         @Override
         void onSuccess(String response) {
            processJson(response, callBack);
         }
      };

      String params_url = "apps/" + OneSignal.appId + "/android_params.js";
      String userId = OneSignal.getUserId();
      if (userId != null)
         params_url += "?player_id=" + userId;

      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Starting request to get Android parameters.");
      OneSignalRestClient.get(params_url, responseHandler, OneSignalRestClient.CACHE_KEY_REMOTE_PARAMS);
   }

   static private void processJson(String json, final @NonNull CallBack callBack) {
      final JSONObject responseJson;
      try {
         responseJson = new JSONObject(json);
      }
      catch (NullPointerException | JSONException t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "Error parsing android_params!: ", t);
         OneSignal.Log(OneSignal.LOG_LEVEL.FATAL, "Response that errored from android_params!: " + json);
         return;
      }

      Params params = new Params() {{
         enterprise = responseJson.optBoolean("enterp", false);
         useEmailAuth = responseJson.optBoolean("use_email_auth", false);
         notificationChannels = responseJson.optJSONArray("chnl_lst");
         firebaseAnalytics = responseJson.optBoolean("fba", false);
         restoreTTLFilter = responseJson.optBoolean("restore_ttl_filter", true);
         googleProjectNumber = responseJson.optString("android_sender_id", null);
         clearGroupOnSummaryClick = responseJson.optBoolean("clear_group_on_summary_click", true);
         receiveReceiptEnabled = responseJson.optBoolean("receive_receipts_enable", false);

         outcomesParams = new OutcomesParams();
         // Process outcomes params
         if (responseJson.has(OUTCOME_PARAM)) {
            JSONObject outcomes = responseJson.optJSONObject(OUTCOME_PARAM);

            if (outcomes.has(DIRECT_PARAM)) {
               JSONObject direct = outcomes.optJSONObject(DIRECT_PARAM);
               outcomesParams.directEnabled = direct.optBoolean(ENABLED_PARAM);
            }
            if (outcomes.has(INDIRECT_PARAM)) {
               JSONObject indirect = outcomes.optJSONObject(INDIRECT_PARAM);
               outcomesParams.indirectEnabled = indirect.optBoolean(ENABLED_PARAM);

               if (indirect.has(NOTIFICATION_ATTRIBUTION_PARAM)) {
                  JSONObject indirectNotificationAttribution = indirect.optJSONObject(NOTIFICATION_ATTRIBUTION_PARAM);
                  outcomesParams.indirectAttributionWindow = indirectNotificationAttribution.optInt("minutes_since_displayed", DEFAULT_INDIRECT_ATTRIBUTION_WINDOW);
                  outcomesParams.notificationLimit = indirectNotificationAttribution.optInt("limit", DEFAULT_NOTIFICATION_LIMIT);
               }
            }
            if (outcomes.has(UNATTRIBUTED_PARAM)) {
               JSONObject unattributed = outcomes.optJSONObject(UNATTRIBUTED_PARAM);
               outcomesParams.unattributedEnabled = unattributed.optBoolean(ENABLED_PARAM);
            }
         }

         fcmParams = new FCMParams();
         if (responseJson.has(FCM_PARENT_PARAM)) {
            JSONObject fcm = responseJson.optJSONObject(FCM_PARENT_PARAM);
            fcmParams.apiKey = fcm.optString(FCM_API_KEY, null);
            fcmParams.appId = fcm.optString(FCM_APP_ID, null);
            fcmParams.projectId = fcm.optString(FCM_PROJECT_ID, null);
         }
      }};

      callBack.complete(params);
   }
}
