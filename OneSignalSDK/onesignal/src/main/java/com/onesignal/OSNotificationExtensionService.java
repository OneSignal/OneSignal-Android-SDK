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

public abstract class OSNotificationExtensionService {

    static final int EXTENDER_SERVICE_JOB_ID = 2071862121;

    // Timeout in seconds for  as the postDelayed timer for the setNotificationDisplayType timeoutHandler
    private static final long SHOW_NOTIFICATION_TIMEOUT = 5 * 1_000L;

    // The extension service app AndroidManifest.xml meta data tag key name
    private static final String EXTENSION_SERVICE_META_DATA_TAG_NAME = "com.onesignal.NotificationExtensionServiceClass";

    private static OSNotificationIntentService notificationIntentService;

    static OSNotificationIntentService getProcessingIntentServiceInstance() {
        if (notificationIntentService == null)
            notificationIntentService = new OSNotificationIntentService();
        return notificationIntentService;
    }

    // Handler and Runnable used to timeout the setNotificationDisplayType method call from
    //    notificationWillShowInForeground callback
    private static Handler timeoutHandler;
    private static Runnable timeoutRunnable;

    /**
     *
     */
    static OneSignal.NotificationWillShowInForegroundHandler extNotificationWillShowInForegroundHandler;

    static void setExtNotificationWillShowInForegroundHandler(OneSignal.NotificationWillShowInForegroundHandler handler) {
        extNotificationWillShowInForegroundHandler = handler;
    }

    /**
     * Controlled by runNotificationWillShowInForegroundHandlers, fires only the extension service
     *  NotificationWillShowInForegroundHandler
     * @param notifJob  - NotificationGenerationJob passed into the NotificationWillShowInForegroundHandler callback
     */
    static void fireExtNotificationWillShowInForegroundHandler(final NotificationGenerationJob notifJob) {
        if (extNotificationWillShowInForegroundHandler == null)
            return;

        // TODO: Enqueue work here with NotificationWillShowInForegroundIntentService
        extNotificationWillShowInForegroundHandler.notificationWillShowInForeground(notifJob);
        OSNotificationExtensionService.showNotification(notifJob);
    }

    /**
     *
     */
    static OneSignal.NotificationOpenedHandler extNotificationOpenedHandler;

    static void setExtNotificationOpenedHandler(OneSignal.NotificationOpenedHandler handler) {
        extNotificationOpenedHandler = handler;

        if (OneSignal.isInitDone())
            OneSignal.fireUnprocessedOpenedNotificationHandlers();
    }

    /**
     * Controlled by runNotificationOpenedHandlers, fires only the ext NotificationOpenedHandler
     * @param openedResult - OSNotificationOpenedResult to be passed into the NotificationOpenedHandler callback
     * @return - boolean informing whether the handler was called
     */
    static boolean fireExtNotificationOpenedHandler(final OSNotificationOpenedResult openedResult) {
        if (extNotificationOpenedHandler == null)
            return false;

        // TODO: Enqueue work here with NotificationOpenedIntentService
        extNotificationOpenedHandler.notificationOpened(openedResult);

        return true;
    }

    /**
     * Developer may call to override the way a notification is being shown.
     * If this method is called the SDK will display the notification based on the parameter passed in.
     */
    static void showNotification(final NotificationGenerationJob notifJob) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "setNotificationDisplayType called with inFocusDisplayType: " + notifJob.inFocusDisplayType.toString());
        GenerateNotification.fromJsonPayload(notifJob);
    }

    /**
     *
     */
    interface NotificationWillShowInForegroundCompletionHandler {
        void complete(NotificationGenerationJob notifJob, boolean bubble);
    }

    /**
     * Called before calling the notificationWillShowInForeground callback
     * If any work inside of the notificationWillShowInForeground takes longer than the timeout, the
     *    notification will be shown using default OSNotificationDisplayOption NOTIFICATION
     * <br/><br/>
     * @see OSNotificationExtensionService#destroyShowNotificationTimeout()
     */
    static void startShowNotificationTimeout(final NotificationGenerationJob notifJob, final NotificationWillShowInForegroundCompletionHandler handler) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                timeoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        destroyShowNotificationTimeout();
                        // Perform callback work since timeout was reached
                        handler.complete(notifJob, true);
                    }
                };

                if (timeoutHandler != null)
                    timeoutHandler.postDelayed(timeoutRunnable, SHOW_NOTIFICATION_TIMEOUT);
            }
        });
    }

    /**
     *
     * @see OSNotificationExtensionService#startShowNotificationTimeout(NotificationGenerationJob, NotificationWillShowInForegroundCompletionHandler)
     */
    static void destroyShowNotificationTimeout() {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                if (timeoutHandler != null)
                    timeoutHandler.removeCallbacks(timeoutRunnable);

                timeoutHandler = null;
                timeoutRunnable = null;
            }
        });
    }

    /**
     * In addition to using the setters to set all of the handlers you can also create your own implementation
     *  within a separate class and give your AndroidManifest.xml a special meta data tag
     * <br/><br/>
     * The meta data tag looks like this:
     *  <meta-data android:name="com.onesignal.NotificationExtensionServiceClass" android:value="com.company.ExtensionService" />
     * <br/><br/>
     * In the case of the {@link OneSignal.NotificationWillShowInForegroundHandler},
     *  {@link OneSignal.NotificationOpenedHandler}, and {@link OneSignal.InAppMessageClickHandler},
     *  there are also setters for these handlers. So why create this new class and implement
     *  the same handlers, won't they just overwrite each other?
     * <br/><br/>
     * No, the idea here is to keep track of two separate handlers and keep them both
     *  100% optional. The extension handlers are set using the class implementations and the app
     *  handlers set through the setter methods.
     * <br/><br/>
     * The extension handlers will always be called first and then bubble to the app handlers
     * <br/><br/>
     * The {@link OneSignal.NotificationProcessingHandler}
     * <br/><br/>
     * @see OneSignal.NotificationProcessingHandler
     * @see OneSignal.NotificationWillShowInForegroundHandler
     * @see OneSignal.NotificationOpenedHandler
     * @see OneSignal.InAppMessageClickHandler
     */
    static void setupNotificationExtensionServiceClass() {
        String className = OSUtils.getManifestMeta(OneSignal.appContext, EXTENSION_SERVICE_META_DATA_TAG_NAME);

        // No meta data containing extension service class name
        if (className == null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "No class found, not setting up OSNotificationExtensionService");
            return;
        }

        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Found class: " + className + ", attempting to call constructor");
        // Pass an instance of the given class to set any overridden handlers
        try {
            Class<?> clazz = Class.forName(className);
            Object clazzInstance = clazz.newInstance();
            if (clazzInstance instanceof OneSignal.NotificationProcessingHandler) {
                OSNotificationExtensionService.getProcessingIntentServiceInstance().setNotificationProcessingHandler((OneSignal.NotificationProcessingHandler) clazzInstance);
                OneSignal.hasNotificationExtensionService = true;
            }
            if (clazzInstance instanceof OneSignal.NotificationWillShowInForegroundHandler) {
                OSNotificationExtensionService.setExtNotificationWillShowInForegroundHandler((OneSignal.NotificationWillShowInForegroundHandler) clazzInstance);
            }
            if (clazzInstance instanceof OneSignal.NotificationOpenedHandler) {
                OSNotificationExtensionService.setExtNotificationOpenedHandler((OneSignal.NotificationOpenedHandler) clazzInstance);
            }
            if (clazzInstance instanceof OneSignal.InAppMessageClickHandler) {
                OneSignal.setInAppMessageClickHandler((OneSignal.InAppMessageClickHandler) clazzInstance);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}