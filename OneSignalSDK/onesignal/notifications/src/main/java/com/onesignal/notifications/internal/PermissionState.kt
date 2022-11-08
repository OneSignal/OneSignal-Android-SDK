package com.onesignal.notifications.internal

import com.onesignal.notifications.IPermissionState

internal class PermissionState(
    override var notificationsEnabled: Boolean
) : IPermissionState
