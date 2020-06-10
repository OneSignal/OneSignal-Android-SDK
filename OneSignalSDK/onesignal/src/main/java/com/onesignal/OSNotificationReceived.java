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

import android.content.Context;

import org.json.JSONObject;

public class OSNotificationReceived extends OSTimeoutHandler {

    // Timeout in millis before applying defaults
    static final long PROCESSING_NOTIFICATION_TIMEOUT = 30 * 1_000L;

    // The notification extender keeping track of notification received data and override settings
    private NotificationExtender notificationExtender;

    // boolean keeping track of whether complete was called or not
    boolean isCompleted = false;

    public OSNotificationPayload payload;
    private boolean isRestoring;
    private boolean isAppInFocus;
    private boolean shouldDisplay;

    OSNotificationReceived(Context context,
                           JSONObject jsonPayload,
                           boolean restoring,
                           boolean appActive,
                           Long restoreTimestamp,
                           NotificationExtender.OverrideSettings currentBaseOverrideSettings) {

        setTimeout(PROCESSING_NOTIFICATION_TIMEOUT);

        payload = NotificationBundleProcessor.OSNotificationPayloadFrom(jsonPayload);
        isRestoring = restoring;
        isAppInFocus = appActive;

        notificationExtender = new NotificationExtender(context, jsonPayload, restoreTimestamp, restoring, currentBaseOverrideSettings);
    }

    public NotificationExtender getNotificationExtender() {
        return notificationExtender;
    }

    public boolean isRestoring() {
        return isRestoring;
    }

    public boolean isAppInFocus() {
        return isAppInFocus;
    }

    /**
     *
     */
    public void setModifiedContent(NotificationExtender.OverrideSettings overrideSettings) {
        notificationExtender.setModifiedContentForNotification(overrideSettings);
    }

    /**
     *
     */
    public OSNotificationReceivedResult display() {
        shouldDisplay = true;
        return notificationExtender.displayNotification();
    }

    // Method controlling completion from the NotificationProcessingHandler
    //    If a dev does not call this at the end of the onNotificationProcessing implementation, a runnable will fire after
    //    a timer and complete by default
    public void complete() {
        destroyTimeout();

        if (isCompleted) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "notificationProcessingHandler already completed, returning early");
            return;
        }
        isCompleted = true;

        if (shouldDisplay)
            OneSignal.fireNotificationWillShowInForegroundHandlers(notificationExtender.notifJob);
    }
}

