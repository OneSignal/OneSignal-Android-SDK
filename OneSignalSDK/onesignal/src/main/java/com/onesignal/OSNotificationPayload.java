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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.NotificationBundleProcessor.PUSH_ADDITIONAL_DATA_KEY;

// Notification properties received from OneSignal.

/**
 * Contents and settings of the notification received. See
 * <a href="https://documentation.onesignal.com/docs/android-native-sdk#section--osnotificationpayload-">
 *     OSNotificationPayload | OneSignal Docs
 * </a> for a list of explanations for each field.
 */
public class OSNotificationPayload {
   private String notificationID;
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
   
   public OSNotificationPayload() {
   }
   
   public OSNotificationPayload(JSONObject jsonObject) {
      notificationID = jsonObject.optString("notificationID");
      title = jsonObject.optString("title");
      
      body = jsonObject.optString("body");
      additionalData = jsonObject.optJSONObject("additionalData");
      smallIcon = jsonObject.optString("smallIcon");
      largeIcon = jsonObject.optString("largeIcon");
      bigPicture = jsonObject.optString("bigPicture");
      smallIconAccentColor = jsonObject.optString("smallIconAccentColor");
      launchURL = jsonObject.optString("launchURL");
      sound = jsonObject.optString("sound");
      ledColor = jsonObject.optString("ledColor");
      lockScreenVisibility = jsonObject.optInt("lockScreenVisibility");
      groupKey = jsonObject.optString("groupKey");
      groupMessage = jsonObject.optString("groupMessage");
      
      if (jsonObject.has("actionButtons")) {
         actionButtons = new ArrayList<>();
         JSONArray jsonArray = jsonObject.optJSONArray("actionButtons");
         for (int i = 0; i < jsonArray.length(); i++)
            actionButtons.add(new ActionButton(jsonArray.optJSONObject(i)));
      }
   
      fromProjectNumber = jsonObject.optString("fromProjectNumber");
      collapseId = jsonObject.optString("collapseId");
      priority = jsonObject.optInt("priority");
      rawPayload = jsonObject.optString("rawPayload");
   }

   public String getNotificationID() {
      return notificationID;
   }

   public String getTemplateName() {
      return templateName;
   }

   public String getTemplateId() {
      return templateId;
   }

   public String getTitle() {
      return title;
   }

   public String getBody() {
      return body;
   }

   public JSONObject getAdditionalData() {
      return additionalData;
   }

   public String getSmallIcon() {
      return smallIcon;
   }

   public String getLargeIcon() {
      return largeIcon;
   }

   public String getBigPicture() {
      return bigPicture;
   }

   public String getSmallIconAccentColor() {
      return smallIconAccentColor;
   }

   public String getLaunchURL() {
      return launchURL;
   }

   public String getSound() {
      return sound;
   }

   public String getLedColor() {
      return ledColor;
   }

   public int getLockScreenVisibility() {
      return lockScreenVisibility;
   }

   public String getGroupKey() {
      return groupKey;
   }

   public String getGroupMessage() {
      return groupMessage;
   }

   public List<ActionButton> getActionButtons() {
      return actionButtons;
   }

   public String getFromProjectNumber() {
      return fromProjectNumber;
   }

   public BackgroundImageLayout getBackgroundImageLayout() {
      return backgroundImageLayout;
   }

   public String getCollapseId() {
      return collapseId;
   }

   public int getPriority() {
      return priority;
   }

   public String getRawPayload() {
      return rawPayload;
   }

   /**
    * List of action buttons on the notification. Part of {@link OSNotificationPayload}.
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
    * If a background image was set, this object will be available. Part of {@link OSNotificationPayload}.
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

   public JSONObject toJSONObject() {
      JSONObject json = new JSONObject();

      try {
         json.put("notificationID", notificationID);
         json.put("title", title);
         json.put("body", body);
         json.put("smallIcon", smallIcon);
         json.put("largeIcon", largeIcon);
         json.put("bigPicture", bigPicture);
         json.put("smallIconAccentColor", smallIconAccentColor);
         json.put("launchURL", launchURL);
         json.put("sound", sound);
         json.put("ledColor", ledColor);
         json.put("lockScreenVisibility", lockScreenVisibility);
         json.put("groupKey", groupKey);
         json.put("groupMessage", groupMessage);
         json.put("fromProjectNumber", fromProjectNumber);
         json.put("collapseId", collapseId);
         json.put("priority", priority);

         if (additionalData != null)
            json.put("additionalData", additionalData);

         if (actionButtons != null) {
            JSONArray actionButtonJsonArray = new JSONArray();
            for (ActionButton actionButton : actionButtons) {
               actionButtonJsonArray.put(actionButton.toJSONObject());
            }
            json.put("actionButtons", actionButtonJsonArray);
         }

         json.put("rawPayload", rawPayload);
      }
      catch (Throwable t) {
         t.printStackTrace();
      }

      return json;
   }

   public void fromJSONString(String jsonString) {
      try {
         JSONObject json = new JSONObject(jsonString);
         notificationID = json.getString("notificationID");
         title = json.getString("title");
         body = json.getString("body");
         smallIcon = json.getString("smallIcon");
         largeIcon = json.getString("largeIcon");
         bigPicture = json.getString("bigPicture");
         smallIconAccentColor = json.getString("smallIconAccentColor");
         launchURL = json.getString("launchURL");
         sound = json.getString("sound");
         ledColor = json.getString("ledColor");
         lockScreenVisibility = json.getInt("lockScreenVisibility");
         groupKey = json.getString("groupKey");
         groupMessage = json.getString("groupMessage");
         fromProjectNumber = json.getString("fromProjectNumber");
         collapseId = json.getString("collapseId");
         priority = json.getInt("priority");

         if (json.has("additionalData"))
            additionalData = new JSONObject(json.getString("additionalData"));

         if (json.has("actionButtons")) {
            JSONArray actionButtonJsonArray = new JSONArray(json.getString("actionButtons"));
            for (int i = 0; i < actionButtonJsonArray.length(); i++) {
               JSONObject actionButton = actionButtonJsonArray.getJSONObject(i);
               actionButtons.add(new ActionButton(actionButton));
            }
         }

         rawPayload = json.getString("rawPayload");
      }
      catch (Throwable t) {
         t.printStackTrace();
      }
   }

   static OSNotificationPayload OSNotificationPayloadFrom(JSONObject currentJsonPayload, JSONObject customJson) {
      OSNotificationPayload notification = new OSNotificationPayload();
      notification.notificationID = customJson.optString("i");
      notification.templateId = customJson.optString("ti");
      notification.templateName = customJson.optString("tn");
      notification.rawPayload = currentJsonPayload.toString();
      notification.additionalData = customJson.optJSONObject(PUSH_ADDITIONAL_DATA_KEY);
      notification.launchURL = customJson.optString("u", null);

      notification.body = currentJsonPayload.optString("alert", null);
      notification.title = currentJsonPayload.optString("title", null);
      notification.smallIcon = currentJsonPayload.optString("sicon", null);
      notification.bigPicture = currentJsonPayload.optString("bicon", null);
      notification.largeIcon = currentJsonPayload.optString("licon", null);
      notification.sound = currentJsonPayload.optString("sound", null);
      notification.groupKey = currentJsonPayload.optString("grp", null);
      notification.groupMessage = currentJsonPayload.optString("grp_msg", null);
      notification.smallIconAccentColor = currentJsonPayload.optString("bgac", null);
      notification.ledColor = currentJsonPayload.optString("ledc", null);
      String visibility = currentJsonPayload.optString("vis", null);
      if (visibility != null)
         notification.lockScreenVisibility = Integer.parseInt(visibility);
      notification.fromProjectNumber = currentJsonPayload.optString("from", null);
      notification.priority = currentJsonPayload.optInt("pri", 0);
      String collapseKey = currentJsonPayload.optString("collapse_key", null);
      if (!"do_not_collapse".equals(collapseKey))
         notification.collapseId = collapseKey;

      try {
         setActionButtons(notification);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationPayload.actionButtons values!", t);
      }

      try {
         setBackgroundImageLayout(notification, currentJsonPayload);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationPayload.backgroundImageLayout values!", t);
      }

      return notification;
   }

   private static void setActionButtons(OSNotificationPayload notification) throws Throwable {
      if (notification.additionalData != null && notification.additionalData.has("actionButtons")) {
         JSONArray jsonActionButtons = notification.additionalData.getJSONArray("actionButtons");
         notification.actionButtons = new ArrayList<>();

         for (int i = 0; i < jsonActionButtons.length(); i++) {
            JSONObject jsonActionButton = jsonActionButtons.getJSONObject(i);
            OSNotificationPayload.ActionButton actionButton = new OSNotificationPayload.ActionButton();
            actionButton.id = jsonActionButton.optString("id", null);
            actionButton.text = jsonActionButton.optString("text", null);
            actionButton.icon = jsonActionButton.optString("icon", null);
            notification.actionButtons.add(actionButton);
         }
         notification.additionalData.remove(BUNDLE_KEY_ACTION_ID);
         notification.additionalData.remove("actionButtons");
      }
   }

   private static void setBackgroundImageLayout(OSNotificationPayload notification, JSONObject currentJsonPayload) throws Throwable {
      String jsonStrBgImage = currentJsonPayload.optString("bg_img", null);
      if (jsonStrBgImage != null) {
         JSONObject jsonBgImage = new JSONObject(jsonStrBgImage);
         notification.backgroundImageLayout = new OSNotificationPayload.BackgroundImageLayout();
         notification.backgroundImageLayout.image = jsonBgImage.optString("img");
         notification.backgroundImageLayout.titleTextColor = jsonBgImage.optString("tc");
         notification.backgroundImageLayout.bodyTextColor = jsonBgImage.optString("bc");
      }
   }


}