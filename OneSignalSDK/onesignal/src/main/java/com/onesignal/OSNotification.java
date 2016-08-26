/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
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

import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class OSNotification {

   public enum DisplayType {
      // Notification shown in the notification shade.
      Notification,

      // Notification shown as an in app alert.
      InAppAlert,

      // Notification was silent and not displayed.
      None
   }

   // Is app Active.
   public boolean isAppInFocus;

   // Was it displayed to the user.
   public boolean shown;

   // Android notification id. Can later be used to dismiss the notification programmatically.
   public int androidNotificationId;

   // Notification payload received from OneSignal
   public OSNotificationPayload payload;

   public DisplayType displayType;

   // Will be set if a summary notification is opened.
   //    The payload will be the most recent notification received.
   public List<OSNotificationPayload> groupedNotifications;

   public String stringify() {

      JSONObject mainObj = new JSONObject();

      try {
         mainObj.put("isAppInFocus", isAppInFocus);
         mainObj.put("shown", shown);
         mainObj.put("androidNotificationId", androidNotificationId);
         mainObj.put("displayType", displayType);
         JSONObject pay = new JSONObject();
         pay.put("notificationID", payload.notificationID);
         pay.put("title", payload.title);
         pay.put("body", payload.body);
         pay.put("additionalData", payload.additionalData.toString());
         pay.put("smallIcon", payload.smallIcon);
         pay.put("largeIcon", payload.largeIcon);
         pay.put("bigPicture", payload.bigPicture);
         pay.put("smallIconAccentColor", payload.smallIconAccentColor);
         pay.put("launchURL", payload.launchURL);
         pay.put("sound", payload.sound);
         pay.put("ledColor", payload.ledColor);
         pay.put("lockScreenVisibility", payload.lockScreenVisibility);
         pay.put("groupKey", payload.groupKey);
         pay.put("groupMessage", payload.groupMessage);
         pay.put("actionButtons", payload.actionButtons);
         pay.put("fromProjectNumber", payload.fromProjectNumber);
         pay.put("rawPayload", payload.rawPayload);
         mainObj.put("payload", pay);
      }
      catch(JSONException e) {e.printStackTrace();}

      return mainObj.toString();
   }
}
