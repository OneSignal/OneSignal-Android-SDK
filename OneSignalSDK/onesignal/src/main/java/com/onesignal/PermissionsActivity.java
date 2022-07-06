/**
 * Modified MIT License
 *
 * Copyright 2022 OneSignal
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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.onesignal.AndroidSupportV4Compat.ActivityCompat;

import java.util.HashMap;

public class PermissionsActivity extends Activity {

   interface PermissionCallback {
      void onAccept();
      void onReject(boolean fallbackToSettings);
   }

   private static final String TAG = PermissionsActivity.class.getCanonicalName();
   // TODO this will be removed once the handled is deleted
   // Default animation duration in milliseconds
   private static final int DELAY_TIME_CALLBACK_CALL = 500;
   private static final int ONESIGNAL_PERMISSION_REQUEST_CODE = 2;
   private static final int REQUEST_SETTINGS = 3;

   private static boolean waiting, fallbackToSettings, neverAskAgainClicked;
   private static ActivityLifecycleHandler.ActivityAvailableListener activityAvailableListener;

   private static final String INTENT_EXTRA_PERMISSION_TYPE = "INTENT_EXTRA_PERMISSION_TYPE";
   private static final String INTENT_EXTRA_ANDROID_PERMISSION_STRING = "INTENT_EXTRA_ANDROID_PERMISSION_STRING";
   private static final String INTENT_EXTRA_CALLBACK_CLASS = "INTENT_EXTRA_CALLBACK_CLASS";

   private String permissionRequestType;

   private String androidPermissionString;

   private static final HashMap<String, PermissionCallback> callbackMap = new HashMap<>();

   public static void registerAsCallback(
      @NonNull String permissionType,
      @NonNull PermissionCallback callback
   ) {
      callbackMap.put(permissionType, callback);
   }

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      OneSignal.initWithContext(this);

      handleBundleParams(getIntent().getExtras());
   }

   @Override
   protected void onNewIntent(Intent intent) {
      super.onNewIntent(intent);
      handleBundleParams(intent.getExtras());
   }

   private void handleBundleParams(Bundle extras) {
      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/30
      // Activity maybe invoked directly through automated testing, omit prompting on old Android versions.
      if (Build.VERSION.SDK_INT < 23) {
         finish();
         overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out);
         return;
      }

      reregisterCallbackHandlers(extras);

      permissionRequestType = extras.getString(INTENT_EXTRA_PERMISSION_TYPE);
      androidPermissionString = extras.getString(INTENT_EXTRA_ANDROID_PERMISSION_STRING);
      requestPermission(androidPermissionString);
   }

   // Required if the app was killed while this prompt was showing
   private void reregisterCallbackHandlers(Bundle extras) {
      String className = extras.getString(INTENT_EXTRA_CALLBACK_CLASS);
      try {
         // Loads class into memory so it's static initialization block runs
         Class.forName(className);
      } catch (ClassNotFoundException e) {
         throw new RuntimeException(
            "Could not find callback class for PermissionActivity: " + className
         );
      }
   }

   private void requestPermission(String androidPermissionString) {
      if (!waiting) {
         waiting = true;
         neverAskAgainClicked = !ActivityCompat.shouldShowRequestPermissionRationale(PermissionsActivity.this, androidPermissionString);
         ActivityCompat.requestPermissions(this, new String[]{androidPermissionString}, ONESIGNAL_PERMISSION_REQUEST_CODE);
      }
   }

   @Override
   public void onRequestPermissionsResult(final int requestCode, @NonNull String permissions[], @NonNull final int[] grantResults) {
      waiting = false;

      // TODO improve this method
      // TODO after we remove IAM from being an activity window we may be able to remove this handler
      // This is not a good solution!
      // Current problem: IAM depends on an activity, because of prompt permission the evaluation of IAM
      // is being called before the prompt activity dismisses, so it's attaching the IAM to PermissionActivity
      // We need to wait for other activity to show
      if (requestCode == ONESIGNAL_PERMISSION_REQUEST_CODE) {
         new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
               boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

               PermissionCallback callback = callbackMap.get(permissionRequestType);
               if (callback == null)
                  throw new RuntimeException("Missing handler for permissionRequestType: " + permissionRequestType);

               if (granted)
                  callback.onAccept();
               else {
                  callback.onReject(shouldShowSettings());
               }
            }
         }, DELAY_TIME_CALLBACK_CALL);
      }

      ActivityLifecycleHandler activityLifecycleHandler = ActivityLifecycleListener.getActivityLifecycleHandler();
      if (activityLifecycleHandler != null)
         activityLifecycleHandler.removeActivityAvailableListener(TAG);
      finish();
      overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out);
   }

   private boolean shouldShowSettings() {
      return fallbackToSettings
              && neverAskAgainClicked
              && !ActivityCompat.shouldShowRequestPermissionRationale(PermissionsActivity.this, androidPermissionString);
   }

   static void startPrompt(
      boolean fallbackCondition,
      String permissionRequestType,
      String androidPermissionString,
      Class<?> callbackClass
   ) {
      if (PermissionsActivity.waiting)
         return;

      fallbackToSettings = fallbackCondition;
      activityAvailableListener = new ActivityLifecycleHandler.ActivityAvailableListener() {
         @Override
         public void available(@NonNull Activity activity) {
            if (!activity.getClass().equals(PermissionsActivity.class)) {
               Intent intent = new Intent(activity, PermissionsActivity.class);
               intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
               intent.putExtra(INTENT_EXTRA_PERMISSION_TYPE, permissionRequestType)
                     .putExtra(INTENT_EXTRA_ANDROID_PERMISSION_STRING, androidPermissionString)
                     .putExtra(INTENT_EXTRA_CALLBACK_CLASS, callbackClass.getName());
               activity.startActivity(intent);
               activity.overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out);
            }
         }
      };

      ActivityLifecycleHandler activityLifecycleHandler = ActivityLifecycleListener.getActivityLifecycleHandler();
      if (activityLifecycleHandler != null)
         activityLifecycleHandler.addActivityAvailableListener(TAG, activityAvailableListener);
   }
}
