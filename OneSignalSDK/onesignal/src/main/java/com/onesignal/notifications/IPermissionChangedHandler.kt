package com.onesignal.notifications

/**
 * Implement this interface and provide an instance to [INotificationsManager.addPermissionChangedHandler]
 * in order to receive control when the push permission state has changed on the current device.
 */
interface IPermissionChangedHandler {

    /**
     * Called when the permission state has changed.
     *
     * @param permission Whether this app/device now has push notification permission.
     */
    fun onPermissionChanged(permission: Boolean)
}
