package com.onesignal;

import android.content.Context;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow class for OSNotificationWorkManager.NotificationWorker methods.
 * These method will always run inside a worker, but given that Roboelectric runs everything under MainThread
 * we need to shadow them and emulate the background thread.
 */
@Implements(OSNotificationWorkManager.NotificationWorker.class)
public class ShadowNotificationWorker {

    public static boolean callProcessingHandlerUnderThread = true;
    public static boolean completeWorkUnderThread = true;

    @Implementation
    public void callProcessingHandler(final Context context, final OSNotificationReceived notificationReceived) {
        if (callProcessingHandlerUnderThread)
            new Thread(new Runnable() {
                public void run() {
                    OneSignal.notificationProcessingHandler.notificationProcessing(context, notificationReceived);
                }
            }, "OS_NOTIFICATION_RECEIVED_COMPLETE").start();
        else
            OneSignal.notificationProcessingHandler.notificationProcessing(context, notificationReceived);
    }

    @Implementation
    public void completeWork(final OSNotificationReceived notificationReceived) {
        if (completeWorkUnderThread) {
            new Thread(new Runnable() {
                public void run() {
                    notificationReceived.complete();
                }
            }, "OS_NOTIFICATION_RECEIVED_COMPLETE").start();
        } else {
            notificationReceived.complete();
        }
    }

    public static void resetStatics() {
        callProcessingHandlerUnderThread = true;
        completeWorkUnderThread = true;
    }
}
