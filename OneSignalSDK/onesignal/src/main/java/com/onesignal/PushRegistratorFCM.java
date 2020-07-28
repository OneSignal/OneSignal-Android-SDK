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

import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

class PushRegistratorFCM extends PushRegistratorAbstractGoogle {

   // project_info.project_id
   private static final String FCM_DEFAULT_PROJECT_ID = "onesignal-shared-public";
   // client.client_info.mobilesdk_app_id
   private static final String FCM_DEFAULT_APP_ID = "1:754795614042:android:c682b8144a8dd52bc1ad63";
   // client.api_key.current_key
   private static final String FCM_DEFAULT_API_KEY_BASE64 = "QUl6YVN5QW5UTG41LV80TWMyYTJQLWRLVWVFLWFCdGd5Q3JqbFlV";

   private static final String FCM_APP_NAME = "ONESIGNAL_SDK_FCM_APP_NAME";

   private FirebaseApp firebaseApp;

   @Override
   String getProviderName() {
      return "FCM";
   }

   @Override
   String getToken(String senderId) throws Throwable {
      initFirebaseApp(senderId);
      FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance(firebaseApp);
      return instanceId.getToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE);
   }

   private void initFirebaseApp(String senderId) {
      if (firebaseApp != null)
         return;

      OneSignalRemoteParams.Params remoteParams = OneSignal.getRemoteParams();
      FirebaseOptions firebaseOptions =
         new FirebaseOptions.Builder()
            .setGcmSenderId(senderId)
            .setApplicationId(getAppId(remoteParams))
            .setApiKey(getApiKey(remoteParams))
            .setProjectId(getProjectId(remoteParams))
            .build();
      firebaseApp = FirebaseApp.initializeApp(OneSignal.appContext, firebaseOptions, FCM_APP_NAME);
   }

   private static @NonNull String getAppId(OneSignalRemoteParams.Params remoteParams) {
      if (remoteParams.fcmParams.appId != null)
         return remoteParams.fcmParams.appId;
      return FCM_DEFAULT_APP_ID;
   }

   private static @NonNull String getApiKey(OneSignalRemoteParams.Params remoteParams) {
      if (remoteParams.fcmParams.apiKey != null)
         return remoteParams.fcmParams.apiKey;
      return new String(Base64.decode(FCM_DEFAULT_API_KEY_BASE64, Base64.DEFAULT));
   }

   private static @NonNull String getProjectId(OneSignalRemoteParams.Params remoteParams) {
      if (remoteParams.fcmParams.projectId != null)
         return remoteParams.fcmParams.projectId;
      return FCM_DEFAULT_PROJECT_ID;
   }
}
