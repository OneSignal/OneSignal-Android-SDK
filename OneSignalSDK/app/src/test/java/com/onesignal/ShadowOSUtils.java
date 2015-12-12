package com.onesignal;

import org.robolectric.annotation.Implements;

@Implements(OSUtils.class)
public class ShadowOSUtils {
   public static int deviceType = 1;

   public int getDeviceType() {
      return deviceType;
   }
}