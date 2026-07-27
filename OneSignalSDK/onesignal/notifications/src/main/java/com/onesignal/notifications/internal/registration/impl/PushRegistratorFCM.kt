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
        return getTokenWithClassFirebaseMessaging()
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun getTokenWithClassFirebaseMessaging(): String {
        // We use firebaseApp.get(FirebaseMessaging.class) instead of FirebaseMessaging.getInstance()
        //   as the latter uses the default Firebase app. We need to use a custom Firebase app as
        //   the senderId is provided at runtime.
        val fcmInstance = firebaseApp!!.get(FirebaseMessaging::class.java)
        return FirebaseTokenProvider(
            legacyTokenTask = fcmInstance.token,
            // Reflection keeps firebase-messaging 23.x and 24.x binary-compatible.
            registerForFid = {
                @Suppress("UNCHECKED_CAST")
                fcmInstance.javaClass.getMethod("register").invoke(fcmInstance) as Task<Void>
            },
            installationIdTask = { FirebaseInstallations.getInstance(firebaseApp!!).id },
        ).getToken()
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

internal class FirebaseTokenProvider(
    private val legacyTokenTask: Task<String>,
    private val registerForFid: () -> Task<Void>,
    private val installationIdTask: () -> Task<String>,
) {
    fun getToken(): String {
        try {
            return await(legacyTokenTask)
        } catch (e: IllegalStateException) {
            if (!e.message.orEmpty().startsWith(FID_REGISTRATION_REQUIRED_ERROR)) {
                throw e
            }
        }

        await(registerForFid())
        return await(installationIdTask())
    }

    private fun <T> await(task: Task<T>): T {
        try {
            return Tasks.await(task)
        } catch (e: ExecutionException) {
            throw task.exception ?: e
        }
    }

    private companion object {
        const val FID_REGISTRATION_REQUIRED_ERROR = "API disabled. Please use"
    }
}
