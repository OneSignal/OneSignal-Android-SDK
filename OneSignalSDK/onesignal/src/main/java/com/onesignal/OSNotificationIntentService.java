package com.onesignal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class OSNotificationIntentService extends JobIntentService {

    private static OSNotificationIntentService service = null;
    static OSNotificationIntentService getInstance() {
        if (service == null)
            service = new OSNotificationIntentService();
        return service;
    }

    private JSONObject currentJsonPayload;
    private boolean currentlyRestoring;
    private Long restoreTimestamp;

    OSNotificationDisplayedResult osNotificationDisplayedResult;

    OSNotificationIntentService.OverrideSettings currentBaseOverrideSettings = null;

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
        OSNotificationExtensionService.wasShowNotificationCalled = false;

        if (intent == null)
            return;

        processIntent(intent);
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void processIntent(Intent intent) {
        Bundle bundle = intent.getExtras();

        // Service maybe triggered without extras on some Android devices on boot.
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
            // Make sure a notificationProcessingHandler was created by extending OSNotificationExtensionService
            if (OSNotificationExtensionService.notificationProcessingHandler != null)
                developerProcessed = OSNotificationExtensionService.notificationProcessingHandler.onNotificationProcessing(receivedResult);

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

    static Intent getIntent(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent().setAction("com.onesignal.NotificationExtension").setPackage(context.getPackageName());
        List<ResolveInfo> resolveInfo = packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA);
        if (resolveInfo.size() < 1)
            return null;

        intent.setComponent(new ComponentName(context, resolveInfo.get(0).serviceInfo.name));

        return intent;
    }

}
