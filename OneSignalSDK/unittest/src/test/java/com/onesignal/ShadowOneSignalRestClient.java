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

import org.json.JSONException;
import org.json.JSONObject;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.Iterator;

@Implements(OneSignalRestClient.class)
public class ShadowOneSignalRestClient {

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
   }

   public static JSONObject lastPost;
   public static ArrayList<Request> requests;
   public static String lastUrl;
   public static boolean failNext, failNextPut, failAll, failPosts;
   public static String failResponse, nextSuccessResponse, nextSuccessfulGETResponse;
   public static int networkCallCount;

   public static String pushUserId, emailUserId;

   public static JSONObject paramExtras;

   public static void resetStatics() {
      pushUserId = "a2f7f967-e8cc-11e4-bed1-118f05be4511";
      emailUserId = "b007f967-98cc-11e4-bed1-118f05be4522";

      requests = new ArrayList<>();
      lastPost = null;
      networkCallCount = 0;

      nextSuccessfulGETResponse = null;

      failResponse = "{}";
      nextSuccessResponse = null;
      failNext = false;
      failNextPut = false;
      failAll = false;
      failPosts = false;

      paramExtras = null;
   }

   private static void trackRequest(REST_METHOD method, JSONObject payload, String url) {
      if (method == REST_METHOD.POST || method == REST_METHOD.PUT)
         lastPost = payload;
      lastUrl = url;
      networkCallCount++;

      requests.add(new Request(method, payload, url));

      System.out.println(networkCallCount + ":" + method + "@URL:" + url + "\nBODY: " + payload);
   }

   private static boolean doFail(OneSignalRestClient.ResponseHandler responseHandler, boolean doFail) {
      if (failNext || failAll || doFail) {
         responseHandler.onFailure(400, failResponse, new Exception());
         failNext = failNextPut = false;
         return true;
      }

      return false;
   }

   private static boolean doFail(OneSignalRestClient.ResponseHandler responseHandler) {
      return doFail(responseHandler, false);
   }

   private static void mockPost(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      trackRequest(REST_METHOD.POST, jsonBody, url);

      if (doFail(responseHandler, failPosts)) return;

      String retJson;
      if (url.contains("on_session"))
         retJson = "{}";
      else {
         int device_type = jsonBody.optInt("device_type", 0);
         String id = device_type == 11 ? emailUserId : pushUserId;
         retJson = "{\"id\": \"" + id + "\"}";
      }

      if (nextSuccessResponse != null) {
         if (responseHandler != null)
            responseHandler.onSuccess(nextSuccessResponse);
         nextSuccessResponse = null;
      }
      else if (responseHandler != null)
         responseHandler.onSuccess(retJson);
   }

   public static void post(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      mockPost(url, jsonBody, responseHandler);
   }

   public static void postSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      mockPost(url, jsonBody, responseHandler);
   }

   public static void putSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      trackRequest(REST_METHOD.PUT, jsonBody, url);

      if (doFail(responseHandler, failNextPut)) return;

      responseHandler.onSuccess("{\"id\": \"" + pushUserId + "\"}");
   }

   public static void put(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      trackRequest(REST_METHOD.PUT, jsonBody, url);

      if (doFail(responseHandler, failNextPut)) return;

      responseHandler.onSuccess("{}");
   }

   public static void get(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      trackRequest(REST_METHOD.GET, null, url);
   
      if (nextSuccessResponse != null) {
         responseHandler.onSuccess(nextSuccessResponse);
         nextSuccessResponse = null;
      }
      else {
         try {
            JSONObject getResponseJson = new JSONObject("{\"awl_list\": {" +
                  "\"IlIfoQBT5jXgkgn6nBsIrGJn5t0Yd91GqKAGoApIYzk=\": 1," +
                  "\"Q3zjDf/4NxXU1QpN9WKp/iwVYNPQZ0js2EDDNO+eo0o=\": 1" +
                  "}, \"android_sender_id\": \"87654321\"}");
            if (paramExtras != null) {
               Iterator<String> keys = paramExtras.keys();
               while(keys.hasNext()) {
                  String key = keys.next();
                  getResponseJson.put(key, paramExtras.get(key));
               }
            }
            responseHandler.onSuccess(getResponseJson.toString());
         } catch (JSONException e) {
            e.printStackTrace();
         }
      }
   }

   public static void getSync(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      trackRequest(REST_METHOD.GET, null, url);

      if (doFail(responseHandler)) return;

      if (nextSuccessfulGETResponse != null) {
         responseHandler.onSuccess(nextSuccessfulGETResponse);
         nextSuccessfulGETResponse = null;
      }
      else
         responseHandler.onSuccess("{}");
   }
}
