package com.onesignal.notifications.internal.registration.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.mocks.MockHelper
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
        MockHelper.configModelStore(),
        applicationService,
        mockk(relaxed = true),
        mockk(relaxed = true),
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

    test("registers the installation id through the matching default FirebaseApp") {
        val messaging = mockk<FirebaseMessaging>()
        val app = defaultApp(null, messaging)
        val installations = mockk<FirebaseInstallations>()
        every { installations.id } returns Tasks.forResult("installation-id")
        mockkStatic(FirebaseInstallations::class)
        every { FirebaseInstallations.getInstance(app) } returns installations
        mockkObject(FCMTokenProvider)
        every { FCMTokenProvider.invokeRegister(messaging) } returns Tasks.forResult(null)
        val registrator =
            registrator(
                legacyToken = Tasks.forException(disabledLegacyApi),
                installedApps = listOf(app),
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "installation-id"
        verify(exactly = 1) { FCMTokenProvider.invokeRegister(messaging) }
        verify(exactly = 1) { FirebaseInstallations.getInstance(app) }
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
})
