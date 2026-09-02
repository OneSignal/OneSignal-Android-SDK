package com.onesignal.notifications.internal.registration.impl

/**
 * Thrown when FCM registration cannot proceed because there is no single Firebase
 * project whose sender id, project id, application id, and API key all belong
 * together. Callers map this to [com.onesignal.user.internal.subscriptions.SubscriptionStatus.INVALID_FCM_SENDER_ID].
 */
internal class InvalidFcmProjectException(message: String) : Exception(message)

/**
 * Client-side Firebase project credentials used to register for FCM.
 *
 * Google requires the sender id, project id, application id, and API key to belong
 * to the same Firebase project. FCM token APIs use the project in these fields, not
 * the sender id alone — mixing a dashboard sender with OneSignal's shared project
 * (`onesignal-shared-public` / `754795614042`) mints a token the customer's FCM v1
 * credentials cannot send to, which the backend reports as Invalid Google Project
 * Number (`-6`).
 */
internal data class FcmProjectCredentials(
    val senderId: String,
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
) {
    val isComplete: Boolean
        get() =
            senderId.isNotBlank() &&
                projectId.isNotBlank() &&
                applicationId.isNotBlank() &&
                apiKey.isNotBlank()

    /**
     * Project number embedded in a Firebase application id (`1:{number}:{platform}:{hash}`).
     * Null when the id is missing or not in that form.
     */
    val projectNumber: String?
        get() = projectNumberFromApplicationId(applicationId)

    companion object {
        fun projectNumberFromApplicationId(applicationId: String): String? {
            val parts = applicationId.split(":")
            val number = parts.getOrNull(1) ?: return null
            return number.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        }
    }
}

internal data class FcmFirebaseConfig(
    val credentials: FcmProjectCredentials,
    val source: Source,
    val reuseDefaultApp: Boolean,
) {
    enum class Source {
        /**
         * Host app's default [com.google.firebase.FirebaseApp], initialized from
         * `google-services.json`.
         */
        GOOGLE_SERVICES,

        /** Complete `fcm` object from `android_params.js` for the customer's project. */
        BACKEND,

        /**
         * OneSignal's shared public Firebase project. Only used when the dashboard
         * sender id is that shared project's own sender — never mixed with a
         * customer sender id.
         */
        SHARED_DEFAULT,
    }
}

/**
 * OneSignal's legacy shared public Firebase project. FCM token APIs
 * use these project fields and ignore a mismatched dashboard sender, so they
 * must only be selected when the dashboard sender is this project's sender
 * (`754795614042`).
 */
internal object FcmSharedProject {
    const val PROJECT_ID = "onesignal-shared-public"
    const val SENDER_ID = "754795614042"
    const val APPLICATION_ID = "1:754795614042:android:c682b8144a8dd52bc1ad63"

    fun isShared(credentials: FcmProjectCredentials): Boolean {
        return credentials.projectId == PROJECT_ID ||
            credentials.projectNumber == SENDER_ID
    }
}

/**
 * Picks a single consistent Firebase project for FCM registration, or null when
 * none of the sources agree with the dashboard sender.
 *
 * Preference order:
 * 1. Host `google-services.json` when it is complete and compatible with the dashboard.
 * 2. Backend `fcm` params when they are a complete *customer* project compatible with the dashboard.
 * 3. OneSignal's shared project, only when the dashboard sender is the shared project's sender.
 *
 * A missing dashboard sender is compatible with a customer google-services / backend
 * project (those supply the sender). It is not compatible with the shared fallback —
 * that path is what used to create a subscription that did not care about sender id.
 */
internal object FcmFirebaseConfigResolver {
    fun resolve(
        dashboardSenderId: String?,
        defaultApp: FcmProjectCredentials?,
        backend: FcmProjectCredentials?,
        sharedDefault: FcmProjectCredentials,
    ): FcmFirebaseConfig? {
        val dashboard = normalizeSender(dashboardSenderId)

        val matchingDefaultApp =
            defaultApp?.takeIf { it.isComplete && compatibleWithDashboard(it, dashboard) }
        val matchingBackend =
            backend?.takeIf {
                it.isComplete &&
                    !FcmSharedProject.isShared(it) &&
                    compatibleWithDashboard(it, dashboard)
            }
        val useShared = dashboard == FcmSharedProject.SENDER_ID && sharedDefault.isComplete

        return when {
            matchingDefaultApp != null ->
                FcmFirebaseConfig(
                    credentials = matchingDefaultApp,
                    source = FcmFirebaseConfig.Source.GOOGLE_SERVICES,
                    reuseDefaultApp = true,
                )
            matchingBackend != null ->
                FcmFirebaseConfig(
                    credentials = matchingBackend,
                    source = FcmFirebaseConfig.Source.BACKEND,
                    reuseDefaultApp = false,
                )
            useShared ->
                FcmFirebaseConfig(
                    credentials = sharedDefault,
                    source = FcmFirebaseConfig.Source.SHARED_DEFAULT,
                    reuseDefaultApp = false,
                )
            else -> null
        }
    }

    /**
     * Compatible when there is no dashboard sender (the credentials supply it) or when
     * the credentials' sender and application-id project number both match the dashboard.
     */
    internal fun compatibleWithDashboard(
        credentials: FcmProjectCredentials,
        dashboardSenderId: String?,
    ): Boolean {
        val fromAppId = credentials.projectNumber
        return dashboardSenderId == null ||
            (
                credentials.senderId == dashboardSenderId &&
                    (fromAppId == null || fromAppId == dashboardSenderId)
                )
    }

    internal fun normalizeSender(senderId: String?): String? {
        return senderId?.trim()?.takeIf { it.isNotEmpty() }
    }
}
