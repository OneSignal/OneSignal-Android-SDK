package com.onesignal;

import android.content.Context;
import android.util.Log;

import org.robolectric.annotation.Implements;

@Implements(OSUtils.class)
public class ShadowOSUtils {
   static int deviceType = 1;
   public static String carrierName = "test1";

   public static int subscribableStatus = 1;

   public int getDeviceType() {
      return deviceType;
   }

   public String getCarrierName() {
      return carrierName;
   }

   public int initializationChecker(Context context, int deviceType, String oneSignalAppId) {
      Log.e("HERE", "subscribableStatus: " + subscribableStatus);
      return subscribableStatus;
   }
}