package com.onesignal.user.internal.subscriptions

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.subscriptions.impl.SubscriptionManager
import com.onesignal.user.subscriptions.ISmsSubscription
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify

class SubscriptionManagerTests : FunSpec({

    test("initializes subscriptions from model store") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)
        val pushSubscription = SubscriptionModel()
        pushSubscription.id = "subscription1"
        pushSubscription.type = SubscriptionType.PUSH
        pushSubscription.status = SubscriptionStatus.SUBSCRIBED
        pushSubscription.optedIn = true
        pushSubscription.address = "pushToken"

        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription2"
        emailSubscription.type = SubscriptionType.EMAIL
        emailSubscription.status = SubscriptionStatus.SUBSCRIBED
        emailSubscription.optedIn = false
        emailSubscription.address = "email@email.co"

        val smsSubscription = SubscriptionModel()
        smsSubscription.id = "subscription3"
        smsSubscription.type = SubscriptionType.SMS
        smsSubscription.status = SubscriptionStatus.SUBSCRIBED
        smsSubscription.optedIn = false
        smsSubscription.address = "+15558675309"

        val listOfSubscriptions = listOf(pushSubscription, emailSubscription, smsSubscription)
        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        val subscriptions = subscriptionManager.subscriptions

        // Then
        subscriptions.collection.count() shouldBe 3
        subscriptions.push shouldNotBe null
        subscriptions.push!!.id shouldBe pushSubscription.id
        subscriptions.push!!.token shouldBe pushSubscription.address
        subscriptions.push!!.optedIn shouldBe pushSubscription.optedIn
        subscriptions.emails.count() shouldBe 1
        subscriptions.emails[0].id shouldBe emailSubscription.id
        subscriptions.emails[0].email shouldBe emailSubscription.address
        subscriptions.smss.count() shouldBe 1
        subscriptions.smss[0].id shouldBe smsSubscription.id
        subscriptions.smss[0].number shouldBe smsSubscription.address
    }

    test("add email subscription adds to model store") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val listOfSubscriptions = listOf<SubscriptionModel>()
        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.addEmailSubscription("name@company.com")

        // Then
        verify {
            mockSubscriptionModelStore.add(
                withArg {
                    it.type shouldBe SubscriptionType.EMAIL
                    it.address shouldBe "name@company.com"
                    it.optedIn shouldBe true
                    it.status shouldBe SubscriptionStatus.SUBSCRIBED
                },
            )
        }
    }

    test("add sms subscription adds to model store") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val listOfSubscriptions = listOf<SubscriptionModel>()
        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.addSmsSubscription("+15558675309")

        // Then
        verify {
            mockSubscriptionModelStore.add(
                withArg {
                    it.type shouldBe SubscriptionType.SMS
                    it.address shouldBe "+15558675309"
                    it.optedIn shouldBe true
                    it.status shouldBe SubscriptionStatus.SUBSCRIBED
                },
            )
        }
    }

    test("add push subscription adds to model store") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val listOfSubscriptions = listOf<SubscriptionModel>()
        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.addOrUpdatePushSubscriptionToken("pushToken", SubscriptionStatus.SUBSCRIBED)

        // Then
        verify {
            mockSubscriptionModelStore.add(
                withArg {
                    it.type shouldBe SubscriptionType.PUSH
                    it.address shouldBe "pushToken"
                    it.optedIn shouldBe true
                    it.status shouldBe SubscriptionStatus.SUBSCRIBED
                },
            )
        }
    }

    test("update push subscription updates model store") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val pushSubscription = SubscriptionModel()
        pushSubscription.id = "subscription1"
        pushSubscription.type = SubscriptionType.PUSH
        pushSubscription.status = SubscriptionStatus.SUBSCRIBED
        pushSubscription.optedIn = true
        pushSubscription.address = "pushToken1"

        val listOfSubscriptions = listOf(pushSubscription)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions
        every { mockSubscriptionModelStore.get("subscription1") } returns pushSubscription

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.addOrUpdatePushSubscriptionToken("pushToken2", SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER)

        // Then
        pushSubscription.address shouldBe "pushToken2"
        pushSubscription.status shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER
    }

    test("remove email subscription removes from model store") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription1"
        emailSubscription.type = SubscriptionType.EMAIL
        emailSubscription.status = SubscriptionStatus.SUBSCRIBED
        emailSubscription.optedIn = true
        emailSubscription.address = "name@company.com"

        val listOfSubscriptions = listOf(emailSubscription)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions
        every { mockSubscriptionModelStore.remove("subscription1") } just runs

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.removeEmailSubscription("name@company.com")

        // Then
        verify(exactly = 1) { mockSubscriptionModelStore.remove("subscription1") }
    }

    test("remove sms subscription removes from model store") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription1"
        emailSubscription.type = SubscriptionType.SMS
        emailSubscription.status = SubscriptionStatus.SUBSCRIBED
        emailSubscription.optedIn = true
        emailSubscription.address = "+18458675309"

        val listOfSubscriptions = listOf(emailSubscription)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions
        every { mockSubscriptionModelStore.remove("subscription1") } just runs

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.removeSmsSubscription("+18458675309")

        // Then
        verify(exactly = 1) { mockSubscriptionModelStore.remove("subscription1") }
    }

    test("subscription added when model added") {
        // Given
        val smsSubscription = SubscriptionModel()
        smsSubscription.id = "subscription1"
        smsSubscription.type = SubscriptionType.SMS
        smsSubscription.status = SubscriptionStatus.SUBSCRIBED
        smsSubscription.optedIn = true
        smsSubscription.address = "+18458675309"

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)
        val listOfSubscriptions = listOf<SubscriptionModel>()

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val spySubscriptionChangedHandler = spyk<ISubscriptionChangedHandler>()

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)
        subscriptionManager.subscribe(spySubscriptionChangedHandler)

        // When
        subscriptionManager.onModelAdded(smsSubscription, ModelChangeTags.NORMAL)
        val subscriptions = subscriptionManager.subscriptions

        // Then
        subscriptions.smss.count() shouldBe 1
        subscriptions.smss[0].id shouldBe smsSubscription.id
        subscriptions.smss[0].number shouldBe smsSubscription.address
        verify(exactly = 1) {
            spySubscriptionChangedHandler.onSubscriptionAdded(
                withArg {
                    it.id shouldBe smsSubscription.id
                    it should beInstanceOf<ISmsSubscription>()
                    (it as ISmsSubscription).number shouldBe smsSubscription.address
                },
            )
        }
    }

    test("subscription modified when model updated") {
        // Given
        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription1"
        emailSubscription.type = SubscriptionType.SMS
        emailSubscription.status = SubscriptionStatus.SUBSCRIBED
        emailSubscription.optedIn = true
        emailSubscription.address = "+18458675309"

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)
        val listOfSubscriptions = listOf(emailSubscription)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val spySubscriptionChangedHandler = spyk<ISubscriptionChangedHandler>()

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)
        subscriptionManager.subscribe(spySubscriptionChangedHandler)

        // When
        emailSubscription.address = "+15551234567"
        subscriptionManager.onModelUpdated(
            ModelChangedArgs(
                emailSubscription,
                SubscriptionModel::address.name,
                SubscriptionModel::address.name,
                "+15558675309",
                "+15551234567",
            ),
            ModelChangeTags.NORMAL,
        )
        val subscriptions = subscriptionManager.subscriptions

        // Then
        subscriptions.smss.count() shouldBe 1
        subscriptions.smss[0].id shouldBe emailSubscription.id
        subscriptions.smss[0].number shouldBe "+15551234567"
        verify(exactly = 1) { spySubscriptionChangedHandler.onSubscriptionChanged(any(), any()) }
    }

    test("subscription removed when model removed") {
        // Given
        val smsSubscription = SubscriptionModel()
        smsSubscription.id = "subscription1"
        smsSubscription.type = SubscriptionType.SMS
        smsSubscription.status = SubscriptionStatus.SUBSCRIBED
        smsSubscription.optedIn = true
        smsSubscription.address = "+18458675309"

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)
        val listOfSubscriptions = listOf(smsSubscription)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val spySubscriptionChangedHandler = spyk<ISubscriptionChangedHandler>()

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)
        subscriptionManager.subscribe(spySubscriptionChangedHandler)

        // When
        subscriptionManager.onModelRemoved(smsSubscription, ModelChangeTags.NORMAL)
        val subscriptions = subscriptionManager.subscriptions

        // Then
        subscriptions.smss.count() shouldBe 0
        verify(exactly = 1) {
            spySubscriptionChangedHandler.onSubscriptionRemoved(
                withArg {
                    it.id shouldBe smsSubscription.id
                    it should beInstanceOf<ISmsSubscription>()
                    (it as ISmsSubscription).number shouldBe smsSubscription.address
                },
            )
        }
    }
})
