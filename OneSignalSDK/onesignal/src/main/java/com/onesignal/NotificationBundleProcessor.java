/**
 * Modified MIT License
 * 
 * Copyright 2019 OneSignal
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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.OneSignalDbContract.NotificationTable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Set;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID;
import static com.onesignal.NotificationExtenderService.EXTENDER_SERVICE_JOB_ID;

/** Processes the Bundle received from a push.
 * This class handles both processing bundles from a BroadcastReceiver or from a Service
 *   - Entry points are processBundleFromReceiver or ProcessFromGCMIntentService respectively
 * NOTE: Could split up this class since it does a number of different things
 * */
class NotificationBundleProcessor {

   public static final String PUSH_ADDITIONAL_DATA_KEY = "a";

   public static final String PUSH_MINIFIED_BUTTONS_LIST = "o";
   public static final String PUSH_MINIFIED_BUTTON_ID = "i";
   public static final String PUSH_MINIFIED_BUTTON_TEXT = "n";
   public static final String PUSH_MINIFIED_BUTTON_ICON = "p";

   private static final String IAM_PREVIEW_KEY = "os_in_app_message_preview_id";
   static final String DEFAULT_ACTION = "__DEFAULT__";


   static void ProcessFromGCMIntentService(Context context, BundleCompat bundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      OneSignal.setAppContext(context);
      try {
         String jsonStrPayload = bundle.getString("json_payload");
         if (jsonStrPayload == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "json_payload key is nonexistent from mBundle passed to ProcessFromGCMIntentService: " + bundle);
            return;
         }
   
         NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
         notifJob.restoring = bundle.getBoolean("restoring", false);
         notifJob.shownTimeStamp = bundle.getLong("timestamp");
         notifJob.jsonPayload = new JSONObject(jsonStrPayload);
         notifJob.isInAppPreviewPush = inAppPreviewPushUUID(notifJob.jsonPayload) != null;

         if (!notifJob.restoring &&
             !notifJob.isInAppPreviewPush &&
             OneSignal.notValidOrDuplicated(context, notifJob.jsonPayload))
            return;

         if (bundle.containsKey("android_notif_id")) {
            if (overrideSettings == null)
               overrideSettings = new NotificationExtenderService.OverrideSettings();
            overrideSettings.androidNotificationId = bundle.getInt("android_notif_id");
         }
         
         notifJob.overrideSettings = overrideSettings;
         ProcessJobForDisplay(notifJob);

         // Delay to prevent CPU spikes.
         //    Normally more than one notification is restored at a time.
         if (notifJob.restoring)
            OSUtils.sleep(100);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static int ProcessJobForDisplay(NotificationGenerationJob notifJob) {
      notifJob.showAsAlert = OneSignal.getInAppAlertNotificationEnabled() && OneSignal.isAppActive();
      processCollapseKey(notifJob);

      boolean doDisplay = shouldDisplayNotif(notifJob);
      if (doDisplay)
         GenerateNotification.fromJsonPayload(notifJob);

      if (!notifJob.restoring && !notifJob.isInAppPreviewPush) {
         processNotification(notifJob, false);
         try {
            JSONObject jsonObject = new JSONObject(notifJob.jsonPayload.toString());
            jsonObject.put(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notifJob.getAndroidId());
            OneSignal.handleNotificationReceived(newJsonArray(jsonObject), true, notifJob.showAsAlert);
         } catch (JSONException t) {
            t.printStackTrace();
         }
      }

      return notifJob.getAndroidId();
   }

   private static boolean shouldDisplayNotif(NotificationGenerationJob notifJob) {
      // Validate that the current Android device is Android 4.4 or higher and the current job is a
      //    preview push
      if (notifJob.isInAppPreviewPush && Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2)
         return false;

      // Otherwise, this is a normal notification and should be shown
      return notifJob.hasExtender() ||
              shouldDisplay(notifJob.jsonPayload.optString("alert"));
   }

   private static JSONArray bundleAsJsonArray(Bundle bundle) {
      JSONArray jsonArray = new JSONArray();
      jsonArray.put(bundleAsJSONObject(bundle));
      return jsonArray;
   }

   private static void saveAndProcessNotification(Context context, Bundle bundle, boolean opened, int notificationId) {
      NotificationGenerationJob notifJob = new NotificationGenerationJob(context);
      notifJob.jsonPayload = bundleAsJSONObject(bundle);
      notifJob.overrideSettings = new NotificationExtenderService.OverrideSettings();
      notifJob.overrideSettings.androidNotificationId = notificationId;

      processNotification(notifJob, opened);
   }

   /**
    * Save notification, updates Outcomes, and sends Received Receipt if they are enabled.
    */
   static void processNotification(NotificationGenerationJob notifiJob, boolean opened) {
      saveNotification(notifiJob, opened);

      if (!notifiJob.isNotificationToDisplay())
         return;
      String notificationId = notifiJob.getApiNotificationId();
      OneSignal.getSessionManager().onNotificationReceived(notificationId);
      OSReceiveReceiptController.getInstance().sendReceiveReceipt(notificationId);
   }

   // Saving the notification provides the following:
   //   * Prevent duplicates
   //   * Build summary notifications
   //   * Collapse key / id support - Used to lookup the android notification id later
   //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
   //   * Future - Public API to get a list of notifications
   private static void saveNotification(NotificationGenerationJob notifiJob, boolean opened) {
      Context context = notifiJob.context;
      JSONObject jsonPayload = notifiJob.jsonPayload;
      
      try {
         JSONObject customJSON = getCustomJSONObject(notifiJob.jsonPayload);
   
         OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notifiJob.context);

         // Count any notifications with duplicated android notification ids as dismissed.
         // -1 is used to note never displayed
         if (notifiJob.isNotificationToDisplay()) {
            String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notifiJob.getAndroidIdWithoutCreate();

            ContentValues values = new ContentValues();
            values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

            dbHelper.update(NotificationTable.TABLE_NAME, values, whereStr, null);
            BadgeCountUpdater.update(dbHelper, context);
         }

         // Save just received notification to DB
         ContentValues values = new ContentValues();
         values.put(NotificationTable.COLUMN_NAME_NOTIFICATION_ID, customJSON.optString("i"));
         if (jsonPayload.has("grp"))
            values.put(NotificationTable.COLUMN_NAME_GROUP_ID, jsonPayload.optString("grp"));
         if (jsonPayload.has("collapse_key") && !"do_not_collapse".equals(jsonPayload.optString("collapse_key")))
            values.put(NotificationTable.COLUMN_NAME_COLLAPSE_ID, jsonPayload.optString("collapse_key"));

         values.put(NotificationTable.COLUMN_NAME_OPENED, opened ? 1 : 0);
         if (!opened)
            values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, notifiJob.getAndroidIdWithoutCreate());

         if (notifiJob.getTitle() != null)
            values.put(NotificationTable.COLUMN_NAME_TITLE, notifiJob.getTitle().toString());
         if (notifiJob.getBody() != null)
            values.put(NotificationTable.COLUMN_NAME_MESSAGE, notifiJob.getBody().toString());

         // Set expire_time
         long sentTime = jsonPayload.optLong("google.sent_time", SystemClock.currentThreadTimeMillis()) / 1_000L;
         int ttl = jsonPayload.optInt("google.ttl", NotificationRestorer.DEFAULT_TTL_IF_NOT_IN_PAYLOAD);
         long expireTime = sentTime + ttl;
         values.put(NotificationTable.COLUMN_NAME_EXPIRE_TIME, expireTime);

         values.put(NotificationTable.COLUMN_NAME_FULL_DATA, jsonPayload.toString());

         dbHelper.insertOrThrow(NotificationTable.TABLE_NAME, null, values);

         if (!opened)
            BadgeCountUpdater.update(dbHelper, context);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static void markRestoredNotificationAsDismissed(NotificationGenerationJob notifiJob) {
      if (notifiJob.getAndroidIdWithoutCreate() == -1)
         return;

      String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notifiJob.getAndroidIdWithoutCreate();

      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notifiJob.context);

      ContentValues values = new ContentValues();
      values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

      dbHelper.update(NotificationTable.TABLE_NAME, values, whereStr, null);
      BadgeCountUpdater.update(dbHelper, notifiJob.context);
   }

   static @NonNull JSONObject bundleAsJSONObject(Bundle bundle) {
      JSONObject json = new JSONObject();
      Set<String> keys = bundle.keySet();

      for (String key : keys) {
         try {
            json.put(key, bundle.get(key));
         } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "bundleAsJSONObject error for key: " + key, e);
         }
      }

      return json;
   }

   // Format our short keys used for buttons into more readable ones.
   private static void unMinifyButtonsFromBundle(Bundle gcmBundle) {
      if (!gcmBundle.containsKey(PUSH_MINIFIED_BUTTONS_LIST))
         return;
      
      try {
         JSONObject customJSON = new JSONObject(gcmBundle.getString("custom"));
         JSONObject additionalDataJSON;

         if (customJSON.has(PUSH_ADDITIONAL_DATA_KEY))
            additionalDataJSON = customJSON.getJSONObject(PUSH_ADDITIONAL_DATA_KEY);
         else
            additionalDataJSON = new JSONObject();

         JSONArray buttons = new JSONArray(gcmBundle.getString(PUSH_MINIFIED_BUTTONS_LIST));
         gcmBundle.remove(PUSH_MINIFIED_BUTTONS_LIST);

         for (int i = 0; i < buttons.length(); i++) {
            JSONObject button = buttons.getJSONObject(i);

            String buttonText = button.getString(PUSH_MINIFIED_BUTTON_TEXT);
            button.remove(PUSH_MINIFIED_BUTTON_TEXT);

            String buttonId;
            if (button.has(PUSH_MINIFIED_BUTTON_ID)) {
               buttonId = button.getString(PUSH_MINIFIED_BUTTON_ID);
               button.remove(PUSH_MINIFIED_BUTTON_ID);
            } else
               buttonId = buttonText;

            button.put("id", buttonId);
            button.put("text", buttonText);

            if (button.has(PUSH_MINIFIED_BUTTON_ICON)) {
               button.put("icon", button.getString(PUSH_MINIFIED_BUTTON_ICON));
               button.remove(PUSH_MINIFIED_BUTTON_ICON);
            }
         }

         additionalDataJSON.put("actionButtons", buttons);
         additionalDataJSON.put(BUNDLE_KEY_ACTION_ID, DEFAULT_ACTION);
         if (!customJSON.has(PUSH_ADDITIONAL_DATA_KEY))
            customJSON.put(PUSH_ADDITIONAL_DATA_KEY, additionalDataJSON);

         gcmBundle.putString("custom", customJSON.toString());
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   static OSNotificationPayload OSNotificationPayloadFrom(JSONObject currentJsonPayload) {
      OSNotificationPayload notification = new OSNotificationPayload();
      try {
         JSONObject customJson = getCustomJSONObject(currentJsonPayload);
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
      } catch (JSONException t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error assigning OSNotificationPayload values!", t);
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

   private static void processCollapseKey(NotificationGenerationJob notifJob) {
      if (notifJob.restoring)
         return;
      if (!notifJob.jsonPayload.has("collapse_key") || "do_not_collapse".equals(notifJob.jsonPayload.optString("collapse_key")))
         return;
      String collapse_id = notifJob.jsonPayload.optString("collapse_key");

      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notifJob.context);
      Cursor cursor = dbHelper.query(
              NotificationTable.TABLE_NAME,
              new String[]{NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID}, // retColumn
              NotificationTable.COLUMN_NAME_COLLAPSE_ID + " = ? AND " +
                      NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                      NotificationTable.COLUMN_NAME_OPENED + " = 0 ",
              new String[]{collapse_id},
              null, null, null);

      if (cursor.moveToFirst()) {
         int androidNotificationId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
         notifJob.setAndroidIdWithOutOverriding(androidNotificationId);
      }

      cursor.close();
   }

   //  Process bundle passed from gcm / adm broadcast receiver.
   static @NonNull ProcessedBundleResult processBundleFromReceiver(Context context, final Bundle bundle) {
      ProcessedBundleResult result = new ProcessedBundleResult();
      
      // Not a OneSignal GCM message
      if (!OSNotificationFormatHelper.isOneSignalBundle(bundle))
         return result;
      result.isOneSignalPayload = true;

      unMinifyButtonsFromBundle(bundle);

      JSONObject pushPayloadJson = bundleAsJSONObject(bundle);

      // Show In-App message preview it is in the payload & the app is in focus
      String previewUUID = inAppPreviewPushUUID(pushPayloadJson);
      if (previewUUID != null) {
         // If app is in focus display the IAMs preview now
         if (OneSignal.isAppActive()) {
            result.inAppPreviewShown = true;
            OneSignal.getInAppMessageController().displayPreviewMessage(previewUUID);
         }
         // Return early, we don't want the extender service or etc. to fire for IAM previews
         return result;
      }

      if (startExtenderService(context, bundle, result))
         return result;

      // We already ran a getNotificationIdFromGCMBundle == null check above so this will only be true for dups
      result.isDup = OneSignal.notValidOrDuplicated(context, pushPayloadJson);
      if (result.isDup)
         return result;

      String alert = bundle.getString("alert");

      // Save as a opened notification to prevent duplicates.
      if (!shouldDisplay(alert)) {
         saveAndProcessNotification(context, bundle, true, -1);
         // Current thread is meant to be short lived.
         //    Make a new thread to do our OneSignal work on.
         new Thread(new Runnable() {
            public void run() {
               OneSignal.handleNotificationReceived(bundleAsJsonArray(bundle), false, false);
            }
         }, "OS_PROC_BUNDLE").start();
      }
      
      return result;
   }

   static @Nullable String inAppPreviewPushUUID(JSONObject payload) {
      JSONObject osCustom;
      try {
         osCustom = getCustomJSONObject(payload);
      } catch (JSONException e) {
         return null;
      }

      if (!osCustom.has(PUSH_ADDITIONAL_DATA_KEY))
         return null;

      JSONObject additionalData = osCustom.optJSONObject(PUSH_ADDITIONAL_DATA_KEY);
      if (additionalData.has(IAM_PREVIEW_KEY))
         return additionalData.optString(IAM_PREVIEW_KEY);
      return null;
   }

   // NotificationExtenderService still makes additional checks such as notValidOrDuplicated
   private static boolean startExtenderService(Context context, Bundle bundle, ProcessedBundleResult result) {
      Intent intent = NotificationExtenderService.getIntent(context);
      if (intent == null)
         return false;

      intent.putExtra("json_payload", bundleAsJSONObject(bundle).toString());
      intent.putExtra("timestamp", System.currentTimeMillis() / 1000L);

      boolean isHighPriority = Integer.parseInt(bundle.getString("pri", "0")) > 9;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
         NotificationExtenderService.enqueueWork(
            context,
            intent.getComponent(),
            EXTENDER_SERVICE_JOB_ID,
            intent,
            isHighPriority
         );
      else
         context.startService(intent);

      result.hasExtenderService = true;
      return true;
   }

   static boolean shouldDisplay(String body) {
      boolean hasBody = body != null && !"".equals(body);
      boolean showAsAlert = OneSignal.getInAppAlertNotificationEnabled();
      boolean isActive = OneSignal.isAppActive();
      return hasBody &&
                (OneSignal.getNotificationsWhenActiveEnabled()
              || showAsAlert
              || !isActive);
   }

   static @NonNull JSONArray newJsonArray(JSONObject jsonObject) {
      return new JSONArray().put(jsonObject);
   }

   static JSONObject getCustomJSONObject(JSONObject jsonObject) throws JSONException {
      return new JSONObject(jsonObject.optString("custom"));
   }
   
   static boolean hasRemoteResource(Bundle bundle) {
      return isBuildKeyRemote(bundle, "licon")
          || isBuildKeyRemote(bundle, "bicon")
          || bundle.getString("bg_img", null) != null;
   }
   
   private static boolean isBuildKeyRemote(Bundle bundle, String key) {
      String value = bundle.getString(key, "").trim();
      return value.startsWith("http://") || value.startsWith("https://");
   }
   
   static class ProcessedBundleResult {
      boolean isOneSignalPayload;
      boolean hasExtenderService;
      boolean isDup;
      boolean inAppPreviewShown;
      
      boolean processed() {
         return !isOneSignalPayload || hasExtenderService || isDup || inAppPreviewShown;
      }
   }
}