package com.onesignal;

import java.security.SecureRandom;

class OneSignalChromeTabAndroidFrame extends OneSignalChromeTab {
   private static boolean opened;

   static void setup(String appId, String userId, String adId) {
      if (opened)
         return;

      if (OneSignal.remoteParams == null || OneSignal.remoteParams.enterprise)
         return;

      if (appId == null || userId == null)
         return;

      String params = "?app_id=" + appId + "&user_id=" + userId;
      if (adId != null)
         params += "&ad_id=" + adId;
      params += "&cbs_id=" + new SecureRandom().nextInt(Integer.MAX_VALUE);

      opened = open("https://onesignal.com/android_frame.html" + params, false);
   }
}
