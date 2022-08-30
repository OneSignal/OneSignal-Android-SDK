package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.notification.IPermissionState
import com.onesignal.onesignal.notification.IPermissionStateChanges

class PermissionStateChanges(
    override val from: IPermissionState,
    override val to: IPermissionState
) : IPermissionStateChanges
