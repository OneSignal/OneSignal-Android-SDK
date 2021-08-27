/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
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

import com.test.onesignal.RestClientValidator;

import org.json.JSONException;
import org.json.JSONObject;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.onesignal.UserState.DEVICE_TYPE_EMAIL;
import static com.onesignal.UserState.DEVICE_TYPE_SMS;

@Implements(OneSignalRestClient.class)
public class ShadowOneSignalRestClient {

   public static final String PUSH_USER_ID = "a2f7f967-e8cc-11e4-bed1-118f05be4511";
   public static final String EMAIL_USER_ID = "b007f967-98cc-11e4-bed1-118f05be4522";
   public static final String SMS_USER_ID = "d007f967-98cc-11e4-bed1-118f05be4522";
   private static final String REQUIRES_USER_PRIVACY_CONSENT = "requires_user_privacy_consent";
   private static final String RECEIVE_RECEIPTS_ENABLE = "receive_receipts_enable";

   public enum REST_METHOD {
      GET, POST, PUT
   }

   public static class Request {
      public REST_METHOD method;
      public JSONObject payload;
      public String url;

      Request(REST_METHOD method, JSONObject payload, String url) {
         this.method = method;
         this.payload = payload;
         this.url = url;
      }

      @Override
      public String toString() {
         return "Request{" +
                 "method=" + method +
                 ", payload=" + payload +
                 ", url='" + url + '\'' +
                 '}';
      }
   }

   private static class PendingResponse {
      boolean success;
      String response;
      OneSignalRestClient.ResponseHandler responseHandler;
      Object waitingThread;

      PendingResponse(boolean success, String response, OneSignalRestClient.ResponseHandler responseHandler) {
         this.success = success;
         this.response = response;
         this.responseHandler = responseHandler;
      }

      PendingResponse(Object waitingThread) {
         this.waitingThread = waitingThread;
      }
   }

   public static List<String> successfulGETResponses = new ArrayList<>();
   public static ArrayList<Request> requests;
   public static String lastUrl, failResponse, nextSuccessResponse, nextSuccessfulGETResponse, nextSuccessfulRegistrationResponse, pushUserId, emailUserId, smsUserId, failMethod;
   public static Pattern nextSuccessfulGETResponsePattern;
   public static JSONObject lastPost;
   public static boolean failNext, failNextPut, failAll, failPosts, failGetParams;
   public static int failHttpCode;
   public static int networkCallCount;

   
   // Pauses any network callbacks from firing.
   // Also blocks any sync network calls.
   public static boolean freezeResponses;
   private static ConcurrentHashMap<Object, PendingResponse> pendingResponses = new ConcurrentHashMap<>();

   private static String remoteParamsGetHtmlResponse = null;
   private static final String REMOTE_PARAMS = " {" +
           "\"awl_list\":{}," +
           "\"android_sender_id\":\"87654321\"," +
           "\"chnl_lst\":[]," +
           "\"enterp\":false," +
           "\"outcomes\":{" +
           "\"direct\":{\"enabled\":true}," +
           "\"indirect\":{" +
           "\"notification_attribution\":{" +
           "\"minutes_since_displayed\":60," +
           "\"limit\":10},\"enabled\":true" +
           "}," +
           "\"unattributed\":{" +
           "\"enabled\":true" +
           "}" +
           "}," +
           "\"receive_receipts_enable\":false," +
           "\"unsubscribe_on_notifications_disabled\":true," +
           "\"disable_gms_missing_prompt\":true," +
           "\"location_shared\":true" +
           "}";
   private static final String IAM_GET_HTML_RESPONSE;
   static {
      String value = null;
      try {
         value = new JSONObject()
                 .put("html", "<html></html>")
                 .put("display_duration", 0.0)
                 .toString();
      } catch (JSONException e) { }
      IAM_GET_HTML_RESPONSE = value;
   }

   public static void resetStatics() {
      pushUserId = PUSH_USER_ID;
      emailUserId = EMAIL_USER_ID;
      smsUserId = SMS_USER_ID;

      requests = new ArrayList<>();
      lastPost = null;
      networkCallCount = 0;

      nextSuccessfulGETResponse = null;
      nextSuccessfulGETResponsePattern = null;

      failMethod = null;
      failResponse = "{}";
      nextSuccessfulRegistrationResponse = null;
      nextSuccessResponse = null;
      failNext = false;
      failNextPut = false;
      failAll = false;
      failPosts = false;
      failGetParams = false;
      failHttpCode = 400;

      freezeResponses = false;
      pendingResponses = new ConcurrentHashMap<>();
      remoteParamsGetHtmlResponse = null;
   }

   public static void unFreezeResponses() {
      if (!freezeResponses)
         return;
      freezeResponses = false;
      for (Object thread : pendingResponses.keySet()) {
         if (thread instanceof Thread) {
            System.out.println("Start thread notify: " + thread);
            synchronized (thread) {
               thread.notifyAll();
            }
            System.out.println("End thread notify: " + thread);
         }
      }
   }

   public static void setNextSuccessfulJSONResponse(JSONObject response) throws JSONException {
      nextSuccessResponse = response.toString(1);
   }

   public static void setNextFailureJSONResponse(JSONObject response) throws JSONException {
      failResponse = response.toString(1);
   }

   public static void setNextSuccessfulGETJSONResponse(JSONObject response) throws JSONException {
      nextSuccessfulGETResponse = response.toString(1);
   }

   public static void setNextSuccessfulRegistrationResponse(JSONObject response) throws JSONException {
      nextSuccessfulRegistrationResponse = response.toString(1);
   }

   public static void setSuccessfulGETJSONResponses(JSONObject... responses) throws JSONException {
      for (JSONObject response : responses) {
         successfulGETResponses.add(response.toString(1));
      }
   }

   public static void setRemoteParamsRequirePrivacyConsent(boolean requirePrivacyConsent) throws JSONException {
      JSONObject remoteParams = new JSONObject().put(REQUIRES_USER_PRIVACY_CONSENT, requirePrivacyConsent);
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);
   }

   public static void setRemoteParamsReceiveReceiptsEnable(boolean requirePrivacyConsent) throws JSONException {
      JSONObject remoteParams = new JSONObject().put(RECEIVE_RECEIPTS_ENABLE, requirePrivacyConsent);
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);
   }

   public static void setRemoteParamsGetHtmlResponse(JSONObject params) throws JSONException {
      JSONObject remoteParams = new JSONObject(REMOTE_PARAMS);
      Iterator<String> keys = params.keys();

      while (keys.hasNext()) {
         String key = keys.next();
         remoteParams.put(key, params.get(key));
      }
      remoteParamsGetHtmlResponse = remoteParams.toString();
   }

   public static void setAndRemoveKeyFromRemoteParams(String key) throws JSONException {
      JSONObject remoteParams = new JSONObject(REMOTE_PARAMS);
      if (remoteParams.has(key)) {
         remoteParams.remove(key);
      }
      remoteParamsGetHtmlResponse = remoteParams.toString();
   }

   public static void setRemoteParamsGetHtmlResponse(String response) {
      remoteParamsGetHtmlResponse = response;
   }

   public static void setRemoteParamsGetHtmlResponse() {
      remoteParamsGetHtmlResponse = REMOTE_PARAMS;
   }

   private static void freezeSyncCall() {
      if (!freezeResponses)
         return;

      Object toLock = Thread.currentThread();
      pendingResponses.put(toLock, new PendingResponse(toLock));
      synchronized (toLock) {
         try {
            toLock.wait();
         } catch (InterruptedException e) {
            e.printStackTrace();
         }
      }
   }

   public static boolean isAFrozenThread(Thread thread) {
      synchronized (thread) {
         boolean isIt = pendingResponses.containsKey(thread);
         return isIt;
      }
   }

   private static void trackRequest(REST_METHOD method, JSONObject payload, String url) throws JSONException {
      if (method == REST_METHOD.POST || method == REST_METHOD.PUT)
         lastPost = payload;
      lastUrl = url;
      networkCallCount++;

      Request request = new Request(method, payload, url);
      requests.add(request);

      RestClientValidator.validateRequest(request);

      System.out.println(networkCallCount + ":" + method + "@URL:" + url + "\nBODY: " + payload);
   }

   private static boolean suspendResponse(
      boolean success,
      String response,
      OneSignalRestClient.ResponseHandler responseHandler) {
      if (!freezeResponses || responseHandler == null)
         return false;

      pendingResponses.put(responseHandler, new PendingResponse(success, response, responseHandler));
      return true;
   }

   private static boolean doFail(OneSignalRestClient.ResponseHandler responseHandler, boolean doFail) {
      if (failNext || failAll || doFail || handleFailMethod(lastUrl, responseHandler)) {
         if (!suspendResponse(false, failResponse, responseHandler))
            responseHandler.onFailure(failHttpCode, failResponse, new Exception());
         failNext = failNextPut = false;
         return true;
      }

      return false;
   }

   private static boolean doFail(OneSignalRestClient.ResponseHandler responseHandler) {
      return doFail(responseHandler, false);
   }

   private static void mockPost(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      if (doFail(responseHandler, failPosts)) return;

      String retJson;

      if (nextSuccessfulRegistrationResponse != null && url.contains("players")) {
         retJson = nextSuccessfulRegistrationResponse;
         nextSuccessfulRegistrationResponse = null;
      }
      else if (url.contains("on_session"))
         retJson = "{}";
      else {
         int device_type = jsonBody.optInt("device_type", 0);
         String id;
         switch (device_type) {
            case DEVICE_TYPE_EMAIL:
               id = emailUserId;
               break;
            case DEVICE_TYPE_SMS:
               id = smsUserId;
               break;
            default:
               id = pushUserId;
         }
         retJson = "{\"id\": \"" + id + "\"}";
      }

      if (nextSuccessResponse != null) {
         if (responseHandler != null) {
            if (!suspendResponse(true, nextSuccessResponse, responseHandler))
               responseHandler.onSuccess(nextSuccessResponse);
         }
         nextSuccessResponse = null;
      }
      else if (responseHandler != null) {
         if (!suspendResponse(true, retJson, responseHandler))
            responseHandler.onSuccess(retJson);
      }
   }

   public static void post(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) throws JSONException {
      trackRequest(REST_METHOD.POST, jsonBody, url);
      mockPost(url, jsonBody, responseHandler);
   }

   public static void postSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) throws JSONException {
      trackRequest(REST_METHOD.POST, jsonBody, url);
      freezeSyncCall();
      mockPost(url, jsonBody, responseHandler);
   }

   public static void putSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) throws JSONException {
      trackRequest(REST_METHOD.PUT, jsonBody, url);

      freezeSyncCall();

      if (doFail(responseHandler, failNextPut)) return;

      String response = "{\"id\": \"" + pushUserId + "\"}";
      if (!suspendResponse(true, response, responseHandler))
         responseHandler.onSuccess(response);
   }

   public static void put(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) throws JSONException {
      trackRequest(REST_METHOD.PUT, jsonBody, url);

      if (doFail(responseHandler, failNextPut)) return;

      responseHandler.onSuccess("{}");
   }

   public static void get(final String url, final OneSignalRestClient.ResponseHandler responseHandler, String cacheKey) throws JSONException {
       trackRequest(REST_METHOD.GET, null, url);
       if (failGetParams && doFail(responseHandler, true)) return;

       if (handleRemoteParamsIfAvailable(url, responseHandler))
           return;

       if (handleGetIAM(url, responseHandler))
           return;

       if (doNextSuccessfulGETResponse(url, responseHandler))
           return;

       if (nextSuccessResponse != null) {
           responseHandler.onSuccess(nextSuccessResponse);
           nextSuccessResponse = null;
       }
   }

   public static void getSync(final String url, final OneSignalRestClient.ResponseHandler responseHandler, String cacheKey) throws JSONException {
      trackRequest(REST_METHOD.GET, null, url);

      if (doFail(responseHandler)) return;

      if (doNextSuccessfulGETResponse(url, responseHandler))
         return;

      if (handleGetIAM(url, responseHandler))
         return;

      responseHandler.onSuccess("{}");
   }

   private static boolean doNextSuccessfulGETResponse(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      if (nextSuccessfulGETResponse != null &&
         (nextSuccessfulGETResponsePattern == null || nextSuccessfulGETResponsePattern.matcher(url).matches())) {
         responseHandler.onSuccess(nextSuccessfulGETResponse);
         nextSuccessfulGETResponse = null;
         return true;
      } else if (successfulGETResponses.size() > 0) {
         responseHandler.onSuccess(successfulGETResponses.remove(0));
         return true;
      }
      return false;
   }

   private static boolean handleGetIAM(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      if (!url.startsWith("in_app_messages"))
         return false;

      responseHandler.onSuccess(IAM_GET_HTML_RESPONSE);
      return true;
   }

   private static boolean handleRemoteParamsIfAvailable(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      if (remoteParamsGetHtmlResponse == null || !url.contains("android_params"))
         return false;

      responseHandler.onSuccess(remoteParamsGetHtmlResponse);
      return true;
   }

   private static boolean handleFailMethod(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      return failMethod != null && url.contains(failMethod);
   }
}
