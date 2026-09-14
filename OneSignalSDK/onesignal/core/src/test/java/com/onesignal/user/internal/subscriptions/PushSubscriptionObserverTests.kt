package com.onesignal.user.internal.subscriptions

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.subscriptions.impl.SubscriptionManager
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end coverage for the push subscription observer, kept in its own spec because
 * [IPushSubscriptionObserver] callbacks are delivered through `Dispatchers.Main` and the rest of the
 * subscription specs run without a main dispatcher installed.
 *
 * The chain under test is the real one: a property write on a model held by a [SubscriptionModelStore]
 * notifies the store, which re-broadcasts to [SubscriptionManager], which builds the changed state
 * from the push subscription and hands it to the app's observer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushSubscriptionObserverTests : FunSpec({

    beforeTest {
        Logging.logLevel = LogLevel.NONE
        // Observer callbacks go out via suspendifyOnMain, so tests need a main dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterTest {
        Dispatchers.resetMain()
    }

    /**
     * A store holding one opted-in push subscription, with [SubscriptionManager] subscribed to it and
     * an observer attached. Returns the model to mutate and a latch plus recorded states to assert on.
     */
    fun attachObserverToPushSubscription(): Triple<SubscriptionModel, CountDownLatch, MutableList<PushSubscriptionChangedState>> {
        val pushSubscriptionModel = SubscriptionModel()
        pushSubscriptionModel.id = "subscription1"
        pushSubscriptionModel.type = SubscriptionType.PUSH
        pushSubscriptionModel.address = "pushToken"
        pushSubscriptionModel.status = SubscriptionStatus.SUBSCRIBED
        pushSubscriptionModel.optedIn = true

        val subscriptionModelStore = SubscriptionModelStore(MockPreferencesService())
        subscriptionModelStore.add(pushSubscriptionModel)

        // Constructing the manager subscribes it to the store and builds its subscription list.
        val subscriptionManager =
            SubscriptionManager(
                mockk<IApplicationService>(),
                mockk<ISessionService>(relaxed = true),
                subscriptionModelStore,
            )

        val observedStates = mutableListOf<PushSubscriptionChangedState>()
        val observerCalled = CountDownLatch(1)
        subscriptionManager.subscriptions.push.addObserver(
            object : IPushSubscriptionObserver {
                override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
                    observedStates.add(state)
                    observerCalled.countDown()
                }
            },
        )

        return Triple(pushSubscriptionModel, observerCalled, observedStates)
    }

    // Both codes take the same path, so both are pinned end to end rather than only the one the
    // unit-level tests happen to exercise.
    listOf(
        SubscriptionStatus.MANUALLY_UNSUBSCRIBED,
        SubscriptionStatus.DISABLED_FROM_REST_API,
    ).forEach { remoteDisable ->
        test("hydrating a ${remoteDisable.value} disable reports optedIn false to the app's observer") {
            // Given an opted-in push subscription with an observer attached
            val (pushSubscriptionModel, observerCalled, observedStates) = attachObserverToPushSubscription()

            // When RefreshUser records the server's disable, which it writes with the HYDRATE tag.
            // Nothing on the path from the model to the observer filters on that tag, which is why
            // the app hears about a disable it never asked for locally.
            pushSubscriptionModel.setIntProperty(
                SubscriptionModel::remoteDisabledReason.name,
                remoteDisable.value,
                ModelChangeTags.HYDRATE,
            )

            // Then the observer sees a real transition, not an unchanged pair
            observerCalled.await(5, TimeUnit.SECONDS) shouldBe true
            observedStates.size shouldBe 1
            observedStates[0].previous.optedIn shouldBe true
            observedStates[0].current.optedIn shouldBe false
            // The rest of the state is untouched: only the opt-in answer moved.
            observedStates[0].current.id shouldBe "subscription1"
            observedStates[0].current.token shouldBe "pushToken"
        }
    }
})
