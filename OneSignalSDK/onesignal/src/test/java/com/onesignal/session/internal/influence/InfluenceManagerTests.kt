package com.onesignal.session.internal.influence

import com.onesignal.core.internal.application.AppEntryAction
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.influence.impl.InfluenceManager
import com.onesignal.session.internal.session.ISessionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class InfluenceManagerTests : FunSpec({

    test("default are disabled influences") {
        /* Given */
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockApplicationService = mockk<IApplicationService>()
        val mockConfigModelStore = MockHelper.configModelStore {
            it.influenceParams.isDirectEnabled = false
            it.influenceParams.isIndirectEnabled = false
            it.influenceParams.isUnattributedEnabled = false
        }
        val mockPreferences = MockPreferencesService()
        val influenceManager = InfluenceManager(mockSessionService, mockApplicationService, mockConfigModelStore, mockPreferences, MockHelper.time(1111))

        /* When */
        val influences = influenceManager.influences

        /* Then */
        influences.count() shouldBe 2
        val notificationInfluence = influences.first { it.influenceChannel == InfluenceChannel.NOTIFICATION }
        notificationInfluence.influenceType.isDisabled() shouldBe true
        notificationInfluence.directId shouldBe null
        notificationInfluence.ids shouldBe null

        val iamInfluence = influences.first { it.influenceChannel == InfluenceChannel.IAM }
        iamInfluence.influenceType.isDisabled() shouldBe true
        iamInfluence.directId shouldBe null
        iamInfluence.ids shouldBe null
    }

    test("session begin are unattributed influences") {
        /* Given */
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockApplicationService = mockk<IApplicationService>()
        every { mockApplicationService.entryState } returns AppEntryAction.APP_OPEN

        val mockConfigModelStore = MockHelper.configModelStore {
            it.influenceParams.isDirectEnabled = true
            it.influenceParams.isIndirectEnabled = true
            it.influenceParams.isUnattributedEnabled = true
        }
        val mockPreferences = MockPreferencesService()
        val influenceManager = InfluenceManager(mockSessionService, mockApplicationService, mockConfigModelStore, mockPreferences, MockHelper.time(1111))

        /* When */
        influenceManager.onSessionStarted()
        val influences = influenceManager.influences

        /* Then */
        influences.count() shouldBe 2
        val notificationInfluence = influences.first { it.influenceChannel == InfluenceChannel.NOTIFICATION }
        notificationInfluence.influenceType.isUnattributed() shouldBe true
        notificationInfluence.directId shouldBe null
        notificationInfluence.ids shouldBe null

        val iamInfluence = influences.first { it.influenceChannel == InfluenceChannel.IAM }
        iamInfluence.influenceType.isUnattributed() shouldBe true
        iamInfluence.directId shouldBe null
        iamInfluence.ids shouldBe null
    }

    test("notification received creates notification indirect influence") {
        /* Given */
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockApplicationService = mockk<IApplicationService>()
        every { mockApplicationService.entryState } returns AppEntryAction.APP_OPEN

        val mockConfigModelStore = MockHelper.configModelStore {
            it.influenceParams.isDirectEnabled = true
            it.influenceParams.isIndirectEnabled = true
            it.influenceParams.isUnattributedEnabled = true
        }
        val mockPreferences = MockPreferencesService()
        val influenceManager = InfluenceManager(mockSessionService, mockApplicationService, mockConfigModelStore, mockPreferences, MockHelper.time(1111))

        /* When */
        influenceManager.onNotificationReceived("notificationId")
        influenceManager.onSessionActive()
        val influences = influenceManager.influences

        /* Then */
        influences.count() shouldBe 2
        val notificationInfluence = influences.first { it.influenceChannel == InfluenceChannel.NOTIFICATION }
        notificationInfluence.influenceType.isIndirect() shouldBe true
        notificationInfluence.ids shouldNotBe null
        notificationInfluence.ids!!.length() shouldBe 1
        notificationInfluence.ids!![0].toString() shouldBe "notificationId"

        val iamInfluence = influences.first { it.influenceChannel == InfluenceChannel.IAM }
        iamInfluence.influenceType.isUnattributed() shouldBe true
        iamInfluence.ids shouldBe null
    }

    test("IAM received creates IAM indirect influence") {
        /* Given */
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockApplicationService = mockk<IApplicationService>()
        val mockConfigModelStore = MockHelper.configModelStore {
            it.influenceParams.isDirectEnabled = true
            it.influenceParams.isIndirectEnabled = true
            it.influenceParams.isUnattributedEnabled = true
        }
        val mockPreferences = MockPreferencesService()
        val influenceManager = InfluenceManager(mockSessionService, mockApplicationService, mockConfigModelStore, mockPreferences, MockHelper.time(1111))

        /* When */
        influenceManager.onInAppMessageDisplayed("inAppMessageId")
        influenceManager.onInAppMessageDismissed()
        val influences = influenceManager.influences

        /* Then */
        influences.count() shouldBe 2
        val notificationInfluence = influences.first { it.influenceChannel == InfluenceChannel.NOTIFICATION }
        notificationInfluence.influenceType.isUnattributed() shouldBe true
        notificationInfluence.directId shouldBe null
        notificationInfluence.ids shouldBe null

        val iamInfluence = influences.first { it.influenceChannel == InfluenceChannel.IAM }
        iamInfluence.influenceType.isIndirect() shouldBe true
        iamInfluence.influenceChannel shouldBe InfluenceChannel.IAM
        iamInfluence.ids shouldNotBe null
        iamInfluence.ids!!.length() shouldBe 1
        iamInfluence.ids!![0].toString() shouldBe "inAppMessageId"
    }

    test("notification opened creates notification direct influence") {
        /* Given */
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockApplicationService = mockk<IApplicationService>()
        val mockConfigModelStore = MockHelper.configModelStore {
            it.influenceParams.isDirectEnabled = true
            it.influenceParams.isIndirectEnabled = true
            it.influenceParams.isUnattributedEnabled = true
        }
        val mockPreferences = MockPreferencesService()
        val influenceManager = InfluenceManager(mockSessionService, mockApplicationService, mockConfigModelStore, mockPreferences, MockHelper.time(1111))

        /* When */
        influenceManager.onNotificationReceived("notificationId")
        influenceManager.onDirectInfluenceFromNotification("notificationId")
        val influences = influenceManager.influences

        /* Then */
        influences.count() shouldBe 2
        val notificationInfluence = influences.first { it.influenceChannel == InfluenceChannel.NOTIFICATION }
        notificationInfluence.influenceType.isDirect() shouldBe true
        notificationInfluence.influenceChannel shouldBe InfluenceChannel.NOTIFICATION
        notificationInfluence.directId shouldBe "notificationId"

        val iamInfluence = influences.first { it.influenceChannel == InfluenceChannel.IAM }
        iamInfluence.influenceType.isUnattributed() shouldBe true
        iamInfluence.directId shouldBe null
        iamInfluence.ids shouldBe null
    }

    test("IAM clicked while open creates IAM direct influence") {
        /* Given */
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockApplicationService = mockk<IApplicationService>()
        val mockConfigModelStore = MockHelper.configModelStore {
            it.influenceParams.isDirectEnabled = true
            it.influenceParams.isIndirectEnabled = true
            it.influenceParams.isUnattributedEnabled = true
        }
        val mockPreferences = MockPreferencesService()
        val influenceManager = InfluenceManager(mockSessionService, mockApplicationService, mockConfigModelStore, mockPreferences, MockHelper.time(1111))

        /* When */
        influenceManager.onInAppMessageDisplayed("inAppMessageId")
        influenceManager.onDirectInfluenceFromIAM("inAppMessageId")
        val influences = influenceManager.influences

        /* Then */
        influences.count() shouldBe 2
        val notificationInfluence = influences.first { it.influenceChannel == InfluenceChannel.NOTIFICATION }
        notificationInfluence.influenceType.isUnattributed() shouldBe true
        notificationInfluence.directId shouldBe null
        notificationInfluence.ids shouldBe null

        val iamInfluence = influences.first { it.influenceChannel == InfluenceChannel.IAM }
        iamInfluence.influenceType.isDirect() shouldBe true
        iamInfluence.influenceChannel shouldBe InfluenceChannel.IAM
        iamInfluence.directId shouldBe "inAppMessageId"
    }

    test("IAM clicked then dismissed creates IAM indirect influence") {
        /* Given */
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockApplicationService = mockk<IApplicationService>()
        val mockConfigModelStore = MockHelper.configModelStore {
            it.influenceParams.isDirectEnabled = true
            it.influenceParams.isIndirectEnabled = true
            it.influenceParams.isUnattributedEnabled = true
        }
        val mockPreferences = MockPreferencesService()
        val influenceManager = InfluenceManager(mockSessionService, mockApplicationService, mockConfigModelStore, mockPreferences, MockHelper.time(1111))

        /* When */
        influenceManager.onInAppMessageDisplayed("inAppMessageId")
        influenceManager.onDirectInfluenceFromIAM("inAppMessageId")
        influenceManager.onInAppMessageDismissed()
        val influences = influenceManager.influences

        /* Then */
        influences.count() shouldBe 2
        val notificationInfluence = influences.first { it.influenceChannel == InfluenceChannel.NOTIFICATION }
        notificationInfluence.influenceType.isUnattributed() shouldBe true
        notificationInfluence.directId shouldBe null
        notificationInfluence.ids shouldBe null

        val iamInfluence = influences.first { it.influenceChannel == InfluenceChannel.IAM }
        iamInfluence.influenceType.isIndirect() shouldBe true
        iamInfluence.influenceChannel shouldBe InfluenceChannel.IAM
        iamInfluence.directId shouldBe "inAppMessageId"
    }
})
