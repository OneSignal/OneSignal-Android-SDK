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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.onesignal.OneSignal.OSNotificationDisplay;

/**
 * The notification the user received
 * <br/><br/>
 * {@link #isAppInFocus} - Was app in focus.
 * {@link #shown} - Was notification shown to the user. Will be {@code false} for silent notifications.
 * {@link #androidNotificationId} - Android Notification ID assigned to the notification. Can be used to cancel or replace the notification
 * {@link #payload} - Payload received from OneSignal
 * {@link #displayOption} - How the notification will be displayed to the user, includes options:
 *    {@link OSNotificationDisplay#SILENT}
 *    {@link OSNotificationDisplay#NOTIFICATION}
 * <br/><br/>
 * {@link #groupedNotifications} - If the notification is a summary notification for a group, this will contain
 * all notification payloads it was created from.
 */
public class OSNotification {
   
   public OSNotification() {
   }
   
   public OSNotification(JSONObject jsonObject) {
      isAppInFocus = jsonObject.optBoolean("isAppInFocus");
      shown = jsonObject.optBoolean("shown", shown);
      androidNotificationId = jsonObject.optInt("androidNotificationId");
      displayOption = OSNotificationDisplay.values()[jsonObject.optInt("displayType")];

      if (jsonObject.has("groupedNotifications")) {
         JSONArray jsonArray = jsonObject.optJSONArray("groupedNotifications");
         groupedNotifications = new ArrayList<>();
         for (int i = 0; i < jsonArray.length(); i++)
            groupedNotifications.add(new OSNotificationPayload(jsonArray.optJSONObject(i)));
      }
   
      if (jsonObject.has("payload"))
         payload = new OSNotificationPayload(jsonObject.optJSONObject("payload"));
   }

   // Is app Active.
   public boolean isAppInFocus;

   // Was it displayed to the user.
   public boolean shown;

   // Android notification id. Can later be used to dismiss the notification programmatically.
   public int androidNotificationId;

   // Notification payload received from OneSignal
   public OSNotificationPayload payload;

   public OSNotificationDisplay displayOption;

   // Will be set if a summary notification is opened.
   //    The payload will be the most recent notification received.
   public List<OSNotificationPayload> groupedNotifications;

   public JSONObject toJSONObject() {
      JSONObject mainObj = new JSONObject();

      try {
         mainObj.put("isAppInFocus", isAppInFocus);
         mainObj.put("shown", shown);
         mainObj.put("androidNotificationId", androidNotificationId);
         mainObj.put("displayType", displayOption.ordinal());

         if (groupedNotifications != null) {
            JSONArray payloadJsonArray = new JSONArray();
            for(OSNotificationPayload payload : groupedNotifications)
               payloadJsonArray.put(payload.toJSONObject());
            mainObj.put("groupedNotifications", payloadJsonArray);
         }

         mainObj.put("payload", payload.toJSONObject());
      }
      catch(JSONException e) {
         e.printStackTrace();
      }

      return mainObj;
   }

}
