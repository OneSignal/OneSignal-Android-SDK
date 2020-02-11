package com.onesignal.sdktest.notification;

import com.onesignal.NotificationGenerationJob;
import com.onesignal.OSInAppMessageAction;
import com.onesignal.OSNotificationDisplayedResult;
import com.onesignal.OSNotificationExtensionService;
import com.onesignal.OSNotificationIntentService;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSNotificationReceivedResult;
import com.onesignal.OneSignal;

public class AppNotificationExtensionService implements
        OSNotificationExtensionService.NotificationProcessingHandler,
        OSNotificationExtensionService.NotificationWillShowInForegroundHandler,
        OSNotificationExtensionService.NotificationOpenedHandler,
        OneSignal.InAppMessageClickHandler {

    public AppNotificationExtensionService() {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "AppNotificationExtensionService constructor called!!!");
        OSNotificationExtensionService.setNotificationProcessingHandler(this);

        // Example using public OneSignal handler setters
        OneSignal.setNotificationWillShowInForegroundHandler(this);
        OneSignal.setNotificationOpenedHandler(this);
        OneSignal.setInAppMessageClickHandler(this);
    }

    @Override
    public boolean onNotificationProcessing(OSNotificationReceivedResult notification) {
      if (notification.payload.actionButtons != null) {
         for (OSNotificationPayload.ActionButton button : notification.payload.actionButtons) {
            System.out.println("button:" + button.toString());
         }
      }

      OSNotificationIntentService.OverrideSettings overrideSettings = new OSNotificationIntentService.OverrideSettings();
//      overrideSettings.extender = new NotificationCompat.Extender() {
//         @Override
//         public NotificationCompat.Builder extend(NotificationCompat.Builder builder) {
//            return builder.setColor(getResources().getColor(R.color.colorPrimary));
//         }
//      };

      OSNotificationDisplayedResult displayedResult = notification.modifyNotification(overrideSettings);
      if (displayedResult != null)
          OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification displayed with id: " + displayedResult.androidNotificationId);

      return true;
   }

    @Override
    public void notificationWillShowInForeground(NotificationGenerationJob notifJob) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification received!!!");

        // This must be called in order to specify the type of OSInFocusDisplay for the
        //      notification.
        //  Otherwise, the SDK will resort to the 30 second timeout and show the notification in the shade
        notifJob.showNotification(OneSignal.OSInFocusDisplay.NOTIFICATION);
    }

    @Override
    public void notificationOpened(OSNotificationOpenResult result) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification opened!!!");

    }

    @Override
    public void inAppMessageClicked(OSInAppMessageAction result) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification opened!!!");
    }
}