package com.onesignal.notification.internal

import com.onesignal.notification.IPermissionState
import com.onesignal.notification.IPermissionStateChanges

class PermissionStateChanges(
    override val from: IPermissionState,
    override val to: IPermissionState
) : IPermissionStateChanges
