/**
 * Modified MIT License
 * <p>
 * Copyright 2019 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
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
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.onesignal.OneSignalDbContract.NotificationTable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.OSUtils.isStringNotEmpty;

/** Processes the Bundle received from a push.
 * This class handles both processing bundles from a BroadcastReceiver or from a Service
 *   - Entry points are processBundleFromReceiver or ProcessFromFCMIntentService respectively
 * NOTE: Could split up this class since it does a number of different things
 * */
class NotificationBundleProcessor {

    public static final String PUSH_ADDITIONAL_DATA_KEY = "a";

    public static final String PUSH_MINIFIED_BUTTONS_LIST = "o";
    public static final String PUSH_MINIFIED_BUTTON_ID = "i";
    public static final String PUSH_MINIFIED_BUTTON_TEXT = "n";
    public static final String PUSH_MINIFIED_BUTTON_ICON = "p";

    private static final String IAM_PREVIEW_KEY = "os_in_app_message_preview_id";
    private static final String ANDROID_NOTIFICATION_ID = "android_notif_id";
    static final String DEFAULT_ACTION = "__DEFAULT__";

    static void processFromFCMIntentService(final Context context, BundleCompat bundle) {
        OneSignal.initWithContext(context);
        try {
            final String jsonStrPayload = bundle.getString("json_payload");
            if (jsonStrPayload == null) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "json_payload key is nonexistent from mBundle passed to ProcessFromFCMIntentService: " + bundle);
                return;
            }

            final JSONObject jsonPayload = new JSONObject(jsonStrPayload);
            final boolean isRestoring = bundle.getBoolean("is_restoring", false);
            final long shownTimeStamp = bundle.getLong("timestamp");
            final boolean isIamPreview = inAppPreviewPushUUID(jsonPayload) != null;

            int androidNotificationId = 0;
            if (bundle.containsKey(ANDROID_NOTIFICATION_ID))
                androidNotificationId = bundle.getInt(ANDROID_NOTIFICATION_ID);

            final int finalAndroidNotificationId = androidNotificationId;
            OSNotificationDataController.InvalidOrDuplicateNotificationCallback callback = new OSNotificationDataController.InvalidOrDuplicateNotificationCallback() {
                @Override
                public void onResult(boolean result) {
                    if (!isRestoring
                            && !isIamPreview
                            && result)
                        return;

                    String osNotificationId = OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload);
                    OSNotificationWorkManager.beginEnqueueingWork(
                            context,
                            osNotificationId,
                            finalAndroidNotificationId,
                            jsonStrPayload,
                            isRestoring,
                            shownTimeStamp,
                            false);

                    // Delay to prevent CPU spikes.
                    // Normally more than one notification is restored at a time.
                    if (isRestoring)
                        OSUtils.sleep(100);
                }
            };

            OneSignal.notValidOrDuplicated(jsonPayload, callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Recommended method to process notification before displaying
     * Only use the {@link NotificationBundleProcessor#processJobForDisplay(OSNotificationGenerationJob, boolean)}
     *  in the event where you want to mark a notification as opened or displayed different than the defaults
     */
    @WorkerThread
    static int processJobForDisplay(OSNotificationGenerationJob notificationJob, boolean fromBackgroundLogic) {
        OSNotificationController notificationController = new OSNotificationController(notificationJob, notificationJob.isRestoring(), true);
        return processJobForDisplay(notificationController, false, fromBackgroundLogic);
    }

    @WorkerThread
    static int processJobForDisplay(OSNotificationController notificationController, boolean fromBackgroundLogic) {
        return processJobForDisplay(notificationController, false, fromBackgroundLogic);
    }

    @WorkerThread
    static int processJobForDisplay(OSNotificationController notificationController, boolean opened, boolean fromBackgroundLogic) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Starting processJobForDisplay opened: " + opened + " fromBackgroundLogic: " + fromBackgroundLogic);
        OSNotificationGenerationJob notificationJob = notificationController.getNotificationJob();

        processCollapseKey(notificationJob);

        int androidNotificationId = notificationJob.getAndroidIdWithoutCreate();
        boolean doDisplay = shouldDisplayNotif(notificationJob);
        if (doDisplay) {
            androidNotificationId = notificationJob.getAndroidId();
            if (fromBackgroundLogic && OneSignal.shouldFireForegroundHandlers()) {
                notificationController.setFromBackgroundLogic(false);
                OneSignal.fireForegroundHandlers(notificationController);
                // Notification will be processed by foreground user complete or timer complete
                return androidNotificationId;
            } else {
                GenerateNotification.fromJsonPayload(notificationJob);
            }
        }

        if (!notificationJob.isRestoring() && !notificationJob.isIamPreview()) {
            processNotification(notificationJob, opened);

            // No need to keep notification duplicate check on memory, we have database check at this point
            // Without removing duplicate, summary restoration might not happen
            String osNotificationId = OSNotificationFormatHelper.getOSNotificationIdFromJson(notificationController.getNotificationJob().getJsonPayload());
            OSNotificationWorkManager.removeNotificationIdProcessed(osNotificationId);
            OneSignal.handleNotificationReceived(notificationJob);
        }

        return androidNotificationId;
    }

    private static boolean shouldDisplayNotif(OSNotificationGenerationJob notificationJob) {
        // Validate that the current Android device is Android 4.4 or higher and the current job is a
        //    preview push
        if (notificationJob.isIamPreview() && Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2)
            return false;

        // Otherwise, this is a normal notification and should be shown
        return notificationJob.hasExtender() || isStringNotEmpty(notificationJob.getJsonPayload().optString("alert"));
    }

    /**
     * Save notification, updates Outcomes, and sends Received Receipt if they are enabled.
     */
    static void processNotification(OSNotificationGenerationJob notificationJob, boolean opened) {
        saveNotification(notificationJob, opened);

        if (!notificationJob.isNotificationToDisplay())
            return;

        String notificationId = notificationJob.getApiNotificationId();
        OneSignal.getSessionManager().onNotificationReceived(notificationId);
        OSReceiveReceiptController.getInstance().sendReceiveReceipt(notificationId);
    }

   // Saving the notification provides the following:
   //   * Prevent duplicates
   //   * Build summary notifications
   //   * Collapse key / id support - Used to lookup the android notification id later
   //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
   //   * Future - Public API to get a list of notifications
   private static void saveNotification(OSNotificationGenerationJob notificationJob, boolean opened) {
      OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Saving Notification job: " + notificationJob.toString());
      Context context = notificationJob.getContext();
      JSONObject jsonPayload = notificationJob.getJsonPayload();

      try {
         JSONObject customJSON = getCustomJSONObject(notificationJob.getJsonPayload());

         OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notificationJob.getContext());

         // Count any notifications with duplicated android notification ids as dismissed.
         // -1 is used to note never displayed
         if (notificationJob.isNotificationToDisplay()) {
            String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notificationJob.getAndroidIdWithoutCreate();

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
            values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, notificationJob.getAndroidIdWithoutCreate());

         if (notificationJob.getTitle() != null)
            values.put(NotificationTable.COLUMN_NAME_TITLE, notificationJob.getTitle().toString());
         if (notificationJob.getBody() != null)
            values.put(NotificationTable.COLUMN_NAME_MESSAGE, notificationJob.getBody().toString());

         // Set expire_time
         long sentTime = jsonPayload.optLong("google.sent_time", OneSignal.getTime().getCurrentThreadTimeMillis()) / 1_000L;
         int ttl = jsonPayload.optInt("google.ttl", OSNotificationRestoreWorkManager.DEFAULT_TTL_IF_NOT_IN_PAYLOAD);
         long expireTime = sentTime + ttl;
         values.put(NotificationTable.COLUMN_NAME_EXPIRE_TIME, expireTime);

         values.put(NotificationTable.COLUMN_NAME_FULL_DATA, jsonPayload.toString());

         dbHelper.insertOrThrow(NotificationTable.TABLE_NAME, null, values);
         OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Notification saved values: " + values.toString());
         if (!opened)
            BadgeCountUpdater.update(dbHelper, context);
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

    static void markRestoredNotificationAsDismissed(OSNotificationGenerationJob notifiJob) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Marking restored notifications as dismissed: " + notifiJob.toString());
        if (notifiJob.getAndroidIdWithoutCreate() == -1)
            return;

        String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notifiJob.getAndroidIdWithoutCreate();

        OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notifiJob.getContext());

        ContentValues values = new ContentValues();
        values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

        dbHelper.update(NotificationTable.TABLE_NAME, values, whereStr, null);
        BadgeCountUpdater.update(dbHelper, notifiJob.getContext());
   }

    static @NonNull
    JSONObject bundleAsJSONObject(Bundle bundle) {
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

    // Format our short keys into more readable ones.
    private static void maximizeButtonsFromBundle(Bundle fcmBundle) {
        if (!fcmBundle.containsKey("o"))
            return;

        try {
            JSONObject customJSON = new JSONObject(fcmBundle.getString("custom"));
            JSONObject additionalDataJSON;

            if (customJSON.has(PUSH_ADDITIONAL_DATA_KEY))
                additionalDataJSON = customJSON.getJSONObject(PUSH_ADDITIONAL_DATA_KEY);
            else
                additionalDataJSON = new JSONObject();

            JSONArray buttons = new JSONArray(fcmBundle.getString(PUSH_MINIFIED_BUTTONS_LIST));
            fcmBundle.remove(PUSH_MINIFIED_BUTTONS_LIST);
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

            fcmBundle.putString("custom", customJSON.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void processCollapseKey(OSNotificationGenerationJob notificationJob) {
        if (notificationJob.isRestoring())
            return;
        if (!notificationJob.getJsonPayload().has("collapse_key") || "do_not_collapse".equals(notificationJob.getJsonPayload().optString("collapse_key")))
            return;
        String collapse_id = notificationJob.getJsonPayload().optString("collapse_key");

      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notificationJob.getContext());
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
         notificationJob.setAndroidIdWithoutOverriding(androidNotificationId);
      }

      cursor.close();
   }

    //  Process bundle passed from fcm / adm broadcast receiver.
    static void processBundleFromReceiver(Context context, final Bundle bundle, final ProcessBundleReceiverCallback bundleReceiverCallback) {
        final ProcessedBundleResult bundleResult = new ProcessedBundleResult();

        // Not a OneSignal GCM message
        if (!OSNotificationFormatHelper.isOneSignalBundle(bundle)) {
            bundleReceiverCallback.onBundleProcessed(bundleResult);
            return;
        }

        bundleResult.isOneSignalPayload = true;
        maximizeButtonsFromBundle(bundle);

        if (inAppMessagePreviewHandled(bundleResult, bundle)) {
            // Return early, we don't want the extender service or etc. to fire for IAM previews
            bundleReceiverCallback.onBundleProcessed(bundleResult);
            return;
        }

        NotificationProcessingCallback processingCallback = new NotificationProcessingCallback() {
            @Override
            public void onResult(boolean notificationProcessed) {
                // Bundle already non null, checked under isOneSignalBundle
                if (!notificationProcessed) {
                    // We already check for bundle == null under isOneSignalBundle
                    // At this point we know notification is duplicate
                    bundleResult.isDup = true;
                }
                bundleReceiverCallback.onBundleProcessed(bundleResult);
            }
        };

        startNotificationProcessing(context, bundle, bundleResult, processingCallback);
    }

    private static boolean inAppMessagePreviewHandled(ProcessedBundleResult bundleResult, Bundle bundle) {
        JSONObject pushPayloadJson = bundleAsJSONObject(bundle);
        // Show In-App message preview it is in the payload & the app is in focus
        String previewUUID = inAppPreviewPushUUID(pushPayloadJson);
        if (previewUUID != null) {
            // If app is in focus display the IAMs preview now
            if (OneSignal.isAppActive()) {
                bundleResult.inAppPreviewShown = true;
                OneSignal.getInAppMessageController().displayPreviewMessage(previewUUID);
            }
            return true;
        }

        return false;
    }

    static @Nullable
    String inAppPreviewPushUUID(JSONObject payload) {
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

    private static void startNotificationProcessing(final Context context,
                                                    final Bundle bundle,
                                                    final ProcessedBundleResult bundleResult,
                                                    final NotificationProcessingCallback notificationProcessingCallback) {
        final JSONObject jsonPayload = bundleAsJSONObject(bundle);
        final long timestamp = OneSignal.getTime().getCurrentTimeMillis() / 1000L;
        final boolean isRestoring = bundle.getBoolean("is_restoring", false);
        final boolean isHighPriority = Integer.parseInt(bundle.getString("pri", "0")) > 9;

        OSNotificationDataController.InvalidOrDuplicateNotificationCallback callback = new OSNotificationDataController.InvalidOrDuplicateNotificationCallback() {

            @Override
            public void onResult(boolean result) {
                if (!isRestoring
                        && result) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "startNotificationProcessing returning, with context: " + context + " and bundle: " + bundle);
                    notificationProcessingCallback.onResult(false);
                    return;
                }

                String osNotificationId = OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload);
                int androidNotificationId = 0;
                if (bundle.containsKey(ANDROID_NOTIFICATION_ID))
                    androidNotificationId = bundle.getInt(ANDROID_NOTIFICATION_ID);

                OSNotificationWorkManager.beginEnqueueingWork(
                        context,
                        osNotificationId,
                        androidNotificationId,
                        jsonPayload.toString(),
                        isRestoring,
                        timestamp,
                        isHighPriority);

                bundleResult.isWorkManagerProcessing = true;
                notificationProcessingCallback.onResult(true);
            }
        };

        OneSignal.notValidOrDuplicated(jsonPayload, callback);
    }

    static @NonNull
    JSONArray newJsonArray(JSONObject jsonObject) {
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
        boolean isDup;
        boolean inAppPreviewShown;
        boolean isWorkManagerProcessing;

        boolean processed() {
            return !isOneSignalPayload || isDup || inAppPreviewShown || isWorkManagerProcessing;
        }
    }

    interface ProcessBundleReceiverCallback {

        /**
         * @param processedResult the processed bundle result
         */
        void onBundleProcessed(@Nullable ProcessedBundleResult processedResult);
    }

    interface NotificationProcessingCallback {

        /**
         * @param notificationProcessed is true if notification was processed, otherwise false
         */
        void onResult(boolean notificationProcessed);
    }
}