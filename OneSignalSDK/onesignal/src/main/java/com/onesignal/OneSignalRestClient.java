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

   static void put(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {

      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, "PUT", jsonBody, responseHandler);
         }
      }).start();
   }

   static void post(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {
      new Thread(new Runnable() {
         public void run() {
            makeRequest(url, "POST", jsonBody, responseHandler);
         }
      }).start();
   }

   static void getSync(final String url, final ResponseHandler responseHandler) {
      makeRequest(url, null, null, responseHandler);
   }

   static void putSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
      makeRequest(url, "PUT", jsonBody, responseHandler);
   }

   static void postSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
      makeRequest(url, "POST", jsonBody, responseHandler);
   }

   private static void makeRequest(String url, String method, JSONObject jsonBody, ResponseHandler responseHandler) {
      HttpURLConnection con = null;
      int httpResponse = -1;
      String json = null;

      try {
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, BASE_URL + url);
         con = (HttpURLConnection)new URL(BASE_URL + url).openConnection();
         con.setUseCaches(false);
         con.setConnectTimeout(TIMEOUT);
         con.setReadTimeout(TIMEOUT);

         if (jsonBody != null)
            con.setDoInput(true);

         if (method != null) {
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestMethod(method);
            con.setDoOutput(true);
         }

         if (jsonBody != null) {
            String strJsonBody = jsonBody.toString();
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, method + " SEND JSON: " + strJsonBody);

            byte[] sendBytes = strJsonBody.getBytes("UTF-8");
            con.setFixedLengthStreamingMode(sendBytes.length);

            OutputStream outputStream = con.getOutputStream();
            outputStream.write(sendBytes);
         }

         httpResponse = con.getResponseCode();

         InputStream inputStream;
         Scanner scanner;
         if (httpResponse == HttpURLConnection.HTTP_OK) {
            inputStream = con.getInputStream();
            scanner = new Scanner(inputStream, "UTF-8");
            json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            scanner.close();
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, method + " RECEIVED JSON: " + json);

            if (responseHandler != null)
               responseHandler.onSuccess(json);
         }
         else {
            inputStream = con.getErrorStream();
            if (inputStream == null)
               inputStream = con.getInputStream();

            if (inputStream != null) {
               scanner = new Scanner(inputStream, "UTF-8");
               json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
               scanner.close();
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, method + " RECEIVED JSON: " + json);
            }
            else
               OneSignal.Log(OneSignal.LOG_LEVEL.WARN, method + " HTTP Code: " + httpResponse + " No response body!");

            if (responseHandler != null)
               responseHandler.onFailure(httpResponse, json, null);
         }
      } catch (Throwable t) {
         if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException)
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Could not send last request, device is offline. Throwable: " + t.getClass().getName());
         else
            OneSignal.Log(OneSignal.LOG_LEVEL.WARN, method + " Error thrown from network stack. ", t);

         if (responseHandler != null)
            responseHandler.onFailure(httpResponse, null, t);
      }
      finally {
         if (con != null)
            con.disconnect();
      }
   }
}