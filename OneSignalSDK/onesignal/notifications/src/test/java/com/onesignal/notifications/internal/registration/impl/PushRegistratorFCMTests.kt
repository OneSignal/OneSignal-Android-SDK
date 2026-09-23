package com.onesignal.notifications.internal.registration.impl

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
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
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.IOException

private const val SENDER_ID = "123456789012"

private fun defaultApp(
    senderId: String?,
    messaging: FirebaseMessaging = mockk(),
): FirebaseApp {
    val options = mockk<FirebaseOptions>()
    every { options.gcmSenderId } returns senderId
    every { options.applicationId } returns "1:$SENDER_ID:android:abc"

    val app = mockk<FirebaseApp>()
    every { app.name } returns FirebaseApp.DEFAULT_APP_NAME
    every { app.options } returns options
    every { app.get(FirebaseMessaging::class.java) } returns messaging

    return app
}

private fun registrator(
    legacyToken: Task<String>,
    installedApps: List<FirebaseApp> = emptyList(),
    configModelStore: ConfigModelStore = MockHelper.configModelStore(),
    deviceService: IDeviceService = mockk(relaxed = true),
): PushRegistratorFCM {
    val messaging = mockk<FirebaseMessaging>()
    every { messaging.token } returns legacyToken

    val onesignalApp = mockk<FirebaseApp>()
    every { onesignalApp.get(FirebaseMessaging::class.java) } returns messaging

    mockkStatic(FirebaseApp::class)
    every { FirebaseApp.initializeApp(any(), any<FirebaseOptions>(), any()) } returns onesignalApp
    every { FirebaseApp.getApps(any()) } returns installedApps

    val applicationService = mockk<IApplicationService>()
    every { applicationService.appContext } returns ApplicationProvider.getApplicationContext<Context>()

    return PushRegistratorFCM(
        configModelStore,
        applicationService,
        mockk(relaxed = true),
        deviceService,
    )
}

private class FidRegistration(
    val registrator: PushRegistratorFCM,
    val messaging: FirebaseMessaging,
)

private data class FidFailureCase(
    val failure: Exception,
    val expectedStatus: SubscriptionStatus,
    val expectedAttempts: Int,
)

private fun fidRegistration(
    registerResult: Task<Void>,
    installationId: Task<String> = Tasks.forResult("installation-id"),
): FidRegistration {
    val metaData = Bundle().apply { putBoolean("firebase_messaging_installation_id_enabled", true) }
    mockkObject(AndroidUtils)
    every { AndroidUtils.getManifestMetaBundle(any()) } returns metaData
    val messaging = mockk<FirebaseMessaging>()
    val app = defaultApp(SENDER_ID, messaging)
    val installations = mockk<FirebaseInstallations>()
    every { installations.id } returns installationId
    mockkStatic(FirebaseInstallations::class)
    every { FirebaseInstallations.getInstance(app) } returns installations
    mockkObject(FCMTokenProvider)
    every { FCMTokenProvider.hasRegisterMethod(FirebaseMessaging::class.java) } returns true
    every { FCMTokenProvider.invokeRegister(messaging) } returns registerResult
    val configModelStore =
        MockHelper.configModelStore {
            it.isInitializedWithRemote = true
            it.googleProjectNumber = SENDER_ID
        }
    val deviceService = mockk<IDeviceService>()
    every { deviceService.hasFCMLibrary } returns true
    every { deviceService.isGMSInstalledAndEnabled } returns true
    val registrator =
        registrator(
            legacyToken = Tasks.forResult("unused-fcm-token"),
            installedApps = listOf(app),
            configModelStore = configModelStore,
            deviceService = deviceService,
        )
    return FidRegistration(registrator, messaging)
}

// Tasks.await rejects the main thread, and runTest skips the registrator's retry backoff delays.
private suspend fun registerSkippingBackoff(registrator: PushRegistratorFCM): IPushRegistrator.RegisterResult =
    withContext(Dispatchers.IO) {
        lateinit var result: IPushRegistrator.RegisterResult
        runTest { result = registrator.registerForPush() }
        result
    }

@RobolectricTest
class PushRegistratorFCMTests : FunSpec({
    val disabledLegacyApi = IllegalStateException("API disabled. Please use {@link #register()} instead.")

    afterEach { unmockkAll() }

    test("returns the FCM token from OneSignal's own FirebaseApp") {
        val registrator = registrator(legacyToken = Tasks.forResult("fcm-token"))

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "fcm-token"
    }

    test("recreates OneSignal's FirebaseApp when the sender id changes") {
        val firstMessaging = mockk<FirebaseMessaging>()
        every { firstMessaging.token } returns Tasks.forResult("first-token")
        val firstApp = mockk<FirebaseApp>(relaxed = true)
        every { firstApp.get(FirebaseMessaging::class.java) } returns firstMessaging
        val secondMessaging = mockk<FirebaseMessaging>()
        every { secondMessaging.token } returns Tasks.forResult("second-token")
        val secondApp = mockk<FirebaseApp>(relaxed = true)
        every { secondApp.get(FirebaseMessaging::class.java) } returns secondMessaging
        val initializedOptions = mutableListOf<FirebaseOptions>()
        val registrator = registrator(legacyToken = Tasks.forResult("unused-token"))
        every {
            FirebaseApp.initializeApp(any(), capture(initializedOptions), any())
        } returnsMany listOf(firstApp, secondApp)

        val firstToken = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }
        val secondToken = withContext(Dispatchers.IO) { registrator.getToken("999999999999") }

        firstToken shouldBe "first-token"
        secondToken shouldBe "second-token"
        initializedOptions.map { it.gcmSenderId } shouldBe listOf(SENDER_ID, "999999999999")
        verify(exactly = 1) { firstApp.delete() }
    }

    test("registers a legacy FCM token using the locally derived sender id before the dashboard provides one") {
        val app = defaultApp(null)
        val configModelStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = null
            }
        val deviceService = mockk<IDeviceService>()
        every { deviceService.hasFCMLibrary } returns true
        every { deviceService.isGMSInstalledAndEnabled } returns true
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("fcm-token"),
                installedApps = listOf(app),
                configModelStore = configModelStore,
                deviceService = deviceService,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe "fcm-token"
        result.status shouldBe SubscriptionStatus.SUBSCRIBED
        verify(exactly = 1) { FirebaseApp.initializeApp(any(), any<FirebaseOptions>(), any()) }
    }

    test("registers the installation id through the matching default FirebaseApp") {
        val metaData = Bundle().apply { putBoolean("firebase_messaging_installation_id_enabled", true) }
        mockkObject(AndroidUtils)
        every { AndroidUtils.getManifestMetaBundle(any()) } returns metaData
        val messaging = mockk<FirebaseMessaging>()
        val app = defaultApp(null, messaging)
        val installations = mockk<FirebaseInstallations>()
        every { installations.id } returns Tasks.forResult("installation-id")
        mockkStatic(FirebaseInstallations::class)
        every { FirebaseInstallations.getInstance(app) } returns installations
        mockkObject(FCMTokenProvider)
        every { FCMTokenProvider.hasRegisterMethod(FirebaseMessaging::class.java) } returns true
        every { FCMTokenProvider.invokeRegister(messaging) } returns Tasks.forResult(null)
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("unused-fcm-token"),
                installedApps = listOf(app),
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "installation-id"
        verify(exactly = 1) { FCMTokenProvider.invokeRegister(messaging) }
        verify(exactly = 1) { FirebaseInstallations.getInstance(app) }
        verify(exactly = 0) { FirebaseApp.initializeApp(any(), any<FirebaseOptions>(), any()) }
    }

    test("uses the legacy token when the manifest flag is a string true") {
        val metaData = Bundle().apply { putString("firebase_messaging_installation_id_enabled", "true") }
        mockkObject(AndroidUtils)
        every { AndroidUtils.getManifestMetaBundle(any()) } returns metaData
        mockkObject(FCMTokenProvider)
        every { FCMTokenProvider.hasRegisterMethod(FirebaseMessaging::class.java) } returns true
        val registrator = registrator(legacyToken = Tasks.forResult("fcm-token"))

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "fcm-token"
        verify(exactly = 1) { FirebaseApp.initializeApp(any(), any<FirebaseOptions>(), any()) }
    }

    test("registers an installation id before the dashboard has a sender id") {
        val metaData = Bundle().apply { putBoolean("firebase_messaging_installation_id_enabled", true) }
        mockkObject(AndroidUtils)
        every { AndroidUtils.getManifestMetaBundle(any()) } returns metaData
        val messaging = mockk<FirebaseMessaging>()
        val app = defaultApp(null, messaging)
        val installations = mockk<FirebaseInstallations>()
        every { installations.id } returns Tasks.forResult("installation-id")
        mockkStatic(FirebaseInstallations::class)
        every { FirebaseInstallations.getInstance(app) } returns installations
        mockkObject(FCMTokenProvider)
        every { FCMTokenProvider.hasRegisterMethod(FirebaseMessaging::class.java) } returns true
        every { FCMTokenProvider.invokeRegister(messaging) } returns Tasks.forResult(null)
        val configModelStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = null
            }
        val deviceService = mockk<IDeviceService>()
        every { deviceService.hasFCMLibrary } returns true
        every { deviceService.isGMSInstalledAndEnabled } returns true
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("unused-fcm-token"),
                installedApps = listOf(app),
                configModelStore = configModelStore,
                deviceService = deviceService,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe "installation-id"
        result.status shouldBe SubscriptionStatus.SUBSCRIBED
    }

    test("explains the problem when the app has no default FirebaseApp to register with") {
        val registrator = registrator(legacyToken = Tasks.forException(disabledLegacyApi))

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<FCMInstallationIdException> { registrator.getToken(SENDER_ID) }
            }

        thrown.message!! shouldContain "no default FirebaseApp"
        thrown.message!! shouldContain "firebase_messaging_installation_id_enabled=not set"
        thrown.message!! shouldContain "com.google.gms.google-services"
        thrown.message!! shouldContain "manifest merging"
    }

    test("reports a missing default Firebase app through the push registration status") {
        val configModelStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = SENDER_ID
            }
        val deviceService = mockk<IDeviceService>()
        every { deviceService.hasFCMLibrary } returns true
        every { deviceService.isGMSInstalledAndEnabled } returns true
        val registrator =
            registrator(
                legacyToken = Tasks.forException(disabledLegacyApi),
                configModelStore = configModelStore,
                deviceService = deviceService,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe null
        result.status shouldBe SubscriptionStatus.FIREBASE_FCM_FID_DEFAULT_APP_MISSING
        result.isExistingTokenInvalid shouldBe false
    }

    test("reports an unavailable FID register API through the push registration status") {
        val metaData = Bundle().apply { putBoolean("firebase_messaging_installation_id_enabled", true) }
        mockkObject(AndroidUtils)
        every { AndroidUtils.getManifestMetaBundle(any()) } returns metaData
        val app = defaultApp(SENDER_ID)
        mockkObject(FCMTokenProvider)
        every { FCMTokenProvider.hasRegisterMethod(FirebaseMessaging::class.java) } returns false
        val configModelStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = SENDER_ID
            }
        val deviceService = mockk<IDeviceService>()
        every { deviceService.hasFCMLibrary } returns true
        every { deviceService.isGMSInstalledAndEnabled } returns true
        val registrator =
            registrator(
                legacyToken = Tasks.forException(disabledLegacyApi),
                installedApps = listOf(app),
                configModelStore = configModelStore,
                deviceService = deviceService,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe null
        result.status shouldBe SubscriptionStatus.FIREBASE_FCM_FID_REGISTER_API_UNAVAILABLE
        result.isExistingTokenInvalid shouldBe false
    }

    test("reports a generic FID runtime failure through the push registration status") {
        val metaData = Bundle().apply { putBoolean("firebase_messaging_installation_id_enabled", true) }
        mockkObject(AndroidUtils)
        every { AndroidUtils.getManifestMetaBundle(any()) } returns metaData
        val messaging = mockk<FirebaseMessaging>()
        val app = defaultApp(SENDER_ID, messaging)
        mockkObject(FCMTokenProvider)
        every { FCMTokenProvider.hasRegisterMethod(FirebaseMessaging::class.java) } returns true
        every {
            FCMTokenProvider.invokeRegister(messaging)
        } returns Tasks.forException<Void>(IllegalStateException("registration failed"))
        val configModelStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = SENDER_ID
            }
        val deviceService = mockk<IDeviceService>()
        every { deviceService.hasFCMLibrary } returns true
        every { deviceService.isGMSInstalledAndEnabled } returns true
        val registrator =
            registrator(
                legacyToken = Tasks.forException(disabledLegacyApi),
                installedApps = listOf(app),
                configModelStore = configModelStore,
                deviceService = deviceService,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe null
        result.status shouldBe SubscriptionStatus.FIREBASE_FCM_FID_REGISTRATION_FAILED
        result.isExistingTokenInvalid shouldBe false
    }

    listOf(
        FidFailureCase(
            IOException("SERVICE_NOT_AVAILABLE"),
            SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE,
            expectedAttempts = 3,
        ),
        FidFailureCase(
            IOException("AUTHENTICATION_FAILED"),
            SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED,
            expectedAttempts = 3,
        ),
        FidFailureCase(
            IOException("TOO_MANY_REGISTRATIONS"),
            SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER,
            expectedAttempts = 1,
        ),
        FidFailureCase(
            IllegalStateException("registration failed"),
            SubscriptionStatus.FIREBASE_FCM_FID_REGISTRATION_FAILED,
            expectedAttempts = 1,
        ),
    ).forEach { (failure, expectedStatus, expectedAttempts) ->
        test("maps a FID register failure of ${failure.javaClass.simpleName}(${failure.message}) to $expectedStatus") {
            val fid = fidRegistration(registerResult = Tasks.forException(failure))

            val result = registerSkippingBackoff(fid.registrator)

            result.id shouldBe null
            result.status shouldBe expectedStatus
            result.isExistingTokenInvalid shouldBe false
            verify(exactly = expectedAttempts) { FCMTokenProvider.invokeRegister(fid.messaging) }
            verify(exactly = 0) { FirebaseApp.initializeApp(any(), any<FirebaseOptions>(), any()) }
        }
    }

    test("maps an IOException from FID retrieval to the IOException status instead of a FID registration failure") {
        val fid =
            fidRegistration(
                registerResult = Tasks.forResult(null),
                installationId = Tasks.forException(IOException("INTERNAL_SERVER_ERROR")),
            )

        val result = registerSkippingBackoff(fid.registrator)

        result.id shouldBe null
        result.status shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER
        result.isExistingTokenInvalid shouldBe false
        verify(exactly = 1) { FCMTokenProvider.invokeRegister(fid.messaging) }
    }

    test("reports an invalid sender id when FID registration would use a different Firebase project") {
        val configModelStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = SENDER_ID
            }
        val deviceService = mockk<IDeviceService>()
        every { deviceService.hasFCMLibrary } returns true
        every { deviceService.isGMSInstalledAndEnabled } returns true
        val registrator =
            registrator(
                legacyToken = Tasks.forException(disabledLegacyApi),
                installedApps = listOf(defaultApp("999999999999")),
                configModelStore = configModelStore,
                deviceService = deviceService,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe null
        result.status shouldBe SubscriptionStatus.INVALID_FCM_SENDER_ID
        result.isExistingTokenInvalid shouldBe true
    }

    test("uses OneSignal's FirebaseApp for a legacy token when the default app has a different sender id") {
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.token } returns Tasks.forResult("wrong-project-token")
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("fcm-token"),
                installedApps = listOf(defaultApp("999999999999", messaging)),
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "fcm-token"
        verify(exactly = 0) { messaging.token }
        verify(exactly = 1) { FirebaseApp.initializeApp(any(), any<FirebaseOptions>(), any()) }
    }
})
