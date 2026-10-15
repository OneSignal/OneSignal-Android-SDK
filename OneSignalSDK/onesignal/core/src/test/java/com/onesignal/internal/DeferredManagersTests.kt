package com.onesignal.internal

import com.onesignal.IUserJwtInvalidatedListener
import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationsManager
import com.onesignal.notifications.IPermissionObserver
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.subscriptions.IPushSubscription
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking

private class FakeSource<T : Any>(private val real: T) : ManagerSource<T> {
    var ready = false
    val ignored = mutableListOf<String>()
    val queued = mutableListOf<String>()
    val awaited = mutableListOf<String>()

    override fun get(member: String): T? = if (ready) real else null.also { ignored += member }

    override fun peek(): T? = if (ready) real else null

    override suspend fun await(member: String): T? {
        awaited += member
        return get(member)
    }

    override fun logQueued(member: String) {
        queued += member
    }
}

// Before ready, nothing reaches the real manager and each call is logged by name; after, each forwards once.
private fun <T : Any> assertForwardsOnlyWhenReady(
    real: T,
    source: FakeSource<T>,
    deferred: T,
    calls: List<Pair<String, T.() -> Unit>>,
) {
    calls.forEach { (_, call) -> deferred.call() }
    confirmVerified(real)
    source.ignored shouldContainExactly calls.map { it.first }

    source.ready = true
    calls.forEach { (_, call) ->
        deferred.call()
        verify(exactly = 1) { real.call() }
    }
    confirmVerified(real)
}

// Before ready, registrations are queued (not forwarded); replay applies them in order, once.
private fun <T : Any> assertQueuesAndReplays(
    real: T,
    source: FakeSource<T>,
    deferred: DeferredManager<T>,
    calls: List<Pair<String, T.() -> Unit>>,
) {
    @Suppress("UNCHECKED_CAST")
    val manager = deferred as T
    calls.forEach { (_, call) -> manager.call() }
    confirmVerified(real)
    source.queued shouldContainExactly calls.map { it.first }
    source.ignored shouldBe emptyList()

    source.ready = true
    deferred.replay(real)
    verifyOrder { calls.forEach { (_, call) -> real.call() } }
    deferred.replay(real)
    calls.forEach { (_, call) -> verify(exactly = 1) { real.call() } }
}

class DeferredManagersTests : FunSpec({
    test("DeferredUserManager returns defaults before ready and the real values after") {
        val real = mockk<IUserManager>()
        every { real.onesignalId } returns "osid"
        every { real.externalId } returns "extid"
        every { real.getTags() } returns mapOf("k" to "v")
        val source = FakeSource(real)
        val deferred = DeferredUserManager(source)

        deferred.onesignalId shouldBe ""
        deferred.externalId shouldBe ""
        deferred.getTags() shouldBe emptyMap()

        source.ready = true
        deferred.onesignalId shouldBe "osid"
        deferred.externalId shouldBe "extid"
        deferred.getTags() shouldBe mapOf("k" to "v")
    }

    test("DeferredUserManager forwards every action only once ready") {
        val real = mockk<IUserManager>(relaxed = true)
        val source = FakeSource(real)

        assertForwardsOnlyWhenReady(
            real,
            source,
            DeferredUserManager(source),
            listOf(
                "setLanguage" to { setLanguage("en") },
                "addAlias" to { addAlias("label", "id") },
                "addAliases" to { addAliases(mapOf("label" to "id")) },
                "removeAlias" to { removeAlias("label") },
                "removeAliases" to { removeAliases(listOf("label")) },
                "addEmail" to { addEmail("a@b.c") },
                "removeEmail" to { removeEmail("a@b.c") },
                "addSms" to { addSms("+15555550100") },
                "removeSms" to { removeSms("+15555550100") },
                "addTag" to { addTag("key", "value") },
                "addTags" to { addTags(mapOf("key" to "value")) },
                "removeTag" to { removeTag("key") },
                "removeTags" to { removeTags(listOf("key")) },
                "trackEvent" to { trackEvent("event", mapOf("p" to 1)) },
            ),
        )
    }

    test("DeferredUserManager queues observer registrations and replays them") {
        val real = mockk<IUserManager>(relaxed = true)
        val source = FakeSource(real)
        val observer = mockk<IUserStateObserver>()

        assertQueuesAndReplays(
            real,
            source,
            DeferredUserManager(source),
            listOf(
                "addObserver" to { addObserver(observer) },
            ),
        )
    }

    test("push subscription captured before ready forwards actions and replays observers") {
        val realPush = mockk<IPushSubscription>(relaxed = true)
        every { realPush.id } returns "subid"
        every { realPush.token } returns "token"
        every { realPush.optedIn } returns true
        val realUser = mockk<IUserManager>(relaxed = true)
        every { realUser.pushSubscription } returns realPush
        val source = FakeSource(realUser)
        val deferredUser = DeferredUserManager(source)
        val captured = deferredUser.pushSubscription
        val observer = mockk<IPushSubscriptionObserver>()

        captured.id shouldBe ""
        captured.token shouldBe ""
        captured.optedIn shouldBe false
        captured.optIn()
        captured.optOut()
        captured.addObserver(observer)
        confirmVerified(realPush)
        source.ignored shouldContainExactly
            listOf("pushSubscription.id", "pushSubscription.token", "pushSubscription.optedIn", "pushSubscription.optIn", "pushSubscription.optOut")
        source.queued shouldContainExactly listOf("pushSubscription.addObserver")

        source.ready = true
        deferredUser.replay(realUser)
        verify(exactly = 1) { realPush.addObserver(observer) }
        captured.id shouldBe "subid"
        captured.token shouldBe "token"
        captured.optedIn shouldBe true
        captured.optIn()
        captured.removeObserver(observer)
        verify(exactly = 1) {
            realPush.optIn()
            realPush.removeObserver(observer)
        }
    }

    test("DeferredNotificationsManager returns defaults before ready, forwards actions and replays listeners") {
        val real = mockk<INotificationsManager>(relaxed = true)
        every { real.permission } returns true
        every { real.canRequestPermission } returns true
        coEvery { real.requestPermission(any()) } returns true
        val source = FakeSource(real)
        val deferred = DeferredNotificationsManager(source)
        val permissionObserver = mockk<IPermissionObserver>()
        val lifecycleListener = mockk<INotificationLifecycleListener>()
        val clickListener = mockk<INotificationClickListener>()

        deferred.permission shouldBe false
        deferred.canRequestPermission shouldBe false
        runBlocking { deferred.requestPermission(true) } shouldBe false
        source.ignored.clear()

        assertQueuesAndReplays(
            real,
            source,
            deferred,
            listOf(
                "addPermissionObserver" to { addPermissionObserver(permissionObserver) },
                "addForegroundLifecycleListener" to { addForegroundLifecycleListener(lifecycleListener) },
                "addClickListener" to { addClickListener(clickListener) },
            ),
        )

        source.ready = false
        assertForwardsOnlyWhenReady(
            real,
            source,
            deferred,
            listOf(
                "removeNotification" to { removeNotification(1) },
                "removeGroupedNotifications" to { removeGroupedNotifications("group") },
                "clearAllNotifications" to { clearAllNotifications() },
            ),
        )

        deferred.permission shouldBe true
        deferred.canRequestPermission shouldBe true
        runBlocking { deferred.requestPermission(true) } shouldBe true
        coVerify(exactly = 1) { real.requestPermission(true) }
    }

    test("DeferredInAppMessagesManager forwards actions and replays listeners and paused") {
        val real = mockk<IInAppMessagesManager>(relaxed = true)
        every { real.paused } returns true
        val source = FakeSource(real)
        val deferred = DeferredInAppMessagesManager(source)
        val lifecycleListener = mockk<IInAppMessageLifecycleListener>()
        val clickListener = mockk<IInAppMessageClickListener>()

        deferred.paused shouldBe false
        source.ignored.clear()

        assertQueuesAndReplays(
            real,
            source,
            deferred,
            listOf(
                "paused" to { paused = true },
                "addLifecycleListener" to { addLifecycleListener(lifecycleListener) },
                "addClickListener" to { addClickListener(clickListener) },
            ),
        )

        source.ready = false
        assertForwardsOnlyWhenReady(
            real,
            source,
            deferred,
            listOf(
                "addTrigger" to { addTrigger("key", "value") },
                "addTriggers" to { addTriggers(mapOf("key" to "value")) },
                "removeTrigger" to { removeTrigger("key") },
                "removeTriggers" to { removeTriggers(listOf("key")) },
                "clearTriggers" to { clearTriggers() },
            ),
        )

        deferred.paused shouldBe true
    }

    test("DeferredSessionManager forwards every action only once ready") {
        val real = mockk<ISessionManager>(relaxed = true)
        val source = FakeSource(real)

        assertForwardsOnlyWhenReady(
            real,
            source,
            DeferredSessionManager(source),
            listOf(
                "addOutcome" to { addOutcome("outcome") },
                "addUniqueOutcome" to { addUniqueOutcome("outcome") },
                "addOutcomeWithValue" to { addOutcomeWithValue("outcome", 1.5f) },
            ),
        )
    }

    test("DeferredLocationManager replays isShared and suspends for requestPermission") {
        val real = mockk<ILocationManager>(relaxed = true)
        every { real.isShared } returns true
        coEvery { real.requestPermission() } returns true
        val source = FakeSource(real)
        val deferred = DeferredLocationManager(source)

        deferred.isShared shouldBe false
        runBlocking { deferred.requestPermission() } shouldBe false
        source.ignored.clear()

        assertQueuesAndReplays(real, source, deferred, listOf("isShared" to { isShared = true }))

        deferred.isShared shouldBe true
        runBlocking { deferred.requestPermission() } shouldBe true
        source.awaited shouldContainExactly listOf("requestPermission", "requestPermission")
    }

    test("suspend members resolve through await, never the blocking get") {
        val real = mockk<INotificationsManager>(relaxed = true)
        val source = FakeSource(real).apply { ready = true }

        runBlocking { DeferredNotificationsManager(source).requestPermission(false) }

        source.awaited shouldContainExactly listOf("requestPermission")
    }

    test("a registration made after replay forwards instead of queueing") {
        val real = mockk<IUserManager>(relaxed = true)
        val source = FakeSource(real)
        val deferred = DeferredUserManager(source)
        val observer = mockk<IUserStateObserver>()

        deferred.replay(real)
        deferred.addObserver(observer)

        source.queued shouldBe emptyList()
        verify(exactly = 0) { real.addObserver(observer) }
        source.ignored shouldContainExactly listOf("addObserver")
    }

    test("a replayed call that throws does not stop the rest") {
        val real = mockk<IInAppMessagesManager>(relaxed = true)
        val clickListener = mockk<IInAppMessageClickListener>()
        every { real.paused = any() } throws IllegalStateException("module missing")
        val source = FakeSource(real)
        val deferred = DeferredInAppMessagesManager(source)
        com.onesignal.debug.internal.logging.Logging.logLevel = com.onesignal.debug.LogLevel.NONE

        deferred.paused = true
        deferred.addClickListener(clickListener)
        deferred.replay(real)

        verify(exactly = 1) { real.addClickListener(clickListener) }
    }

    test("a removal before replay cancels its queued add, so add and remove pairs never pile up") {
        val real = mockk<INotificationsManager>(relaxed = true)
        val deferred = DeferredNotificationsManager(FakeSource(real))
        val kept = mockk<INotificationClickListener>()

        repeat(1_000) {
            val listener = mockk<INotificationClickListener>()
            deferred.addClickListener(listener)
            deferred.removeClickListener(listener)
        }
        deferred.addClickListener(kept)
        val pending = DeferredManager::class.java.getDeclaredField("pending").apply { isAccessible = true }
        (pending.get(deferred) as List<*>).size shouldBe 1

        deferred.replay(real)
        verify(exactly = 1) { real.addClickListener(any()) }
        verify(exactly = 1) { real.addClickListener(kept) }
        verify(exactly = 0) { real.removeClickListener(any()) }
    }

    test("a removal cancels only the matching queued add of the same kind") {
        val real = mockk<IUserManager>(relaxed = true)
        every { real.pushSubscription } returns mockk(relaxed = true)
        val deferred = DeferredUserManager(FakeSource(real))
        val first = mockk<IUserStateObserver>()
        val second = mockk<IUserStateObserver>()

        deferred.addObserver(first)
        deferred.addObserver(second)
        deferred.removeObserver(first)
        deferred.replay(real)

        verify(exactly = 0) { real.addObserver(first) }
        verify(exactly = 1) { real.addObserver(second) }
    }

    test("a setting changed twice before replay applies only its latest value, and reads back before init") {
        val iam = mockk<IInAppMessagesManager>(relaxed = true)
        val iamSource = FakeSource(iam)
        val deferredIam = DeferredInAppMessagesManager(iamSource)
        val location = mockk<ILocationManager>(relaxed = true)
        val locationSource = FakeSource(location)
        val deferredLocation = DeferredLocationManager(locationSource)

        deferredIam.paused = true
        deferredIam.paused = false
        deferredLocation.isShared = false
        deferredLocation.isShared = true

        deferredIam.paused shouldBe false
        deferredLocation.isShared shouldBe true
        iamSource.ignored shouldBe emptyList()
        locationSource.ignored shouldBe emptyList()

        deferredIam.replay(iam)
        deferredLocation.replay(location)
        verify(exactly = 1) { iam.paused = false }
        verify(exactly = 0) { iam.paused = true }
        verify(exactly = 1) { location.isShared = true }
        verify(exactly = 0) { location.isShared = false }
    }

    test("JWT invalidated listeners are queued before ready, replayed once, and a removal cancels its add") {
        val store = mockk<JwtTokenStore>(relaxed = true)
        val source = FakeSource(store)
        val deferred = DeferredJwtListeners(source)
        val kept = mockk<IUserJwtInvalidatedListener>()
        val cancelled = mockk<IUserJwtInvalidatedListener>()

        deferred.add(kept)
        deferred.add(cancelled)
        deferred.remove(cancelled)
        confirmVerified(store)
        source.ignored shouldBe emptyList()

        deferred.replay(store)
        deferred.replay(store)
        verify(exactly = 1) { store.addUserJwtInvalidatedListener(kept) }
        verify(exactly = 0) { store.addUserJwtInvalidatedListener(cancelled) }
        verify(exactly = 0) { store.removeUserJwtInvalidatedListener(any()) }
    }
})
