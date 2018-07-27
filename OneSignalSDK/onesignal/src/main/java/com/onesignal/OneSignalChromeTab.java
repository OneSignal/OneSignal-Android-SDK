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

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;

import java.security.SecureRandom;

class OneSignalChromeTab {
   
   private static boolean opened;
   
   static void setup(Context context, String appId, String userId, String adId) {
      if (opened)
         return;
      
      if (OneSignal.mEnterp)
         return;
      
      if (userId == null)
         return;
      
      try {
         Class.forName("android.support.customtabs.CustomTabsServiceConnection");
      } catch (ClassNotFoundException e) {
         return;
      }
   
      String params = "?app_id=" + appId + "&user_id=" + userId;
      if (adId != null)
         params += "&ad_id=" + adId;
      params += "&cbs_id=" + new SecureRandom().nextInt(Integer.MAX_VALUE);
      
      CustomTabsServiceConnection connection = new OneSignalCustomTabsServiceConnection(context, params);
      opened = CustomTabsClient.bindCustomTabsService(context, "com.android.chrome", connection);
   }
   
   private static class OneSignalCustomTabsServiceConnection extends CustomTabsServiceConnection {
   
      private Context mContext;
      private String mParams;
      
      OneSignalCustomTabsServiceConnection(Context context, String params) {
         mContext = context;
         mParams = params;
      }
      
      @Override
      public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient customTabsClient) {
         if (customTabsClient == null)
            return;

         customTabsClient.warmup(0);

         CustomTabsSession session = customTabsClient.newSession(new CustomTabsCallback()  {
            public void onNavigationEvent(int navigationEvent, Bundle extras) {
               super.onNavigationEvent(navigationEvent, extras);
            }

            public void extraCallback(String callbackName, Bundle args) {
               super.extraCallback(callbackName, args);
            }
         });

         if (session == null)
            return;

         Uri uri = Uri.parse("https://onesignal.com/android_frame.html" + mParams);
         session.mayLaunchUrl(uri, null, null);

         // Shows tab as it's own Activity
         /*
         CustomTabsIntent.Builder mBuilder = new CustomTabsIntent.Builder(session);
         CustomTabsIntent customTabsIntent = mBuilder.build();
         customTabsIntent.intent.setData(uri);
         customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
         mContext.startActivity(customTabsIntent.intent, customTabsIntent.startAnimationBundle);
         */
      }
   
      @Override
      public void onServiceDisconnected(ComponentName name) {
      }
   }
}
