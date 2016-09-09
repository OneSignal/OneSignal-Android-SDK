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
import android.support.annotation.NonNull;

import com.onesignal.AndroidSupportV4Compat.ActivityCompat;

public class PermissionsActivity extends Activity {

   private static final int REQUEST_LOCATION = 2;

   static boolean waiting, answered;
   private static ActivityLifecycleHandler.ActivityAvailableListener activityAvailableListener;
   
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

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

      if (OneSignal.initDone)
         requestPermission();
   }

   private void requestPermission() {
      // https://github.com/OneSignal/OneSignal-Android-SDK/issues/30
      // Activity maybe invoked directly through automated testing, omit prompting on old Android versions.
      if (Build.VERSION.SDK_INT < 23) {
         finish();
         return;
      }

      if (!waiting) {
         waiting = true;
         ActivityCompat.requestPermissions(this, new String[]{LocationGMS.requestPermission}, REQUEST_LOCATION);
      }
   }

   @Override
   public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
      answered = true;
      waiting = false;

      if (requestCode == REQUEST_LOCATION) {
         if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            LocationGMS.startGetLocation();
         else
            LocationGMS.fireFailedComplete();
      }

      ActivityLifecycleHandler.removeActivityAvailableListener(activityAvailableListener);
      finish();
   }


   static void startPrompt() {
      if (PermissionsActivity.waiting || PermissionsActivity.answered)
         return;

      activityAvailableListener = new ActivityLifecycleHandler.ActivityAvailableListener() {
         @Override
         public void available(Activity activity) {
            if (!activity.getClass().equals(PermissionsActivity.class)) {
               Intent intent = new Intent(activity, PermissionsActivity.class);
               intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
               activity.startActivity(intent);
            }
         }
      };

      ActivityLifecycleHandler.setActivityAvailableListener(activityAvailableListener);
   }
}
