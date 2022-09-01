package com.onesignal.notification.internal

import com.onesignal.notification.IPermissionState

internal class PermissionState(
    override var notificationsEnabled: Boolean
) : IPermissionState
