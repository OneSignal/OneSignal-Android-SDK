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
   public static Thread testThread;
   public static boolean failNext, failAll;
   public static String failResponse = "{}", nextSuccessResponse;
   public static int networkCallCount;

   public static final String testUserId = "a2f7f967-e8cc-11e4-bed1-118f05be4511";

   public static boolean interruptibleDelayNext;
   private static Thread lastInteruptiableDelayThread;

   public static void interruptHTTPDelay() {
      if (lastInteruptiableDelayThread != null) { //&& lastInteruptiableDelayThread.getState() == Thread.State.TIMED_WAITING) {
         lastInteruptiableDelayThread.interrupt();
         lastInteruptiableDelayThread = null;
      }
   }

   static void safeInterrupt() {
      if (testThread != null && testThread.getState() == Thread.State.TIMED_WAITING)
         testThread.interrupt();
   }

   private static void doInterruptibleDelay() {
      if (interruptibleDelayNext) {
         lastInteruptiableDelayThread = Thread.currentThread();
         interruptibleDelayNext = false;
         try { Thread.sleep(20000); } catch (InterruptedException e) {}
      }
   }

   private static boolean doFail(OneSignalRestClient.ResponseHandler responseHandler) {
      if (failNext || failAll) {
         responseHandler.onFailure(400, failResponse, new Exception());
         safeInterrupt();
         failNext = false;
         return true;
      }

      return false;
   }

   private static void mockPost(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      lastUrl = url;
      networkCallCount++;
      lastPost = jsonBody;

      doInterruptibleDelay();
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

      safeInterrupt();
   }

   static void post(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      mockPost(url, jsonBody, responseHandler);
   }

   static void postSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      mockPost(url, jsonBody, responseHandler);
   }

   static void putSync(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      lastUrl = url;
      networkCallCount++;
      lastPost = jsonBody;

      System.out.println("lastPost:jsonBody: " + lastPost.toString());

      doInterruptibleDelay();
      if (doFail(responseHandler)) return;

      responseHandler.onSuccess("{\"id\": \"" + testUserId + "\"}");

      safeInterrupt();
   }

   static void put(String url, JSONObject jsonBody, OneSignalRestClient.ResponseHandler responseHandler) {
      lastUrl = url;
      networkCallCount++;
      lastPost = jsonBody;

      doInterruptibleDelay();
      if (doFail(responseHandler)) return;

      System.out.println("lastPost:jsonBody: " + lastPost.toString());

      responseHandler.onSuccess("{}");

      safeInterrupt();
   }

   static void getSync(final String url, final OneSignalRestClient.ResponseHandler responseHandler) {
      lastUrl = url;
      networkCallCount++;
      doInterruptibleDelay();
      if (doFail(responseHandler)) return;

      if (nextSuccessResponse != null) {
         responseHandler.onSuccess(nextSuccessResponse);
         nextSuccessResponse = null;
      }
      else
         responseHandler.onSuccess("{}");

      safeInterrupt();
   }
}
