package com.onesignal.sdktest.notification;

import androidx.core.app.NotificationCompat;

import com.onesignal.NotificationGenerationJob;
import com.onesignal.OSInAppMessageAction;
import com.onesignal.OSNotificationDisplayedResult;
import com.onesignal.OSNotificationIntentService;
import com.onesignal.OSNotificationOpenedResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignal;

import java.math.BigInteger;

public class AppNotificationExtensionService implements
        OneSignal.NotificationProcessingHandler,
        OneSignal.NotificationWillShowInForegroundHandler,
        OneSignal.NotificationOpenedHandler,
        OneSignal.InAppMessageClickHandler {

    @Override
    public boolean onNotificationProcessing(OSNotificationReceivedResult notification) {
        if (notification.payload.actionButtons != null) {
            for (OSNotificationPayload.ActionButton button : notification.payload.actionButtons) {
                System.out.println("button:" + button.toString());
            }
        }

        OSNotificationIntentService.OverrideSettings overrideSettings = new OSNotificationIntentService.OverrideSettings();
        overrideSettings.extender = new NotificationCompat.Extender() {
            @Override
            public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
                return builder.setColor(new BigInteger("00FF0000", 16).intValue());
            }
        };

        OSNotificationDisplayedResult displayedResult = notification.modifyNotification(overrideSettings);
        if (displayedResult != null)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification displayed with id: " + displayedResult.androidNotificationId);

        return true;
    }

    @Override
    public void notificationWillShowInForeground(NotificationGenerationJob notifJob) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Ext notification received!!!");

        // This must be called in order to specify the type of OSNotificationDisplayOption for the
        //      notification.
        //  Otherwise, the SDK will resort to the 30 second timeout and show the notification in the shade
        notifJob.setNotificationDisplayType(OneSignal.OSNotificationDisplayOption.NOTIFICATION);
        notifJob.complete(true);
    }

    @Override
    public void notificationOpened(OSNotificationOpenedResult result) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification opened!!!");

    }

    @Override
    public void inAppMessageClicked(OSInAppMessageAction result) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "In app message clicked!!!");
    }
}