package com.onesignal.notifications.internal.registration.impl

import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import java.util.concurrent.ExecutionException

internal class PushRegistratorFCM(
    var _configModelStore: ConfigModelStore,
    val _applicationService: IApplicationService,
    upgradePrompt: GooglePlayServicesUpgradePrompt,
    deviceService: IDeviceService,
) : PushRegistratorAbstractGoogle(deviceService, _configModelStore, upgradePrompt) {
    companion object {
        private const val FCM_APP_NAME = "ONESIGNAL_SDK_FCM_APP_NAME"

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
        initFirebaseApp(senderId)
        return getTokenWithClassFirebaseMessaging(senderId)
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun getTokenWithClassFirebaseMessaging(senderId: String): String {
        // We use firebaseApp.get(FirebaseMessaging.class) instead of FirebaseMessaging.getInstance()
        //   as the latter uses the default Firebase app. We need to use a custom Firebase app as
        //   the senderId is provided at runtime.
        val fcmInstance = firebaseApp!!.get(FirebaseMessaging::class.java)
        return FCMTokenProvider.getToken(senderId, { fcmInstance.token }, ::defaultAppRegistration)
    }

    // Installation ID registration is rejected unless the sender id, app id, and api key all belong
    //   to the same Firebase project. Our own FirebaseApp pairs the app's sender id with OneSignal's
    //   shared project credentials, so only the host app's default FirebaseApp can be used for it.
    private fun defaultAppRegistration(): FCMTokenProvider.InstallationIdRegistration? {
        val defaultApp =
            FirebaseApp
                .getApps(_applicationService.appContext)
                .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: return null

        return FCMTokenProvider.InstallationIdRegistration(
            senderId = defaultApp.options.gcmSenderId,
            register = { register(defaultApp.get(FirebaseMessaging::class.java)) },
            installationId = { FirebaseInstallations.getInstance(defaultApp).id },
        )
    }

    // FirebaseMessaging.register was added in firebase-messaging:25.1.0, which is newer than the
    //   version this module compiles against.
    private fun register(firebaseMessaging: FirebaseMessaging): Task<*> {
        return firebaseMessaging.javaClass
            .getMethod("register")
            .invoke(firebaseMessaging) as Task<*>
    }

    private fun initFirebaseApp(senderId: String) {
        if (firebaseApp != null) return
        val firebaseOptions =
            FirebaseOptions
                .Builder()
                .setGcmSenderId(senderId)
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .build()
        firebaseApp = FirebaseApp.initializeApp(_applicationService.appContext, firebaseOptions, FCM_APP_NAME)
    }
}

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

    /**
     * Retrieves an FCM token for [senderId], falling back to Firebase Installation ID registration
     * when the host app has opted into it. Opting in disables the legacy token API for the whole
     * app, not just the FirebaseApp that opted in.
     */
    fun getToken(
        senderId: String,
        legacyToken: () -> Task<String>,
        installationIdRegistration: () -> InstallationIdRegistration?,
    ): String {
        return try {
            await(legacyToken())
        } catch (e: IllegalStateException) {
            if (!isLegacyTokenApiDisabled(e)) throw e

            registerInstallationId(senderId, installationIdRegistration())
        }
    }

    private fun registerInstallationId(
        senderId: String,
        registration: InstallationIdRegistration?,
    ): String {
        if (registration == null) {
            throw IllegalStateException(
                "Firebase Installation ID registration is enabled but this app has no default " +
                    "FirebaseApp to register with. Add your Firebase configuration " +
                    "(google-services.json), or remove firebase_messaging_installation_id_enabled " +
                    "from your manifest to keep using the legacy FCM token API.",
            )
        }

        if (registration.senderId != senderId) {
            throw IllegalStateException(
                "Firebase Installation ID registration is enabled but the default FirebaseApp uses " +
                    "sender id ${registration.senderId}, while OneSignal is configured with sender " +
                    "id $senderId. Point both at the same Firebase project, or remove " +
                    "firebase_messaging_installation_id_enabled from your manifest to keep using " +
                    "the legacy FCM token API.",
            )
        }

        await(registration.register())
        return await(registration.installationId())
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
