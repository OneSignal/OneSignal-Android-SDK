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

import org.json.JSONObject;

import java.security.SecureRandom;

public class OSNotificationGenerationJob {

    private OSNotification notification;
    private Context context;
    private JSONObject jsonPayload;
    private boolean restoring;
    private boolean isNotificationToDisplay;

    private Long shownTimeStamp;

    private CharSequence overriddenBodyFromExtender;
    private CharSequence overriddenTitleFromExtender;
    private Uri overriddenSound;
    private Integer overriddenFlags;
    private Integer orgFlags;
    private Uri orgSound;

    OSNotificationGenerationJob(Context context) {
        this.context = context;
    }

    OSNotificationGenerationJob(Context context, JSONObject jsonPayload) {
        this(context, new OSNotification(jsonPayload), jsonPayload);
    }

    OSNotificationGenerationJob(Context context, OSNotification notification, JSONObject jsonPayload) {
        this.context = context;
        this.jsonPayload = jsonPayload;
        this.setNotification(notification);
    }

    /**
     * Get the notification title from the payload
     */
    CharSequence getTitle() {
        if (overriddenTitleFromExtender != null)
            return overriddenTitleFromExtender;
        return notification.getTitle();
    }

    /**
     * Get the notification body from the payload
     */
    CharSequence getBody() {
        if (overriddenBodyFromExtender != null)
            return overriddenBodyFromExtender;
        return notification.getBody();
    }

    /**
     * Get the notification additional data json from the payload
     */
    JSONObject getAdditionalData() {
        return notification.getAdditionalData() != null ? notification.getAdditionalData() : new JSONObject();
    }

    /**
     * Determine whether this notification has been displayed.
     */
    boolean isNotificationToDisplay() {
        return isNotificationToDisplay;
    }

    /**
     * Indicate whether notification has been displayed.
     */
    void setIsNotificationToDisplay(boolean isNotificationToDisplay) {
        this.isNotificationToDisplay = isNotificationToDisplay;
    }

    boolean hasExtender() {
        return notification.getNotificationExtender() != null;
    }

    String getApiNotificationId() {
        return OneSignal.getNotificationIdFromFCMJson(jsonPayload);
    }

    Integer getAndroidId() {
        return notification.getAndroidNotificationId() ;
    }

    public OSNotification getNotification() {
        return notification;
    }

    public void setNotification(OSNotification notification) {
        // If there is no android ID on the notification coming in, create one either
        // copying from the previous one or generating a new one.
        if (notification != null && !notification.hasNotificationId()) {
            if (this.notification != null && this.notification.hasNotificationId())
                notification.setAndroidNotificationId(this.notification.getAndroidNotificationId());
            else
                notification.setAndroidNotificationId(new SecureRandom().nextInt());
        }

        this.notification = notification;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public JSONObject getJsonPayload() {
        return jsonPayload;
    }

    public void setJsonPayload(JSONObject jsonPayload) {
        this.jsonPayload = jsonPayload;
    }

    public boolean isRestoring() {
        return restoring;
    }

    public void setRestoring(boolean restoring) {
        this.restoring = restoring;
    }

    public Long getShownTimeStamp() {
        return shownTimeStamp;
    }

    public void setShownTimeStamp(Long shownTimeStamp) {
        this.shownTimeStamp = shownTimeStamp;
    }

    public CharSequence getOverriddenBodyFromExtender() {
        return overriddenBodyFromExtender;
    }

    public void setOverriddenBodyFromExtender(CharSequence overriddenBodyFromExtender) {
        this.overriddenBodyFromExtender = overriddenBodyFromExtender;
    }

    public CharSequence getOverriddenTitleFromExtender() {
        return overriddenTitleFromExtender;
    }

    public void setOverriddenTitleFromExtender(CharSequence overriddenTitleFromExtender) {
        this.overriddenTitleFromExtender = overriddenTitleFromExtender;
    }

    public Uri getOverriddenSound() {
        return overriddenSound;
    }

    public void setOverriddenSound(Uri overriddenSound) {
        this.overriddenSound = overriddenSound;
    }

    public Integer getOverriddenFlags() {
        return overriddenFlags;
    }

    public void setOverriddenFlags(Integer overriddenFlags) {
        this.overriddenFlags = overriddenFlags;
    }

    public Integer getOrgFlags() {
        return orgFlags;
    }

    public void setOrgFlags(Integer orgFlags) {
        this.orgFlags = orgFlags;
    }

    public Uri getOrgSound() {
        return orgSound;
    }

    public void setOrgSound(Uri orgSound) {
        this.orgSound = orgSound;
    }

    @Override
    public String toString() {
        return "OSNotificationGenerationJob{" +
                "jsonPayload=" + jsonPayload +
                ", isRestoring=" + restoring +
                ", isNotificationToDisplay=" + isNotificationToDisplay +
                ", shownTimeStamp=" + shownTimeStamp +
                ", overriddenBodyFromExtender=" + overriddenBodyFromExtender +
                ", overriddenTitleFromExtender=" + overriddenTitleFromExtender +
                ", overriddenSound=" + overriddenSound +
                ", overriddenFlags=" + overriddenFlags +
                ", orgFlags=" + orgFlags +
                ", orgSound=" + orgSound +
                ", notification=" + notification +
                '}';
    }
}
