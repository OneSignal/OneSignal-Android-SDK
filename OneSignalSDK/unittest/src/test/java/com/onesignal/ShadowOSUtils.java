package com.onesignal;

import android.content.Context;

import org.robolectric.annotation.Implements;

import java.util.HashSet;

@Implements(OSUtils.class)
public class ShadowOSUtils {

   private static String FIRE_OS_CLASS = "com.amazon.device.messaging.ADM";

   static int deviceType;
   public static String carrierName;
   public static int subscribableStatus;

   /**
    * Used to mock the existence of FireOS ADM class for Amazon devices
    * TODO: This could apply to other classes that might not be available in our UnitTests but
    *  we want to mock in a way that makes sense and tests our SDK the closest to production functionality
    */
   private static HashSet<String> availableClasses;

   public static void mockAmazonDevice() {
      ShadowOSUtils.addClass(ShadowOSUtils.FIRE_OS_CLASS);
   }

   private static void addClass(String className) {
      availableClasses.add(className);
   }

   /**
    * Reset all static values (should be called before each test)
    */
   public static void resetStatics() {
      deviceType = 0;
      carrierName = "test1";
      subscribableStatus = 1;

      availableClasses = new HashSet<>();
   }

   public int getDeviceType() {
      // Class only available on the FireOS, if this class exists we want to mock Amazon deviceType
      if (availableClasses.contains(FIRE_OS_CLASS)) {
         deviceType = UserState.DEVICE_TYPE_FIREOS;
      } else {
         deviceType = UserState.DEVICE_TYPE_ANDROID;
      }
      // TODO: We will need to account for other device types as they are supported in the SDK

      return deviceType;
   }

   public String getCarrierName() {
      return carrierName;
   }

   int initializationChecker(Context context, String oneSignalAppId) {
      return subscribableStatus;
   }
}