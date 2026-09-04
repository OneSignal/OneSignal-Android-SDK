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
import kotlinx.coroutines.withContext

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

@RobolectricTest
class PushRegistratorFCMTests : FunSpec({
    val disabledLegacyApi = IllegalStateException("API disabled. Please use {@link #register()} instead.")

    afterEach { unmockkAll() }

    test("returns the FCM token from OneSignal's own FirebaseApp") {
        val registrator = registrator(legacyToken = Tasks.forResult("fcm-token"))

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "fcm-token"
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
                shouldThrow<IllegalStateException> { registrator.getToken(SENDER_ID) }
            }

        thrown.message!! shouldContain "no default FirebaseApp"
        thrown.message!! shouldContain "firebase_messaging_installation_id_enabled=not set"
    }

    test("does not register against a default FirebaseApp with a different sender id") {
        val registrator =
            registrator(
                legacyToken = Tasks.forException(disabledLegacyApi),
                installedApps = listOf(defaultApp("999999999999")),
            )

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> { registrator.getToken(SENDER_ID) }
            }

        thrown.message!! shouldContain "sender id 999999999999"
    }

    test("does not request a legacy token from a default FirebaseApp with a different sender id") {
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.token } returns Tasks.forResult("wrong-project-token")
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("unused-fcm-token"),
                installedApps = listOf(defaultApp("999999999999", messaging)),
            )

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> { registrator.getToken(SENDER_ID) }
            }

        thrown.message!! shouldContain "sender id 999999999999"
        verify(exactly = 0) { messaging.token }
    }
})
