package com.onesignal.notifications.internal.registration.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SENDER_ID = "388536902528"
private const val HOST_PROJECT_ID = "customer-project"
private const val HOST_APP_ID = "1:388536902528:android:abc"
private const val HOST_API_KEY = "AIza-customer"

private fun firebaseOptions(
    senderId: String = SENDER_ID,
    projectId: String = HOST_PROJECT_ID,
    applicationId: String = HOST_APP_ID,
    apiKey: String = HOST_API_KEY,
): FirebaseOptions {
    return FirebaseOptions
        .Builder()
        .setGcmSenderId(senderId)
        .setProjectId(projectId)
        .setApplicationId(applicationId)
        .setApiKey(apiKey)
        .build()
}

private fun defaultApp(
    senderId: String = SENDER_ID,
    messaging: FirebaseMessaging? = null,
): FirebaseApp {
    val app = mockk<FirebaseApp>()
    every { app.name } returns FirebaseApp.DEFAULT_APP_NAME
    every { app.options } returns firebaseOptions(senderId = senderId)
    if (messaging != null) {
        every { app.get(FirebaseMessaging::class.java) } returns messaging
    }
    return app
}

private fun registrator(
    legacyToken: Task<String>,
    hostApp: FirebaseApp? = null,
    captureNamedOptions: CapturingSlot<FirebaseOptions>? = null,
): PushRegistratorFCM {
    val messaging = mockk<FirebaseMessaging>()
    every { messaging.token } returns legacyToken

    val namedApp = mockk<FirebaseApp>()
    every { namedApp.get(FirebaseMessaging::class.java) } returns messaging
    every { namedApp.options } returns firebaseOptions()

    mockkStatic(FirebaseApp::class)
    if (captureNamedOptions != null) {
        every {
            FirebaseApp.initializeApp(any<Context>(), capture(captureNamedOptions), any<String>())
        } returns namedApp
    } else {
        every {
            FirebaseApp.initializeApp(any<Context>(), any<FirebaseOptions>(), any<String>())
        } returns namedApp
    }

    val applicationService = mockk<IApplicationService>()
    every { applicationService.appContext } returns ApplicationProvider.getApplicationContext<Context>()

    return PushRegistratorFCM(
        MockHelper.configModelStore(),
        applicationService,
        mockk(relaxed = true),
        mockk(relaxed = true),
        defaultFirebaseApp = { hostApp },
    )
}

@RobolectricTest
class PushRegistratorFCMTests : FunSpec({
    val disabledLegacyApi = IllegalStateException("API disabled. Please use {@link #register()} instead.")

    beforeEach {
        Logging.logLevel = LogLevel.NONE
    }

    afterEach { unmockkAll() }

    test("reuses the host FirebaseApp when google-services.json matches the dashboard sender id") {
        val hostMessaging = mockk<FirebaseMessaging>()
        every { hostMessaging.token } returns Tasks.forResult("host-token")
        val hostApp = defaultApp(messaging = hostMessaging)
        val registrator = registrator(legacyToken = Tasks.forResult("unused"), hostApp = hostApp)

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "host-token"
    }

    test("initializes a named FirebaseApp from backend FCM params when google-services.json is absent") {
        val optionsSlot = slot<FirebaseOptions>()
        val configStore =
            MockHelper.configModelStore {
                it.fcmParams.projectId = "backend-project"
                it.fcmParams.appId = "1:388536902528:android:backend"
                it.fcmParams.apiKey = "AIza-backend"
            }
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.token } returns Tasks.forResult("backend-token")
        val namedApp = mockk<FirebaseApp>()
        every { namedApp.get(FirebaseMessaging::class.java) } returns messaging

        mockkStatic(FirebaseApp::class)
        every {
            FirebaseApp.initializeApp(any<Context>(), capture(optionsSlot), any<String>())
        } returns namedApp

        val applicationService = mockk<IApplicationService>()
        every { applicationService.appContext } returns ApplicationProvider.getApplicationContext<Context>()

        val registrator =
            PushRegistratorFCM(
                configStore,
                applicationService,
                mockk(relaxed = true),
                mockk(relaxed = true),
                defaultFirebaseApp = { null },
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "backend-token"
        optionsSlot.captured.projectId shouldBe "backend-project"
        optionsSlot.captured.gcmSenderId shouldBe SENDER_ID
        optionsSlot.captured.applicationId shouldBe "1:388536902528:android:backend"
        optionsSlot.captured.apiKey shouldBe "AIza-backend"
    }

    test("falls back to OneSignal's shared Firebase project when no host or backend credentials exist") {
        val optionsSlot = slot<FirebaseOptions>()
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("shared-token"),
                hostApp = null,
                captureNamedOptions = optionsSlot,
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "shared-token"
        optionsSlot.captured.projectId shouldBe "onesignal-shared-public"
        optionsSlot.captured.gcmSenderId shouldBe SENDER_ID
        optionsSlot.captured.applicationId shouldBe "1:754795614042:android:c682b8144a8dd52bc1ad63"
    }

    test("does not reuse a host FirebaseApp whose sender id differs from the dashboard") {
        val optionsSlot = slot<FirebaseOptions>()
        val hostApp = defaultApp(senderId = "999999999999")
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("named-token"),
                hostApp = hostApp,
                captureNamedOptions = optionsSlot,
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "named-token"
        optionsSlot.captured.projectId shouldBe "onesignal-shared-public"
    }

    test("explains the problem when installation id registration is enabled without a host FirebaseApp") {
        val registrator = registrator(legacyToken = Tasks.forException(disabledLegacyApi), hostApp = null)

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> { registrator.getToken(SENDER_ID) }
            }

        thrown.message!! shouldContain "no default FirebaseApp"
        thrown.message!! shouldContain "firebase_messaging_installation_id_enabled=not set"
    }

    test("attempts installation id registration against the host FirebaseApp when the legacy API is disabled") {
        val hostMessaging = mockk<FirebaseMessaging>()
        every { hostMessaging.token } returns Tasks.forException(disabledLegacyApi)
        val hostApp = defaultApp(messaging = hostMessaging)
        val registrator = registrator(legacyToken = Tasks.forException(disabledLegacyApi), hostApp = hostApp)

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> { registrator.getToken(SENDER_ID) }
            }

        thrown.message!! shouldContain "25.1.0 or newer"
    }

    test("initializes the FirebaseApp only once") {
        val hostMessaging = mockk<FirebaseMessaging>()
        every { hostMessaging.token } returns Tasks.forResult("host-token")
        val hostApp = defaultApp(messaging = hostMessaging)
        val registrator = registrator(legacyToken = Tasks.forResult("unused"), hostApp = hostApp)

        withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }
        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "host-token"
    }

    test("maps FirebaseOptions into FCM project credentials") {
        val credentials = PushRegistratorFCM.credentialsFrom(firebaseOptions())

        credentials!!.senderId shouldBe SENDER_ID
        credentials.projectId shouldBe HOST_PROJECT_ID
        credentials.applicationId shouldBe HOST_APP_ID
        credentials.apiKey shouldBe HOST_API_KEY
    }

    test("returns null credentials when FirebaseOptions are missing a sender id") {
        val options =
            FirebaseOptions
                .Builder()
                .setProjectId(HOST_PROJECT_ID)
                .setApplicationId(HOST_APP_ID)
                .setApiKey(HOST_API_KEY)
                .build()

        PushRegistratorFCM.credentialsFrom(options) shouldBe null
        PushRegistratorFCM.credentialsFrom(null) shouldBe null
    }

    test("returns null credentials when FirebaseOptions are missing a project id") {
        val options =
            FirebaseOptions
                .Builder()
                .setGcmSenderId(SENDER_ID)
                .setApplicationId(HOST_APP_ID)
                .setApiKey(HOST_API_KEY)
                .build()

        PushRegistratorFCM.credentialsFrom(options) shouldBe null
    }

    test("reads backend FCM params at registration time rather than construction") {
        val optionsSlot = slot<FirebaseOptions>()
        val configStore = MockHelper.configModelStore()
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.token } returns Tasks.forResult("late-backend-token")
        val namedApp = mockk<FirebaseApp>()
        every { namedApp.get(FirebaseMessaging::class.java) } returns messaging

        mockkStatic(FirebaseApp::class)
        every {
            FirebaseApp.initializeApp(any<Context>(), capture(optionsSlot), any<String>())
        } returns namedApp

        val applicationService = mockk<IApplicationService>()
        every { applicationService.appContext } returns ApplicationProvider.getApplicationContext<Context>()

        val registrator =
            PushRegistratorFCM(
                configStore,
                applicationService,
                mockk(relaxed = true),
                mockk(relaxed = true),
                defaultFirebaseApp = { null },
            )

        configStore.model.fcmParams.projectId = "late-backend-project"
        configStore.model.fcmParams.appId = "1:388536902528:android:late"
        configStore.model.fcmParams.apiKey = "AIza-late"

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "late-backend-token"
        optionsSlot.captured.projectId shouldBe "late-backend-project"
    }

    test("hostDefaultFirebaseApp reads google-services.json via FirebaseApp.initializeApp") {
        mockkStatic(FirebaseApp::class)
        val app = mockk<FirebaseApp>()
        every { FirebaseApp.initializeApp(any<Context>()) } returns app

        val result =
            PushRegistratorFCM.hostDefaultFirebaseApp(
                ApplicationProvider.getApplicationContext(),
            )

        result shouldBe app
    }

    test("hostDefaultFirebaseApp falls back to getApps when initializeApp throws") {
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(any<Context>()) } throws IllegalStateException("failed")
        val app = mockk<FirebaseApp>()
        every { app.name } returns FirebaseApp.DEFAULT_APP_NAME
        every { FirebaseApp.getApps(any()) } returns listOf(app)

        val result =
            PushRegistratorFCM.hostDefaultFirebaseApp(
                ApplicationProvider.getApplicationContext(),
            )

        result shouldBe app
    }

    test("hostDefaultFirebaseApp returns null when initializeApp throws and no default app exists") {
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(any<Context>()) } throws IllegalStateException("failed")
        val namedApp = mockk<FirebaseApp>()
        every { namedApp.name } returns "ONESIGNAL_SDK_FCM_APP_NAME"
        every { FirebaseApp.getApps(any()) } returns listOf(namedApp)

        val result =
            PushRegistratorFCM.hostDefaultFirebaseApp(
                ApplicationProvider.getApplicationContext(),
            )

        result shouldBe null
    }

    test("ignores incomplete backend FCM params") {
        val optionsSlot = slot<FirebaseOptions>()
        val configStore =
            MockHelper.configModelStore {
                it.fcmParams.projectId = "backend-project"
            }
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.token } returns Tasks.forResult("shared-token")
        val namedApp = mockk<FirebaseApp>()
        every { namedApp.get(FirebaseMessaging::class.java) } returns messaging

        mockkStatic(FirebaseApp::class)
        every {
            FirebaseApp.initializeApp(any<Context>(), capture(optionsSlot), any<String>())
        } returns namedApp

        val applicationService = mockk<IApplicationService>()
        every { applicationService.appContext } returns ApplicationProvider.getApplicationContext<Context>()

        val registrator =
            PushRegistratorFCM(
                configStore,
                applicationService,
                mockk(relaxed = true),
                mockk(relaxed = true),
                defaultFirebaseApp = { null },
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "shared-token"
        optionsSlot.captured.projectId shouldBe "onesignal-shared-public"
    }
})
