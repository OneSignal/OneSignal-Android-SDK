package com.onesignal.notifications

/**
 * Implement this interface and provide an instance to [INotificationsManager.addPermissionObserver]
 * in order to receive control when the push permission state has changed on the current device.
 */
interface IPermissionObserver {

    /**
     * Called when the permission state has changed.
     *
     * @param permission Whether this app/device now has push notification permission.
     */
    fun onNotificationPermissionChange(permission: Boolean)
}
