package com.onesignal.internal

import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager
import com.onesignal.user.subscriptions.IPushSubscription
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.verify
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction2
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMembers

private class NotReadySource<T : Any> : ManagerSource<T> {
    override fun get(member: String): T? = null

    override fun peek(): T? = null

    override suspend fun await(member: String): T? = null

    override fun logQueued(member: String) = Unit
}

private val standIns: Map<KClass<*>, KClass<*>> =
    mapOf(
        IUserManager::class to DeferredUserManager::class,
        IPushSubscription::class to DeferredPushSubscription::class,
        INotificationsManager::class to DeferredNotificationsManager::class,
        IInAppMessagesManager::class to DeferredInAppMessagesManager::class,
        ILocationManager::class to DeferredLocationManager::class,
        ISessionManager::class to DeferredSessionManager::class,
    )

private class ReplayCase<T : Any>(
    val type: KClass<T>,
    val member: String,
    val standIn: (ManagerSource<T>) -> DeferredManager<T>,
    val call: T.() -> Unit,
) {
    fun run() {
        val real = mockkClass(type, relaxed = true)
        val deferred = standIn(NotReadySource())
        @Suppress("UNCHECKED_CAST")
        (deferred as T).call()
        verify(exactly = 0) { real.call() }
        deferred.replay(real)
        verify(exactly = 1) { real.call() }
    }
}

// One listener per case, so the replayed call can be verified against the same instance.
private inline fun <reified T : Any, reified L : Any> registration(
    noinline standIn: (ManagerSource<T>) -> DeferredManager<T>,
    add: KFunction2<T, L, Unit>,
): ReplayCase<T> {
    val listener = mockk<L>(relaxed = true)
    return ReplayCase(T::class, add.name, standIn) { add(this, listener) }
}

private inline fun <reified T : Any> setting(
    noinline standIn: (ManagerSource<T>) -> DeferredManager<T>,
    property: KMutableProperty1<T, Boolean>,
) = ReplayCase(T::class, property.name, standIn) { property.set(this, true) }

private val replayCases: List<ReplayCase<*>> =
    listOf(
        registration(::DeferredUserManager, IUserManager::addObserver),
        registration(::DeferredPushSubscription, IPushSubscription::addObserver),
        registration(::DeferredNotificationsManager, INotificationsManager::addPermissionObserver),
        registration(::DeferredNotificationsManager, INotificationsManager::addForegroundLifecycleListener),
        registration(::DeferredNotificationsManager, INotificationsManager::addClickListener),
        registration(::DeferredInAppMessagesManager, IInAppMessagesManager::addLifecycleListener),
        registration(::DeferredInAppMessagesManager, IInAppMessagesManager::addClickListener),
        setting(::DeferredInAppMessagesManager, IInAppMessagesManager::paused),
        setting(::DeferredLocationManager, ILocationManager::isShared),
    )

private val replayedMember = Regex("^add\\w*(Listener|Observer)$")
private val removedMember = Regex("^remove\\w*(Listener|Observer)$")

private class RemoveCase<T : Any>(
    val type: KClass<T>,
    val member: String,
    val standIn: (ManagerSource<T>) -> DeferredManager<T>,
    val add: T.() -> Unit,
    val remove: T.() -> Unit,
) {
    fun run() {
        val real = mockkClass(type, relaxed = true)
        val deferred = standIn(NotReadySource())
        @Suppress("UNCHECKED_CAST")
        (deferred as T).add()
        @Suppress("UNCHECKED_CAST")
        (deferred as T).remove()
        deferred.replay(real)
        verify(exactly = 0) { real.add() }
    }
}

private inline fun <reified T : Any, reified L : Any> removal(
    noinline standIn: (ManagerSource<T>) -> DeferredManager<T>,
    add: KFunction2<T, L, Unit>,
    remove: KFunction2<T, L, Unit>,
): RemoveCase<T> {
    val listener = mockk<L>(relaxed = true)
    return RemoveCase(T::class, remove.name, standIn, { add(this, listener) }, { remove(this, listener) })
}

private val removeCases: List<RemoveCase<*>> =
    listOf(
        removal(::DeferredUserManager, IUserManager::addObserver, IUserManager::removeObserver),
        removal(::DeferredPushSubscription, IPushSubscription::addObserver, IPushSubscription::removeObserver),
        removal(::DeferredNotificationsManager, INotificationsManager::addPermissionObserver, INotificationsManager::removePermissionObserver),
        removal(::DeferredNotificationsManager, INotificationsManager::addForegroundLifecycleListener, INotificationsManager::removeForegroundLifecycleListener),
        removal(::DeferredNotificationsManager, INotificationsManager::addClickListener, INotificationsManager::removeClickListener),
        removal(::DeferredInAppMessagesManager, IInAppMessagesManager::addLifecycleListener, IInAppMessagesManager::removeLifecycleListener),
        removal(::DeferredInAppMessagesManager, IInAppMessagesManager::addClickListener, IInAppMessagesManager::removeClickListener),
    )

private fun KCallable<*>.signature() = "$name(${parameters.drop(1).joinToString { it.type.toString() }})"

private fun KClass<*>.apiMembers() = members.filter { it.name !in setOf("equals", "hashCode", "toString") }

class DeferredManagersCoverageTests : FunSpec({
    test("every interface member is implemented on its stand-in, not inherited from a default body") {
        val missing =
            standIns.flatMap { (iface, standIn) ->
                val declared = standIn.declaredMembers.map { it.signature() }.toSet()
                iface.apiMembers().map { it.signature() }.filter { it !in declared }
                    .map { "${iface.simpleName}.$it: implement it in ${standIn.simpleName}" }
            }
        missing shouldBe emptyList()
    }

    test("every listener, observer and setting has a replay case") {
        val covered = replayCases.map { "${it.type.simpleName}.${it.member}" }.toSet()
        val uncovered =
            standIns.keys.flatMap { iface ->
                iface.apiMembers().filter { it is KMutableProperty<*> || replayedMember.matches(it.name) }
                    .map { "${iface.simpleName}.${it.name}" }
                    .filter { it !in covered }
            }
        withClue("decide whether each queues before init, then add it to replayCases") { uncovered shouldBe emptyList() }
    }

    test("each queued registration or setting reaches the real manager on replay") {
        replayCases.forEach { case -> withClue("${case.type.simpleName}.${case.member}") { case.run() } }
    }

    test("every remove has an add-then-remove case") {
        val covered = removeCases.map { "${it.type.simpleName}.${it.member}" }.toSet()
        val uncovered =
            standIns.keys.flatMap { iface ->
                iface.apiMembers().filter { removedMember.matches(it.name) }
                    .map { "${iface.simpleName}.${it.name}" }
                    .filter { it !in covered }
            }
        withClue("pair each add with an add-then-remove that expects the real manager to see neither") { uncovered shouldBe emptyList() }
    }

    test("a remove before replay cancels the queued add") {
        removeCases.forEach { case -> withClue("${case.type.simpleName}.${case.member}") { case.run() } }
    }
})
