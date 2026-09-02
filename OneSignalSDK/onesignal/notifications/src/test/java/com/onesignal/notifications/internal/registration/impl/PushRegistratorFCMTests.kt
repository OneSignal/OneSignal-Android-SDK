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
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
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

private fun namedApp(messaging: FirebaseMessaging): FirebaseApp {
    val app = mockk<FirebaseApp>()
    every { app.name } returns "ONESIGNAL_SDK_FCM_APP_NAME"
    every { app.get(FirebaseMessaging::class.java) } returns messaging
    every { app.options } returns firebaseOptions()
    every { app.delete() } just runs
    return app
}

private fun applicationService(): IApplicationService {
    val applicationService = mockk<IApplicationService>()
    every { applicationService.appContext } returns ApplicationProvider.getApplicationContext<Context>()
    return applicationService
}

private fun deviceService(
    hasFcm: Boolean = true,
    gmsEnabled: Boolean = true,
) = MockHelper.deviceService().also {
    every { it.hasFCMLibrary } returns hasFcm
    every { it.isGMSInstalledAndEnabled } returns gmsEnabled
    every { it.isAndroidDeviceType } returns true
}

private fun registrator(
    legacyToken: Task<String>,
    hostApp: FirebaseApp? = null,
    captureNamedOptions: CapturingSlot<FirebaseOptions>? = null,
    configStore: ConfigModelStore = MockHelper.configModelStore(),
    device: IDeviceService = deviceService(),
): PushRegistratorFCM {
    val messaging = mockk<FirebaseMessaging>()
    every { messaging.token } returns legacyToken
    val named = namedApp(messaging)

    mockkStatic(FirebaseApp::class)
    if (captureNamedOptions != null) {
        every {
            FirebaseApp.initializeApp(any<Context>(), capture(captureNamedOptions), any<String>())
        } returns named
    } else {
        every {
            FirebaseApp.initializeApp(any<Context>(), any<FirebaseOptions>(), any<String>())
        } returns named
    }

    return PushRegistratorFCM(
        configStore,
        applicationService(),
        mockk(relaxed = true),
        device,
        defaultFirebaseApp = { hostApp },
    )
}

@RobolectricTest
class PushRegistratorFCMTests : FunSpec({
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
        verify(exactly = 0) { FirebaseApp.initializeApp(any<Context>(), any<FirebaseOptions>(), any<String>()) }
    }

    test("initializes a named FirebaseApp from backend FCM params when google-services.json is absent") {
        val optionsSlot = slot<FirebaseOptions>()
        val configStore =
            MockHelper.configModelStore {
                it.fcmParams.projectId = "backend-project"
                it.fcmParams.appId = "1:388536902528:android:backend"
                it.fcmParams.apiKey = "AIza-backend"
            }
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("backend-token"),
                hostApp = null,
                captureNamedOptions = optionsSlot,
                configStore = configStore,
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "backend-token"
        optionsSlot.captured.projectId shouldBe "backend-project"
        optionsSlot.captured.gcmSenderId shouldBe SENDER_ID
        optionsSlot.captured.applicationId shouldBe "1:388536902528:android:backend"
        optionsSlot.captured.apiKey shouldBe "AIza-backend"
    }

    test("does not mint a token from the shared project when the dashboard sender belongs to a customer project") {
        val registrator = registrator(legacyToken = Tasks.forResult("shared-token"), hostApp = null)

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<InvalidFcmProjectException> { registrator.getToken(SENDER_ID) }
            }

        val message = thrown.message
        message shouldContain "cannot mix sender id $SENDER_ID"
        message shouldContain FcmSharedProject.PROJECT_ID
        verify(exactly = 0) { FirebaseApp.initializeApp(any<Context>(), any<FirebaseOptions>(), any<String>()) }
    }

    test("uses OneSignal's shared Firebase project only when the dashboard sender is the shared sender") {
        val optionsSlot = slot<FirebaseOptions>()
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("shared-token"),
                hostApp = null,
                captureNamedOptions = optionsSlot,
            )

        val token = withContext(Dispatchers.IO) { registrator.getToken(FcmSharedProject.SENDER_ID) }

        token shouldBe "shared-token"
        optionsSlot.captured.projectId shouldBe FcmSharedProject.PROJECT_ID
        optionsSlot.captured.gcmSenderId shouldBe FcmSharedProject.SENDER_ID
        optionsSlot.captured.applicationId shouldBe FcmSharedProject.APPLICATION_ID
    }

    test("does not reuse a host FirebaseApp whose sender id differs from the dashboard") {
        val hostApp = defaultApp(senderId = "999999999999")
        val registrator = registrator(legacyToken = Tasks.forResult("named-token"), hostApp = hostApp)

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<InvalidFcmProjectException> { registrator.getToken(SENDER_ID) }
            }

        thrown.message shouldContain "cannot mix sender id $SENDER_ID"
    }

    test("initializes the FirebaseApp only once when credentials are unchanged") {
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
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("late-backend-token"),
                hostApp = null,
                captureNamedOptions = optionsSlot,
                configStore = configStore,
            )

        configStore.model.fcmParams.projectId = "late-backend-project"
        configStore.model.fcmParams.appId = "1:388536902528:android:late"
        configStore.model.fcmParams.apiKey = "AIza-late"

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "late-backend-token"
        optionsSlot.captured.projectId shouldBe "late-backend-project"
    }

    test("re-resolves Firebase credentials when backend params change after the first registration") {
        val optionsSlot = slot<FirebaseOptions>()
        val configStore =
            MockHelper.configModelStore {
                it.fcmParams.projectId = "backend-project-a"
                it.fcmParams.appId = "1:388536902528:android:a"
                it.fcmParams.apiKey = "AIza-a"
            }
        val messaging = mockk<FirebaseMessaging>()
        every { messaging.token } returns Tasks.forResult("token-a") andThen Tasks.forResult("token-b")
        val firstNamed = namedApp(messaging)
        every { firstNamed.options } returns
            firebaseOptions(
                projectId = "backend-project-a",
                applicationId = "1:388536902528:android:a",
                apiKey = "AIza-a",
            )

        mockkStatic(FirebaseApp::class)
        every {
            FirebaseApp.initializeApp(any<Context>(), capture(optionsSlot), any<String>())
        } returns firstNamed

        val registrator =
            PushRegistratorFCM(
                configStore,
                applicationService(),
                mockk(relaxed = true),
                deviceService(),
                defaultFirebaseApp = { null },
            )

        withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        configStore.model.fcmParams.projectId = "backend-project-b"
        configStore.model.fcmParams.appId = "1:388536902528:android:b"
        configStore.model.fcmParams.apiKey = "AIza-b"

        val token = withContext(Dispatchers.IO) { registrator.getToken(SENDER_ID) }

        token shouldBe "token-b"
        verify(exactly = 1) { firstNamed.delete() }
        optionsSlot.captured.projectId shouldBe "backend-project-b"
    }

    test("hostDefaultFirebaseApp prefers an already-initialized default FirebaseApp") {
        mockkStatic(FirebaseApp::class)
        val app = mockk<FirebaseApp>()
        every { FirebaseApp.getInstance() } returns app

        val result =
            PushRegistratorFCM.hostDefaultFirebaseApp(
                ApplicationProvider.getApplicationContext(),
            )

        result shouldBe app
        verify(exactly = 0) { FirebaseApp.initializeApp(any<Context>()) }
    }

    test("hostDefaultFirebaseApp reads google-services.json via FirebaseApp.initializeApp") {
        mockkStatic(FirebaseApp::class)
        val app = mockk<FirebaseApp>()
        every { FirebaseApp.getInstance() } throws IllegalStateException("no default")
        every { FirebaseApp.initializeApp(any<Context>()) } returns app

        val result =
            PushRegistratorFCM.hostDefaultFirebaseApp(
                ApplicationProvider.getApplicationContext(),
            )

        result shouldBe app
    }

    test("hostDefaultFirebaseApp falls back to getApps when initializeApp throws") {
        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.getInstance() } throws IllegalStateException("no default")
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
        every { FirebaseApp.getInstance() } throws IllegalStateException("no default")
        every { FirebaseApp.initializeApp(any<Context>()) } throws IllegalStateException("failed")
        val named = mockk<FirebaseApp>()
        every { named.name } returns "ONESIGNAL_SDK_FCM_APP_NAME"
        every { FirebaseApp.getApps(any()) } returns listOf(named)

        val result =
            PushRegistratorFCM.hostDefaultFirebaseApp(
                ApplicationProvider.getApplicationContext(),
            )

        result shouldBe null
    }

    test("ignores incomplete backend FCM params") {
        val configStore =
            MockHelper.configModelStore {
                it.fcmParams.projectId = "backend-project"
            }
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("shared-token"),
                hostApp = null,
                configStore = configStore,
            )

        withContext(Dispatchers.IO) {
            shouldThrow<InvalidFcmProjectException> { registrator.getToken(SENDER_ID) }
        }
    }

    test("registerForPush reports INVALID_FCM_SENDER_ID instead of subscribing via the shared project") {
        val configStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = SENDER_ID
            }
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("shared-token"),
                hostApp = null,
                configStore = configStore,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe null
        result.status shouldBe SubscriptionStatus.INVALID_FCM_SENDER_ID
    }

    test("registerForPush succeeds when google-services.json matches the dashboard sender") {
        val hostMessaging = mockk<FirebaseMessaging>()
        every { hostMessaging.token } returns Tasks.forResult("host-token")
        val hostApp = defaultApp(messaging = hostMessaging)
        val configStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = SENDER_ID
            }
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("unused"),
                hostApp = hostApp,
                configStore = configStore,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe "host-token"
        result.status shouldBe SubscriptionStatus.SUBSCRIBED
    }

    test("registerForPush uses google-services.json when the dashboard sender is missing") {
        val hostMessaging = mockk<FirebaseMessaging>()
        every { hostMessaging.token } returns Tasks.forResult("host-token")
        val hostApp = defaultApp(messaging = hostMessaging)
        val configStore =
            MockHelper.configModelStore {
                it.isInitializedWithRemote = true
                it.googleProjectNumber = null
            }
        val registrator =
            registrator(
                legacyToken = Tasks.forResult("unused"),
                hostApp = hostApp,
                configStore = configStore,
            )

        val result = withContext(Dispatchers.IO) { registrator.registerForPush() }

        result.id shouldBe "host-token"
        result.status shouldBe SubscriptionStatus.SUBSCRIBED
    }

    test("sharedDefaultCredentials uses the shared project's own sender rather than the dashboard sender") {
        val credentials = PushRegistratorFCM.sharedDefaultCredentials()

        credentials.senderId shouldBe FcmSharedProject.SENDER_ID
        credentials.projectId shouldBe FcmSharedProject.PROJECT_ID
        credentials.applicationId shouldBe FcmSharedProject.APPLICATION_ID
        credentials.isComplete shouldBe true
    }
})
