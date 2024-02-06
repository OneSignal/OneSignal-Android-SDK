package com.onesignal.inAppMessages

/**
 * The data provided to [IInAppMessageClickListener.onClick] when an IAM
 * has been clicked by the user.
 */
interface IInAppMessageClickEvent {
    /**
     * The IAM that was clicked by the user.
     */
    val message: IInAppMessage

    /**
     * The result of the user clicking the IAM.
     */
    val result: IInAppMessageClickResult
}
