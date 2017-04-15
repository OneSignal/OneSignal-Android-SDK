package com.onesignal;


import android.content.Context;
import android.content.Intent;

import org.robolectric.annotation.Implements;

@Implements(NotificationRestorer.class)
public class ShadowNotificationRestorer {
   public static void startService(Context context, Intent intent) {
      NotificationBundleProcessor.ProcessFromGCMIntentService(context, intent.getExtras(), null);
   }
}
