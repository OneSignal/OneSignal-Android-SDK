package com.onesignal.user.internal.subscriptions

import com.onesignal.common.IDManager.LOCAL_PREFIX
import com.onesignal.common.PIIHasher
import com.onesignal.common.modeling.IModelChangedHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.Subscription
import com.onesignal.user.internal.subscriptions.impl.SubscriptionManager
import com.onesignal.user.subscriptions.ISmsSubscription
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify

class SubscriptionManagerTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

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

        // When a new token arrives (a successful registration always reports SUBSCRIBED with a
        // token; an error always reports a null token, never a token paired with an error status)
        subscriptionManager.addOrUpdatePushSubscriptionToken("pushToken2", SubscriptionStatus.SUBSCRIBED)

        // Then
        pushSubscription.address shouldBe "pushToken2"
        pushSubscription.status shouldBe SubscriptionStatus.SUBSCRIBED
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

    // This is a common case where updates (such as optedIn) should
    // still propagate even if we haven't sent the POST /users create
    // call yet. Motivation for this test was a bug was discovered
    // where calling OneSignal.User.pushSubscription.optIn() was not
    // prompting for notification permission if it was called before
    // the create User network call finished.
    test("subscription modified when model updated, but with local-id") {
        // Given
        val pushSubscriptionModel = SubscriptionModel()
        pushSubscriptionModel.id = "${LOCAL_PREFIX}subscription1"
        pushSubscriptionModel.type = SubscriptionType.PUSH
        pushSubscriptionModel.optedIn = true
        pushSubscriptionModel.address = "my_push_token-org"

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)
        val listOfSubscriptions = listOf(pushSubscriptionModel)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions

        val spySubscriptionChangedHandler = spyk<ISubscriptionChangedHandler>()

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)
        subscriptionManager.subscribe(spySubscriptionChangedHandler)

        // When
        pushSubscriptionModel.address = "my_push_token-new"
        subscriptionManager.onModelUpdated(
            ModelChangedArgs(
                pushSubscriptionModel,
                SubscriptionModel::address.name,
                SubscriptionModel::address.name,
                "my_push_token-org",
                "my_push_token-new",
            ),
            ModelChangeTags.NORMAL,
        )
        val subscriptions = subscriptionManager.subscriptions

        // Then
        (subscriptions.push as Subscription).model shouldBeSameInstanceAs pushSubscriptionModel
        subscriptions.push.token shouldBe "my_push_token-new"
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

    test("remove email subscription matches hashed address (pre-hydration)") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription1"
        emailSubscription.type = SubscriptionType.EMAIL
        emailSubscription.status = SubscriptionStatus.SUBSCRIBED
        emailSubscription.optedIn = true
        emailSubscription.address = PIIHasher.hash("name@company.com")

        val listOfSubscriptions = listOf(emailSubscription)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions
        every { mockSubscriptionModelStore.remove("subscription1") } just runs

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When — raw email is passed but model has hashed address
        subscriptionManager.removeEmailSubscription("name@company.com")

        // Then
        verify(exactly = 1) { mockSubscriptionModelStore.remove("subscription1") }
    }

    test("remove sms subscription matches hashed address (pre-hydration)") {
        // Given
        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        val smsSubscription = SubscriptionModel()
        smsSubscription.id = "subscription1"
        smsSubscription.type = SubscriptionType.SMS
        smsSubscription.status = SubscriptionStatus.SUBSCRIBED
        smsSubscription.optedIn = true
        smsSubscription.address = PIIHasher.hash("+18458675309")

        val listOfSubscriptions = listOf(smsSubscription)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.add(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOfSubscriptions
        every { mockSubscriptionModelStore.remove("subscription1") } just runs

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When — raw phone is passed but model has hashed address
        subscriptionManager.removeSmsSubscription("+18458675309")

        // Then
        verify(exactly = 1) { mockSubscriptionModelStore.remove("subscription1") }
    }

    test("email getter returns empty string when address is hashed") {
        // Given
        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription1"
        emailSubscription.type = SubscriptionType.EMAIL
        emailSubscription.address = PIIHasher.hash("user@example.com")

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(emailSubscription)

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        val subscriptions = subscriptionManager.subscriptions

        // Then — public getter returns "" for hashed address
        subscriptions.emails.count() shouldBe 1
        subscriptions.emails[0].email shouldBe ""
    }

    test("email getter returns raw value when address is not hashed") {
        // Given
        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription1"
        emailSubscription.type = SubscriptionType.EMAIL
        emailSubscription.address = "user@example.com"

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(emailSubscription)

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        val subscriptions = subscriptionManager.subscriptions

        // Then
        subscriptions.emails[0].email shouldBe "user@example.com"
    }

    test("sms getter returns empty string when address is hashed") {
        // Given
        val smsSubscription = SubscriptionModel()
        smsSubscription.id = "subscription1"
        smsSubscription.type = SubscriptionType.SMS
        smsSubscription.address = PIIHasher.hash("+15558675309")

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(smsSubscription)

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        val subscriptions = subscriptionManager.subscriptions

        // Then — public getter returns "" for hashed address
        subscriptions.smss.count() shouldBe 1
        subscriptions.smss[0].number shouldBe ""
    }

    test("sms getter returns raw value when address is not hashed") {
        // Given
        val smsSubscription = SubscriptionModel()
        smsSubscription.id = "subscription1"
        smsSubscription.type = SubscriptionType.SMS
        smsSubscription.address = "+15558675309"

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(smsSubscription)

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        val subscriptions = subscriptionManager.subscriptions

        // Then
        subscriptions.smss[0].number shouldBe "+15558675309"
    }

    test("getByEmail finds subscription with hashed address") {
        // Given
        val emailSubscription = SubscriptionModel()
        emailSubscription.id = "subscription1"
        emailSubscription.type = SubscriptionType.EMAIL
        emailSubscription.address = PIIHasher.hash("user@example.com")

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(emailSubscription)

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        val result = subscriptionManager.subscriptions.getByEmail("user@example.com")

        // Then
        result shouldNotBe null
        result!!.id shouldBe "subscription1"
    }

    test("getBySMS finds subscription with hashed address") {
        // Given
        val smsSubscription = SubscriptionModel()
        smsSubscription.id = "subscription1"
        smsSubscription.type = SubscriptionType.SMS
        smsSubscription.address = PIIHasher.hash("+15558675309")

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)

        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(smsSubscription)

        val subscriptionManager = SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        val result = subscriptionManager.subscriptions.getBySMS("+15558675309")

        // Then
        result shouldNotBe null
        result!!.id shouldBe "subscription1"
    }

    // A transient, tokenless FCM/HMS token-fetch failure (e.g. -9 SERVICE_NOT_AVAILABLE) must not
    // downgrade a push subscription that is already SUBSCRIBED with a valid cached token. A failed
    // registration reports a null token alongside the error status, so the cached address is left
    // intact and persisting the error would spuriously unsubscribe the device on the backend.
    test("transient tokenless token-fetch error does not downgrade a healthy SUBSCRIBED push subscription") {
        val retryableErrors =
            listOf(
                SubscriptionStatus.FIREBASE_FCM_INIT_ERROR,
                SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE,
                SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER,
                SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION,
                SubscriptionStatus.HMS_TOKEN_TIMEOUT,
                SubscriptionStatus.HMS_API_EXCEPTION_OTHER,
                SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED,
            )

        for (status in retryableErrors) {
            // Given a healthy push subscription
            val pushSubscription = SubscriptionModel()
            pushSubscription.id = "subscription1"
            pushSubscription.type = SubscriptionType.PUSH
            pushSubscription.status = SubscriptionStatus.SUBSCRIBED
            pushSubscription.optedIn = true
            pushSubscription.address = "validToken"

            val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
            val mockApplicationService = mockk<IApplicationService>()
            val mockSessionService = mockk<ISessionService>(relaxed = true)
            every { mockSubscriptionModelStore.subscribe(any()) } just runs
            every { mockSubscriptionModelStore.list() } returns listOf(pushSubscription)

            val subscriptionManager =
                SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

            val changeHandler = mockk<IModelChangedHandler>(relaxed = true)
            pushSubscription.subscribe(changeHandler)

            // When a failed registration reports a null token alongside the transient error
            subscriptionManager.addOrUpdatePushSubscriptionToken(null, status)

            // Then the healthy SUBSCRIBED state and cached token are preserved
            pushSubscription.status shouldBe SubscriptionStatus.SUBSCRIBED
            pushSubscription.address shouldBe "validToken"
            // And no model change is emitted, so no backend subscription-update operation is generated
            verify(exactly = 0) { changeHandler.onChanged(any(), any()) }
        }
    }

    // The guard must be narrow: real opt-outs / permission changes are also tokenless, but they are
    // not retryable token errors and must continue to downgrade the subscription as before.
    test("non-retryable tokenless statuses still downgrade a SUBSCRIBED push subscription") {
        val nonRetryableStatuses =
            listOf(
                SubscriptionStatus.NO_PERMISSION,
                SubscriptionStatus.UNSUBSCRIBE,
                SubscriptionStatus.DISABLED_FROM_REST_API_DEFAULT_REASON,
            )

        for (status in nonRetryableStatuses) {
            // Given a healthy push subscription
            val pushSubscription = SubscriptionModel()
            pushSubscription.id = "subscription1"
            pushSubscription.type = SubscriptionType.PUSH
            pushSubscription.status = SubscriptionStatus.SUBSCRIBED
            pushSubscription.optedIn = true
            pushSubscription.address = "validToken"

            val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
            val mockApplicationService = mockk<IApplicationService>()
            val mockSessionService = mockk<ISessionService>(relaxed = true)
            every { mockSubscriptionModelStore.subscribe(any()) } just runs
            every { mockSubscriptionModelStore.list() } returns listOf(pushSubscription)

            val subscriptionManager =
                SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

            val changeHandler = mockk<IModelChangedHandler>(relaxed = true)
            pushSubscription.subscribe(changeHandler)

            // When
            subscriptionManager.addOrUpdatePushSubscriptionToken(null, status)

            // Then the downgrade is applied
            pushSubscription.status shouldBe status
            // And the change is emitted so the backend subscription is updated
            verify(exactly = 1) { changeHandler.onChanged(any(), any()) }
        }
    }

    test("transient tokenless token-fetch error is persisted when there is no cached token to protect") {
        // Given a SUBSCRIBED push subscription with no cached token (nothing to protect)
        val pushSubscription = SubscriptionModel()
        pushSubscription.id = "subscription1"
        pushSubscription.type = SubscriptionType.PUSH
        pushSubscription.status = SubscriptionStatus.SUBSCRIBED
        pushSubscription.optedIn = true
        pushSubscription.address = ""

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)
        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(pushSubscription)

        val subscriptionManager =
            SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.addOrUpdatePushSubscriptionToken(
            null,
            SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE,
        )

        // Then the error is persisted (the guard only protects an already-healthy, tokened subscription)
        pushSubscription.status shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE
    }

    test("transient tokenless token-fetch error is persisted when the subscription is not already SUBSCRIBED") {
        // Given a push subscription that is already in a non-subscribed state
        val pushSubscription = SubscriptionModel()
        pushSubscription.id = "subscription1"
        pushSubscription.type = SubscriptionType.PUSH
        pushSubscription.status = SubscriptionStatus.NO_PERMISSION
        pushSubscription.optedIn = true
        pushSubscription.address = "validToken"

        val mockSubscriptionModelStore = mockk<SubscriptionModelStore>()
        val mockApplicationService = mockk<IApplicationService>()
        val mockSessionService = mockk<ISessionService>(relaxed = true)
        every { mockSubscriptionModelStore.subscribe(any()) } just runs
        every { mockSubscriptionModelStore.list() } returns listOf(pushSubscription)

        val subscriptionManager =
            SubscriptionManager(mockApplicationService, mockSessionService, mockSubscriptionModelStore)

        // When
        subscriptionManager.addOrUpdatePushSubscriptionToken(
            null,
            SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE,
        )

        // Then the status is updated (there is no healthy SUBSCRIBED state to protect)
        pushSubscription.status shouldBe SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE
    }

    test("SubscriptionStatus.isRetryableTokenError flags exactly the transient token-fetch errors") {
        // Given the full enum
        // When filtering by the new flag
        val retryable = SubscriptionStatus.values().filter { it.isRetryableTokenError }.toSet()

        // Then it is exactly the transient FCM/HMS token-fetch errors and nothing else
        retryable shouldBe
            setOf(
                SubscriptionStatus.FIREBASE_FCM_INIT_ERROR,
                SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE,
                SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER,
                SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION,
                SubscriptionStatus.HMS_TOKEN_TIMEOUT,
                SubscriptionStatus.HMS_API_EXCEPTION_OTHER,
                SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED,
            )
    }

    test("SubscriptionStatus.isRetryableTokenError is false for healthy and user-driven statuses") {
        // SUBSCRIBED, permission/opt-out, and permanent configuration errors must never be treated
        // as transient, otherwise the guard would mask real subscription state changes.
        listOf(
            SubscriptionStatus.SUBSCRIBED,
            SubscriptionStatus.NO_PERMISSION,
            SubscriptionStatus.UNSUBSCRIBE,
            SubscriptionStatus.INVALID_FCM_SENDER_ID,
            SubscriptionStatus.OUTDATED_GOOGLE_PLAY_SERVICES_APP,
            SubscriptionStatus.HMS_ARGUMENTS_INVALID,
            SubscriptionStatus.DISABLED_FROM_REST_API_DEFAULT_REASON,
            SubscriptionStatus.ERROR,
        ).forEach { it.isRetryableTokenError shouldBe false }
    }
})
