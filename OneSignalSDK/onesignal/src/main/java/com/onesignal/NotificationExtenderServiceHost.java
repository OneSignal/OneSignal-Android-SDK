package com.onesignal;

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.support.annotation.Nullable;

import java.util.List;

public class NotificationExtenderServiceHost extends IntentService {
   
   public NotificationExtenderServiceHost() {
      super("NotificationExtenderServiceHost");
      setIntentRedelivery(true);
   }
   
   @Override
   protected void onHandleIntent(@Nullable Intent intent) {
      // TODO: Create NotificationExtenderService instance here, passing this as a Context.
   }
}
