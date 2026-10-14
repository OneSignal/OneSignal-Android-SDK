package com.onesignal.internal

// Public entry points that wait for or require init. [label] is the API name the app called.
internal enum class SdkOperation(val label: String) {
    INIT_WITH_CONTEXT("initWithContext"),
    SESSION("Session"),
    NOTIFICATIONS("Notifications"),
    LOCATION("Location"),
    IN_APP_MESSAGES("InAppMessages"),
    USER("User"),
    LOGIN("login"),
    LOGOUT("logout"),
    UPDATE_USER_JWT("updateUserJwt"),
    ADD_USER_JWT_INVALIDATED_LISTENER("addUserJwtInvalidatedListener"),
    REMOVE_USER_JWT_INVALIDATED_LISTENER("removeUserJwtInvalidatedListener"),
    GET_SESSION("getSession"),
    GET_NOTIFICATIONS("getNotifications"),
    GET_LOCATION("getLocation"),
    GET_IN_APP_MESSAGES("getInAppMessages"),
    GET_USER("getUser"),
    GET_CONSENT_REQUIRED("getConsentRequired"),
    SET_CONSENT_REQUIRED("setConsentRequired"),
    GET_CONSENT_GIVEN("getConsentGiven"),
    SET_CONSENT_GIVEN("setConsentGiven"),
    GET_DISABLE_GMS_MISSING_PROMPT("getDisableGMSMissingPrompt"),
    SET_DISABLE_GMS_MISSING_PROMPT("setDisableGMSMissingPrompt"),
    ;

    override fun toString() = label
}
