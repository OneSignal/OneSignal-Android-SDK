/**
 * Modified MIT License
 * 
 * Copyright 2015 OneSignal
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

import java.io.UnsupportedEncodingException;

import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import android.content.Context;

import com.loopj.android.http.*;

// We use new Threads for async calls instead of loopj's AsyncHttpClient for 2 reasons:
// 1. To make sure our callbacks finish in cases where these methods might be called from a short lived thread.
// 2. If there isn't a looper on the current thread we can't use loopj's built in async implementation without calling
//    Looper.prepare() which can have unexpected results on the current thread.

class OneSignalRestClient {
   private static final String BASE_URL = "https://onesignal.com/api/v1/";
   private static final int TIMEOUT = 20000;

   private static SyncHttpClient clientSync = new SyncHttpClient();

   static {
      // setTimeout method = socket timeout
      // setMaxRetriesAndTimeout = sleep between retries
      clientSync.setTimeout(TIMEOUT);
      clientSync.setMaxRetriesAndTimeout(3, TIMEOUT);
   }

   static void put(final Context context, final String url, JSONObject jsonBody, final ResponseHandlerInterface responseHandler) throws UnsupportedEncodingException {
      final StringEntity entity = new StringEntity(jsonBody.toString());

      new Thread(new Runnable() {
         public void run() {
            clientSync.put(context, BASE_URL + url, entity, "application/json", responseHandler);
         }
      }).start();
   }

   static void post(final Context context, final String url, JSONObject jsonBody, final ResponseHandlerInterface responseHandler) throws UnsupportedEncodingException {
      final StringEntity entity = new StringEntity(jsonBody.toString());

      new Thread(new Runnable() {
         public void run() {
            clientSync.post(context, BASE_URL + url, entity, "application/json", responseHandler);
         }
      }).start();
   }

   static void get(final Context context, final String url, final ResponseHandlerInterface responseHandler) {
      new Thread(new Runnable() {
         public void run() {
            clientSync.get(context, BASE_URL + url, responseHandler);
         }
      }).start();
   }

   static void putSync(Context context, String url, JSONObject jsonBody, ResponseHandlerInterface responseHandler) throws UnsupportedEncodingException {
      StringEntity entity = new StringEntity(jsonBody.toString());
      clientSync.put(context, BASE_URL + url, entity, "application/json", responseHandler);
   }

   static void postSync(Context context, String url, JSONObject jsonBody, ResponseHandlerInterface responseHandler) throws UnsupportedEncodingException {
      StringEntity entity = new StringEntity(jsonBody.toString());
      clientSync.post(context, BASE_URL + url, entity, "application/json", responseHandler);
   }

}