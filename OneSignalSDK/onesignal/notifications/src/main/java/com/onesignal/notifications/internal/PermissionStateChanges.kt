package com.onesignal.notifications.internal

import com.onesignal.notifications.IPermissionState
import com.onesignal.notifications.IPermissionStateChanges

internal class PermissionStateChanges(
    override val from: IPermissionState,
    override val to: IPermissionState
) : IPermissionStateChanges
