package com.onesignal.notifications.internal.registration.impl

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

/**
 * Retrieves an FCM registration token, falling back to Firebase Installation ID registration
 * when the host app has opted into it.
 *
 * Opting in (via `firebase_messaging_installation_id_enabled`) disables the legacy token API
 * for the whole process, not just the [com.google.firebase.FirebaseApp] that opted in. In that
 * case the only usable token is a Firebase Installation ID issued by a real Firebase project —
 * which means the host app's `google-services.json`, not OneSignal's shared project.
 */
internal object FCMTokenProvider {
    /**
     * The Firebase Installation ID registration that replaces the legacy token API, along with the
     * sender id of the Firebase project it would register against.
     */
    class InstallationIdRegistration(
        val senderId: String?,
        val register: () -> Task<*>,
        val installationId: () -> Task<String>,
    )

    fun getToken(
        senderId: String,
        installationIdEnabled: () -> String,
        legacyToken: () -> Task<String>,
        installationIdRegistration: () -> InstallationIdRegistration?,
    ): String {
        return try {
            await(legacyToken())
        } catch (e: IllegalStateException) {
            if (!isLegacyTokenApiDisabled(e)) throw e

            registerInstallationId(senderId, installationIdEnabled(), installationIdRegistration())
        }
    }

    private fun registerInstallationId(
        senderId: String,
        installationIdEnabled: String,
        registration: InstallationIdRegistration?,
    ): String {
        val optedIn = "firebase_messaging_installation_id_enabled=$installationIdEnabled"

        if (registration == null) {
            throw IllegalStateException(
                "Firebase Installation ID registration is enabled ($optedIn) but this app has no " +
                    "default FirebaseApp to register with. Add your Firebase configuration " +
                    "(google-services.json) and apply the google-services Gradle plugin, or set " +
                    "firebase_messaging_installation_id_enabled to false in your manifest to keep " +
                    "using the legacy FCM token API.",
            )
        }

        if (registration.senderId != senderId) {
            throw IllegalStateException(
                "Firebase Installation ID registration is enabled ($optedIn) but the default " +
                    "FirebaseApp uses sender id ${registration.senderId}, while OneSignal is " +
                    "configured with sender id $senderId. Point google-services.json and the " +
                    "OneSignal dashboard at the same Firebase project, or set " +
                    "firebase_messaging_installation_id_enabled to false in your manifest to keep " +
                    "using the legacy FCM token API.",
            )
        }

        await(registration.register())
        return await(registration.installationId())
    }

    /**
     * Calls `register()` reflectively. [com.google.firebase.messaging.FirebaseMessaging.register]
     * was added in firebase-messaging 25.1.0. This module compiles against the preferred 24.0.0,
     * but the non-strict Gradle constraint lets apps select newer versions through conflict
     * resolution.
     */
    fun invokeRegister(target: Any): Task<*> {
        val register =
            try {
                target.javaClass.getMethod("register")
            } catch (e: NoSuchMethodException) {
                throw IllegalStateException(
                    "Firebase Installation ID registration is enabled but " +
                        "FirebaseMessaging.register() was not found. It requires firebase-messaging " +
                        "25.1.0 or newer, and has to survive minification, so check that OneSignal's " +
                        "consumer ProGuard rules are applied.",
                    e,
                )
            }

        return try {
            register.invoke(target) as Task<*>
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun isLegacyTokenApiDisabled(exception: IllegalStateException): Boolean {
        val message = exception.message ?: return false
        return message.contains("API disabled") && message.contains("register()")
    }

    private fun <T> await(task: Task<T>): T {
        try {
            return Tasks.await(task)
        } catch (e: ExecutionException) {
            throw task.exception ?: e
        }
    }
}
