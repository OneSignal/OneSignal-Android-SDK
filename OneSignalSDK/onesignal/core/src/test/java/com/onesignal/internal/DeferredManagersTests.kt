package com.onesignal.internal

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
import com.onesignal.user.state.IUserStateObserver
import com.onesignal.user.subscriptions.IPushSubscription
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking

private class Readiness<T : Any>(private val real: T) {
    var ready = false

    fun resolve(): T? = if (ready) real else null
}

// Runs every call before ready (nothing reaches the real manager), then after (each one forwards).
private fun <T : Any> assertForwardsOnlyWhenReady(
    real: T,
    readiness: Readiness<T>,
    deferred: T,
    calls: List<T.() -> Unit>,
) {
    calls.forEach { deferred.it() }
    confirmVerified(real)

    readiness.ready = true
    calls.forEach { call ->
        deferred.call()
        verify(exactly = 1) { real.call() }
    }
    confirmVerified(real)
}

class DeferredManagersTests : FunSpec({
    test("DeferredUserManager returns defaults before ready and the real values after") {
        val real = mockk<IUserManager>()
        every { real.onesignalId } returns "osid"
        every { real.externalId } returns "extid"
        every { real.getTags() } returns mapOf("k" to "v")
        val readiness = Readiness(real)
        val deferred = DeferredUserManager(readiness::resolve)

        deferred.onesignalId shouldBe ""
        deferred.externalId shouldBe ""
        deferred.getTags() shouldBe emptyMap()

        readiness.ready = true
        deferred.onesignalId shouldBe "osid"
        deferred.externalId shouldBe "extid"
        deferred.getTags() shouldBe mapOf("k" to "v")
    }

    test("DeferredUserManager forwards every method only once ready") {
        val real = mockk<IUserManager>(relaxed = true)
        val readiness = Readiness(real)
        val observer = mockk<IUserStateObserver>()

        assertForwardsOnlyWhenReady(
            real,
            readiness,
            DeferredUserManager(readiness::resolve),
            listOf(
                { setLanguage("en") },
                { addAlias("label", "id") },
                { addAliases(mapOf("label" to "id")) },
                { removeAlias("label") },
                { removeAliases(listOf("label")) },
                { addEmail("a@b.c") },
                { removeEmail("a@b.c") },
                { addSms("+15555550100") },
                { removeSms("+15555550100") },
                { addTag("key", "value") },
                { addTags(mapOf("key" to "value")) },
                { removeTag("key") },
                { removeTags(listOf("key")) },
                { addObserver(observer) },
                { removeObserver(observer) },
                { trackEvent("event", mapOf("p" to 1)) },
            ),
        )
    }

    test("push subscription captured before ready forwards to the real one once ready") {
        val realPush = mockk<IPushSubscription>(relaxed = true)
        every { realPush.id } returns "subid"
        every { realPush.token } returns "token"
        every { realPush.optedIn } returns true
        val realUser = mockk<IUserManager>()
        every { realUser.pushSubscription } returns realPush
        val readiness = Readiness(realUser)
        val captured = DeferredUserManager(readiness::resolve).pushSubscription
        val observer = mockk<IPushSubscriptionObserver>()

        captured.id shouldBe ""
        captured.token shouldBe ""
        captured.optedIn shouldBe false
        captured.optIn()
        captured.optOut()
        captured.addObserver(observer)
        captured.removeObserver(observer)
        confirmVerified(realPush)

        readiness.ready = true
        captured.id shouldBe "subid"
        captured.token shouldBe "token"
        captured.optedIn shouldBe true
        captured.optIn()
        captured.optOut()
        captured.addObserver(observer)
        captured.removeObserver(observer)
        verify(exactly = 1) {
            realPush.optIn()
            realPush.optOut()
            realPush.addObserver(observer)
            realPush.removeObserver(observer)
        }
    }

    test("DeferredNotificationsManager returns defaults before ready and forwards every method once ready") {
        val real = mockk<INotificationsManager>(relaxed = true)
        every { real.permission } returns true
        every { real.canRequestPermission } returns true
        coEvery { real.requestPermission(any()) } returns true
        val readiness = Readiness(real)
        val deferred = DeferredNotificationsManager(readiness::resolve)
        val permissionObserver = mockk<IPermissionObserver>()
        val lifecycleListener = mockk<INotificationLifecycleListener>()
        val clickListener = mockk<INotificationClickListener>()

        deferred.permission shouldBe false
        deferred.canRequestPermission shouldBe false
        runBlocking { deferred.requestPermission(true) } shouldBe false

        assertForwardsOnlyWhenReady(
            real,
            readiness,
            deferred,
            listOf(
                { removeNotification(1) },
                { removeGroupedNotifications("group") },
                { clearAllNotifications() },
                { addPermissionObserver(permissionObserver) },
                { removePermissionObserver(permissionObserver) },
                { addForegroundLifecycleListener(lifecycleListener) },
                { removeForegroundLifecycleListener(lifecycleListener) },
                { addClickListener(clickListener) },
                { removeClickListener(clickListener) },
            ),
        )

        deferred.permission shouldBe true
        deferred.canRequestPermission shouldBe true
        runBlocking { deferred.requestPermission(true) } shouldBe true
        coVerify(exactly = 1) { real.requestPermission(true) }
    }

    test("DeferredInAppMessagesManager returns defaults before ready and forwards every method once ready") {
        val real = mockk<IInAppMessagesManager>(relaxed = true)
        every { real.paused } returns true
        val readiness = Readiness(real)
        val deferred = DeferredInAppMessagesManager(readiness::resolve)
        val lifecycleListener = mockk<IInAppMessageLifecycleListener>()
        val clickListener = mockk<IInAppMessageClickListener>()

        deferred.paused shouldBe false

        assertForwardsOnlyWhenReady(
            real,
            readiness,
            deferred,
            listOf(
                { paused = true },
                { addTrigger("key", "value") },
                { addTriggers(mapOf("key" to "value")) },
                { removeTrigger("key") },
                { removeTriggers(listOf("key")) },
                { clearTriggers() },
                { addLifecycleListener(lifecycleListener) },
                { removeLifecycleListener(lifecycleListener) },
                { addClickListener(clickListener) },
                { removeClickListener(clickListener) },
            ),
        )

        deferred.paused shouldBe true
    }

    test("DeferredSessionManager forwards every method only once ready") {
        val real = mockk<ISessionManager>(relaxed = true)
        val readiness = Readiness(real)

        assertForwardsOnlyWhenReady(
            real,
            readiness,
            DeferredSessionManager(readiness::resolve),
            listOf(
                { addOutcome("outcome") },
                { addUniqueOutcome("outcome") },
                { addOutcomeWithValue("outcome", 1.5f) },
            ),
        )
    }

    test("DeferredLocationManager returns defaults before ready and forwards every method once ready") {
        val real = mockk<ILocationManager>(relaxed = true)
        every { real.isShared } returns true
        coEvery { real.requestPermission() } returns true
        val readiness = Readiness(real)
        val deferred = DeferredLocationManager(readiness::resolve)

        deferred.isShared shouldBe false
        runBlocking { deferred.requestPermission() } shouldBe false

        assertForwardsOnlyWhenReady(real, readiness, deferred, listOf({ isShared = true }))

        deferred.isShared shouldBe true
        runBlocking { deferred.requestPermission() } shouldBe true
    }
})
