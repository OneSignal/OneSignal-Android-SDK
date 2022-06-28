package com.onesignal.onesignal.internal.notification

import com.onesignal.onesignal.notification.IPermissionState

class PermissionState(
    override var notificationsEnabled: Boolean
) : IPermissionState