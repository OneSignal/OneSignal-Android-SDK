package com.onesignal.notification

interface IPermissionChangedHandler {
    fun onPermissionChanged(stateChanges: IPermissionStateChanges?)
}
