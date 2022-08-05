/**
 * Modified MIT License
 *
 * Copyright 2022 OneSignal
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

package com.onesignal.onesignal.notification.internal.permissions

interface INotificationPermissionController {
    /**
     * Prompt the user for notification permission.  Note it is possible the application
     * will be killed while the permission prompt is being displayed to the user. When the
     * app restarts it will begin with the permission prompt.  In this case this suspending
     * function has been killed as well, the permission callbacks should be used to cover
     * that case.
     *
     * @return true if permissions are enabled. False if they are not enabled, null if the user
     * was directed to the permission settings and could not be determined at this time. When this
     * does happen, the app will detect the permissions on app focus and drive permission callbacks
     * to notify of the status.
     */
    suspend fun prompt(fallbackToSettings: Boolean) : Boolean?
}