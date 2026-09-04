package com.onesignal.notifications.internal.registration.impl

import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

internal class PushRegistratorFCM(
    var _configModelStore: ConfigModelStore,
    val _applicationService: IApplicationService,
    upgradePrompt: GooglePlayServicesUpgradePrompt,
    deviceService: IDeviceService,
) : PushRegistratorAbstractGoogle(deviceService, _configModelStore, upgradePrompt) {
    companion object {
        private const val FCM_APP_NAME = "ONESIGNAL_SDK_FCM_APP_NAME"

        private const val INSTALLATION_ID_ENABLED_METADATA = "firebase_messaging_installation_id_enabled"

        // project_info.project_id
        private const val FCM_DEFAULT_PROJECT_ID = "onesignal-shared-public"

        // client.client_info.mobilesdk_app_id
        private const val FCM_DEFAULT_APP_ID = "1:754795614042:android:c682b8144a8dd52bc1ad63"

        // client.api_key.current_key
        private const val FCM_DEFAULT_API_KEY_BASE64 = "QUl6YVN5QW5UTG41LV80TWMyYTJQLWRLVWVFLWFCdGd5Q3JqbFlV"
    }

    private val projectId: String
    private val appId: String
    private val apiKey: String

    private var firebaseApp: FirebaseApp? = null
    override val providerName: String
        get() = "FCM"

    init {
        val fcpParams = _configModelStore.model.fcmParams

        this.projectId = fcpParams.projectId ?: FCM_DEFAULT_PROJECT_ID
        this.appId = fcpParams.appId ?: FCM_DEFAULT_APP_ID
        val defaultApiKey = String(Base64.decode(FCM_DEFAULT_API_KEY_BASE64, Base64.DEFAULT))
        this.apiKey = fcpParams.apiKey ?: defaultApiKey
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    override suspend fun getToken(senderId: String): String {
        return FCMTokenProvider.getToken(
            senderId = senderId,
            installationIdEnabled = ::installationIdEnabled,
            legacyToken = { getLegacyToken(senderId) },
            installationIdApiAvailable = { FCMTokenProvider.hasRegisterMethod(FirebaseMessaging::class.java) },
            installationIdRegistration = ::defaultAppRegistration,
        )
    }

    private fun getLegacyToken(senderId: String): Task<String> {
        val app = initFirebaseApp(senderId)
        // We use the named app's FirebaseMessaging instance instead of FirebaseMessaging.getInstance()
        //   as the latter uses the default Firebase app. We need to use a custom Firebase app as
        //   the senderId is provided at runtime.
        return app.get(FirebaseMessaging::class.java).token
    }

    // Manifest merging means the flag can arrive from a dependency instead of the app's own
    //   manifest, so report what the app actually resolved to. Read as a raw value because a
    //   string "true" reads as false when asked for a boolean.
    private fun installationIdEnabled(): String {
        val metaData = AndroidUtils.getManifestMetaBundle(_applicationService.appContext)
        return metaData?.get(INSTALLATION_ID_ENABLED_METADATA)?.toString() ?: "not set"
    }

    // Installation ID registration is rejected unless the sender id, app id, and api key all belong
    //   to the same Firebase project. Our own FirebaseApp pairs the app's sender id with OneSignal's
    //   shared project credentials, so only the host app's default FirebaseApp can be used for it.
    private fun defaultAppRegistration(): FCMTokenProvider.InstallationIdRegistration? {
        val defaultApp =
            FirebaseApp
                .getApps(_applicationService.appContext)
                .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: return null
        val defaultSenderId =
            FCMTokenProvider.defaultSenderId(
                defaultApp.options.gcmSenderId,
                defaultApp.options.applicationId,
            )

        return FCMTokenProvider.InstallationIdRegistration(
            senderId = defaultSenderId,
            register = { FCMTokenProvider.invokeRegister(defaultApp.get(FirebaseMessaging::class.java)) },
            installationId = { FirebaseInstallations.getInstance(defaultApp).id },
        )
    }

    private fun initFirebaseApp(senderId: String): FirebaseApp {
        firebaseApp?.let { return it }
        val firebaseOptions =
            FirebaseOptions
                .Builder()
                .setGcmSenderId(senderId)
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .build()
        return FirebaseApp.initializeApp(_applicationService.appContext, firebaseOptions, FCM_APP_NAME)
            .also { firebaseApp = it }
    }
}

internal object FCMTokenProvider {
    fun defaultSenderId(
        senderId: String?,
        applicationId: String,
    ): String? =
        when {
            senderId != null -> senderId
            !applicationId.startsWith("1:") -> applicationId
            else -> applicationId.split(":").getOrNull(1)?.ifEmpty { null }
        }

    /**
     * The Firebase Installation ID registration that replaces the legacy token API, along with the
     * sender id of the Firebase project it would register against.
     */
    class InstallationIdRegistration(
        val senderId: String?,
        val register: () -> Task<*>,
        val installationId: () -> Task<String>,
    )

    /**
     * Retrieves an FCM token for [senderId]. When the host app has opted into Firebase Installation
     * ID registration and that API is available, it is used directly because opting in disables the
     * legacy token API for the whole app, not just the FirebaseApp that opted in.
     */
    fun getToken(
        senderId: String,
        installationIdEnabled: () -> String,
        legacyToken: () -> Task<String>,
        installationIdApiAvailable: () -> Boolean = { false },
        installationIdRegistration: () -> InstallationIdRegistration?,
    ): String {
        val installationIdEnabledValue = installationIdEnabled()
        if (installationIdEnabledValue.equals("true", ignoreCase = true) && installationIdApiAvailable()) {
            return registerInstallationId(senderId, installationIdEnabledValue, installationIdRegistration())
        }

        return try {
            await(legacyToken())
        } catch (e: IllegalStateException) {
            if (!isLegacyTokenApiDisabled(e)) throw e

            registerInstallationId(senderId, installationIdEnabledValue, installationIdRegistration())
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
                    "(google-services.json), or set firebase_messaging_installation_id_enabled to " +
                    "false in your manifest to keep using the legacy FCM token API.",
            )
        }

        if (registration.senderId != senderId) {
            throw IllegalStateException(
                "Firebase Installation ID registration is enabled ($optedIn) but the default " +
                    "FirebaseApp uses sender id ${registration.senderId}, while OneSignal is " +
                    "configured with sender id $senderId. Point both at the same Firebase project, " +
                    "or set firebase_messaging_installation_id_enabled to false in your manifest " +
                    "to keep using the legacy FCM token API.",
            )
        }

        await(registration.register())
        return await(registration.installationId())
    }

    /**
     * Calls register() reflectively. FirebaseMessaging.register was added in firebase-messaging
     * 25.1.0. This module compiles against the preferred 24.0.0, but the non-strict Gradle
     * constraint lets apps select newer versions through conflict resolution.
     */
    fun hasRegisterMethod(targetClass: Class<*>): Boolean =
        try {
            targetClass.getMethod("register")
            true
        } catch (_: NoSuchMethodException) {
            false
        }

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

        // invoke() wraps anything register() throws synchronously, which would hide the cause.
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
