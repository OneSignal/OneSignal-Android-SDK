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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class BackgroundBroadcaster {

   public static void Invoke(Context context, Bundle extras, boolean isActive) {
      if (extras.containsKey("bgn") && "1".equals(extras.getString("bgn"))) {
         Intent intent = new Intent();
         intent.setAction("com.onesignal.BackgroundBroadcast.RECEIVE");
         intent.setPackage(context.getPackageName());

         Bundle fullBundle = new Bundle(extras);
         fullBundle.putBoolean("isActive", isActive);
         intent.putExtra("data", fullBundle);

         context.sendBroadcast(intent);
         OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, "Sent OneSignal BackgroundBroadcaster");
      }
   }
}