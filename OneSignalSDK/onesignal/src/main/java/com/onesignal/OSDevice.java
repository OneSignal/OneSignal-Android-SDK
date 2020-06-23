/**
 * Modified MIT License
 *
 * Copyright 2020 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
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

    /**
     * Get the app's notification permission
     * @return false if the user disabled notifications for the app, otherwise true
     */
    public boolean isNotificationEnabled() {
        return OneSignal.getPermissionSubscriptionState().permissionStatus.getEnabled();
    }

    /**
     * Get whether the user is subscribed to OneSignal notifications or not
     * @return false if the user is not subscribed to OneSignal notifications, otherwise true
     */
    public boolean isUserSubscribed() {
        return OneSignal.getPermissionSubscriptionState().subscriptionStatus.getUserSubscriptionSetting();
    }

    /**
     * Get whether the user is subscribed
     * @return true if {@link #isNotificationEnabled}, {@link #isUserSubscribed}, {@link #getUserId} and {@link #getPushToken} are true, otherwise false
     */
    public boolean isSubscribed() {
        return OneSignal.getPermissionSubscriptionState().subscriptionStatus.getSubscribed();
    }

    /**
     * Get user id from registration
     * @return user id if user is registered, otherwise false
     */
    public String getUserId() {
        return OneSignal.getPermissionSubscriptionState().subscriptionStatus.getUserId();
    }

    /**
     * Get device push token
     * @return push token if available, otherwise null
     */
    public String getPushToken() {
        return OneSignal.getPermissionSubscriptionState().subscriptionStatus.getPushToken();
    }

    /**
     * Get the user email id
     * @return email id if user address was registered, otherwise null
     */
    public String getEmailUserId() {
        return OneSignal.getPermissionSubscriptionState().emailSubscriptionStatus.getEmailUserId();
    }

    /**
     * Get the user email
     * @return email address if set, otherwise null
     */
    public String getEmailAddress() {
        return OneSignal.getPermissionSubscriptionState().emailSubscriptionStatus.getEmailAddress();
    }
}
