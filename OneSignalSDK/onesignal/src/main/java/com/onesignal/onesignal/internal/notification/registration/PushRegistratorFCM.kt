package com.onesignal.onesignal.internal.notification.registration

import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.FirebaseOptions
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.device.IDeviceService
import com.onesignal.onesignal.internal.params.IParamsService
import com.onesignal.onesignal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.Error
import java.lang.Exception
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

internal class PushRegistratorFCM(
    var _paramsService: IParamsService,
    val _applicationService: IApplicationService,
    deviceService: IDeviceService) : PushRegistratorAbstractGoogle(deviceService) {

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
        val fcpParams = _paramsService.fcpParams

        this.projectId = fcpParams.projectId ?: FCM_DEFAULT_PROJECT_ID
        this.appId = fcpParams.appId ?: FCM_DEFAULT_APP_ID
        val defaultApiKey = String(Base64.decode(FCM_DEFAULT_API_KEY_BASE64, Base64.DEFAULT))
        this.apiKey = fcpParams.apiKey ?: defaultApiKey
    }

    @Throws(ExecutionException::class, InterruptedException::class, IOException::class)
    override suspend fun getToken(senderId: String): String {
        initFirebaseApp(senderId)
        try {
            return getTokenWithClassFirebaseMessaging()
        } catch (e: NoClassDefFoundError) {
            // Class or method wil be missing at runtime if firebase-message older than 21.0.0 is used.
            Logging.info("FirebaseMessaging.getToken not found, attempting to use FirebaseInstanceId.getToken")
        } catch (e: NoSuchMethodError) {
            Logging.info("FirebaseMessaging.getToken not found, attempting to use FirebaseInstanceId.getToken")
        }

        // Fallback for firebase-message versions older than 21.0.0
        return getTokenWithClassFirebaseInstanceId(senderId)
    }

    // This method is only used if firebase-message older than 21.0.0 is in the app
    // We are using reflection here so we can compile with firebase-message:22.0.0 and newer
    //   - This version of Firebase has completely removed FirebaseInstanceId
    @Deprecated("")
    @Throws(IOException::class)
    private suspend fun getTokenWithClassFirebaseInstanceId(senderId: String): String = coroutineScope {
        var token: String = ""
        // The following code is equivalent to:
        //   FirebaseInstanceId instanceId = FirebaseInstanceId.getInstance(firebaseApp);
        //   return instanceId.getToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE);
        val exception: Exception = try {
            val firebaseInstanceIdClass = Class.forName("com.google.firebase.iid.FirebaseInstanceId")
            val getInstanceMethod = firebaseInstanceIdClass.getMethod("getInstance", FirebaseApp::class.java)
            val instanceId = getInstanceMethod.invoke(null, firebaseApp)
            val getTokenMethod = instanceId.javaClass.getMethod("getToken", String::class.java, String::class.java)

            launch(Dispatchers.Default) {
                val tkn = getTokenMethod.invoke(instanceId, senderId, "FCM")
                token = tkn as String
            }

            return@coroutineScope token
        } catch (e: ClassNotFoundException) {
            e
        } catch (e: NoSuchMethodException) {
            e
        } catch (e: IllegalAccessException) {
            e
        } catch (e: InvocationTargetException) {
            e
        }

        throw Error(
            "Reflection error on FirebaseInstanceId.getInstance(firebaseApp).getToken(senderId, FirebaseMessaging.INSTANCE_ID_SCOPE)",
            exception)
    }

    // We use firebaseApp.get(FirebaseMessaging.class) instead of FirebaseMessaging.getInstance()
    //   as the latter uses the default Firebase app. We need to use a custom Firebase app as
    //   the senderId is provided at runtime.
    @Throws(ExecutionException::class, InterruptedException::class)
    private suspend fun getTokenWithClassFirebaseMessaging() : String = coroutineScope {
        var token: String = ""
        launch(Dispatchers.Default) {
            // FirebaseMessaging.getToken API was introduced in firebase-messaging:21.0.0
            // We use firebaseApp.get(FirebaseMessaging.class) instead of FirebaseMessaging.getInstance()
            //   as the latter uses the default Firebase app. We need to use a custom Firebase app as
            //   the senderId is provided at runtime.
            val fcmInstance = firebaseApp!!.get(FirebaseMessaging::class.java)
            // FirebaseMessaging.getToken API was introduced in firebase-messaging:21.0.0
            val tokenTask = fcmInstance.token
            token = Tasks.await(tokenTask)
        }

        return@coroutineScope token
    }

    private fun initFirebaseApp(senderId: String) {
        if (firebaseApp != null) return
        val firebaseOptions = FirebaseOptions.Builder()
            .setGcmSenderId(senderId)
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .build()
        firebaseApp = FirebaseApp.initializeApp(_applicationService.appContext!!, firebaseOptions, FCM_APP_NAME)
    }
}