package com.onesignal;

import android.content.Context;

import org.robolectric.annotation.Implements;

@Implements(OSUtils.class)
public class ShadowOSUtils {

   public static String carrierName;
   public static int subscribableStatus;

   // FireOS: ADM
   public static boolean supportsADM;
   public boolean supportsADM() {
      return supportsADM;
   }

   // Google: GCM / FCM
   public static boolean hasFCMLibrary;
   boolean hasFCMLibrary() {
      return hasFCMLibrary;
   }
   public static boolean hasGCMLibrary;
   public static boolean hasGCMLibrary() {
      return hasGCMLibrary;
   }
   public static boolean isGMSInstalledAndEnabled;
   public static boolean isGMSInstalledAndEnabled() {
      return isGMSInstalledAndEnabled;
   }

   // Huawei: HMS
   public static boolean hasHMSAvailability;
   public boolean hasHMSAvailability() {
      return hasHMSAvailability;
   }
   public static boolean hasHMSPushKitLibrary;
   public boolean hasHMSPushKitLibrary() {
      return hasHMSPushKitLibrary;
   }
   public static boolean isHMSCoreInstalledAndEnabled;
   public static boolean isHMSCoreInstalledAndEnabled() {
      return isHMSCoreInstalledAndEnabled;
   }
   public static boolean isHMSCoreInstalledAndEnabledFallback() {
      return isHMSCoreInstalledAndEnabled;
   }

   public static void supportsHMS(boolean value) {
      hasHMSPushKitLibrary = true;
      hasHMSAvailability = true;
      isHMSCoreInstalledAndEnabled = true;
   }

   /**
    * Reset all static values (should be called before each test)
    */
   public static void resetStatics() {
      carrierName = "test1";
      subscribableStatus = 1;

      supportsADM = false;
      hasFCMLibrary = false;
      hasGCMLibrary = false;
      isGMSInstalledAndEnabled = false;
      hasHMSAvailability = false;
      hasHMSPushKitLibrary = false;
      isHMSCoreInstalledAndEnabled = false;

   }

   public String getCarrierName() {
      return carrierName;
   }

   int initializationChecker(Context context, String oneSignalAppId) {
      return subscribableStatus;
   }
}