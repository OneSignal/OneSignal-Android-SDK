package com.onesignal.onesignal.iam

/**
 * An enumeration of the possible places action URL's can be loaded,
 * such as an in-app webview
 */
enum class InAppMessageActionUrlType {
    /** Opens in an in-app webview **/
    IN_APP_WEBVIEW,

    /** Moves app to background and opens URL in browser **/
    BROWSER,

    /** Loads the URL on the in-app message webview itself **/
    REPLACE_CONTENT;
}