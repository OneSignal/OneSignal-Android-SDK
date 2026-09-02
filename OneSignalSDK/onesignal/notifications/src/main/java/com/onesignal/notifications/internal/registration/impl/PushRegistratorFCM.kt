package com.onesignal.notifications.internal.registration.impl

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.ExecutionException

internal class PushRegistratorFCM(
    var _configModelStore: ConfigModelStore,
    val _applicationService: IApplicationService,
    upgradePrompt: GooglePlayServicesUpgradePrompt,
    deviceService: IDeviceService,
    private val defaultFirebaseApp: (Context) -> FirebaseApp? = { hostDefaultFirebaseApp(it) },
) : PushRegistratorAbstractGoogle(deviceService, _configModelStore, upgradePrompt) {
    companion object {
        private const val FCM_APP_NAME = "ONESIGNAL_SDK_FCM_APP_NAME"

        // client.api_key.current_key from OneSignal's shared public google-services.json
        private const val FCM_DEFAULT_API_KEY_BASE64 = "QUl6YVN5QW5UTG41LV80TWMyYTJQLWRLVWVFLWFCdGd5Q3JqbFlV"

        /**
         * The host app's default Firebase app, created from `google-services.json` when present.
         *
         * Prefer a read-only lookup of an already-initialized default app (FirebaseInitProvider
         * or the host app). Only call [FirebaseApp.initializeApp] with a [Context] when that
         * lookup fails: that path reads the string resources the google-services Gradle plugin
         * generated. If those resources are missing it returns null.
         */
        internal fun hostDefaultFirebaseApp(context: Context): FirebaseApp? {
            try {
                return FirebaseApp.getInstance()
            } catch (_: IllegalStateException) {
                // Default app not initialized yet.
            }
            return try {
                FirebaseApp.initializeApp(context)
            } catch (e: IllegalStateException) {
                Logging.debug("Unable to initialize the default FirebaseApp from google-services.json", e)
                FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            }
        }

        internal fun credentialsFrom(options: FirebaseOptions?): FcmProjectCredentials? {
            if (options == null) return null
            val credentials =
                FcmProjectCredentials(
                    senderId = options.gcmSenderId.orEmpty(),
                    projectId = options.projectId.orEmpty(),
                    applicationId = options.applicationId,
                    apiKey = options.apiKey,
                )
            return credentials.takeIf { it.isComplete }
        }

        internal fun sharedDefaultCredentials(): FcmProjectCredentials {
            val defaultApiKey = String(Base64.decode(FCM_DEFAULT_API_KEY_BASE64, Base64.DEFAULT))
            return FcmProjectCredentials(
                senderId = FcmSharedProject.SENDER_ID,
                projectId = FcmSharedProject.PROJECT_ID,
                applicationId = FcmSharedProject.APPLICATION_ID,
                apiKey = defaultApiKey,
            )
        }
    }

    private val firebaseLock = Any()
    private var firebaseApp: FirebaseApp? = null

    override val providerName: String
        get() = "FCM"

    @Throws(ExecutionException::class, InterruptedException::class, InvalidFcmProjectException::class)
    override suspend fun getToken(senderId: String): String {
        val app = initFirebaseApp(senderId)
        return getTokenWithClassFirebaseMessaging(app)
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun getTokenWithClassFirebaseMessaging(app: FirebaseApp): String {
        // Use firebaseApp.get(FirebaseMessaging.class) instead of FirebaseMessaging.getInstance()
        // so registration is against the project we resolved, not whatever default app the host
        // happens to have.
        val fcmInstance = app.get(FirebaseMessaging::class.java)
        val tokenTask = fcmInstance.token
        try {
            return Tasks.await(tokenTask)
        } catch (e: ExecutionException) {
            throw tokenTask.exception ?: e
        }
    }

    private fun initFirebaseApp(dashboardSenderId: String): FirebaseApp {
        synchronized(firebaseLock) {
            val context = _applicationService.appContext
            val hostApp = defaultFirebaseApp(context)
            val hostCredentials = credentialsFrom(hostApp?.options)
            logIfHostSenderMismatches(dashboardSenderId, hostCredentials)

            val resolved =
                FcmFirebaseConfigResolver.resolve(
                    dashboardSenderId = dashboardSenderId,
                    defaultApp = hostCredentials,
                    backend = backendCredentials(dashboardSenderId),
                    sharedDefault = sharedDefaultCredentials(),
                ) ?: throw InvalidFcmProjectException(missingProjectMessage(dashboardSenderId))

            logResolvedConfig(resolved)

            val current = firebaseApp
            val app =
                when {
                    current != null && credentialsMatch(current.options, resolved.credentials) ->
                        current
                    resolved.reuseDefaultApp -> {
                        val host =
                            hostApp
                                ?: throw InvalidFcmProjectException(missingProjectMessage(dashboardSenderId))
                        deleteNamedAppIfPresent(current)
                        host
                    }
                    else -> {
                        deleteNamedAppIfPresent(current)
                        FirebaseApp.initializeApp(
                            context,
                            resolved.credentials.toFirebaseOptions(),
                            FCM_APP_NAME,
                        )
                    }
                }
            firebaseApp = app
            return app
        }
    }

    private fun deleteNamedAppIfPresent(current: FirebaseApp?) {
        if (current != null && current.name == FCM_APP_NAME) {
            current.delete()
            firebaseApp = null
        }
    }

    private fun backendCredentials(dashboardSenderId: String): FcmProjectCredentials? {
        val fcmParams = _configModelStore.model.fcmParams
        val projectId = fcmParams.projectId
        val appId = fcmParams.appId
        val apiKey = fcmParams.apiKey
        if (projectId.isNullOrBlank() || appId.isNullOrBlank() || apiKey.isNullOrBlank()) {
            return null
        }
        val senderFromAppId = FcmProjectCredentials.projectNumberFromApplicationId(appId)
        val dashboard = FcmFirebaseConfigResolver.normalizeSender(dashboardSenderId)
        val senderId =
            when {
                dashboard != null && (senderFromAppId == null || senderFromAppId == dashboard) -> dashboard
                senderFromAppId != null -> senderFromAppId
                else -> dashboard ?: ""
            }
        return FcmProjectCredentials(
            senderId = senderId,
            projectId = projectId,
            applicationId = appId,
            apiKey = apiKey,
        )
    }

    private fun logIfHostSenderMismatches(
        dashboardSenderId: String,
        hostCredentials: FcmProjectCredentials?,
    ) {
        val dashboard = FcmFirebaseConfigResolver.normalizeSender(dashboardSenderId) ?: return
        if (hostCredentials == null || hostCredentials.senderId == dashboard) return
        Logging.warn(
            "google-services.json sender id ${hostCredentials.senderId} does not match the " +
                "OneSignal dashboard sender id $dashboard. FCM will not use the host " +
                "Firebase project until they match. Point both at the same Firebase project.",
        )
    }

    private fun logResolvedConfig(resolved: FcmFirebaseConfig) {
        val credentials = resolved.credentials
        when (resolved.source) {
            FcmFirebaseConfig.Source.GOOGLE_SERVICES ->
                Logging.info(
                    "FCM registration is using this app's google-services.json " +
                        "(project=${credentials.projectId}, sender=${credentials.senderId}).",
                )
            FcmFirebaseConfig.Source.BACKEND ->
                Logging.info(
                    "FCM registration is using Firebase credentials from OneSignal " +
                        "(project=${credentials.projectId}, sender=${credentials.senderId}).",
                )
            FcmFirebaseConfig.Source.SHARED_DEFAULT ->
                Logging.info(
                    "FCM registration is using OneSignal's shared Firebase project " +
                        "(${credentials.projectId} / ${credentials.senderId}).",
                )
        }
    }

    private fun missingProjectMessage(dashboardSenderId: String): String {
        val dashboard = FcmFirebaseConfigResolver.normalizeSender(dashboardSenderId)
        return if (dashboard == null) {
            "Missing Google Project number!\n" +
                "Please enter a Google Project number / Sender ID under App Settings > Android > " +
                "Configuration on the OneSignal dashboard, or add this app's google-services.json " +
                "so FCM registration uses your Firebase project."
        } else {
            "FCM registration cannot mix sender id $dashboard with OneSignal's shared Firebase " +
                "project (${FcmSharedProject.PROJECT_ID} / ${FcmSharedProject.SENDER_ID}). " +
                "Add this app's google-services.json (same sender id as the OneSignal dashboard) " +
                "or complete Android FCM configuration on the dashboard so tokens belong to your project."
        }
    }
}

internal fun FcmProjectCredentials.toFirebaseOptions(): FirebaseOptions {
    return FirebaseOptions
        .Builder()
        .setGcmSenderId(senderId)
        .setApplicationId(applicationId)
        .setApiKey(apiKey)
        .setProjectId(projectId)
        .build()
}

internal fun credentialsMatch(
    options: FirebaseOptions,
    credentials: FcmProjectCredentials,
): Boolean {
    return options.gcmSenderId == credentials.senderId &&
        options.projectId == credentials.projectId &&
        options.applicationId == credentials.applicationId &&
        options.apiKey == credentials.apiKey
}
