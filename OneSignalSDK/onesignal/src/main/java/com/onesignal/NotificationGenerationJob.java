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


import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.security.SecureRandom;

class NotificationGenerationJob {
   Context context;
   JSONObject jsonPayload;
   boolean restoring;
   
   boolean showAsAlert;
   
   Long shownTimeStamp;
   
   CharSequence overriddenBodyFromExtender;
   CharSequence overriddenTitleFromExtender;
   Uri overriddenSound;
   Integer overriddenFlags;
   Integer orgFlags;
   Uri orgSound;
   
   NotificationGenerationJob(Context context) {
      this.context = context;
   }
   
   NotificationExtenderService.OverrideSettings overrideSettings;
   
   CharSequence getTitle() {
      if (overriddenTitleFromExtender != null)
         return overriddenTitleFromExtender;
      return jsonPayload.optString("title", null);
   }
   
   CharSequence getBody() {
      if (overriddenBodyFromExtender != null)
         return overriddenBodyFromExtender;
      return jsonPayload.optString("alert", null);
   }
   
   Integer getAndroidId() {
      if (overrideSettings == null)
         overrideSettings = new NotificationExtenderService.OverrideSettings();
      if (overrideSettings.androidNotificationId == null)
         overrideSettings.androidNotificationId = new SecureRandom().nextInt();
      
      return overrideSettings.androidNotificationId;
   }

   int getAndroidIdWithoutCreate() {
      if (overrideSettings == null || overrideSettings.androidNotificationId == null)
         return -1;

      return overrideSettings.androidNotificationId;
   }

   void setAndroidIdWithOutOverriding(Integer id) {
      if (id == null)
         return;

      if (overrideSettings != null && overrideSettings.androidNotificationId != null)
         return;

      if (overrideSettings == null)
         overrideSettings = new NotificationExtenderService.OverrideSettings();
      overrideSettings.androidNotificationId = id;
   }

   boolean hasExtender() {
      return overrideSettings != null && overrideSettings.extender != null;
   }
}
