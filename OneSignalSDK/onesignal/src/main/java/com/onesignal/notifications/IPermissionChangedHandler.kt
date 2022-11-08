package com.onesignal.notifications

/**
 * Implement this interface and provide an instance to [INotificationsManager.addPushPermissionHandler]
 * in order to receive control when the push permission state has changed on the current device.
 */
interface IPermissionChangedHandler {

    /**
     * Called when the permission state has changed.
     *
     * @param stateChanges The permission state changes that have occurred.
     */
    fun onPermissionChanged(stateChanges: IPermissionStateChanges?)
}
