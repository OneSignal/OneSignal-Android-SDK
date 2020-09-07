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

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;

public class OSNotificationGenerationJob {

    // Timeout in seconds before applying defaults
    private static final long SHOW_NOTIFICATION_TIMEOUT = 25 * 1_000L;

    private static final String TITLE_PAYLOAD_PARAM = "title";
    private static final String ALERT_PAYLOAD_PARAM = "alert";
    private static final String CUSTOM_PAYLOAD_PARAM = "custom";
    private static final String ADDITIONAL_DATA_PAYLOAD_PARAM = "a";

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

    OSNotificationExtender.OverrideSettings overrideSettings;

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
            overrideSettings = new OSNotificationExtender.OverrideSettings();

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
            overrideSettings = new OSNotificationExtender.OverrideSettings();
        overrideSettings.androidNotificationId = id;
    }

    private OSNotificationDisplay getNotificationDisplayOption() {
        return this.displayOption;
    }

    private void setNotificationDisplayOption(OSNotificationDisplay displayOption) {
        this.displayOption = displayOption;
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
     * Contains other class which implement this one {@link NotificationGenerationJob}:
     *    1. {@link AppNotificationGenerationJob}
     */
    static class NotificationGenerationJob {

        // Used to toggle when complete is called so it can not be called more than once
        boolean isComplete = false;

        private Runnable timeoutRunnable;
        // The actual notificationJob with notification payload data
        private OSNotificationGenerationJob notificationJob;

        NotificationGenerationJob(OSNotificationGenerationJob notificationJob) {
            this.notificationJob = notificationJob;
        }

        OSNotificationGenerationJob getNotificationJob() {
            return notificationJob;
        }

        protected void startTimeout(Runnable runnable) {
            timeoutRunnable = runnable;
            OSTimeoutHandler.getTimeoutHandler().startTimeout(SHOW_NOTIFICATION_TIMEOUT, runnable);
        }

        protected void destroyTimeout() {
            OSTimeoutHandler.getTimeoutHandler().destroyTimeout(timeoutRunnable);
        }

        public String getApiNotificationId() {
            return notificationJob.getApiNotificationId();
        }

        public int getAndroidNotificationId() {
            return notificationJob.getAndroidIdWithoutCreate();
        }

        public String getTitle() {
            return notificationJob.getTitle().toString();
        }

        public String getBody() {
            return notificationJob.getBody().toString();
        }

        public JSONObject getAdditionalData() {
            return notificationJob.getAdditionalData();
        }

        public OSNotificationDisplay getNotificationDisplayOption() {
            return notificationJob.getNotificationDisplayOption();
        }

        public void setNotificationDisplayOption(OSNotificationDisplay displayOption) {
            notificationJob.setNotificationDisplayOption(displayOption);
        }

        @Override
        public String toString() {
            return "NotificationGenerationJob{" +
                    "isComplete=" + isComplete +
                    ", notificationJob=" + notificationJob +
                    '}';
        }
    }

   /**
    * Used to modify the {@link OSNotificationGenerationJob} inside of the {@link OneSignal.AppNotificationWillShowInForegroundHandler}
    * without exposing internals publicly
    */
   public static class AppNotificationGenerationJob extends NotificationGenerationJob {

        AppNotificationGenerationJob(OSNotificationGenerationJob notificationJob) {
            super(notificationJob);

            startTimeout(new Runnable() {
                @Override
                public void run() {
                    OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "Running complete from AppNotificationGenerationJob timeout runnable!");
                    AppNotificationGenerationJob.this.complete();
                }
            });
        }

        /**
         * Method controlling completion from the AppNotificationWillShowInForegroundHandler
         * If a dev does not call this at the end of the notificationWillShowInForeground implementation,
         *  a runnable will fire after a 30 second timer and complete by default
         */
        public synchronized void complete() {
            destroyTimeout();

            if (isComplete)
                return;

            isComplete = true;

            GenerateNotification.fromJsonPayload(getNotificationJob());
        }
    }

    @Override
    public String toString() {
        return "OSNotificationGenerationJob{" +
                "jsonPayload=" + jsonPayload +
                ", isRestoring=" + isRestoring +
                ", isIamPreview=" + isIamPreview +
                ", displayOption=" + displayOption +
                ", shownTimeStamp=" + shownTimeStamp +
                ", overriddenBodyFromExtender=" + overriddenBodyFromExtender +
                ", overriddenTitleFromExtender=" + overriddenTitleFromExtender +
                ", overriddenSound=" + overriddenSound +
                ", overriddenFlags=" + overriddenFlags +
                ", orgFlags=" + orgFlags +
                ", orgSound=" + orgSound +
                ", overrideSettings=" + overrideSettings +
                '}';
    }
}
