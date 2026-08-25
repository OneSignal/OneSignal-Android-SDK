package com.onesignal.notifications.internal.registration.impl

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.common.AndroidUtils
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

        private const val INSTALLATION_ID_ENABLED_METADATA = "firebase_messaging_installation_id_enabled"

        // project_info.project_id from OneSignal's shared public google-services.json
        private const val FCM_DEFAULT_PROJECT_ID = "onesignal-shared-public"

        // client.client_info.mobilesdk_app_id
        private const val FCM_DEFAULT_APP_ID = "1:754795614042:android:c682b8144a8dd52bc1ad63"

        // client.api_key.current_key
        private const val FCM_DEFAULT_API_KEY_BASE64 = "QUl6YVN5QW5UTG41LV80TWMyYTJQLWRLVWVFLWFCdGd5Q3JqbFlV"

        /**
         * The host app's default Firebase app, created from `google-services.json` when present.
         *
         * [FirebaseApp.initializeApp] with only a [Context] reads the string resources the
         * google-services Gradle plugin generated (`google_app_id`, `gcm_defaultSenderId`,
         * `google_api_key`, `project_id`). If the default app is already initialized it is
         * returned; if those resources are missing it returns null.
         */
        internal fun hostDefaultFirebaseApp(context: Context): FirebaseApp? {
            return try {
                FirebaseApp.initializeApp(context)
            } catch (e: IllegalStateException) {
                Logging.debug("Unable to initialize the default FirebaseApp from google-services.json", e)
                FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            }
        }

        internal fun credentialsFrom(options: FirebaseOptions?): FcmProjectCredentials? {
            return options?.let(::fromOptions)
        }

        private fun fromOptions(options: FirebaseOptions): FcmProjectCredentials? {
            val credentials =
                FcmProjectCredentials(
                    senderId = options.gcmSenderId.orEmpty(),
                    projectId = options.projectId.orEmpty(),
                    applicationId = options.applicationId,
                    apiKey = options.apiKey,
                )
            return credentials.takeIf { it.isComplete }
        }

        internal fun sharedDefaultCredentials(senderId: String): FcmProjectCredentials {
            val defaultApiKey = String(Base64.decode(FCM_DEFAULT_API_KEY_BASE64, Base64.DEFAULT))
            return FcmProjectCredentials(
                senderId = senderId,
                projectId = FCM_DEFAULT_PROJECT_ID,
                applicationId = FCM_DEFAULT_APP_ID,
                apiKey = defaultApiKey,
            )
        }
    }

    private var firebaseApp: FirebaseApp? = null
    private var resolvedSource: FcmFirebaseConfig.Source? = null

    override val providerName: String
        get() = "FCM"

    @Throws(ExecutionException::class, InterruptedException::class)
    override suspend fun getToken(senderId: String): String {
        initFirebaseApp(senderId)
        return getTokenWithClassFirebaseMessaging(senderId)
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun getTokenWithClassFirebaseMessaging(senderId: String): String {
        val fcmInstance = firebaseApp!!.get(FirebaseMessaging::class.java)
        return FCMTokenProvider.getToken(
            senderId,
            ::installationIdEnabled,
            { fcmInstance.token },
            ::installationIdRegistration,
        )
    }

    // Manifest merging means the flag can arrive from a dependency instead of the app's own
    //   manifest, so report what the app actually resolved to. Read as a raw value because a
    //   string "true" reads as false when asked for a boolean.
    private fun installationIdEnabled(): String {
        val metaData = AndroidUtils.getManifestMetaBundle(_applicationService.appContext)
        return metaData?.get(INSTALLATION_ID_ENABLED_METADATA)?.toString() ?: "not set"
    }

    private fun installationIdRegistration(): FCMTokenProvider.InstallationIdRegistration? {
        val hostApp = firebaseAppForInstallationId() ?: return null
        return FCMTokenProvider.InstallationIdRegistration(
            senderId = hostApp.options.gcmSenderId,
            register = { FCMTokenProvider.invokeRegister(hostApp.get(FirebaseMessaging::class.java)) },
            installationId = { FirebaseInstallations.getInstance(hostApp).id },
        )
    }

    private fun firebaseAppForInstallationId(): FirebaseApp? {
        // Installation IDs are issued per Firebase project. The shared OneSignal project cannot
        // mint a usable one for a customer app, so only reuse the FirebaseApp we resolved when it
        // came from google-services.json or complete backend params.
        val current = firebaseApp
        if (current != null && resolvedSource != FcmFirebaseConfig.Source.SHARED_DEFAULT) {
            return current
        }
        return defaultFirebaseApp(_applicationService.appContext)
    }

    private fun initFirebaseApp(senderId: String) {
        if (firebaseApp != null) return

        val context = _applicationService.appContext
        val hostApp = defaultFirebaseApp(context)
        val hostCredentials = credentialsFrom(hostApp?.options)
        logIfHostSenderMismatches(senderId, hostCredentials)

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = senderId,
                defaultApp = hostCredentials,
                backend = backendCredentials(senderId),
                sharedDefault = sharedDefaultCredentials(senderId),
            )
        resolvedSource = resolved.source
        logResolvedConfig(resolved)

        if (resolved.reuseDefaultApp && hostApp != null) {
            firebaseApp = hostApp
            return
        }

        val firebaseOptions =
            FirebaseOptions
                .Builder()
                .setGcmSenderId(resolved.credentials.senderId)
                .setApplicationId(resolved.credentials.applicationId)
                .setApiKey(resolved.credentials.apiKey)
                .setProjectId(resolved.credentials.projectId)
                .build()
        firebaseApp = FirebaseApp.initializeApp(context, firebaseOptions, FCM_APP_NAME)
    }

    private fun backendCredentials(senderId: String): FcmProjectCredentials? {
        val fcmParams = _configModelStore.model.fcmParams
        val projectId = fcmParams.projectId
        val appId = fcmParams.appId
        val apiKey = fcmParams.apiKey
        if (projectId.isNullOrBlank() || appId.isNullOrBlank() || apiKey.isNullOrBlank()) {
            return null
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
        if (hostCredentials == null || hostCredentials.senderId == dashboardSenderId) return
        Logging.warn(
            "google-services.json sender id ${hostCredentials.senderId} does not match the " +
                "OneSignal dashboard sender id $dashboardSenderId. FCM will not use the host " +
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
                        "(${credentials.projectId} / ${credentials.senderId}). Add your app's " +
                        "google-services.json and apply the google-services Gradle plugin so " +
                        "registration uses your own Firebase project. Google now requires this " +
                        "for Firebase Installation ID registration.",
                )
        }
    }
}
