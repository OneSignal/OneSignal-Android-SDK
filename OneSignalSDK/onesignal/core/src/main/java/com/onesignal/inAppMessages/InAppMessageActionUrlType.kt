package com.onesignal.inAppMessages

/**
 * An enumeration of the possible places action URL's can be loaded,
 * such as an in-app webview
 */
enum class InAppMessageActionUrlType(
    private val text: String,
) {
    /**
     * Opens in an in-app webview
     */
    IN_APP_WEBVIEW("webview"),

    /**
     * Moves app to background and opens URL in browser
     */
    BROWSER("browser"),

    /**
     * Loads the URL on the in-app message webview itself
     */
    REPLACE_CONTENT("replacement"),
    ;

    override fun toString(): String {
        return text
    }

    companion object {
        fun fromString(text: String?): InAppMessageActionUrlType? {
            for (type in values()) {
                if (type.text.equals(text, ignoreCase = true)) return type
            }
            return null
        }
    }
}
