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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.firebase.messaging.FirebaseMessaging;

// TODO: 4.0.0 - Switch to using <action android:name="com.google.firebase.INSTANCE_ID_EVENT"/>
// Note: Starting with Firebase Messaging 17.1.0 onNewToken in FirebaseMessagingService should be
//   used instead.

class PushRegistratorFCM extends PushRegistratorAbstractGoogle {

   private static final String FCM_DEFAULT_PROJECT_ID = "onesignal-shared-public"; // project_info.project_id
   private static final String FCM_DEFAULT_APP_ID = "1:754795614042:android:c682b8144a8dd52bc1ad63"; // client.client_info.mobilesdk_app_id
   private static final String FCM_DEFAULT_API_KEY = "AIzaSyAnTLn5-_4Mc2a2P-dKUeE-aBtgyCrjlYU"; // client.api_key.current_key

   private static final String FCM_APP_NAME = "ONESIGNAL_SDK_FCM_APP_NAME";

   private FirebaseApp firebaseApp;

   // Disable in the case where there isn't a default Firebase app as this will crash the app.
   //   The crash will happen where the Google Play services app fires an intent that the token
   //      needs to be refreshed.
   // This checks for gcm_defaultSenderId in values.xml (normally added from google-services.json)
   // https://github.com/OneSignal/OneSignal-Android-SDK/issues/552
   // TODO: FirebaseInstanceIdService was removed in firebase-messaging:18.0.0
   //   Can remove this method once this is our minimum version
   static void disableFirebaseInstanceIdService(Context context) {
      String senderId = OSUtils.getResourceString(context, "gcm_defaultSenderId", null);
      int componentState =
         senderId == null ?
         PackageManager.COMPONENT_ENABLED_STATE_DISABLED :
         PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

      PackageManager pm = context.getPackageManager();
      try {
         ComponentName componentName = new ComponentName(context, FirebaseInstanceIdService.class);
         pm.setComponentEnabledSetting(componentName, componentState, PackageManager.DONT_KILL_APP);
      } catch (NoClassDefFoundError ignored) {
         // Will throw if missing FirebaseInstanceIdService class, ignore in this case.
         // We already print a logcat error in another spot
      } catch (IllegalArgumentException ignored) {
         // also not handled
      }
   }

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

      FirebaseOptions firebaseOptions =
         new FirebaseOptions.Builder()
            .setGcmSenderId(senderId)
            .setApplicationId(getAppId())
            .setApiKey(getApiKey())
            .setProjectId(getProjectId())
            .build();
      firebaseApp = FirebaseApp.initializeApp(OneSignal.appContext, firebaseOptions, FCM_APP_NAME);
   }

   private static @NonNull String getAppId() {
      if (OneSignal.remoteParams.fcmParams.appId != null)
         return OneSignal.remoteParams.fcmParams.appId;
      return FCM_DEFAULT_APP_ID;
   }

   private static @NonNull String getApiKey() {
      if (OneSignal.remoteParams.fcmParams.apiKey != null)
         return OneSignal.remoteParams.fcmParams.apiKey;
      return FCM_DEFAULT_API_KEY;
   }

   private static @NonNull String getProjectId() {
      if (OneSignal.remoteParams.fcmParams.projectId != null)
         return OneSignal.remoteParams.fcmParams.projectId;
      return FCM_DEFAULT_PROJECT_ID;
   }
}
