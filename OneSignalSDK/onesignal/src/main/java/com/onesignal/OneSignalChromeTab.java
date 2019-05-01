/**
 * Modified MIT License
 *
 * Copyright 2019 OneSignal
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
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;

class OneSignalChromeTab {

   private static boolean hasChromeTabLibrary() {
      try {
         // noinspection ConstantConditions
         return android.support.customtabs.CustomTabsServiceConnection.class != null;
      } catch (Throwable e) {
         return false;
      }
   }

   protected static boolean open(String url, boolean openActivity) {
      if (!hasChromeTabLibrary())
         return false;

      CustomTabsServiceConnection connection = new OneSignalCustomTabsServiceConnection(url, openActivity);
      return CustomTabsClient.bindCustomTabsService(OneSignal.appContext, "com.android.chrome", connection);
   }
   
   private static class OneSignalCustomTabsServiceConnection extends CustomTabsServiceConnection {
      private String url;
      private boolean openActivity;
      
      OneSignalCustomTabsServiceConnection(@NonNull String url, boolean openActivity) {
         this.url = url;
         this.openActivity = openActivity;
      }
      
      @Override
      public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient customTabsClient) {
         if (customTabsClient == null)
            return;

         customTabsClient.warmup(0);

         CustomTabsSession session = customTabsClient.newSession(null);
         if (session == null)
            return;

         Uri uri = Uri.parse(url);
         session.mayLaunchUrl(uri, null, null);

         // Shows tab as it's own Activity
         if (openActivity) {
            CustomTabsIntent.Builder mBuilder = new CustomTabsIntent.Builder(session);
            CustomTabsIntent customTabsIntent = mBuilder.build();
            customTabsIntent.intent.setData(uri);
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
               OneSignal.appContext.startActivity(customTabsIntent.intent, customTabsIntent.startAnimationBundle);
            else
               OneSignal.appContext.startActivity(customTabsIntent.intent);
         }
      }
   
      @Override
      public void onServiceDisconnected(ComponentName name) {
      }
   }
}
