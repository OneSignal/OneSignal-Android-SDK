package com.onesignal.session.internal.outcomes

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.influence.IInfluenceManager
import com.onesignal.session.internal.influence.Influence
import com.onesignal.session.internal.influence.InfluenceChannel
import com.onesignal.session.internal.influence.InfluenceType
import com.onesignal.session.internal.outcomes.impl.IOutcomeEventsBackendService
import com.onesignal.session.internal.outcomes.impl.IOutcomeEventsPreferences
import com.onesignal.session.internal.outcomes.impl.IOutcomeEventsRepository
import com.onesignal.session.internal.outcomes.impl.OutcomeEventParams
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsController
import com.onesignal.session.internal.outcomes.impl.OutcomeSource
import com.onesignal.session.internal.outcomes.impl.OutcomeSourceBody
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.PushSubscription
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class OutcomeEventsControllerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("send outcome with disabled influences") {
        /* Given */
        val now = 111L
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        every { mockInfluenceManager.influences } returns listOf(Influence(InfluenceChannel.NOTIFICATION, InfluenceType.DISABLED, null))
        val mockSubscriptionManager = mockk<ISubscriptionManager>()

        val mockOutcomeEventsRepository = spyk<IOutcomeEventsRepository>()
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore(),
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt = outcomeEventsController.sendOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt shouldBe null
        coVerify(exactly = 0) { mockOutcomeEventsBackend.sendOutcomeEvent(any(), any(), any(), any(), any()) }
    }

    test("send outcome with unattributed influences") {
        /* Given */
        val now = 111L
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        every { mockInfluenceManager.influences } returns listOf(Influence(InfluenceChannel.NOTIFICATION, InfluenceType.UNATTRIBUTED, null))

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"

        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsRepository = spyk<IOutcomeEventsRepository>()
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt = outcomeEventsController.sendOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt shouldNotBe null
        evnt!!.name shouldBe "OUTCOME_1"
        evnt.notificationIds shouldBe null
        evnt.weight shouldBe 0
        evnt.session shouldBe InfluenceType.UNATTRIBUTED
        evnt.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        coVerify(exactly = 1) { mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", null, evnt) }
    }

    test("send outcome with indirect influences") {
        /* Given */
        val now = 111L
        val notificationIds = "[\"id1\",\"id2\"]"
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        every { mockInfluenceManager.influences } returns listOf(Influence(InfluenceChannel.NOTIFICATION, InfluenceType.INDIRECT, JSONArray(notificationIds)))

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsRepository = spyk<IOutcomeEventsRepository>()
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt = outcomeEventsController.sendOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt shouldNotBe null
        evnt!!.name shouldBe "OUTCOME_1"
        evnt.notificationIds shouldNotBe null
        evnt.notificationIds!!.toString() shouldBe notificationIds
        evnt.weight shouldBe 0
        evnt.session shouldBe InfluenceType.INDIRECT
        evnt.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        coVerify(exactly = 1) { mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", false, evnt) }
    }

    test("send outcome with direct influence") {
        /* Given */
        val now = 111L
        val notificationIds = "[\"id1\",\"id2\"]"
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        every { mockInfluenceManager.influences } returns listOf(Influence(InfluenceChannel.NOTIFICATION, InfluenceType.DIRECT, JSONArray(notificationIds)))

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsRepository = spyk<IOutcomeEventsRepository>()
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt = outcomeEventsController.sendOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt shouldNotBe null
        evnt!!.name shouldBe "OUTCOME_1"
        evnt.notificationIds shouldNotBe null
        evnt.notificationIds!!.toString() shouldBe notificationIds
        evnt.weight shouldBe 0
        evnt.session shouldBe InfluenceType.DIRECT
        evnt.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        coVerify(exactly = 1) { mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", true, evnt) }
    }

    test("send outcome with weight") {
        /* Given */
        val now = 111L
        val weight = 999F
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        every { mockInfluenceManager.influences } returns listOf(Influence(InfluenceChannel.NOTIFICATION, InfluenceType.UNATTRIBUTED, null))

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsRepository = spyk<IOutcomeEventsRepository>()
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt = outcomeEventsController.sendOutcomeEventWithValue("OUTCOME_1", weight)

        /* Then */
        evnt shouldNotBe null
        evnt!!.name shouldBe "OUTCOME_1"
        evnt.notificationIds shouldBe null
        evnt.weight shouldBe weight
        evnt.session shouldBe InfluenceType.UNATTRIBUTED
        evnt.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        coVerify(exactly = 1) { mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", null, evnt) }
    }

    test("send unique outcome with unattributed influences") {
        /* Given */
        val now = 111L
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        every { mockInfluenceManager.influences } returns listOf(Influence(InfluenceChannel.NOTIFICATION, InfluenceType.UNATTRIBUTED, null))

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsRepository = spyk<IOutcomeEventsRepository>()
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt1 = outcomeEventsController.sendUniqueOutcomeEvent("OUTCOME_1")
        val evnt2 = outcomeEventsController.sendUniqueOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt1 shouldNotBe null
        evnt1!!.name shouldBe "OUTCOME_1"
        evnt1.notificationIds shouldBe null
        evnt1.weight shouldBe 0
        evnt1.session shouldBe InfluenceType.UNATTRIBUTED
        evnt1.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        evnt2 shouldBe null

        coVerify(exactly = 1) { mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", any(), any()) }
    }

    test("send unique outcome with same indirect influences") {
        /* Given */
        val now = 111L
        val notificationIds = "[\"id1\",\"id2\"]"
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        val notificationInfluence = Influence(InfluenceChannel.NOTIFICATION, InfluenceType.INDIRECT, JSONArray(notificationIds))
        every { mockInfluenceManager.influences } returns listOf(notificationInfluence)

        val mockOutcomeEventsRepository = mockk<IOutcomeEventsRepository>()
        coEvery { mockOutcomeEventsRepository.getNotCachedUniqueInfluencesForOutcome("OUTCOME_1", any()) } returns listOf(notificationInfluence) andThen listOf()
        coEvery { mockOutcomeEventsRepository.saveUniqueOutcomeEventParams(any()) }

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt1 = outcomeEventsController.sendUniqueOutcomeEvent("OUTCOME_1")
        val evnt2 = outcomeEventsController.sendUniqueOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt1 shouldNotBe null
        evnt1!!.name shouldBe "OUTCOME_1"
        evnt1.notificationIds shouldNotBe null
        evnt1.notificationIds!!.toString() shouldBe notificationIds
        evnt1.weight shouldBe 0
        evnt1.session shouldBe InfluenceType.INDIRECT
        evnt1.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        evnt2 shouldBe null

        coVerify(exactly = 1) { mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", any(), any()) }
    }

    test("send unique outcome with different indirect influences") {
        /* Given */
        val now = 111L
        val notificationIds1 = "[\"id1\",\"id2\"]"
        val notificationIds2 = "[\"id3\",\"id4\"]"
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        val notificationInfluence1 = Influence(InfluenceChannel.NOTIFICATION, InfluenceType.INDIRECT, JSONArray(notificationIds1))
        val notificationInfluence2 = Influence(InfluenceChannel.NOTIFICATION, InfluenceType.DIRECT, JSONArray(notificationIds2))
        every { mockInfluenceManager.influences } returns listOf(notificationInfluence1) andThen listOf(notificationInfluence2)

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsRepository = mockk<IOutcomeEventsRepository>()
        coEvery { mockOutcomeEventsRepository.getNotCachedUniqueInfluencesForOutcome("OUTCOME_1", any()) } returns listOf(notificationInfluence1) andThen listOf(notificationInfluence2)
        coEvery { mockOutcomeEventsRepository.saveUniqueOutcomeEventParams(any()) }

        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = spyk<IOutcomeEventsBackendService>()

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt1 = outcomeEventsController.sendUniqueOutcomeEvent("OUTCOME_1")
        val evnt2 = outcomeEventsController.sendUniqueOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt1 shouldNotBe null
        evnt1!!.name shouldBe "OUTCOME_1"
        evnt1.notificationIds shouldNotBe null
        evnt1.notificationIds!!.toString() shouldBe notificationIds1
        evnt1.weight shouldBe 0
        evnt1.session shouldBe InfluenceType.INDIRECT
        evnt1.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        evnt2 shouldNotBe null
        evnt2!!.name shouldBe "OUTCOME_1"
        evnt2.notificationIds shouldNotBe null
        evnt2.notificationIds!!.toString() shouldBe notificationIds2
        evnt2.weight shouldBe 0
        evnt2.session shouldBe InfluenceType.DIRECT
        evnt2.timestamp shouldBe 0 // timestamp only set when it had to be saved.

        coVerifySequence {
            mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", false, evnt1)
            mockOutcomeEventsBackend.sendOutcomeEvent(MockHelper.DEFAULT_APP_ID, "onesignalId", "subscriptionId", true, evnt2)
        }
    }

    test("send outcome in offline mode") {
        /* Given */
        val now = 111111L
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val mockInfluenceManager = mockk<IInfluenceManager>()
        every { mockInfluenceManager.influences } returns listOf(Influence(InfluenceChannel.NOTIFICATION, InfluenceType.UNATTRIBUTED, null))

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockOutcomeEventsRepository = spyk<IOutcomeEventsRepository>()
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = mockk<IOutcomeEventsBackendService>()
        coEvery { mockOutcomeEventsBackend.sendOutcomeEvent(any(), any(), any(), any(), any()) } throws BackendException(408, null)

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore(),
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        val evnt = outcomeEventsController.sendOutcomeEvent("OUTCOME_1")

        /* Then */
        evnt shouldBe null

        coVerify(exactly = 1) {
            mockOutcomeEventsRepository.saveOutcomeEvent(
                withArg {
                    it.outcomeId shouldBe "OUTCOME_1"
                    it.weight shouldBe 0
                    it.timestamp shouldBe now / 1000
                }
            )
        }
    }

    test("send unsent outcomes on startup") {
        /* Given */
        val now = 111111L
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockInfluenceManager = mockk<IInfluenceManager>()
        val mockOutcomeEventsRepository = mockk<IOutcomeEventsRepository>()
        coEvery { mockOutcomeEventsRepository.cleanCachedUniqueOutcomeEventNotifications() } just runs
        coEvery { mockOutcomeEventsRepository.deleteOldOutcomeEvent(any()) } just runs
        coEvery { mockOutcomeEventsRepository.getAllEventsToSend() } returns listOf(
            OutcomeEventParams("outcomeId1", OutcomeSource(OutcomeSourceBody(JSONArray().put("notificationId1")), null), .4f, 1111),
            OutcomeEventParams("outcomeId2", OutcomeSource(null, OutcomeSourceBody(JSONArray().put("notificationId2").put("notificationId3"))), .2f, 2222)
        )
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = mockk<IOutcomeEventsBackendService>()
        coEvery { mockOutcomeEventsBackend.sendOutcomeEvent(any(), any(), any(), any(), any()) } just runs

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        outcomeEventsController.start()

        delay(1000)

        /* Then */
        coVerify(exactly = 1) {
            mockOutcomeEventsBackend.sendOutcomeEvent(
                "appId",
                "onesignalId",
                "subscriptionId",
                true,
                withArg {
                    it.name shouldBe "outcomeId1"
                    it.weight shouldBe .4f
                    it.timestamp shouldBe 1111
                    it.notificationIds!!.getString(0) shouldBe "notificationId1"
                }
            )
        }
        coVerify(exactly = 1) {
            mockOutcomeEventsBackend.sendOutcomeEvent(
                "appId",
                "onesignalId",
                "subscriptionId",
                false,
                withArg {
                    it.name shouldBe "outcomeId2"
                    it.weight shouldBe .2f
                    it.timestamp shouldBe 2222
                    it.notificationIds!!.getString(0) shouldBe "notificationId2"
                    it.notificationIds!!.getString(1) shouldBe "notificationId3"
                }
            )
        }
        coVerify(exactly = 1) {
            mockOutcomeEventsRepository.deleteOldOutcomeEvent(
                withArg {
                    it.outcomeId shouldBe "outcomeId1"
                    it.timestamp shouldBe 1111
                }
            )
        }
        coVerify(exactly = 1) {
            mockOutcomeEventsRepository.deleteOldOutcomeEvent(
                withArg {
                    it.outcomeId shouldBe "outcomeId2"
                    it.timestamp shouldBe 2222
                }
            )
        }
    }

    test("send unsent outcomes on startup fails gracefully") {
        /* Given */
        val now = 111111L
        val mockSessionService = mockk<ISessionService>()
        every { mockSessionService.subscribe(any()) } just Runs

        val subscriptionModel = SubscriptionModel()
        subscriptionModel.id = "subscriptionId"
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions.push } returns PushSubscription(subscriptionModel)

        val mockInfluenceManager = mockk<IInfluenceManager>()
        val mockOutcomeEventsRepository = mockk<IOutcomeEventsRepository>()
        coEvery { mockOutcomeEventsRepository.cleanCachedUniqueOutcomeEventNotifications() } just runs
        coEvery { mockOutcomeEventsRepository.getAllEventsToSend() } returns listOf(
            OutcomeEventParams("outcomeId1", OutcomeSource(OutcomeSourceBody(JSONArray().put("notificationId1")), null), .4f, 1111),
            OutcomeEventParams("outcomeId2", OutcomeSource(null, OutcomeSourceBody(JSONArray().put("notificationId2").put("notificationId3"))), .2f, 2222)
        )
        val mockOutcomeEventsPreferences = spyk<IOutcomeEventsPreferences>()
        val mockOutcomeEventsBackend = mockk<IOutcomeEventsBackendService>()
        coEvery { mockOutcomeEventsBackend.sendOutcomeEvent(any(), any(), any(), any(), any()) } throws BackendException(408, null)

        val outcomeEventsController = OutcomeEventsController(
            mockSessionService,
            mockInfluenceManager,
            mockOutcomeEventsRepository,
            mockOutcomeEventsPreferences,
            mockOutcomeEventsBackend,
            MockHelper.configModelStore(),
            MockHelper.identityModelStore { it.onesignalId = "onesignalId" },
            mockSubscriptionManager,
            MockHelper.time(now)
        )

        /* When */
        outcomeEventsController.start()

        delay(1000)

        /* Then */
        coVerify(exactly = 1) {
            mockOutcomeEventsBackend.sendOutcomeEvent(
                "appId",
                "onesignalId",
                "subscriptionId",
                true,
                withArg {
                    it.name shouldBe "outcomeId1"
                    it.weight shouldBe .4f
                    it.timestamp shouldBe 1111
                    it.notificationIds!!.getString(0) shouldBe "notificationId1"
                }
            )
        }
        coVerify(exactly = 1) {
            mockOutcomeEventsBackend.sendOutcomeEvent(
                "appId",
                "onesignalId",
                "subscriptionId",
                false,
                withArg {
                    it.name shouldBe "outcomeId2"
                    it.weight shouldBe .2f
                    it.timestamp shouldBe 2222
                    it.notificationIds!!.getString(0) shouldBe "notificationId2"
                    it.notificationIds!!.getString(1) shouldBe "notificationId3"
                }
            )
        }
        coVerify(exactly = 0) { mockOutcomeEventsRepository.deleteOldOutcomeEvent(any()) }
    }
})
