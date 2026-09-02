package com.onesignal.user.internal.builduser

import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.builduser.impl.RebuildUserService
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class RebuildUserServiceTests : FunSpec({
    val appId = "appId"
    val onesignalId = "onesignalId"
    val subscriptionId = "subscriptionId"

    fun buildService(pushModel: SubscriptionModel?): RebuildUserService {
        val subscriptionModelStore = mockk<SubscriptionModelStore>()
        every { subscriptionModelStore.list() } returns listOfNotNull(pushModel)
        every { subscriptionModelStore.get(any()) } returns pushModel
        return RebuildUserService(
            MockHelper.identityModelStore { it.onesignalId = onesignalId },
            MockHelper.propertiesModelStore { it.onesignalId = onesignalId },
            subscriptionModelStore,
            MockHelper.configModelStore { it.pushSubscriptionId = subscriptionId },
        )
    }

    test("rebuild recreates a REST-API-disabled push subscription from device truth") {
        // Given: the records being rebuilt are gone, so the recorded disable goes with them
        val pushModel =
            SubscriptionModel().apply {
                id = subscriptionId
                type = SubscriptionType.PUSH
                address = "pushToken"
                optedIn = true
                status = SubscriptionStatus.SUBSCRIBED
                restApiDisabledReason = SubscriptionStatus.DISABLED_FROM_REST_API.value
            }
        val service = buildService(pushModel)

        // When
        val operations = service.getRebuildOperationsIfCurrentUser(appId, onesignalId)!!

        // Then
        (operations[0] is LoginUserOperation) shouldBe true
        val create = operations[1] as CreateSubscriptionOperation
        create.subscriptionId shouldBe subscriptionId
        create.enabled shouldBe true
        create.status shouldBe SubscriptionStatus.SUBSCRIBED
        (operations[2] is RefreshUserOperation) shouldBe true
        pushModel.restApiDisabledReason shouldBe 0
    }

    test("rebuild without a push subscription emits only the login and refresh") {
        val service = buildService(null)

        val operations = service.getRebuildOperationsIfCurrentUser(appId, onesignalId)!!

        operations.size shouldBe 2
        (operations[0] is LoginUserOperation) shouldBe true
        (operations[1] is RefreshUserOperation) shouldBe true
    }

    test("rebuild returns null when the current user is no longer the one that needs rebuilding") {
        val service = buildService(null)

        service.getRebuildOperationsIfCurrentUser(appId, "otherOnesignalId") shouldBe null
    }
})
