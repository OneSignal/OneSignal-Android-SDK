package com.onesignal.notifications.internal.listeners

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.internal.channels.INotificationChannelManager
import com.onesignal.notifications.internal.pushtoken.IPushTokenManager
import com.onesignal.notifications.internal.pushtoken.PushTokenResponse
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private const val NEW_TOKEN = "new-token"

/** Mirrors the shape of a real, persisted push subscription model. */
private fun realPushModel(
    status: SubscriptionStatus,
    address: String = "existing-token",
): SubscriptionModel =
    SubscriptionModel().apply {
        id = "sub-id"
        type = SubscriptionType.PUSH
        this.address = address
        this.status = status
        optedIn = true
    }

/** Mirrors UninitializedPushSubscription.createFakePushSub() — empty id signals "no real push sub yet". */
private fun uninitializedPushModel(): SubscriptionModel =
    SubscriptionModel().apply {
        id = ""
        type = SubscriptionType.PUSH
        address = ""
        optedIn = false
    }

private class Harness(
    permission: Boolean,
    pushModel: SubscriptionModel,
    pushTokenResponse: PushTokenResponse =
        PushTokenResponse(NEW_TOKEN, SubscriptionStatus.SUBSCRIBED),
) {
    val configModelStore: ConfigModelStore = mockk(relaxed = true)
    val channelManager: INotificationChannelManager = mockk(relaxed = true)
    val pushTokenManager: IPushTokenManager = mockk()
    val notificationsManager: INotificationsManager = mockk(relaxed = true)
    val subscriptionManager: ISubscriptionManager = mockk(relaxed = true)
    val listener: DeviceRegistrationListener

    init {
        coEvery { pushTokenManager.retrievePushToken() } returns pushTokenResponse
        every { notificationsManager.permission } returns permission
        every { subscriptionManager.pushSubscriptionModel } returns pushModel

        listener =
            DeviceRegistrationListener(
                configModelStore,
                channelManager,
                pushTokenManager,
                notificationsManager,
                subscriptionManager,
            )
    }
}

class DeviceRegistrationListenerTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("start subscribes to config, permission and subscription observables") {
        // Given
        val harness =
            Harness(
                permission = false,
                pushModel = uninitializedPushModel(),
            )

        // When
        harness.listener.start()
        awaitIO()

        // Then
        verify(exactly = 1) { harness.configModelStore.subscribe(harness.listener) }
        verify(exactly = 1) { harness.notificationsManager.addPermissionObserver(harness.listener) }
        verify(exactly = 1) { harness.subscriptionManager.subscribe(harness.listener) }
    }

    test("start does not eagerly retrieve push token when permission is denied") {
        // Given
        val harness =
            Harness(
                permission = false,
                pushModel = uninitializedPushModel(),
            )

        // When
        harness.listener.start()
        awaitIO()

        // Then
        coVerify(exactly = 0) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 0) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(any(), any())
        }
    }

    test("start does not eagerly retrieve push token when permission denied even if status is NO_PERMISSION") {
        // Given
        val harness =
            Harness(
                permission = false,
                pushModel = realPushModel(SubscriptionStatus.NO_PERMISSION, address = ""),
            )

        // When
        harness.listener.start()
        awaitIO()

        // Then
        coVerify(exactly = 0) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 0) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(any(), any())
        }
    }

    test("start does not eagerly retrieve push token when cached push subscription is already SUBSCRIBED") {
        // Given
        val harness =
            Harness(
                permission = true,
                pushModel = realPushModel(SubscriptionStatus.SUBSCRIBED),
            )

        // When
        harness.listener.start()
        awaitIO()

        // Then
        coVerify(exactly = 0) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 0) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(any(), any())
        }
    }

    test("start eagerly retrieves push token when push subscription is uninitialized") {
        // Given
        val harness =
            Harness(
                permission = true,
                pushModel = uninitializedPushModel(),
            )

        // When
        harness.listener.start()
        awaitIO()

        // Then
        coVerify(exactly = 1) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 1) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(
                NEW_TOKEN,
                SubscriptionStatus.SUBSCRIBED,
            )
        }
    }

    test("start eagerly retrieves push token when cached status is NO_PERMISSION but OS permission is granted") {
        // Given — the bug scenario from PR #2622: reinstall / app data clear leaves the
        // subscription stuck at NO_PERMISSION because the permission observer never fires.
        val harness =
            Harness(
                permission = true,
                pushModel = realPushModel(SubscriptionStatus.NO_PERMISSION, address = ""),
            )

        // When
        harness.listener.start()
        awaitIO()

        // Then
        coVerify(exactly = 1) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 1) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(
                NEW_TOKEN,
                SubscriptionStatus.SUBSCRIBED,
            )
        }
    }

    test("start eagerly retrieves push token when cached status is a retryable runtime error") {
        // Given
        val harness =
            Harness(
                permission = true,
                pushModel = realPushModel(SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION),
            )

        // When
        harness.listener.start()
        awaitIO()

        // Then
        coVerify(exactly = 1) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 1) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(
                NEW_TOKEN,
                SubscriptionStatus.SUBSCRIBED,
            )
        }
    }

    test("onNotificationPermissionChange always retrieves push token regardless of cached state") {
        // Given — gate is intentionally bypassed for explicit permission-change events.
        val harness =
            Harness(
                permission = true,
                pushModel = realPushModel(SubscriptionStatus.SUBSCRIBED),
            )

        // When
        harness.listener.onNotificationPermissionChange(true)
        awaitIO()

        // Then
        coVerify(exactly = 1) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 1) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(
                NEW_TOKEN,
                SubscriptionStatus.SUBSCRIBED,
            )
        }
    }

    test("retrieved push token is written with NO_PERMISSION when OS permission is denied at the time of the call") {
        // Given — covers the existing branch in retrievePushTokenAndUpdateSubscription that
        // overrides the FCM-reported status with NO_PERMISSION when permission is missing.
        val harness =
            Harness(
                permission = true,
                pushModel = uninitializedPushModel(),
                pushTokenResponse =
                    PushTokenResponse(NEW_TOKEN, SubscriptionStatus.SUBSCRIBED),
            )
        // Permission flips off between gate evaluation and the IO callback.
        every { harness.notificationsManager.permission } returns true andThen false

        // When
        harness.listener.start()
        awaitIO()

        // Then
        coVerify(exactly = 1) { harness.pushTokenManager.retrievePushToken() }
        verify(exactly = 1) {
            harness.subscriptionManager.addOrUpdatePushSubscriptionToken(
                NEW_TOKEN,
                SubscriptionStatus.NO_PERMISSION,
            )
        }
    }
})
