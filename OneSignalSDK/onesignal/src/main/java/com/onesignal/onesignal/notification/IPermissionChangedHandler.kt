package com.onesignal.onesignal.notification

interface IPermissionChangedHandler {
    fun onOSPermissionChanged(stateChanges: IPermissionStateChanges?)
}