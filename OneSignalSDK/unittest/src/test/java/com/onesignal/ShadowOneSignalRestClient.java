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

import org.json.JSONObject;
import org.robolectric.annotation.Implements;

@Implements(OneSignalRestClient.class)
public class ShadowOneSignalRestClient {

   public static JSONObject lastPost;
   public static String lastUrl;
   public static boolean failNext, failNextPut, failAll;
   public static String failResponse = "{}", nextSuccessResponse, nextSuccessfulGETResponse;
   public static int networkCallCount;

   public static final String testUserId = "a2f7f967-e8cc-11e4-bed1-118f05be4511";

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
      lastUrl = url;
      networkCallCount++;
      lastPost = jsonBody;

      if (doFail(responseHandler)) return;

      String retJson = null;
      if (url.contains("on_session"))
         retJson = "{}";
      else
         retJson = "{\"id\": \"" + testUserId + "\"}";

      if (nextSuccessResponse != null) {
         responseHandler.onSuccess(nextSuccessResponse);
         nextSuccessResponse = null;
      }
      else
         responseHandler.onSuccess(retJson);
   }

   public static void post(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      System.out.println("POST:URL:" + url + "  jsonBody: " + jsonBody.toString());
      mockPost(url, jsonBody, responseHandler);
   }

   public static void postSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      System.out.println("POST Sync:URL:" + url + "  jsonBody: " + jsonBody.toString());
      mockPost(url, jsonBody, responseHandler);
   }

   public static void putSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      lastUrl = url;
      networkCallCount++;
      lastPost = jsonBody;

      System.out.println("putSync:lastPost:jsonBody: " + lastPost.toString());

      if (doFail(responseHandler, failNextPut)) return;

      responseHandler.onSuccess("{\"id\": \"" + testUserId + "\"}");
   }

   public static void put(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      lastUrl = url;
      networkCallCount++;
      lastPost = jsonBody;

      if (doFail(responseHandler, failNextPut)) return;

      System.out.println("put:lastPost:jsonBody: " + lastPost.toString());

      responseHandler.onSuccess("{}");
   }

   public static void get(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      System.out.println("get: " + url);
      networkCallCount++;
   
      if (nextSuccessResponse != null) {
         responseHandler.onSuccess(nextSuccessResponse);
         nextSuccessResponse = null;
      }
      else
         responseHandler.onSuccess("{\"awl_list\": {" +
                                    "\"IlIfoQBT5jXgkgn6nBsIrGJn5t0Yd91GqKAGoApIYzk=\": 1," +
                                    "\"Q3zjDf/4NxXU1QpN9WKp/iwVYNPQZ0js2EDDNO+eo0o=\": 1" +
                                "}, \"android_sender_id\": \"87654321\"}");
   }

   public static void getSync(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      System.out.println("getSync: " + url);

      lastUrl = url;
      networkCallCount++;
      if (doFail(responseHandler)) return;

      if (nextSuccessfulGETResponse != null) {
         responseHandler.onSuccess(nextSuccessfulGETResponse);
         nextSuccessfulGETResponse = null;
      }
      else
         responseHandler.onSuccess("{}");
   }
}
