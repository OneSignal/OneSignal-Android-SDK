/**
 * Modified MIT License
 * 
 * Copyright 2017 OneSignal
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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONObject;

class OneSignalRestClient {
   static class ResponseHandler {
      void onSuccess(String response) {}
      void onFailure(int statusCode, String response, Throwable throwable) {}
   }

   private static final String BASE_URL = "https://onesignal.com/api/v1/";
   private static final int TIMEOUT = 120000;
   private static final int GET_TIMEOUT = 60000;
   
   private static int getThreadTimeout(int timeout) {
      return timeout + 5000;
   }

   static void put(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {

      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, "PUT", jsonBody, responseHandler, TIMEOUT);
         }
      }).start();
   }

   static void post(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {
      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, "POST", jsonBody, responseHandler, TIMEOUT);
         }
      }).start();
   }

   static void get(final String url, final ResponseHandler responseHandler) {
      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, null, null, responseHandler, GET_TIMEOUT);
         }
      }).start();
   }

   static void getSync(final String url, final ResponseHandler responseHandler) {
      makeRequest(url, null, null, responseHandler, GET_TIMEOUT);
   }

   static void putSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
      makeRequest(url, "PUT", jsonBody, responseHandler, TIMEOUT);
   }

   static void postSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
      makeRequest(url, "POST", jsonBody, responseHandler, TIMEOUT);
   }
   
   private static void makeRequest(final String url, final String method, final JSONObject jsonBody, final ResponseHandler responseHandler, final int timeout) {

      //if not a GET request, check if the user provided privacy consent if the application is set to require user privacy consent
      if (method != null && OneSignal.shouldLogUserPrivacyConsentErrorMessageForMethodName(null))
         return;
   
      final Thread[] callbackThread = new Thread[1];
      Thread connectionThread = new Thread(new Runnable() {
         public void run() {
            callbackThread[0] = startHTTPConnection(url, method, jsonBody, responseHandler, timeout);
         }
      }, "OS_HTTPConnection");
      
      connectionThread.start();
      
      // getResponseCode() can hang past it's timeout setting so join it's thread to ensure it is timing out.
      try {
         connectionThread.join(getThreadTimeout(timeout));
         if (connectionThread.getState() != Thread.State.TERMINATED)
            connectionThread.interrupt();
         if (callbackThread[0] != null)
            callbackThread[0].join();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
   }
   
   private static Thread startHTTPConnection(String url, String method, JSONObject jsonBody, ResponseHandler responseHandler, int timeout) {
      HttpURLConnection con = null;
      int httpResponse = -1;
      String json = null;
      Thread callbackThread = null;
   
      try {
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Making request to: " + BASE_URL + url);
         con = (HttpURLConnection)new URL(BASE_URL + url).openConnection();
         
         con.setUseCaches(false);
         con.setConnectTimeout(timeout);
         con.setReadTimeout(timeout);
         
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
      
         httpResponse = con.getResponseCode();
         OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "OneSignalRestClient: After con.getResponseCode  to: " + BASE_URL + url);
      
         InputStream inputStream;
         Scanner scanner;
         if (httpResponse == HttpURLConnection.HTTP_OK) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Successfully finished request to: " + BASE_URL + url);
         
            inputStream = con.getInputStream();
            scanner = new Scanner(inputStream, "UTF-8");
            json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            scanner.close();
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, method + " RECEIVED JSON: " + json);
   
            callbackThread = callResponseHandlerOnSuccess(responseHandler, json);
         }
         else {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OneSignalRestClient: Failed request to: " + BASE_URL + url);
            inputStream = con.getErrorStream();
            if (inputStream == null)
               inputStream = con.getInputStream();
         
            if (inputStream != null) {
               scanner = new Scanner(inputStream, "UTF-8");
               json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
               scanner.close();
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "OneSignalRestClient: " + method + " RECEIVED JSON: " + json);
            }
            else
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "OneSignalRestClient: " + method + " HTTP Code: " + httpResponse + " No response body!");
   
            callbackThread = callResponseHandlerOnFailure(responseHandler, httpResponse, json, null);
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
      });
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
      });
      thread.start();
      
      return thread;
   }
}