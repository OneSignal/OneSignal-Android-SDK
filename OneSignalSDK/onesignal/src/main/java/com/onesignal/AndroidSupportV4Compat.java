/**
 * Modified MIT License
 * Copyright 2016 OneSignal
 *
 * Internals Copyright (C) 2012 The Android Open Source Project
 *     Licensed under the Apache License, Version 2.0 (the "License");
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

// Designed as a compat for use of Android Support v4 revision 23.+ methods when an older revision of the library is included with the app developer's project.
class AndroidSupportV4Compat {

   static class ContextCompat {
      static int checkSelfPermission(@NonNull Context context, @NonNull String permission) {
         // Catch for rare "Unknown exception code: 1 msg null" exception
         // See https://github.com/one-signal/OneSignal-Android-SDK/issues/48 for more details.
         try {
            return context.checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid());
         } catch (Throwable t) {
            Log.e("OneSignal", "checkSelfPermission failed, returning PERMISSION_DENIED");
            return PackageManager.PERMISSION_DENIED;
         }
      }

      static int getColor(Context context, int id) {
         if (Build.VERSION.SDK_INT > 22)
            return context.getColor(id);
         return context.getResources().getColor(id);
      }
   }

   interface RequestPermissionsRequestCodeValidator {
      void validateRequestPermissionsRequestCode(int requestCode);
   }

   static class ActivityCompat {
      static void requestPermissions(final @NonNull Activity activity, final @NonNull String[] permissions, final int requestCode) {
         // OneSignal SDK code already checks that device is Android M, omit else code from the support library.
         ActivityCompatApi23.requestPermissions(activity, permissions, requestCode);
      }
   }

   @TargetApi(23)
   static class ActivityCompatApi23 {
      static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
         if (activity instanceof RequestPermissionsRequestCodeValidator)
            ((RequestPermissionsRequestCodeValidator) activity).validateRequestPermissionsRequestCode(requestCode);
         activity.requestPermissions(permissions, requestCode);
      }
   }
}
