package com.onesignal;

import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class OSNotificationIntentService extends JobIntentService {

    private Long restoreTimestamp;
    private JSONObject currentJsonPayload;
    private boolean currentlyRestoring;
    private OverrideSettings currentBaseOverrideSettings = null;
    private OSNotificationDisplayedResult osNotificationDisplayedResult;

    static OneSignal.NotificationProcessingHandler notificationProcessingHandler;

    public static class OverrideSettings {

        public NotificationCompat.Extender extender;
        public Integer androidNotificationId;

        // Note: Make sure future fields are nullable.
        // Possible future options
        //    int badgeCount;
        //   NotificationCompat.Extender summaryExtender;
        void override(OverrideSettings overrideSettings) {
            if (overrideSettings == null)
                return;

            if (overrideSettings.androidNotificationId != null)
                androidNotificationId = overrideSettings.androidNotificationId;
        }
    }

    /**
     *
     * @param intent The intent describing the work to now be processed.
     */
    @Override
    protected final void onHandleWork(Intent intent) {
        if (intent == null)
            return;

        processIntent(intent);
        FCMBroadcastReceiver.completeWakefulIntent(intent);
    }

    void setNotificationProcessingHandler(OneSignal.NotificationProcessingHandler handler) {
        notificationProcessingHandler = handler;
    }

    /**
     * Developer may call to override some notification settings.
     * If this method is called the SDK will omit it's notification regardless of what is
     *    returned from onNotificationProcessing.
     */
    protected final OSNotificationDisplayedResult modifyNotification(OSNotificationIntentService.OverrideSettings overrideSettings) {
        // Check if this method has been called already or if no override was set.
        if (osNotificationDisplayedResult != null || overrideSettings == null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "modifyNotification called with null overrideSettings");
            return null;
        }

        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "modifyNotification called with overrideSettings: " + overrideSettings.toString());

        overrideSettings.override(currentBaseOverrideSettings);
        osNotificationDisplayedResult = new OSNotificationDisplayedResult();

        NotificationGenerationJob notifJob = createNotifJobFromCurrent();
        notifJob.overrideSettings = overrideSettings;

        osNotificationDisplayedResult.androidNotificationId = NotificationBundleProcessor.ProcessJobForDisplay(notifJob);
        return osNotificationDisplayedResult;
    }

    private void processIntent(Intent intent) {
        Bundle bundle = intent.getExtras();

        // Service may be triggered without extras on some Android devices on boot.
        // https://github.com/OneSignal/OneSignal-Android-SDK/issues/99
        if (bundle == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No extras sent to OSNotificationExtensionService in its Intent!\n" + intent);
            return;
        }

        String jsonStrPayload = bundle.getString("json_payload");
        if (jsonStrPayload == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "json_payload key is nonexistent from bundle passed to OSNotificationExtensionService: " + bundle);
            return;
        }

        try {
            currentJsonPayload = new JSONObject(jsonStrPayload);
            currentlyRestoring = bundle.getBoolean("restoring", false);
            if (bundle.containsKey("android_notif_id")) {
                currentBaseOverrideSettings = new OSNotificationIntentService.OverrideSettings();
                currentBaseOverrideSettings.androidNotificationId = bundle.getInt("android_notif_id");
            }

            if (!currentlyRestoring && OneSignal.notValidOrDuplicated(this, currentJsonPayload))
                return;

            restoreTimestamp = bundle.getLong("timestamp");
            processJsonObject(currentJsonPayload, currentlyRestoring);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void processJsonObject(JSONObject currentJsonPayload, boolean restoring) {
        OSNotificationReceivedResult receivedResult = new OSNotificationReceivedResult();
        receivedResult.payload = NotificationBundleProcessor.OSNotificationPayloadFrom(currentJsonPayload);
        receivedResult.restoring = restoring;
        receivedResult.isAppInFocus = OneSignal.isAppActive();

        osNotificationDisplayedResult = null;
        boolean developerProcessed = false;
        try {
            // Make sure a notificationProcessingHandler was created by implementing NotificationProcessingHandler
            //  inside of your own extension service class
            if (notificationProcessingHandler != null)
                developerProcessed = notificationProcessingHandler.onNotificationProcessing(receivedResult);

        } catch (Throwable t) {
            //noinspection ConstantConditions - modifyNotification might have been called by the developer
            if (osNotificationDisplayedResult == null)
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Displaying normal OneSignal notification.", t);
            else
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Extended notification displayed but custom processing did not finish.", t);
        }

        // If the developer did not call modifyNotification from onNotificationProcessing
        if (osNotificationDisplayedResult == null) {
            // Save as processed to prevent possible duplicate calls from canonical ids.

            boolean display = !developerProcessed &&
                    NotificationBundleProcessor.shouldDisplay(currentJsonPayload.optString("alert"));

            if (!display) {
                if (!restoring) {
                    NotificationGenerationJob notifJob = new NotificationGenerationJob(this);
                    notifJob.jsonPayload = currentJsonPayload;
                    notifJob.overrideSettings = new OSNotificationIntentService.OverrideSettings();
                    notifJob.overrideSettings.androidNotificationId = -1;

                    NotificationBundleProcessor.processNotification(notifJob, true);
                    OneSignal.handleNotificationReceived(notifJob, false);
                }
                // If are are not displaying a restored notification make sure we mark it as dismissed
                //   This will prevent it from being restored again.
                else if (currentBaseOverrideSettings != null)
                    NotificationBundleProcessor.markRestoredNotificationAsDismissed(createNotifJobFromCurrent());
            }
            else
                NotificationBundleProcessor.ProcessJobForDisplay(createNotifJobFromCurrent());

            // Delay to prevent CPU spikes.
            //    Normally more than one notification is restored at a time.
            if (restoring)
                OSUtils.sleep(100);
        }
    }

    NotificationGenerationJob createNotifJobFromCurrent() {
        NotificationGenerationJob notifJob = new NotificationGenerationJob(this);
        notifJob.restoring = currentlyRestoring;
        notifJob.jsonPayload = currentJsonPayload;
        notifJob.shownTimeStamp = restoreTimestamp;
        notifJob.overrideSettings = currentBaseOverrideSettings;

        return notifJob;
    }

}
