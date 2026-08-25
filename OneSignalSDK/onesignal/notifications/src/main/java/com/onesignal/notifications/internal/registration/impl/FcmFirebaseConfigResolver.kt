package com.onesignal.notifications.internal.registration.impl

/**
 * Client-side Firebase project credentials used to register for FCM.
 *
 * Google requires the sender id, project id, application id, and api key to all belong to the
 * same Firebase project. Mixing a customer's sender id with OneSignal's shared project is what
 * the legacy path did, and Play Services rejects that for Firebase Installation ID registration.
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
}

internal data class FcmFirebaseConfig(
    val credentials: FcmProjectCredentials,
    val source: Source,
    val reuseDefaultApp: Boolean,
) {
    enum class Source {
        /**
         * Host app's default [com.google.firebase.FirebaseApp], initialized from
         * `google-services.json` (the google-services Gradle plugin compiles that file into
         * string resources; [com.google.firebase.FirebaseApp.initializeApp] reads them).
         */
        GOOGLE_SERVICES,

        /** Complete `fcm` object from `android_params.js`. */
        BACKEND,

        /**
         * OneSignal's shared public Firebase project. Kept as a last-resort fallback for apps
         * that never added `google-services.json`. Does not work with Installation ID registration.
         */
        SHARED_DEFAULT,
    }
}

/**
 * Picks a single consistent Firebase project for FCM registration.
 *
 * Preference order:
 * 1. The host app's default Firebase app, when its sender id matches the OneSignal dashboard.
 * 2. Backend-provided FCM params (project id / app id / api key) with the dashboard sender id.
 * 3. OneSignal's shared public project, still pairing the dashboard sender id (legacy).
 */
internal object FcmFirebaseConfigResolver {
    fun resolve(
        dashboardSenderId: String,
        defaultApp: FcmProjectCredentials?,
        backend: FcmProjectCredentials?,
        sharedDefault: FcmProjectCredentials,
    ): FcmFirebaseConfig {
        val matchingDefaultApp =
            defaultApp?.takeIf { it.isComplete && it.senderId == dashboardSenderId }
        val completeBackend = backend?.takeIf { it.isComplete }
        return when {
            matchingDefaultApp != null ->
                FcmFirebaseConfig(
                    credentials = matchingDefaultApp,
                    source = FcmFirebaseConfig.Source.GOOGLE_SERVICES,
                    reuseDefaultApp = true,
                )
            completeBackend != null ->
                FcmFirebaseConfig(
                    credentials = completeBackend,
                    source = FcmFirebaseConfig.Source.BACKEND,
                    reuseDefaultApp = false,
                )
            else ->
                FcmFirebaseConfig(
                    credentials = sharedDefault,
                    source = FcmFirebaseConfig.Source.SHARED_DEFAULT,
                    reuseDefaultApp = false,
                )
        }
    }
}
