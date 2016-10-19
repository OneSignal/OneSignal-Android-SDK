package com.onesignal;

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

   public int initializationChecker(int deviceType, String oneSignalAppId) {
      return subscribableStatus;
   }
}