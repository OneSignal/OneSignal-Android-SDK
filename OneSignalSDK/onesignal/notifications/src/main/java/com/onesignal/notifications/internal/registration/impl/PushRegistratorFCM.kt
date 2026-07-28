package com.onesignal.notifications.internal.registration.impl

import android.content.pm.PackageManager
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
        private const val FID_REGISTRATION_ENABLED = "firebase_messaging_installation_id_enabled"
        private const val GMS_PACKAGE_NAME = "com.google.android.gms"

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
    private val hasBackendFcmCredentials: Boolean

    private var firebaseApp: FirebaseApp? = null
    override val providerName: String
        get() = "FCM"

    init {
        val fcpParams = _configModelStore.model.fcmParams

        this.projectId = fcpParams.projectId ?: FCM_DEFAULT_PROJECT_ID
        this.appId = fcpParams.appId ?: FCM_DEFAULT_APP_ID
        val defaultApiKey = String(Base64.decode(FCM_DEFAULT_API_KEY_BASE64, Base64.DEFAULT))
        this.apiKey = fcpParams.apiKey ?: defaultApiKey
        this.hasBackendFcmCredentials =
            fcpParams.projectId != null && fcpParams.appId != null && fcpParams.apiKey != null
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
        val registerMethod =
            fcmInstance.javaClass.methods.firstOrNull {
                it.name == "register" && it.parameterTypes.isEmpty()
            }
        val fidRegistrationEnabled = registerMethod != null && isFidRegistrationEnabled()
        if (fidRegistrationEnabled) {
            // FirebaseMessaging.getToken() is rejected while the manifest flag is set, so there is
            // no legacy path left to fall back to when FID registration cannot be trusted.
            fidRegistrationBlocker(senderId, appId, hasBackendFcmCredentials, gmsVersionCode())?.let {
                throw IllegalStateException("$FID_REGISTRATION_ENABLED is enabled in the manifest, but $it.")
            }
        }

        return FirebaseTokenProvider(
            fidRegistrationEnabled = fidRegistrationEnabled,
            legacyTokenTask = { fcmInstance.token },
            // Reflection keeps firebase-messaging 23.x and 24.x binary-compatible.
            registerForFid = {
                @Suppress("UNCHECKED_CAST")
                registerMethod!!.invoke(fcmInstance) as Task<Void>
            },
            installationIdTask = { FirebaseInstallations.getInstance(firebaseApp!!).id },
        ).getToken()
    }

    private fun isFidRegistrationEnabled(): Boolean {
        val context = _applicationService.appContext
        return try {
            val applicationInfo =
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA,
                )
            applicationInfo.metaData?.getBoolean(FID_REGISTRATION_ENABLED, false) ?: false
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun gmsVersionCode(): Int {
        val context = _applicationService.appContext
        return try {
            context.packageManager.getPackageInfo(GMS_PACKAGE_NAME, 0).versionCode
        } catch (_: PackageManager.NameNotFoundException) {
            0
        }
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

/**
 * firebase-messaging only performs FID registration on this Play Services build or newer. Below it
 * `register()` quietly registers a legacy token instead, which no public API exposes.
 */
private const val MIN_GMS_VERSION_FOR_FID = 261200000

/**
 * FID registration mints an installation ID against the Firebase project identified by [gmpAppId]
 * and then registers it under [senderId], so both must describe the same project. Returns null when
 * registration can be trusted, otherwise the reason it cannot.
 */
internal fun fidRegistrationBlocker(
    senderId: String,
    gmpAppId: String,
    hasBackendFcmCredentials: Boolean,
    gmsVersionCode: Int,
): String? {
    // A v1 app ID is "1:<projectNumber>:android:<hash>", and the project number is the sender ID.
    if (!hasBackendFcmCredentials || gmpAppId.split(':').getOrNull(1) != senderId) {
        return "the FCM credentials in use do not belong to the Firebase project for sender ID " +
            "$senderId. Add your Firebase service account under App Settings > Android on the " +
            "OneSignal dashboard, or remove the manifest flag"
    }

    if (gmsVersionCode < MIN_GMS_VERSION_FOR_FID) {
        return "'Google Play services' $gmsVersionCode predates $MIN_GMS_VERSION_FOR_FID and would " +
            "register a token this SDK cannot read. Update 'Google Play services', or remove the manifest flag"
    }

    return null
}

internal class FirebaseTokenProvider(
    private val fidRegistrationEnabled: Boolean,
    private val legacyTokenTask: () -> Task<String>,
    private val registerForFid: () -> Task<Void>,
    private val installationIdTask: () -> Task<String>,
) {
    fun getToken(): String {
        if (fidRegistrationEnabled) {
            await(registerForFid())
            return await(installationIdTask())
        }

        return await(legacyTokenTask())
    }

    private fun <T> await(task: Task<T>): T {
        try {
            return Tasks.await(task)
        } catch (e: ExecutionException) {
            throw task.exception ?: e
        }
    }
}
