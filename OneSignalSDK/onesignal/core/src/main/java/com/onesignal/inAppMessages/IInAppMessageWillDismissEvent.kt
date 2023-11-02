package com.onesignal.inAppMessages

/**
 * The event passed into [IInAppMessageLifecycleListener.onWillDismiss], it provides access
 * to the In App Message to be dismissed.
 */
interface IInAppMessageWillDismissEvent {
    /**
     * The In App Message that is to be dismissed.
     */
    val message: IInAppMessage
}
