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
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.onesignal.OneSignalDbContract.NotificationTable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Set;

import static com.onesignal.GenerateNotification.BUNDLE_KEY_ACTION_ID;

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
    static final String DEFAULT_ACTION = "__DEFAULT__";

    static final String OS_NOTIFICATION_PROCESSING_THREAD = "OS_NOTIFICATION_PROCESSING_THREAD";

    static void processFromFCMIntentService(Context context, BundleCompat bundle, OSNotificationExtender.OverrideSettings overrideSettings) {
        OneSignal.initWithContext(context);
        try {
            String jsonStrPayload = bundle.getString("json_payload");
            if (jsonStrPayload == null) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "json_payload key is nonexistent from mBundle passed to ProcessFromFCMIntentService: " + bundle);
                return;
            }

            JSONObject jsonPayload = new JSONObject(jsonStrPayload);
            boolean isRestoring = bundle.getBoolean("is_restoring", false);
            long shownTimeStamp = bundle.getLong("timestamp");
            boolean isIamPreview = inAppPreviewPushUUID(jsonPayload) != null;

            int androidNotificationId = 0;
            if (bundle.containsKey("android_notif_id"))
                androidNotificationId = bundle.getInt("android_notif_id");

            if (!isRestoring
                    && !isIamPreview
                    && OneSignal.notValidOrDuplicated(context, jsonPayload))
                return;

            String osNotificationId = OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload);
            OSNotificationWorkManager.beginEnqueueingWork(
                    context,
                    osNotificationId,
                    androidNotificationId,
                    jsonStrPayload,
                    isRestoring,
                    shownTimeStamp,
                    false);

            // Delay to prevent CPU spikes.
            //    Normally more than one notification is restored at a time.
            if (isRestoring)
                OSUtils.sleep(100);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Recommended method to process notification before displaying
     * Only use the {@link NotificationBundleProcessor#processJobForDisplay(OSNotificationGenerationJob, boolean, boolean)}
     *  in the event where you want to mark a notification as opened or displayed different than the defaults
     */
    @WorkerThread
    static int processJobForDisplay(OSNotificationGenerationJob notificationJob) {
        return processJobForDisplay(notificationJob, false, true);
    }

    @WorkerThread
    static int processJobForDisplay(OSNotificationGenerationJob notificationJob, boolean opened, boolean displayed) {
        processCollapseKey(notificationJob);

        int androidNotifId = notificationJob.getAndroidIdWithoutCreate();
        boolean doDisplay = shouldDisplayNotif(notificationJob);
        if (doDisplay) {
            androidNotifId = notificationJob.getAndroidId();
            if (OneSignal.shouldFireForegroundHandlers())
                OneSignal.fireForegroundHandlers(notificationJob);
            else
                GenerateNotification.fromJsonPayload(notificationJob);
        }

        if (!notificationJob.isRestoring && !notificationJob.isIamPreview) {
            processNotification(notificationJob, opened);
            OneSignal.handleNotificationReceived(notificationJob, displayed);
        }

        return androidNotifId;
    }

    private static boolean shouldDisplayNotif(OSNotificationGenerationJob notificationJob) {
        // Validate that the current Android device is Android 4.4 or higher and the current job is a
        //    preview push
        if (notificationJob.isIamPreview && Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2)
            return false;

        // Otherwise, this is a normal notification and should be shown
        return notificationJob.hasExtender() || shouldDisplay(notificationJob.jsonPayload.optString("alert"));
    }

    private static void saveAndProcessDupNotification(Context context, Bundle bundle) {
        OSNotificationGenerationJob notificationJob = new OSNotificationGenerationJob(context);
        notificationJob.jsonPayload = bundleAsJSONObject(bundle);
        notificationJob.overrideSettings = new OSNotificationExtender.OverrideSettings();
        notificationJob.overrideSettings.androidNotificationId = -1;

        processNotification(notificationJob, true);
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
        Context context = notificationJob.context;
        JSONObject jsonPayload = notificationJob.jsonPayload;

        try {
            JSONObject customJSON = getCustomJSONObject(notificationJob.jsonPayload);

            OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notificationJob.context);
            SQLiteDatabase writableDb = null;

            try {
                writableDb = dbHelper.getSQLiteDatabaseWithRetries();

                writableDb.beginTransaction();

                // Count any notifications with duplicated android notification ids as dismissed.
                // -1 is used to note never displayed
                if (notificationJob.isNotificationToDisplay()) {
                    String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notificationJob.getAndroidIdWithoutCreate();

                    ContentValues values = new ContentValues();
                    values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

                    writableDb.update(NotificationTable.TABLE_NAME, values, whereStr, null);
                    BadgeCountUpdater.update(writableDb, context);
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

                writableDb.insertOrThrow(NotificationTable.TABLE_NAME, null, values);

                if (!opened)
                    BadgeCountUpdater.update(writableDb, context);
                writableDb.setTransactionSuccessful();
            } catch (Exception e) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error saving notification record! ", e);
            } finally {
                if (writableDb != null) {
                    try {
                        writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                    } catch (Throwable t) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    static void markRestoredNotificationAsDismissed(OSNotificationGenerationJob notifiJob) {
        if (notifiJob.getAndroidIdWithoutCreate() == -1)
            return;

        String whereStr = NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notifiJob.getAndroidIdWithoutCreate();

        OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notifiJob.context);
        SQLiteDatabase writableDb = null;

        try {
            writableDb = dbHelper.getSQLiteDatabaseWithRetries();
            writableDb.beginTransaction();

            ContentValues values = new ContentValues();
            values.put(NotificationTable.COLUMN_NAME_DISMISSED, 1);

            writableDb.update(NotificationTable.TABLE_NAME, values, whereStr, null);
            BadgeCountUpdater.update(writableDb, notifiJob.context);

            writableDb.setTransactionSuccessful();

        } catch (Exception e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error saving notification record! ", e);
        } finally {
            if (writableDb != null) {
                try {
                    writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                } catch (Throwable t) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
                }
            }
        }
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
        } catch (Throwable t) {
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

    private static void processCollapseKey(OSNotificationGenerationJob notificationJob) {
        if (notificationJob.isRestoring)
            return;
        if (!notificationJob.jsonPayload.has("collapse_key") || "do_not_collapse".equals(notificationJob.jsonPayload.optString("collapse_key")))
            return;
        String collapse_id = notificationJob.jsonPayload.optString("collapse_key");


        OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(notificationJob.context);
        Cursor cursor = null;

        try {
            SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
            cursor = readableDb.query(
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
        } catch (Throwable t) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not read DB to find existing collapse_key!", t);
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }
    }

    //  Process bundle passed from fcm / adm broadcast receiver.
    static @NonNull
    ProcessedBundleResult processBundleFromReceiver(Context context, final Bundle bundle) {
        ProcessedBundleResult result = new ProcessedBundleResult();

        // Not a OneSignal GCM message
        if (!OSNotificationFormatHelper.isOneSignalBundle(bundle))
            return result;
        result.isOneSignalPayload = true;

        maximizeButtonsFromBundle(bundle);

        JSONObject pushPayloadJson = bundleAsJSONObject(bundle);

        // Show In-App message preview it is in the payload & the app is in focus
        String previewUUID = inAppPreviewPushUUID(pushPayloadJson);
        if (previewUUID != null) {
            // If app is in focus display the IAMs preview now
            if (OneSignal.isAppActive()) {
                result.inAppPreviewShown = true;
                OSInAppMessageController.getController().displayPreviewMessage(previewUUID);
            }
            // Return early, we don't want the extender service or etc. to fire for IAM previews
            return result;
        }

        if (startNotificationProcessing(context, bundle, result))
            return result;

        // We already ran a getNotificationIdFromFCMBundle == null check above so this will only be true for dups
        result.isDup = OneSignal.notValidOrDuplicated(context, pushPayloadJson);
        if (result.isDup)
            return result;

        // Create new notificationJob to be processed for display
        final OSNotificationGenerationJob notificationJob = new OSNotificationGenerationJob(context);
        notificationJob.jsonPayload = bundleAsJSONObject(bundle);
        notificationJob.overrideSettings = new OSNotificationExtender.OverrideSettings();

        // Save as a opened notification to prevent duplicates
        String alert = bundle.getString("alert");
        if (!shouldDisplay(alert)) {
            saveAndProcessDupNotification(context, bundle);
            // Current thread is meant to be short lived
            // Make a new thread to do our OneSignal work on
            new Thread(new Runnable() {
                public void run() {
                    OneSignal.handleNotificationReceived(notificationJob, false);
                }
            }, OS_NOTIFICATION_PROCESSING_THREAD).start();
        }

        return result;
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

    // NotificationExtenderService still makes additional checks such as notValidOrDuplicated
    private static boolean startNotificationProcessing(Context context, Bundle bundle, ProcessedBundleResult result) {
        // Service maybe triggered without extras on some Android devices on boot.
        // https://github.com/OneSignal/OneSignal-Android-SDK/issues/99
        if (bundle == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Notification bundle is null, not passing the notification to the work manager");
            return false;
        }

        JSONObject jsonPayload = bundleAsJSONObject(bundle);
        boolean isRestoring = bundle.getBoolean("is_restoring", false);
        long timestamp = OneSignal.getTime().getCurrentTimeMillis() / 1000L;
        boolean isHighPriority = Integer.parseInt(bundle.getString("pri", "0")) > 9;

        int androidNotificationId = 0;
        if (bundle.containsKey("android_notif_id"))
            androidNotificationId = bundle.getInt("android_notif_id");

        if (!isRestoring
                && OneSignal.notValidOrDuplicated(context, jsonPayload))
            return false;

        String osNotificationId = OSNotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload);
        OSNotificationWorkManager.beginEnqueueingWork(
                context,
                osNotificationId,
                androidNotificationId,
                jsonPayload.toString(),
                isRestoring,
                timestamp,
                isHighPriority);

        result.isWorkManagerProcessing = true;
        return true;
    }

    static boolean shouldDisplay(String body) {
        return !TextUtils.isEmpty(body);
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
}