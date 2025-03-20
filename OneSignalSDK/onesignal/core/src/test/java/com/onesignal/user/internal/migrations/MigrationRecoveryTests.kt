package com.onesignal.user.internal.migrations

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class MigrationRecoveryTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("ensure RecoverConfigPushSubscription adds the missing push sub ID") {
        // Given
        val configModelStore = MockHelper.configModelStore()
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val recovery = RecoverConfigPushSubscription(configModelStore, mockSubscriptionModelStore)

        // Set up config model store with null push subscription ID
        val configModel = configModelStore.model
        configModel.pushSubscriptionId = null

        // Add a push subscription
        val pushSubscription = SubscriptionModel()
        pushSubscription.type = SubscriptionType.PUSH
        pushSubscription.id = "0"
        every { mockSubscriptionModelStore.list() } returns listOf(pushSubscription)

        // When
        recovery.start()

        // Then
        configModelStore.model.pushSubscriptionId shouldBe "0"
    }
})
