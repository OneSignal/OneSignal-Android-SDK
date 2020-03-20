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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;

import com.onesignal.AndroidSupportV4Compat.ActivityCompat;

public class PermissionsActivity extends Activity {

   private static final String TAG = PermissionsActivity.class.getCanonicalName();
   // TODO this will be removed once the handled is deleted
   // Default animation duration in milliseconds
   private static final int DELAY_TIME_CALLBACK_CALL = 500;
   private static final int REQUEST_LOCATION = 2;

   static boolean waiting, answered;
   private static ActivityLifecycleHandler.ActivityAvailableListener activityAvailableListener;
   
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      OneSignal.setAppContext(this);

      // Android sets android:hasCurrentPermissionsRequest if the Activity was recreated while
      //  the permission prompt is showing to the user.
      // This can happen if the task is cold resumed from the Recent Apps list.
      if (savedInstanceState != null &&
          savedInstanceState.getBoolean("android:hasCurrentPermissionsRequest", false))
         waiting = true;
      else
         requestPermission();
   }

   @Override
   protected void onNewIntent(Intent intent) {
      super.onNewIntent(intent);

      if (OneSignal.isInitDone())
         requestPermission();
   }

   private void requestPermission() {
      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/30
      // Activity maybe invoked directly through automated testing, omit prompting on old Android versions.
      if (Build.VERSION.SDK_INT < 23) {
         finish();
         overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out);
         return;
      }

      if (!waiting) {
         waiting = true;
         ActivityCompat.requestPermissions(this, new String[]{LocationGMS.requestPermission}, REQUEST_LOCATION);
      }
   }

   @Override
   public void onRequestPermissionsResult(final int requestCode, @NonNull String permissions[], @NonNull final int[] grantResults) {
      answered = true;
      waiting = false;

      // TODO improve this method
      // TODO after we remove IAM from being an activity window we may be able to remove this handler
      // This is not a good solution!
      // Current problem: IAM depends on an activity, because of prompt permission the evaluation of IAM
      // is being called before the prompt activity dismisses, so it's attaching the IAM to PermissionActivity
      // We need to wait for other activity to show
      if (requestCode == REQUEST_LOCATION) {
         new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
               boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
               LocationGMS.sendAndClearPromptHandlers(true, granted);
               if (granted)
                  LocationGMS.startGetLocation();
               else
                  LocationGMS.fireFailedComplete();
            }
         }, DELAY_TIME_CALLBACK_CALL);
      }
      ActivityLifecycleHandler.removeActivityAvailableListener(TAG);
      finish();
      overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out);
   }


   static void startPrompt() {
      if (PermissionsActivity.waiting || PermissionsActivity.answered)
         return;

      activityAvailableListener = new ActivityLifecycleHandler.ActivityAvailableListener() {
         @Override
         public void available(@NonNull Activity activity) {
            if (!activity.getClass().equals(PermissionsActivity.class)) {
               Intent intent = new Intent(activity, PermissionsActivity.class);
               intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
               activity.startActivity(intent);
               activity.overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out);
            }
         }
      };

      ActivityLifecycleHandler.setActivityAvailableListener(TAG, activityAvailableListener);
   }
}
