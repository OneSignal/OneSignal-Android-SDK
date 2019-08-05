package com.onesignal;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

class OneSignalRemoteParams {

   static class Params {
      String googleProjectNumber;
      boolean enterprise;
      boolean useEmailAuth;
      JSONArray notificationChannels;
      boolean firebaseAnalytics;
      boolean restoreTTLFilter;
      boolean clearGroupOnSummaryClick;
   }

   interface CallBack {
      void complete(Params params);
   }

   private static int androidParamsRetries = 0;

   private static final int INCREASE_BETWEEN_RETRIES = 10_000;
   private static final int MIN_WAIT_BETWEEN_RETRIES = 30_000;
   private static final int MAX_WAIT_BETWEEN_RETRIES = 90_000;

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
      }};

      callBack.complete(params);
   }
}
