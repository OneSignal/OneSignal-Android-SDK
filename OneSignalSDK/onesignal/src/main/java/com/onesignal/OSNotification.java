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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.NotificationBundleProcessor.PUSH_ADDITIONAL_DATA_KEY;
import static com.onesignal.OSNotificationController.GOOGLE_SENT_TIME_KEY;
import static com.onesignal.OSNotificationController.GOOGLE_TTL_KEY;
import static com.onesignal.OneSignalHmsEventBridge.HMS_SENT_TIME_KEY;
import static com.onesignal.OneSignalHmsEventBridge.HMS_TTL_KEY;

/**
 * The notification the user received
 * <br/><br/>
 * {@link #androidNotificationId} - Android Notification ID assigned to the notification. Can be used to cancel or replace the notification
 * {@link #groupedNotifications} - If the notification is a summary notification for a group, this will contain
 * all notification payloads it was created from.
 */
public class OSNotification {

   private NotificationCompat.Extender notificationExtender;

   /**
    * Summary notifications grouped
    * Notification payload will have the most recent notification received.
    */
   @Nullable
   private List<OSNotification> groupedNotifications;

   /**
    * Android notification id. Can later be used to dismiss the notification programmatically.
    */
   private int androidNotificationId;

   private String notificationId;
   private String templateName;
   private String templateId;
   private String title;
   private String body;
   private JSONObject additionalData;
   private String smallIcon;
   private String largeIcon;
   private String bigPicture;
   private String smallIconAccentColor;
   private String launchURL;
   private String sound;
   private String ledColor;
   private int lockScreenVisibility = 1;
   private String groupKey;
   private String groupMessage;
   private List<ActionButton> actionButtons;
   private String fromProjectNumber;
   private BackgroundImageLayout backgroundImageLayout;
   private String collapseId;
   private int priority;
   private String rawPayload;

   private long sentTime;
   private int ttl;

   protected OSNotification() {
   }

   OSNotification(@NonNull JSONObject payload) {
      this(null, payload, 0);
   }

   OSNotification(@Nullable List<OSNotification> groupedNotifications, @NonNull JSONObject jsonPayload, int androidNotificationId) {
      initPayloadData(jsonPayload);
      this.groupedNotifications = groupedNotifications;
      this.androidNotificationId = androidNotificationId;
   }

   protected OSNotification(OSNotification notification) {
      this.notificationExtender = notification.notificationExtender;
      this.groupedNotifications = notification.groupedNotifications;
      this.androidNotificationId = notification.androidNotificationId;
      this.notificationId = notification.notificationId;
      this.templateName = notification.templateName;
      this.templateId = notification.templateId;
      this.title = notification.title;
      this.body = notification.body;
      this.additionalData = notification.additionalData;
      this.smallIcon = notification.smallIcon;
      this.largeIcon = notification.largeIcon;
      this.bigPicture = notification.bigPicture;
      this.smallIconAccentColor = notification.smallIconAccentColor;
      this.launchURL = notification.launchURL;
      this.sound = notification.sound;
      this.ledColor = notification.ledColor;
      this.lockScreenVisibility = notification.lockScreenVisibility;
      this.groupKey = notification.groupKey;
      this.groupMessage = notification.groupMessage;
      this.actionButtons = notification.actionButtons;
      this.fromProjectNumber = notification.fromProjectNumber;
      this.backgroundImageLayout = notification.backgroundImageLayout;
      this.collapseId = notification.collapseId;
      this.priority = notification.priority;
      this.rawPayload = notification.rawPayload;
      this.sentTime = notification.sentTime;
      this.ttl = notification.ttl;
   }

   private void initPayloadData(JSONObject currentJsonPayload) {
      JSONObject customJson;
      try {
         customJson = NotificationBundleProcessor.getCustomJSONObject(currentJsonPayload);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationReceivedEvent payload values!", t);
         return;
      }

      long currentTime = OneSignal.getTime().getCurrentTimeMillis();
      if (currentJsonPayload.has(GOOGLE_TTL_KEY)) {
         sentTime = currentJsonPayload.optLong(GOOGLE_SENT_TIME_KEY, currentTime) / 1_000;
         ttl = currentJsonPayload.optInt(GOOGLE_TTL_KEY, OSNotificationRestoreWorkManager.DEFAULT_TTL_IF_NOT_IN_PAYLOAD);
      } else if (currentJsonPayload.has(HMS_TTL_KEY)) {
         sentTime = currentJsonPayload.optLong(HMS_SENT_TIME_KEY, currentTime) / 1_000;
         ttl = currentJsonPayload.optInt(HMS_TTL_KEY, OSNotificationRestoreWorkManager.DEFAULT_TTL_IF_NOT_IN_PAYLOAD);
      } else {
         sentTime = currentTime / 1_000;
         ttl = OSNotificationRestoreWorkManager.DEFAULT_TTL_IF_NOT_IN_PAYLOAD;
      }

      notificationId = customJson.optString("i");
      templateId = customJson.optString("ti");
      templateName = customJson.optString("tn");
      rawPayload = currentJsonPayload.toString();
      additionalData = customJson.optJSONObject(PUSH_ADDITIONAL_DATA_KEY);
      launchURL = customJson.optString("u", null);

      body = currentJsonPayload.optString("alert", null);
      title = currentJsonPayload.optString("title", null);
      smallIcon = currentJsonPayload.optString("sicon", null);
      bigPicture = currentJsonPayload.optString("bicon", null);
      largeIcon = currentJsonPayload.optString("licon", null);
      sound = currentJsonPayload.optString("sound", null);
      groupKey = currentJsonPayload.optString("grp", null);
      groupMessage = currentJsonPayload.optString("grp_msg", null);
      smallIconAccentColor = currentJsonPayload.optString("bgac", null);
      ledColor = currentJsonPayload.optString("ledc", null);
      String visibility = currentJsonPayload.optString("vis", null);
      if (visibility != null)
         lockScreenVisibility = Integer.parseInt(visibility);
      fromProjectNumber = currentJsonPayload.optString("from", null);
      priority = currentJsonPayload.optInt("pri", 0);
      String collapseKey = currentJsonPayload.optString("collapse_key", null);
      if (!"do_not_collapse".equals(collapseKey))
         collapseId = collapseKey;

      try {
         setActionButtons();
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationReceivedEvent.actionButtons values!", t);
      }

      try {
         setBackgroundImageLayout(currentJsonPayload);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationReceivedEvent.backgroundImageLayout values!", t);
      }
   }

   private void setActionButtons() throws Throwable {
      if (additionalData != null && additionalData.has("actionButtons")) {
         JSONArray jsonActionButtons = additionalData.getJSONArray("actionButtons");
         actionButtons = new ArrayList<>();

         for (int i = 0; i < jsonActionButtons.length(); i++) {
            JSONObject jsonActionButton = jsonActionButtons.getJSONObject(i);
            ActionButton actionButton = new ActionButton();
            actionButton.id = jsonActionButton.optString("id", null);
            actionButton.text = jsonActionButton.optString("text", null);
            actionButton.icon = jsonActionButton.optString("icon", null);
            actionButtons.add(actionButton);
         }
         additionalData.remove(BUNDLE_KEY_ACTION_ID);
         additionalData.remove("actionButtons");
      }
   }

   private void setBackgroundImageLayout(JSONObject currentJsonPayload) throws Throwable {
      String jsonStrBgImage = currentJsonPayload.optString("bg_img", null);
      if (jsonStrBgImage != null) {
         JSONObject jsonBgImage = new JSONObject(jsonStrBgImage);
         backgroundImageLayout = new BackgroundImageLayout();
         backgroundImageLayout.image = jsonBgImage.optString("img");
         backgroundImageLayout.titleTextColor = jsonBgImage.optString("tc");
         backgroundImageLayout.bodyTextColor = jsonBgImage.optString("bc");
      }
   }

   public OSMutableNotification mutableCopy() {
      return new OSMutableNotification(this);
   }

   OSNotification copy() {
      return new OSNotificationBuilder()
              .setNotificationExtender(notificationExtender)
              .setGroupedNotifications(groupedNotifications)
              .setAndroidNotificationId(androidNotificationId)
              .setNotificationId(notificationId)
              .setTemplateName(templateName)
              .setTemplateId(templateId)
              .setTitle(title)
              .setBody(body)
              .setAdditionalData(additionalData)
              .setSmallIcon(smallIcon)
              .setLargeIcon(largeIcon)
              .setBigPicture(bigPicture)
              .setSmallIconAccentColor(smallIconAccentColor)
              .setLaunchURL(launchURL)
              .setSound(sound)
              .setLedColor(ledColor)
              .setLockScreenVisibility(lockScreenVisibility)
              .setGroupKey(groupKey)
              .setGroupMessage(groupMessage)
              .setActionButtons(actionButtons)
              .setFromProjectNumber(fromProjectNumber)
              .setBackgroundImageLayout(backgroundImageLayout)
              .setCollapseId(collapseId)
              .setPriority(priority)
              .setRawPayload(rawPayload)
              .setSenttime(sentTime)
              .setTTL(ttl)
              .build();
   }

   public NotificationCompat.Extender getNotificationExtender() {
      return notificationExtender;
   }

   protected void setNotificationExtender(NotificationCompat.Extender notificationExtender) {
      this.notificationExtender = notificationExtender;
   }

   boolean hasNotificationId() {
      return androidNotificationId != 0;
   }

   public int getAndroidNotificationId() {
      return androidNotificationId;
   }

   protected void setAndroidNotificationId(int androidNotificationId) {
      this.androidNotificationId = androidNotificationId;
   }

   @Nullable
   public List<OSNotification> getGroupedNotifications() {
      return groupedNotifications;
   }

   void setGroupedNotifications(@Nullable List<OSNotification> groupedNotifications) {
      this.groupedNotifications = groupedNotifications;
   }

   public String getNotificationId() {
      return notificationId;
   }

   void setNotificationId(String notificationId) {
      this.notificationId = notificationId;
   }

   public String getTemplateName() {
      return templateName;
   }

   void setTemplateName(String templateName) {
      this.templateName = templateName;
   }

   public String getTemplateId() {
      return templateId;
   }

   void setTemplateId(String templateId) {
      this.templateId = templateId;
   }

   public String getTitle() {
      return title;
   }

   void setTitle(String title) {
      this.title = title;
   }

   public String getBody() {
      return body;
   }

   void setBody(String body) {
      this.body = body;
   }

   public JSONObject getAdditionalData() {
      return additionalData;
   }

   void setAdditionalData(JSONObject additionalData) {
      this.additionalData = additionalData;
   }

   public String getSmallIcon() {
      return smallIcon;
   }

   void setSmallIcon(String smallIcon) {
      this.smallIcon = smallIcon;
   }

   public String getLargeIcon() {
      return largeIcon;
   }

   void setLargeIcon(String largeIcon) {
      this.largeIcon = largeIcon;
   }

   public String getBigPicture() {
      return bigPicture;
   }

   void setBigPicture(String bigPicture) {
      this.bigPicture = bigPicture;
   }

   public String getSmallIconAccentColor() {
      return smallIconAccentColor;
   }

   void setSmallIconAccentColor(String smallIconAccentColor) {
      this.smallIconAccentColor = smallIconAccentColor;
   }

   public String getLaunchURL() {
      return launchURL;
   }

   void setLaunchURL(String launchURL) {
      this.launchURL = launchURL;
   }

   public String getSound() {
      return sound;
   }

   void setSound(String sound) {
      this.sound = sound;
   }

   public String getLedColor() {
      return ledColor;
   }

   void setLedColor(String ledColor) {
      this.ledColor = ledColor;
   }

   public int getLockScreenVisibility() {
      return lockScreenVisibility;
   }

   void setLockScreenVisibility(int lockScreenVisibility) {
      this.lockScreenVisibility = lockScreenVisibility;
   }

   public String getGroupKey() {
      return groupKey;
   }

   void setGroupKey(String groupKey) {
      this.groupKey = groupKey;
   }

   public String getGroupMessage() {
      return groupMessage;
   }

   void setGroupMessage(String groupMessage) {
      this.groupMessage = groupMessage;
   }

   public List<ActionButton> getActionButtons() {
      return actionButtons;
   }

   void setActionButtons(List<ActionButton> actionButtons) {
      this.actionButtons = actionButtons;
   }

   public String getFromProjectNumber() {
      return fromProjectNumber;
   }

   void setFromProjectNumber(String fromProjectNumber) {
      this.fromProjectNumber = fromProjectNumber;
   }

   public BackgroundImageLayout getBackgroundImageLayout() {
      return backgroundImageLayout;
   }

   void setBackgroundImageLayout(BackgroundImageLayout backgroundImageLayout) {
      this.backgroundImageLayout = backgroundImageLayout;
   }

   public String getCollapseId() {
      return collapseId;
   }

   void setCollapseId(String collapseId) {
      this.collapseId = collapseId;
   }

   public int getPriority() {
      return priority;
   }

   void setPriority(int priority) {
      this.priority = priority;
   }

   public String getRawPayload() {
      return rawPayload;
   }

   void setRawPayload(String rawPayload) {
      this.rawPayload = rawPayload;
   }

   public long getSentTime() {
      return sentTime;
   }

   private void setSentTime(long sentTime) {
      this.sentTime = sentTime;
   }

   public int getTtl() {
      return ttl;
   }

   private void setTtl(int ttl) {
      this.ttl = ttl;
   }

   public JSONObject toJSONObject() {
      JSONObject mainObj = new JSONObject();

      try {
         mainObj.put("androidNotificationId", androidNotificationId);

         JSONArray payloadJsonArray = new JSONArray();
         if (groupedNotifications != null) {
            for (OSNotification notification : groupedNotifications)
               payloadJsonArray.put(notification.toJSONObject());
         }

         mainObj.put("groupedNotifications", payloadJsonArray);
         mainObj.put("notificationId", notificationId);
         mainObj.put("templateName", templateName);
         mainObj.put("templateId", templateId);
         mainObj.put("title", title);
         mainObj.put("body", body);
         mainObj.put("smallIcon", smallIcon);
         mainObj.put("largeIcon", largeIcon);
         mainObj.put("bigPicture", bigPicture);
         mainObj.put("smallIconAccentColor", smallIconAccentColor);
         mainObj.put("launchURL", launchURL);
         mainObj.put("sound", sound);
         mainObj.put("ledColor", ledColor);
         mainObj.put("lockScreenVisibility", lockScreenVisibility);
         mainObj.put("groupKey", groupKey);
         mainObj.put("groupMessage", groupMessage);
         mainObj.put("fromProjectNumber", fromProjectNumber);
         mainObj.put("collapseId", collapseId);
         mainObj.put("priority", priority);

         if (additionalData != null)
            mainObj.put("additionalData", additionalData);

         if (actionButtons != null) {
            JSONArray actionButtonJsonArray = new JSONArray();
            for (ActionButton actionButton : actionButtons) {
               actionButtonJsonArray.put(actionButton.toJSONObject());
            }
            mainObj.put("actionButtons", actionButtonJsonArray);
         }

         mainObj.put("rawPayload", rawPayload);
      }
      catch(JSONException e) {
         e.printStackTrace();
      }

      return mainObj;
   }

   @Override
   public String toString() {
      return "OSNotification{" +
              "notificationExtender=" + notificationExtender +
              ", groupedNotifications=" + groupedNotifications +
              ", androidNotificationId=" + androidNotificationId +
              ", notificationId='" + notificationId + '\'' +
              ", templateName='" + templateName + '\'' +
              ", templateId='" + templateId + '\'' +
              ", title='" + title + '\'' +
              ", body='" + body + '\'' +
              ", additionalData=" + additionalData +
              ", smallIcon='" + smallIcon + '\'' +
              ", largeIcon='" + largeIcon + '\'' +
              ", bigPicture='" + bigPicture + '\'' +
              ", smallIconAccentColor='" + smallIconAccentColor + '\'' +
              ", launchURL='" + launchURL + '\'' +
              ", sound='" + sound + '\'' +
              ", ledColor='" + ledColor + '\'' +
              ", lockScreenVisibility=" + lockScreenVisibility +
              ", groupKey='" + groupKey + '\'' +
              ", groupMessage='" + groupMessage + '\'' +
              ", actionButtons=" + actionButtons +
              ", fromProjectNumber='" + fromProjectNumber + '\'' +
              ", backgroundImageLayout=" + backgroundImageLayout +
              ", collapseId='" + collapseId + '\'' +
              ", priority=" + priority +
              ", rawPayload='" + rawPayload + '\'' +
              '}';
   }

   /**
    * List of action buttons on the notification.
    */
   public static class ActionButton {
      private String id;
      private String text;
      private String icon;

      public ActionButton() {}

      public ActionButton(JSONObject jsonObject) {
         id = jsonObject.optString("id");
         text = jsonObject.optString("text");
         icon = jsonObject.optString("icon");
      }

      public ActionButton(String id, String text, String icon) {
         this.id = id;
         this.text = text;
         this.icon = icon;
      }

      public JSONObject toJSONObject() {
         JSONObject json = new JSONObject();
         try {
            json.put("id", id);
            json.put("text", text);
            json.put("icon", icon);
         }
         catch (Throwable t) {
            t.printStackTrace();
         }

         return json;
      }

      public String getId() {
         return id;
      }

      public String getText() {
         return text;
      }

      public String getIcon() {
         return icon;
      }
   }

   /**
    * If a background image was set, this object will be available.
    */
   public static class BackgroundImageLayout {
      private String image;
      private String titleTextColor;
      private String bodyTextColor;

      public String getImage() {
         return image;
      }

      public String getTitleTextColor() {
         return titleTextColor;
      }

      public String getBodyTextColor() {
         return bodyTextColor;
      }
   }

   public static class OSNotificationBuilder {

      private NotificationCompat.Extender notificationExtender;
      private List<OSNotification> groupedNotifications;
      private int androidNotificationId;

      private String notificationId;
      private String templateName, templateId;
      private String title, body;
      private JSONObject additionalData;
      private String smallIcon;
      private String largeIcon;
      private String bigPicture;
      private String smallIconAccentColor;
      private String launchURL;
      private String sound;
      private String ledColor;
      private int lockScreenVisibility = 1;
      private String groupKey;
      private String groupMessage;
      private List<ActionButton> actionButtons;
      private String fromProjectNumber;
      private BackgroundImageLayout backgroundImageLayout;
      private String collapseId;
      private int priority;
      private String rawPayload;

      private long sentTime;
      private int ttl;

      public OSNotificationBuilder() {
      }

      public OSNotificationBuilder setNotificationExtender(NotificationCompat.Extender notificationExtender) {
         this.notificationExtender = notificationExtender;
         return this;
      }

      public OSNotificationBuilder setGroupedNotifications(List<OSNotification> groupedNotifications) {
         this.groupedNotifications = groupedNotifications;
         return this;
      }

      public OSNotificationBuilder setAndroidNotificationId(int androidNotificationId) {
         this.androidNotificationId = androidNotificationId;
         return this;
      }

      public OSNotificationBuilder setNotificationId(String notificationId) {
         this.notificationId = notificationId;
         return this;
      }

      public OSNotificationBuilder setTemplateName(String templateName) {
         this.templateName = templateName;
         return this;
      }

      public OSNotificationBuilder setTemplateId(String templateId) {
         this.templateId = templateId;
         return this;
      }

      public OSNotificationBuilder setTitle(String title) {
         this.title = title;
         return this;
      }

      public OSNotificationBuilder setBody(String body) {
         this.body = body;
         return this;
      }

      public OSNotificationBuilder setAdditionalData(JSONObject additionalData) {
         this.additionalData = additionalData;
         return this;
      }

      public OSNotificationBuilder setSmallIcon(String smallIcon) {
         this.smallIcon = smallIcon;
         return this;
      }

      public OSNotificationBuilder setLargeIcon(String largeIcon) {
         this.largeIcon = largeIcon;
         return this;
      }

      public OSNotificationBuilder setBigPicture(String bigPicture) {
         this.bigPicture = bigPicture;
         return this;
      }

      public OSNotificationBuilder setSmallIconAccentColor(String smallIconAccentColor) {
         this.smallIconAccentColor = smallIconAccentColor;
         return this;
      }

      public OSNotificationBuilder setLaunchURL(String launchURL) {
         this.launchURL = launchURL;
         return this;
      }

      public OSNotificationBuilder setSound(String sound) {
         this.sound = sound;
         return this;
      }

      public OSNotificationBuilder setLedColor(String ledColor) {
         this.ledColor = ledColor;
         return this;
      }

      public OSNotificationBuilder setLockScreenVisibility(int lockScreenVisibility) {
         this.lockScreenVisibility = lockScreenVisibility;
         return this;
      }

      public OSNotificationBuilder setGroupKey(String groupKey) {
         this.groupKey = groupKey;
         return this;
      }

      public OSNotificationBuilder setGroupMessage(String groupMessage) {
         this.groupMessage = groupMessage;
         return this;
      }

      public OSNotificationBuilder setActionButtons(List<ActionButton> actionButtons) {
         this.actionButtons = actionButtons;
         return this;
      }

      public OSNotificationBuilder setFromProjectNumber(String fromProjectNumber) {
         this.fromProjectNumber = fromProjectNumber;
         return this;
      }

      public OSNotificationBuilder setBackgroundImageLayout(BackgroundImageLayout backgroundImageLayout) {
         this.backgroundImageLayout = backgroundImageLayout;
         return this;
      }

      public OSNotificationBuilder setCollapseId(String collapseId) {
         this.collapseId = collapseId;
         return this;
      }

      public OSNotificationBuilder setPriority(int priority) {
         this.priority = priority;
         return this;
      }

      public OSNotificationBuilder setRawPayload(String rawPayload) {
         this.rawPayload = rawPayload;
         return this;
      }

      public OSNotificationBuilder setSenttime(long sentTime) {
         this.sentTime = sentTime;
         return this;
      }

      public OSNotificationBuilder setTTL(int ttl) {
         this.ttl = ttl;
         return this;
      }

      public OSNotification build() {
         OSNotification payload = new OSNotification();
         payload.setNotificationExtender(notificationExtender);
         payload.setGroupedNotifications(groupedNotifications);
         payload.setAndroidNotificationId(androidNotificationId);
         payload.setNotificationId(notificationId);
         payload.setTemplateName(templateName);
         payload.setTemplateId(templateId);
         payload.setTitle(title);
         payload.setBody(body);
         payload.setAdditionalData(additionalData);
         payload.setSmallIcon(smallIcon);
         payload.setLargeIcon(largeIcon);
         payload.setBigPicture(bigPicture);
         payload.setSmallIconAccentColor(smallIconAccentColor);
         payload.setLaunchURL(launchURL);
         payload.setSound(sound);
         payload.setLedColor(ledColor);
         payload.setLockScreenVisibility(lockScreenVisibility);
         payload.setGroupKey(groupKey);
         payload.setGroupMessage(groupMessage);
         payload.setActionButtons(actionButtons);
         payload.setFromProjectNumber(fromProjectNumber);
         payload.setBackgroundImageLayout(backgroundImageLayout);
         payload.setCollapseId(collapseId);
         payload.setPriority(priority);
         payload.setRawPayload(rawPayload);
         payload.setSentTime(sentTime);
         payload.setTtl(ttl);
         return payload;
      }
   }
}
