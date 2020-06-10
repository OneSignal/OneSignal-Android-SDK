/**
 * Modified MIT License
 * <p>
 * Copyright 2020 OneSignal
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
import android.net.Uri;

import com.onesignal.OneSignal.OSNotificationDisplay;
import com.onesignal.OSNotificationWillShowInForegroundManager.NotificationWillShowInForegroundManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;

public class OSNotificationGenerationJob {

    private final String TITLE_PAYLOAD_PARAM = "title";
    private final String ALERT_PAYLOAD_PARAM = "alert";
    private final String CUSTOM_PAYLOAD_PARAM = "custom";
    private final String ADDITIONAL_DATA_PAYLOAD_PARAM = "a";

    Context context;
    JSONObject jsonPayload;
    boolean isRestoring;
    boolean isIamPreview;
    OSNotificationDisplay displayOption = OSNotificationDisplay.NOTIFICATION;

    Long shownTimeStamp;

    CharSequence overriddenBodyFromExtender;
    CharSequence overriddenTitleFromExtender;
    Uri overriddenSound;
    Integer overriddenFlags;
    Integer orgFlags;
    Uri orgSound;

    NotificationExtender.OverrideSettings overrideSettings;

    OSNotificationGenerationJob(Context context) {
        this.context = context;
    }

    String getApiNotificationId() {
        return OneSignal.getNotificationIdFromFCMJson(jsonPayload);
    }

    int getAndroidIdWithoutCreate() {
        if (overrideSettings == null || overrideSettings.androidNotificationId == null)
            return -1;

        return overrideSettings.androidNotificationId;
    }

    Integer getAndroidId() {
        if (overrideSettings == null)
            overrideSettings = new NotificationExtender.OverrideSettings();
        if (overrideSettings.androidNotificationId == null)
            overrideSettings.androidNotificationId = new SecureRandom().nextInt();

        return overrideSettings.androidNotificationId;
    }

    /**
     * Get the notification title from the payload
     */
    CharSequence getTitle() {
        if (overriddenTitleFromExtender != null)
            return overriddenTitleFromExtender;
        return jsonPayload.optString(TITLE_PAYLOAD_PARAM, null);
    }

    /**
     * Get the notification body from the payload
     */
    CharSequence getBody() {
        if (overriddenBodyFromExtender != null)
            return overriddenBodyFromExtender;
        return jsonPayload.optString(ALERT_PAYLOAD_PARAM, null);
    }

    /**
     * Get the notification additional data json from the payload
     * @return
     */
    JSONObject getAdditionalData() {
        try {
            return new JSONObject(jsonPayload
                    .optString(CUSTOM_PAYLOAD_PARAM))
                    .getJSONObject(ADDITIONAL_DATA_PAYLOAD_PARAM);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return new JSONObject();
    }

    /**
     * If androidNotificationId is -1 then the notification is a silent one
     */
    boolean isNotificationToDisplay() {
        return getAndroidIdWithoutCreate() != -1;
    }

    boolean hasExtender() {
        return overrideSettings != null && overrideSettings.extender != null;
    }

    void setAndroidIdWithoutOverriding(Integer id) {
        if (id == null)
            return;

        if (overrideSettings != null && overrideSettings.androidNotificationId != null)
            return;

        if (overrideSettings == null)
            overrideSettings = new NotificationExtender.OverrideSettings();
        overrideSettings.androidNotificationId = id;
    }

    private OSNotificationDisplay getNotificationDisplayOption() {
        return this.displayOption;
    }

    private void setNotificationDisplayOption(OSNotificationDisplay displayOption) {
        this.displayOption = displayOption;
    }

    /**
     * Create a {@link ExtNotificationGenerationJob} to manage the {@link OSNotificationGenerationJob}
     *  while the {@link OneSignal.ExtNotificationWillShowInForegroundHandler} is being fired
     */
    ExtNotificationGenerationJob toExtNotificationGenerationJob() {
        return new ExtNotificationGenerationJob(this);
    }

    /**
     * Create a {@link AppNotificationGenerationJob} to manage the {@link OSNotificationGenerationJob}
     *  while the {@link OneSignal.AppNotificationWillShowInForegroundHandler} is being fired
     */
    AppNotificationGenerationJob toAppNotificationGenerationJob() {
        return new AppNotificationGenerationJob(this);
    }

    /**
     * A wrapper for the {@link OSNotificationGenerationJob}
     * Contains two other classes which implement this one {@link NotificationGenerationJob}:
     *    1. {@link ExtNotificationGenerationJob}
     *    2. {@link AppNotificationGenerationJob}
     */
    static class NotificationGenerationJob extends OSTimeoutHandler {

        // Timeout in millis before applying defaults
        static final long SHOW_NOTIFICATION_TIMEOUT = 30 * 1_000L;

        // The actual notifJob with notification payload data
        private OSNotificationGenerationJob notifJob;

        // boolean keeping track of whether complete was called or not
        boolean isCompleted = false;

        NotificationGenerationJob(OSNotificationGenerationJob notifJob) {
            this.notifJob = notifJob;
        }

        OSNotificationGenerationJob getNotifJob() {
            return notifJob;
        }

        public String getApiNotificationId() {
            return notifJob.getApiNotificationId();
        }

        public int getAndroidNotificationId() {
            return notifJob.getAndroidIdWithoutCreate();
        }

        public String getTitle() {
            return notifJob.getTitle().toString();
        }

        public String getBody() {
            return notifJob.getBody().toString();
        }

        public JSONObject getAdditionalData() {
            return notifJob.getAdditionalData();
        }

        public OSNotificationDisplay getNotificationDisplayOption() {
            return notifJob.getNotificationDisplayOption();
        }

        public void setNotificationDisplayOption(OSNotificationDisplay displayOption) {
            notifJob.setNotificationDisplayOption(displayOption);
        }
    }

    /**
     * Used to modify the {@link OSNotificationGenerationJob} inside of the {@link OneSignal.ExtNotificationWillShowInForegroundHandler}
     *    without exposing internals publicly
     */
    public static class ExtNotificationGenerationJob extends NotificationGenerationJob {

        ExtNotificationGenerationJob(OSNotificationGenerationJob notifJob) {
            super(notifJob);

            setTimeout(SHOW_NOTIFICATION_TIMEOUT);
            startTimeout(new Runnable() {
                @Override
                public void run() {
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "extNotificationWillShowInForegroundHandler complete never called, using default post delayed timeout handler's timeout runnable");
                    ExtNotificationGenerationJob.this.complete(true);
                }
            });
        }

        // Method controlling bubbling from the ExtNotificationWillShowInForegroundHandler to the AppNotificationWillShowInForegroundHandler
        //    If a dev does not call this at the end of the notificationWillShowInForeground implementation, a runnable will fire after
        //    a timer and attempt to bubble to the AppNotificationWillShowInForegroundHandler automatically
        public void complete(boolean bubble) {
            destroyTimeout();

            if (isCompleted) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "extNotificationWillShowInForegroundHandler already completed, returning early");
                return;
            }
            isCompleted = true;

            // Move on to showing notification if no AppNotificationWillShowInForegroundHandler exists or bubbling is set false
            if (OneSignal.appNotificationWillShowInForegroundHandler == null || !bubble) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "appNotificationWillShowInForegroundHandler is null or bubble: " + bubble + ", moving on to showing OS notification id: " + getApiNotificationId());
                GenerateNotification.fromJsonPayload(getNotifJob());
                return;
            }

            // If the appNotificationWillShowInForegroundHandler exists and we want to bubble, call
            //    the notificationWillShowInForeground implementation
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "appNotificationWillShowInForegroundHandler exists and bubble: " + bubble + ", enqueueing OS notification id: " + getApiNotificationId());
            NotificationWillShowInForegroundManager.beginEnqueueingWork(getNotifJob().toAppNotificationGenerationJob());
        }
    }

    /**
     * Used to modify the {@link OSNotificationGenerationJob} inside of the {@link OneSignal.AppNotificationWillShowInForegroundHandler}
     *    without exposing internals publicly
     */
    public static class AppNotificationGenerationJob extends NotificationGenerationJob {

        AppNotificationGenerationJob(OSNotificationGenerationJob notifJob) {
            super(notifJob);

            setTimeout(SHOW_NOTIFICATION_TIMEOUT);
            startTimeout(new Runnable() {
                @Override
                public void run() {
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "appNotificationWillShowInForegroundHandler complete never called, using default post delayed timeout handler's timeout runnable");
                    AppNotificationGenerationJob.this.complete();
                }
            });
        }

        // Method controlling completion from the AppNotificationWillShowInForegroundHandler
        //    If a dev does not call this at the end of the notificationWillShowInForeground implementation, a runnable will fire after
        //    a timer and complete by default
        public void complete() {
            destroyTimeout();

            if (isCompleted) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "appNotificationWillShowInForegroundHandler already completed, returning early");
                return;
            }
            isCompleted = true;

            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "NotificationWillShowInForegroundHandler are both complete, moving on to showing OS notification id: " + getApiNotificationId());
            GenerateNotification.fromJsonPayload(getNotifJob());
        }
    }

}
