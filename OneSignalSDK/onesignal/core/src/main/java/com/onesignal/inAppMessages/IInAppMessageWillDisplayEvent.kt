package com.onesignal.inAppMessages

/**
 * The event passed into [IInAppMessageLifecycleListener.onWillDisplay], it provides access
 * to the In App Message to be displayed.
 */
interface IInAppMessageWillDisplayEvent {

    /**
     * The In App Message that is to be displayed.
     */
    val message: IInAppMessage
}
