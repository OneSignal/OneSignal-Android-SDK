package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.notification.IPermissionState

class PermissionState(
    override var notificationsEnabled: Boolean
) : IPermissionState
