package com.onesignal.notification.internal

import com.onesignal.notification.IPermissionState

class PermissionState(
    override var notificationsEnabled: Boolean
) : IPermissionState
