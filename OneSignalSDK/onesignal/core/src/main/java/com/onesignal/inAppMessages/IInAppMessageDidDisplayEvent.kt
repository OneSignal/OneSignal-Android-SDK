package com.onesignal.inAppMessages

/**
 * The event passed into [IInAppMessageLifecycleListener.onDidDisplay], it provides access
 * to the In App Message that has been displayed.
 */
interface IInAppMessageDidDisplayEvent {

    /**
     * The In App Message that has been displayed.
     */
    val message: IInAppMessage
}
