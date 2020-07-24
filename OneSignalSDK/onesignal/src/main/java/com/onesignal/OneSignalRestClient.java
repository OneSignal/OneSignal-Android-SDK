/**
 * Modified MIT License
 * 
 * Copyright 2019 OneSignal
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

import android.net.TrafficStats;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONObject;

class OneSignalRestClient {
   static abstract class ResponseHandler {
      void onSuccess(String response) {}
      void onFailure(int statusCode, String response, Throwable throwable) {}
   }

   static final String CACHE_KEY_GET_TAGS = "CACHE_KEY_GET_TAGS";
   static final String CACHE_KEY_REMOTE_PARAMS = "CACHE_KEY_REMOTE_PARAMS";

   private static final String OS_API_VERSION = "1";
   private static final String OS_ACCEPT_HEADER = "application/vnd.onesignal.v" + OS_API_VERSION + "+json";
   private static final String BASE_URL = "https://api.onesignal.com/";
   
   private static final int THREAD_ID = 10000;
   private static final int TIMEOUT = 120_000;
   private static final int GET_TIMEOUT = 60_000;
   
   private static int getThreadTimeout(int timeout) {
      return timeout + 5_000;
   }

   public static void put(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {

      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, "PUT", jsonBody, responseHandler, TIMEOUT, null);
         }
      }, "OS_REST_ASYNC_PUT").start();
   }

   public static void post(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {
      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, "POST", jsonBody, responseHandler, TIMEOUT, null);
         }
      }, "OS_REST_ASYNC_POST").start();
   }

   public static void get(final String url, final ResponseHandler responseHandler, @NonNull final String cacheKey) {
      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, null, null, responseHandler, GET_TIMEOUT, cacheKey);
         }
      }, "OS_REST_ASYNC_GET").start();
   }

   public static void getSync(final String url, final ResponseHandler responseHandler, @NonNull String cacheKey) {
      makeRequest(url, null, null, responseHandler, GET_TIMEOUT, cacheKey);
   }

   public static void putSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
      makeRequest(url, "PUT", jsonBody, responseHandler, TIMEOUT, null);
   }

   public static void postSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
      makeRequest(url, "POST", jsonBody, responseHandler, TIMEOUT, null);
   }
   
   private static void makeRequest(final String url, final String method, final JSONObject jsonBody, final ResponseHandler responseHandler, final int timeout, final String cacheKey) {
      if (OSUtils.isRunningOnMainThread())
         throw new OneSignalNetworkCallException("Method: " + method + " was called from the Main Thread!");

      // If not a GET request, check if the user provided privacy consent if the application is set to require user privacy consent
      if (method != null && OneSignal.shouldLogUserPrivacyConsentErrorMessageForMethodName(null))
         return;

      final Thread[] callbackThread = new Thread[1];
      Thread connectionThread = new Thread(new Runnable() {
         public void run() {
            callbackThread[0] = startHTTPConnection(url, method, jsonBody, responseHandler, timeout, cacheKey);
         }
      }, "OS_HTTPConnection");
      
      connectionThread.start();
      
      // getResponseCode() can hang past it's timeout setting so join it's thread to ensure it is timing out.
      try {
         // Sequentially wait for connectionThread to execute
         connectionThread.join(getThreadTimeout(timeout));
         if (connectionThread.getState() != Thread.State.TERMINATED)
            connectionThread.interrupt();
         if (callbackThread[0] != null)
            callbackThread[0].join();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
   }
   
   private static Thread startHTTPConnection(String url, String method, JSONObject jsonBody, ResponseHandler responseHandler, int timeout, @Nullable String cacheKey) {
      int httpResponse = -1;
      HttpURLConnection con = null;
      Thread callbackThread;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         TrafficStats.setThreadStatsTag(THREAD_ID);
      }

      try {
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Making request to: " + BASE_URL + url);
         con = newHttpURLConnection(url);

         con.setUseCaches(false);
         con.setConnectTimeout(timeout);
         con.setReadTimeout(timeout);
         con.setRequestProperty("SDK-Version", "onesignal/android/" + OneSignal.VERSION);
         con.setRequestProperty("Accept", OS_ACCEPT_HEADER);

         if (jsonBody != null)
            con.setDoInput(true);

         if (method != null) {
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestMethod(method);
            con.setDoOutput(true);
         }

         if (jsonBody != null) {
            String strJsonBody = jsonBody.toString();
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: " + method + " SEND JSON: " + strJsonBody);

            byte[] sendBytes = strJsonBody.getBytes("UTF-8");
            con.setFixedLengthStreamingMode(sendBytes.length);

            OutputStream outputStream = con.getOutputStream();
            outputStream.write(sendBytes);
         }

         if (cacheKey != null) {
            String eTag = OneSignalPrefs.getString(
               OneSignalPrefs.PREFS_ONESIGNAL,
               OneSignalPrefs.PREFS_OS_ETAG_PREFIX + cacheKey,
               null
            );
            if (eTag != null) {
               con.setRequestProperty("if-none-match", eTag);
               OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Adding header if-none-match: " + eTag);
            }
         }

         // Network request is made from getResponseCode()
         httpResponse = con.getResponseCode();

         OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "OneSignalRestClient: After con.getResponseCode to: " + BASE_URL + url);

         switch (httpResponse) {
           case HttpURLConnection.HTTP_NOT_MODIFIED: // 304
               String cachedResponse = OneSignalPrefs.getString(
                  OneSignalPrefs.PREFS_ONESIGNAL,
                  OneSignalPrefs.PREFS_OS_HTTP_CACHE_PREFIX + cacheKey,
                  null
               );
               OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: " + (method == null ? "GET" : method) + " - Using Cached response due to 304: " + cachedResponse);
               callbackThread = callResponseHandlerOnSuccess(responseHandler, cachedResponse);
            break;
            case HttpURLConnection.HTTP_ACCEPTED:
            case HttpURLConnection.HTTP_OK: // 200
               OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Successfully finished request to: " + BASE_URL + url);

               InputStream inputStream = con.getInputStream();
               Scanner scanner = new Scanner(inputStream, "UTF-8");
               String json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
               scanner.close();
               OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: " + (method == null ? "GET" : method) + " RECEIVED JSON: " + json);

               if (cacheKey != null) {
                  String eTag = con.getHeaderField("etag");
                  if (eTag != null) {
                     OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Response has etag of " + eTag + " so caching the response.");
                     OneSignalPrefs.saveString(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_ETAG_PREFIX + cacheKey,
                        eTag
                     );
                     OneSignalPrefs.saveString(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_HTTP_CACHE_PREFIX + cacheKey,
                        json
                     );
                  }
               }

               callbackThread = callResponseHandlerOnSuccess(responseHandler, json);
               break;
            default: // Request failed
               OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Failed request to: " + BASE_URL + url);
               inputStream = con.getErrorStream();
               if (inputStream == null)
                  inputStream = con.getInputStream();

               String jsonResponse = null;
               if (inputStream != null) {
                  scanner = new Scanner(inputStream, "UTF-8");
                  jsonResponse = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                  scanner.close();
                  OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "OneSignalRestClient: " + method + " RECEIVED JSON: " + jsonResponse);
               }
               else
                  OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "OneSignalRestClient: " + method + " HTTP Code: " + httpResponse + " No response body!");

               callbackThread = callResponseHandlerOnFailure(responseHandler, httpResponse, jsonResponse, null);
         }
      } catch (Throwable t) {
         if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException)
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "OneSignalRestClient: Could not send last request, device is offline. Throwable: " + t.getClass().getName());
         else
            OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "OneSignalRestClient: " + method + " Error thrown from network stack. ", t);
   
         callbackThread = callResponseHandlerOnFailure(responseHandler, httpResponse, null, t);
      }
      finally {
         if (con != null)
            con.disconnect();
      }
      
      return callbackThread;
   }
   
   
   // These helper methods run the callback a new thread so they don't count towards the fallback thread join timer.
   
   private static Thread callResponseHandlerOnSuccess(final ResponseHandler handler, final String response) {
      if (handler == null)
         return null;
      
      Thread thread = new Thread(new Runnable() {
         public void run() {
            handler.onSuccess(response);
         }
      }, "OS_REST_SUCCESS_CALLBACK");
      thread.start();
      
      return thread;
   }
   
   private static Thread callResponseHandlerOnFailure(final ResponseHandler handler, final int statusCode, final String response, final Throwable throwable) {
      if (handler == null)
         return null;
   
      Thread thread = new Thread(new Runnable() {
         public void run() {
            handler.onFailure(statusCode, response, throwable);
         }
      }, "OS_REST_FAILURE_CALLBACK");
      thread.start();
      
      return thread;
   }

   private static HttpURLConnection newHttpURLConnection(String url) throws IOException {
      return (HttpURLConnection)new URL(BASE_URL + url).openConnection();
   }

   private static class OneSignalNetworkCallException extends RuntimeException {
      public OneSignalNetworkCallException(String message) {
         super(message);
      }
   }
}