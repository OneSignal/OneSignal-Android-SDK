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

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

class PushRegistratorFCM extends PushRegistratorAbstractGoogle {

   static class Params {
      @Nullable String projectId;
      @Nullable String appId;
      @Nullable String apiKey;
      Params(
         @Nullable String projectId,
         @Nullable String appId,
         @Nullable String apiKey
      ) {
         this.projectId = projectId;
         this.appId = appId;
         this.apiKey = apiKey;
      }
   }

   private static class ParamsInternal {
      @NonNull String projectId;
      @NonNull String appId;
      @NonNull String apiKey;
      ParamsInternal(
              @NonNull String projectId,
              @NonNull String appId,
              @NonNull String apiKey
      ) {
         this.projectId = projectId;
         this.appId = appId;
         this.apiKey = apiKey;
      }
   }

   // project_info.project_id
   private static final String FCM_DEFAULT_PROJECT_ID = "onesignal-shared-public";
   // client.client_info.mobilesdk_app_id
   private static final String FCM_DEFAULT_APP_ID = "1:754795614042:android:c682b8144a8dd52bc1ad63";
   // client.api_key.current_key
   private static final String FCM_DEFAULT_API_KEY_BASE64 = "QUl6YVN5QW5UTG41LV80TWMyYTJQLWRLVWVFLWFCdGd5Q3JqbFlV";

   private static final String FCM_APP_NAME = "ONESIGNAL_SDK_FCM_APP_NAME";

   private FirebaseApp firebaseApp;

   @NonNull private final Context context;
   @NonNull private final ParamsInternal params;

   PushRegistratorFCM(
       @NonNull Context context,
       @Nullable Params params
   ) {
      this.context = context;

      String projectId = FCM_DEFAULT_PROJECT_ID;
      String appId = FCM_DEFAULT_APP_ID;
      String apiKey = new String(Base64.decode(FCM_DEFAULT_API_KEY_BASE64, Base64.DEFAULT));

      if (params != null) {
         if (params.projectId != null) {
            projectId = params.projectId;
         }
         if (params.appId != null) {
            appId = params.appId;
         }
         if (params.apiKey != null) {
            apiKey = params.apiKey;
         }
      }
      this.params = new ParamsInternal(projectId, appId, apiKey);
   }

   @Override
   String getProviderName() {
      return "FCM";
   }

   @WorkerThread
   @Override
   String getToken(String senderId) throws ExecutionException, InterruptedException, IOException {
      initFirebaseApp(senderId);

      try {
         return getTokenWithClassFirebaseMessaging();
      } catch (NoClassDefFoundError | NoSuchMethodError e) {
         // Class or method wil be missing at runtime if firebase-message older than 21.0.0 is used.
         OneSignal.Log(
            OneSignal.LOG_LEVEL.INFO,
            "FirebaseMessaging.getToken not found, attempting to use FirebaseInstanceId.getToken"
         );
      }

      // Fallback for firebase-message versions older than 21.0.0
      return getTokenWithClassFirebaseInstanceId(senderId);
   }

   // This method is only used if firebase-message older than 21.0.0 is in the app
   // We are using reflection here so we can compile with firebase-message:22.0.0 and newer
   //   - This version of Firebase has completely removed FirebaseInstanceId
   @Deprecated
   @WorkerThread
   private String getTokenWithClassFirebaseInstanceId(String senderId) throws IOException {
      // The following code is equivalent to:
      //   FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance(firebaseApp);
      //   return instanceId.getToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE);
      Exception exception;
      try {
         Class<?> FirebaseInstanceIdClass = Class.forName("com.google.firebase.iid.FirebaseInstanceId");
         Method getInstanceMethod = FirebaseInstanceIdClass.getMethod("getInstance", FirebaseApp.class);
         Object instanceId = getInstanceMethod.invoke(null, firebaseApp);
         Method getTokenMethod = instanceId.getClass().getMethod("getToken", String.class, String.class);
         Object token = getTokenMethod.invoke(instanceId, senderId, "FCM");
         return (String) token;
      } catch (ClassNotFoundException e) {
         exception = e;
      } catch (NoSuchMethodException e) {
         exception = e;
      } catch (IllegalAccessException e) {
         exception = e;
      } catch (InvocationTargetException e) {
         exception = e;
      }

      throw new Error("Reflection error on FirebaseInstanceId.getInstance(firebaseApp).getToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE)", exception);
   }

   @WorkerThread
   private String getTokenWithClassFirebaseMessaging() throws ExecutionException, InterruptedException {
      // We use firebaseApp.get(FirebaseMessaging.class) instead of FirebaseMessaging.getInstance()
      //   as the latter uses the default Firebase app. We need to use a custom Firebase app as
      //   the senderId is provided at runtime.
      FirebaseMessaging fcmInstance = firebaseApp.get(FirebaseMessaging.class);
      // FirebaseMessaging.getToken API was introduced in firebase-messaging:21.0.0
      Task<String> tokenTask = fcmInstance.getToken();
      return Tasks.await(tokenTask);
   }

   private void initFirebaseApp(String senderId) {
      if (firebaseApp != null)
         return;

      FirebaseOptions firebaseOptions =
         new FirebaseOptions.Builder()
            .setGcmSenderId(senderId)
            .setApplicationId(params.appId)
            .setApiKey(params.apiKey)
            .setProjectId(params.projectId)
            .build();
      firebaseApp = FirebaseApp.initializeApp(context, firebaseOptions, FCM_APP_NAME);
   }
}
