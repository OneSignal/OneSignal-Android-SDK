package com.onesignal.onesignal.notification

interface IPermissionChangedHandler {
    fun onPermissionChanged(stateChanges: IPermissionStateChanges?)
}
