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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

// This helper is only used if;
//    1. setFilterOtherGCMReceivers(true) is called.
//    2. Device is running Android Oreo 8.0 (API Level 26+)
//
// Provides helper methods to disable other FCM / GCM receivers and forward intents to them.
// This is used for Android Oreo devices since c2dm broadcasts are no longer ordered broadcasts.
// AndroidManifest.xml c2dm broadcast components are disabled and a runtime equivalent is added.
// The runtime replacements have a DataScheme on their IntentFilter so only this SDK can fire them.
class FCMIntentFilterHelper {
   private static final String C2DM_RECEIVE_ACTION = "com.google.android.c2dm.intent.RECEIVE";
   private static final String PROXY_CATEGORY = "com.onesignal.proxy";
   private static final String PROXY_SCHEME = "os_proxy_scheme";
   private static final String OS_BROADCAST_RECEIVER_CLASS = GcmBroadcastReceiver.class.getName();

   private static boolean runtimeReceiversSetup;

   static void setEnabledStateOfOtherReceivers(Context context, boolean enable) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         setEnabledStateOfOtherReceiversInternal(context, enable);
         if (!enable)
            createRuntimeFilteredReceivers(context);
      }

      OneSignal.saveFilterOtherGCMReceivers(!enable);
   }

   private static void createRuntimeFilteredReceivers(Context context) {
      if (runtimeReceiversSetup)
         return;

      IntentFilter filter = new IntentFilter(C2DM_RECEIVE_ACTION);
      filter.addCategory(PROXY_CATEGORY);
      filter.addDataScheme(PROXY_SCHEME);

      List<ResolveInfo> resolveInfo = getOtherC2DMReceivers(context, true);
      for(ResolveInfo info : resolveInfo) {
         try {
            Class clazz = Class.forName(info.activityInfo.name);
            context.registerReceiver((BroadcastReceiver)clazz.newInstance(), filter);
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }
      runtimeReceiversSetup = true;
   }

   static void sendBroadcastToRuntimeReceivers(Context context, Intent intent) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
         return;

      if (!OneSignal.getFilterOtherGCMReceivers())
         return;

      createRuntimeFilteredReceivers(context);

      intent.addCategory(PROXY_CATEGORY)
            .setData(Uri.parse(PROXY_SCHEME + "://"))
            .setComponent(null);

      Log.w("OneSignal", "sending Runtime proxied broadcast!!!!");
      context.sendBroadcast(intent);
   }

   private static void setEnabledStateOfOtherReceiversInternal(Context context, boolean enable) {
      List<ResolveInfo> resolveInfo = getOtherC2DMReceivers(context, enable);
      PackageManager packageManager = context.getPackageManager();
      String packageName = context.getPackageName();
      int componentEnabledState =
         enable ?
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT :
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED;


      for(ResolveInfo info : resolveInfo) {
         ComponentName componentName = new ComponentName(packageName, info.activityInfo.name);
         packageManager.setComponentEnabledSetting(
            componentName,
            componentEnabledState,
            PackageManager.DONT_KILL_APP
         );
      }
   }

   private static List<ResolveInfo> getOtherC2DMReceivers(Context context, boolean includeDisabled) {
      PackageManager packageManager = context.getPackageManager();
      Intent intent = new Intent(C2DM_RECEIVE_ACTION)
         .setPackage(context.getPackageName());

      int flags = PackageManager.GET_META_DATA;
      if (includeDisabled)
         flags |= PackageManager.GET_DISABLED_COMPONENTS;

      List<ResolveInfo> resolveInfo = packageManager.queryBroadcastReceivers(intent, flags);
      List<ResolveInfo> filteredList = new ArrayList<>(resolveInfo.size() - 1);
      for(ResolveInfo info : resolveInfo) {
         if (!info.activityInfo.name.equals(OS_BROADCAST_RECEIVER_CLASS))
            filteredList.add(info);
      }
      return filteredList;
   }
}
