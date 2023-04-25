package com.onesignal.inAppMessages

/**
 * The event passed into [IInAppMessageLifecycleListener.onDidDismiss], it provides access
 * to the In App Message that has been dismissed.
 */
interface IInAppMessageDidDismissEvent {

    /**
     * The In App Message that has been dismissed.
     */
    val message: IInAppMessage
}
