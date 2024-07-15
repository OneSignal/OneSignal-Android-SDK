package com.onesignal.notifications.internal.pushtoken

import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class PushTokenManagerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
    }

    test("retrievePushToken should fail with missing library when android support libraries are missing") {
        // Given
        val mockPushRegistrator = mockk<IPushRegistrator>()
        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.jetpackLibraryStatus } returns IDeviceService.JetpackLibraryStatus.MISSING

        val pushTokenManager = PushTokenManager(mockPushRegistrator, mockDeviceService)

        // When
        val response = pushTokenManager.retrievePushToken()
        val pushToken = pushTokenManager.pushToken
        val pushTokenStatus = pushTokenManager.pushTokenStatus

        // Then
        response.token shouldBe null
        response.status shouldBe SubscriptionStatus.MISSING_ANDROID_SUPPORT_LIBRARY
        pushToken shouldBe null
        pushTokenStatus shouldBe SubscriptionStatus.MISSING_ANDROID_SUPPORT_LIBRARY
    }

    test("retrievePushToken should fail with outdated library when android support libraries are missing") {
        // Given
        val mockPushRegistrator = mockk<IPushRegistrator>()
        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.jetpackLibraryStatus } returns IDeviceService.JetpackLibraryStatus.OUTDATED

        val pushTokenManager = PushTokenManager(mockPushRegistrator, mockDeviceService)

        // When
        val response = pushTokenManager.retrievePushToken()
        val pushToken = pushTokenManager.pushToken
        val pushTokenStatus = pushTokenManager.pushTokenStatus

        // Then
        response.token shouldBe null
        response.status shouldBe SubscriptionStatus.OUTDATED_ANDROID_SUPPORT_LIBRARY
        pushToken shouldBe null
        pushTokenStatus shouldBe SubscriptionStatus.OUTDATED_ANDROID_SUPPORT_LIBRARY
    }

    test("retrievePushToken should succeed when registration is successful") {
        // Given
        val mockPushRegistrator = mockk<IPushRegistrator>()
        coEvery { mockPushRegistrator.registerForPush() } returns IPushRegistrator.RegisterResult("pushToken", SubscriptionStatus.SUBSCRIBED)
        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.jetpackLibraryStatus } returns IDeviceService.JetpackLibraryStatus.OK

        val pushTokenManager = PushTokenManager(mockPushRegistrator, mockDeviceService)

        // When
        val response = pushTokenManager.retrievePushToken()
        val pushToken = pushTokenManager.pushToken
        val pushTokenStatus = pushTokenManager.pushTokenStatus

        // Then
        coVerify(exactly = 1) { mockPushRegistrator.registerForPush() }
        response.token shouldBe "pushToken"
        response.status shouldBe SubscriptionStatus.SUBSCRIBED
        pushToken shouldBe "pushToken"
        pushTokenStatus shouldBe SubscriptionStatus.SUBSCRIBED
    }

    test("retrievePushToken should fail with failure status from push registrator with config-type error") {
        // Given
        val mockPushRegistrator = mockk<IPushRegistrator>()
        coEvery {
            mockPushRegistrator.registerForPush()
        } returns IPushRegistrator.RegisterResult(null, SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY)
        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.jetpackLibraryStatus } returns IDeviceService.JetpackLibraryStatus.OK

        val pushTokenManager = PushTokenManager(mockPushRegistrator, mockDeviceService)

        // When
        val response = pushTokenManager.retrievePushToken()
        val pushToken = pushTokenManager.pushToken
        val pushTokenStatus = pushTokenManager.pushTokenStatus

        // Then
        coVerify(exactly = 1) { mockPushRegistrator.registerForPush() }
        response.token shouldBe null
        response.status shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
        pushToken shouldBe null
        pushTokenStatus shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
    }

    test("retrievePushToken should fail with failure status from push registrator with runtime-type error") {
        // Given
        val mockPushRegistrator = mockk<IPushRegistrator>()
        coEvery {
            mockPushRegistrator.registerForPush()
        } returns IPushRegistrator.RegisterResult(null, SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION)
        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.jetpackLibraryStatus } returns IDeviceService.JetpackLibraryStatus.OK

        val pushTokenManager = PushTokenManager(mockPushRegistrator, mockDeviceService)

        // When
        val response = pushTokenManager.retrievePushToken()
        val pushToken = pushTokenManager.pushToken
        val pushTokenStatus = pushTokenManager.pushTokenStatus

        // Then
        coVerify(exactly = 1) { mockPushRegistrator.registerForPush() }
        response.token shouldBe null
        response.status shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION
        pushToken shouldBe null
        pushTokenStatus shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION
    }

    test("retrievePushToken should fail with failure status of config-type error even if subsequent runtime-type error") {
        // Given
        val mockPushRegistrator = mockk<IPushRegistrator>()
        coEvery { mockPushRegistrator.registerForPush() } returns
            IPushRegistrator.RegisterResult(null, SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY) andThen
            IPushRegistrator.RegisterResult(null, SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION)

        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.jetpackLibraryStatus } returns IDeviceService.JetpackLibraryStatus.OK

        val pushTokenManager = PushTokenManager(mockPushRegistrator, mockDeviceService)

        // When
        val response1 = pushTokenManager.retrievePushToken()
        val pushToken1 = pushTokenManager.pushToken
        val pushTokenStatus1 = pushTokenManager.pushTokenStatus
        val response2 = pushTokenManager.retrievePushToken()
        val pushToken2 = pushTokenManager.pushToken
        val pushTokenStatus2 = pushTokenManager.pushTokenStatus

        // Then
        coVerify(exactly = 2) { mockPushRegistrator.registerForPush() }
        response1.token shouldBe null
        response1.status shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
        pushToken1 shouldBe null
        pushTokenStatus1 shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
        response2.token shouldBe null
        response2.status shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
        pushToken2 shouldBe null
        pushTokenStatus2 shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
    }

    test("retrievePushToken should fail with failure status of config-type error even if previous runtime-type error") {
        // Given
        val mockPushRegistrator = mockk<IPushRegistrator>()
        coEvery { mockPushRegistrator.registerForPush() } returns
            IPushRegistrator.RegisterResult(null, SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION) andThen
            IPushRegistrator.RegisterResult(null, SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY)

        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.jetpackLibraryStatus } returns IDeviceService.JetpackLibraryStatus.OK

        val pushTokenManager = PushTokenManager(mockPushRegistrator, mockDeviceService)

        // When
        val response1 = pushTokenManager.retrievePushToken()
        val pushToken1 = pushTokenManager.pushToken
        val pushTokenStatus1 = pushTokenManager.pushTokenStatus
        val response2 = pushTokenManager.retrievePushToken()
        val pushToken2 = pushTokenManager.pushToken
        val pushTokenStatus2 = pushTokenManager.pushTokenStatus

        // Then
        coVerify(exactly = 2) { mockPushRegistrator.registerForPush() }
        response1.token shouldBe null
        response1.status shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION
        pushToken1 shouldBe null
        pushTokenStatus1 shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION
        response2.token shouldBe null
        response2.status shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
        pushToken2 shouldBe null
        pushTokenStatus2 shouldBe SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY
    }
})
