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

public class OSDevice {

    private final boolean notificationEnabled;
    private final boolean userSubscribed;
    private final boolean subscribed;
    private final String userId;
    private final String pushToken;
    private final String emailUserId;
    private final String emailAddress;

    public OSDevice(OSPermissionSubscriptionState permissionSubscriptionState) {
        OSSubscriptionState subscriptionStatus = permissionSubscriptionState.subscriptionStatus;
        OSPermissionState permissionStatus = permissionSubscriptionState.permissionStatus;
        OSEmailSubscriptionState emailSubscriptionStatus = permissionSubscriptionState.emailSubscriptionStatus;

        notificationEnabled = permissionStatus.getEnabled();
        userSubscribed = subscriptionStatus.getUserSubscriptionSetting();
        subscribed = subscriptionStatus.getSubscribed();
        userId = subscriptionStatus.getUserId();
        pushToken = subscriptionStatus.getPushToken();
        emailUserId = emailSubscriptionStatus.getEmailUserId();
        emailAddress = emailSubscriptionStatus.getEmailAddress();
    }

    /**
     * Get the app's notification permission
     *
     * @return false if the user disabled notifications for the app, otherwise true
     */
    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }

    /**
     * Get whether the user is subscribed to OneSignal notifications or not
     *
     * @return false if the user is not subscribed to OneSignal notifications, otherwise true
     */
    public boolean isUserSubscribed() {
        return userSubscribed;
    }

    /**
     * Get whether the user is subscribed
     *
     * @return true if {@link #isNotificationEnabled}, {@link #isUserSubscribed}, {@link #getUserId} and {@link #getPushToken} are true, otherwise false
     */
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * Get user id from registration
     *
     * @return user id if user is registered, otherwise false
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Get device push token
     *
     * @return push token if available, otherwise null
     */
    public String getPushToken() {
        return pushToken;
    }

    /**
     * Get the user email id
     *
     * @return email id if user address was registered, otherwise null
     */
    public String getEmailUserId() {
        return emailUserId;
    }

    /**
     * Get the user email
     *
     * @return email address if set, otherwise null
     */
    public String getEmailAddress() {
        return emailAddress;
    }
}
