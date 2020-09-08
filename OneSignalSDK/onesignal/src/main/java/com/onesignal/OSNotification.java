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

import com.onesignal.OneSignal.OSNotificationDisplay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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

   // Will be set if a summary notification is opened.
   // The payload will be the most recent notification received.
   private List<OSNotificationPayload> groupedNotifications;

   // Notification payload received from OneSignal
   private OSNotificationPayload payload;

   private OSNotificationDisplay displayOption;

   // Is app Active.
   private boolean isAppInFocus;

   // Was it displayed to the user.
   private boolean shown;

   // Android notification id. Can later be used to dismiss the notification programmatically.
   private int androidNotificationId;

   public OSNotification(List<OSNotificationPayload> groupedNotifications, OSNotificationPayload payload,
                         OSNotificationDisplay displayOption) {
      this(groupedNotifications, payload, displayOption, false, false, 0);
   }

   public OSNotification(List<OSNotificationPayload> groupedNotifications, OSNotificationPayload payload,
                         OSNotificationDisplay displayOption, boolean isAppInFocus,
                         boolean shown, int androidNotificationId) {
      this.groupedNotifications = groupedNotifications;
      this.payload = payload;
      this.displayOption = displayOption;
      this.isAppInFocus = isAppInFocus;
      this.shown = shown;
      this.androidNotificationId = androidNotificationId;
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

   public OSNotificationPayload getPayload() {
      return payload;
   }

   public OSNotificationDisplay getDisplayOption() {
      return displayOption;
   }

   public List<OSNotificationPayload> getGroupedNotifications() {
      return groupedNotifications;
   }

   public boolean isAppInFocus() {
      return isAppInFocus;
   }

   public boolean isShown() {
      return shown;
   }

   public int getAndroidNotificationId() {
      return androidNotificationId;
   }
}
