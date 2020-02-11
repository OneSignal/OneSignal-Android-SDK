/**
 * Modified MIT License
 * <p>
 * Copyright 2016 OneSignal
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

import android.os.Handler;

/**
 * Class designed to be extended by the developer
 *    The developer can then choose to implement the notification interfaces of their own choice
 *    This includes:
 *       1. OSNotificationExtensionService.NotificationProcessingHandler
 *       2. OSNotificationExtensionService.NotificationWillShowInForegroundHandler
 *       3. OSNotificationExtensionService.NotificationOpenedHandler
 *
 *
 * After creating a NotificationExtenderServiceExample that extends the OSNotificationExtensionService,
 *    they must add the following lines of code to their application as a service in their AndroidManifest.xml
 *
 *       <service
 *          android:name=".NotificationExtensionServiceExample"
 *          android:exported="false">
 *          <intent-filter>
 *             <action android:name="com.onesignal.NotificationExtension" />
 *          </intent-filter>
 *        </service>
 */
public abstract class OSNotificationExtensionService {

    static final int EXTENDER_SERVICE_JOB_ID = 2071862121;

    // Used as the postDelayed timer for the showNotification timeoutHandler
    private static final long SHOW_NOTIFICATION_TIMEOUT = 30 * 1_000L;

    // When the showNotification method is called, this is set true so that the SDK knows that the
    //    developer called the method
    static boolean wasShowNotificationCalled = false;

    // Handler and Runnable used to timeout the showNotification method call from
    //    notificationWillShowInForeground callback
    private static Handler timeoutHandler;
    private static Runnable timeoutRunnable;


    /**
     *
     */
    static OSNotificationExtensionService.NotificationProcessingHandler notificationProcessingHandler;

    public interface NotificationProcessingHandler {
        boolean onNotificationProcessing(OSNotificationReceivedResult notification);
    }

    public static void setNotificationProcessingHandler(OSNotificationExtensionService.NotificationProcessingHandler handler) {
        notificationProcessingHandler = handler;
    }

    /**
     *
     */
    static OSNotificationExtensionService.NotificationWillShowInForegroundHandler notificationWillShowInForegroundHandler;

    public interface NotificationWillShowInForegroundHandler {
        void notificationWillShowInForeground(NotificationGenerationJob notifJob);
    }

    static void setNotificationWillShowInForegroundHandler(OSNotificationExtensionService.NotificationWillShowInForegroundHandler handler) {
        notificationWillShowInForegroundHandler = handler;
    }

    /**
     *
     */
    static OSNotificationExtensionService.NotificationOpenedHandler notificationOpenedHandler;

    public interface NotificationOpenedHandler {
        void notificationOpened(OSNotificationOpenResult result);
    }

    static void setNotificationOpenedHandler(OSNotificationExtensionService.NotificationOpenedHandler handler) {
        notificationOpenedHandler = handler;
    }

    /**
     * Developer may call to override some notification settings.
     * If this method is called the SDK will omit it's notification regardless of what is
     *    returned from onNotificationProcessing.
     */
    protected static OSNotificationDisplayedResult modifyNotification(OSNotificationIntentService.OverrideSettings overrideSettings) {
        OSNotificationIntentService notificationIntentService = OSNotificationIntentService.getInstance();
        // Check if this method has been called already or if no override was set.
        if (notificationIntentService.osNotificationDisplayedResult != null || overrideSettings == null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "modifyNotification called with null overrideSettings");
            return null;
        }

        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "modifyNotification called with overrideSettings: " + overrideSettings.toString());

        overrideSettings.override(notificationIntentService.currentBaseOverrideSettings);
        notificationIntentService.osNotificationDisplayedResult = new OSNotificationDisplayedResult();

        NotificationGenerationJob notifJob = notificationIntentService.createNotifJobFromCurrent();
        notifJob.overrideSettings = overrideSettings;

        notificationIntentService.osNotificationDisplayedResult.androidNotificationId = NotificationBundleProcessor.ProcessJobForDisplay(notifJob);
        return notificationIntentService.osNotificationDisplayedResult;
    }

    /**
     * Developer may call to override the way a notification is being shown.
     * If this method is called the SDK will display the notification based on the parameter passed in.
     */
    static void showNotification(final NotificationGenerationJob notifJob, OneSignal.OSInFocusDisplay inFocusDisplayType) {
        if (timeoutHandler != null)
            timeoutHandler.removeCallbacks(timeoutRunnable);

        wasShowNotificationCalled = true;
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "showNotification called with inFocusDisplayType: " + inFocusDisplayType.toString());

        GenerateNotification.fromJsonPayload(notifJob);
    }

    private static Handler getTimeoutHandler() {
        if (timeoutHandler == null)
            timeoutHandler = new Handler();
        return timeoutHandler;
    }

    /**
     * Called before calling the notificationWillShowInForeground callback
     * If any work inside of the notificationWillShowInForeground takes longer than the timeout, the
     *    notification will be shown using default OSInFocusDisplay NOTIFICATION
     */
    static void startShowNotificationTimeout(final NotificationGenerationJob notifJob) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                timeoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        getTimeoutHandler().removeCallbacks(timeoutRunnable);
                        // Show notification using default OSInFocusDisplay NOTIFICATION
                        GenerateNotification.fromJsonPayload(notifJob);
                    }
                };
                getTimeoutHandler().postDelayed(timeoutRunnable, SHOW_NOTIFICATION_TIMEOUT);
            }
        });
    }

}