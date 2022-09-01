package com.onesignal.iam

interface IInAppMessageAction {
    /**
     * UUID assigned by OneSignal for internal use.
     * Package-private to track which element was tapped to report to the OneSignal dashboard.
     */
    val clickId: String?

    /**
     * An optional click name entered defined by the app developer when creating the IAM
     */
    val clickName: String?

    /**
     * Determines where the URL is opened, ie. Default browser.
     */
    val urlTarget: InAppMessageActionUrlType?

    /**
     * An optional URL that opens when the action takes place
     */
    val clickUrl: String?

    /**
     * UUID for the page in an IAM Carousel
     */
    val pageId: String?

    /**
     * Determines if this was the first action taken on the in app message
     */
    val isFirstClick: Boolean

    /**
     * Determines if tapping on the element should close the In-App Message.
     */
    val closesMessage: Boolean
}
