package com.onesignal.inAppMessages

/**
 * An IAM action represents an action performed by the user in reaction to an IAM
 * being displayed.
 */
interface IInAppMessageClickResult {
    /**
     * An optional action ID that defines the action taken.
     * See [Click Actions | OneSignal Docs](https://documentation.onesignal.com/docs/iam-click-actions#how-to-collect-custom-click-actions).
     */
    val actionId: String?

    /**
     * Determines where the URL is opened, ie. Default browser.
     */
    val urlTarget: InAppMessageActionUrlType?

    /**
     * An optional URL that opens when the action takes place
     */
    val url: String?

    /**
     * Determines if tapping on the element is closing the IAM.
     */
    val closingMessage: Boolean
}
