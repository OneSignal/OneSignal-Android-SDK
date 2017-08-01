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

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

class ActivityLifecycleListener implements Application.ActivityLifecycleCallbacks {

   @Override
   public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
      ActivityLifecycleHandler.onActivityCreated(activity);
   }

   @Override
   public void onActivityStarted(Activity activity) {
      ActivityLifecycleHandler.onActivityStarted(activity);
   }

   @Override
   public void onActivityResumed(Activity activity) {
      ActivityLifecycleHandler.onActivityResumed(activity);
   }

   @Override
   public void onActivityPaused(Activity activity) {
      ActivityLifecycleHandler.onActivityPaused(activity);
   }

   @Override
   public void onActivityStopped(Activity activity) {
      ActivityLifecycleHandler.onActivityStopped(activity);
   }

   @Override
   public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

   @Override
   public void onActivityDestroyed(Activity activity) {
      ActivityLifecycleHandler.onActivityDestroyed(activity);
   }
}
