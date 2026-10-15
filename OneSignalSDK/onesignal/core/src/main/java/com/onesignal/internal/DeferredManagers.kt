package com.onesignal.internal

import com.onesignal.IUserJwtInvalidatedListener
import com.onesignal.debug.internal.logging.Logging
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

/** Resolves the real manager behind a deferred one. [member] names the call in logs. */
internal interface ManagerSource<T : Any> {
    /** The real manager once init succeeds, else null after logging that [member] was ignored. */
    fun get(member: String): T?

    /** Like [get], but silent. */
    fun peek(): T?

    suspend fun await(member: String): T?

    fun logQueued(member: String)
}

// Handed out before init; apps may keep it, so every call re-resolves. Registrations are queued and
// replayed in order on success (a removal cancels its queued add, a setting keeps its latest value);
// other calls are logged and dropped.
internal open class DeferredManager<T : Any>(
    protected val source: ManagerSource<T>,
) {
    private class Pending<T>(val member: String, val target: Any?, val call: (T) -> Unit)

    private val pending = mutableListOf<Pending<T>>()
    private var replayed = false

    protected fun registerOrQueue(
        member: String,
        target: Any,
        call: (T) -> Unit,
    ) = forwardOrQueue(member, call) { pending.add(Pending(member, target, call)) }

    // Before replay the real manager holds nothing, so a removal only has to cancel the queued add.
    protected fun unregisterOrCancel(
        member: String,
        addMember: String,
        target: Any,
        call: (T) -> Unit,
    ) = forwardOrQueue(member, call) {
        val index = pending.indexOfLast { it.member == addMember && it.target === target }
        if (index >= 0) pending.removeAt(index)
    }

    // [onQueued] runs under the queue lock, so a getter cache it sets always matches what replays.
    protected fun setOrQueue(
        member: String,
        onQueued: () -> Unit,
        call: (T) -> Unit,
    ) = forwardOrQueue(member, call) {
        pending.removeAll { it.member == member }
        pending.add(Pending(member, null, call))
        onQueued()
    }

    private fun forwardOrQueue(
        member: String,
        call: (T) -> Unit,
        enqueue: () -> Unit,
    ) {
        source.peek()?.let {
            call(it)
            return
        }
        val queued = synchronized(pending) { if (!replayed) true.also { enqueue() } else false }
        if (queued) source.logQueued(member) else source.get(member)?.let(call)
    }

    @Suppress("TooGenericExceptionCaught")
    open fun replay(real: T) {
        val calls =
            synchronized(pending) {
                replayed = true
                pending.toList().also { pending.clear() }
            }
        for (entry in calls) {
            try {
                entry.call(real)
            } catch (e: Exception) {
                Logging.error("Replaying '${entry.member}' after initWithContext failed", e)
            }
        }
    }
}

@Suppress("TooManyFunctions") // one per IUserManager member
internal class DeferredUserManager(
    source: ManagerSource<IUserManager>,
) : DeferredManager<IUserManager>(source), IUserManager {
    private val deferredPush = DeferredPushSubscription(PushSubscriptionSource(source))

    override val pushSubscription: IPushSubscription get() = source.peek()?.pushSubscription ?: deferredPush
    override val onesignalId: String get() = source.get("onesignalId")?.onesignalId ?: ""
    override val externalId: String get() = source.get("externalId")?.externalId ?: ""

    override fun setLanguage(value: String) = source.get("setLanguage")?.setLanguage(value) ?: Unit

    override fun addAlias(
        label: String,
        id: String,
    ) = source.get("addAlias")?.addAlias(label, id) ?: Unit

    override fun addAliases(aliases: Map<String, String>) = source.get("addAliases")?.addAliases(aliases) ?: Unit

    override fun removeAlias(label: String) = source.get("removeAlias")?.removeAlias(label) ?: Unit

    override fun removeAliases(labels: Collection<String>) = source.get("removeAliases")?.removeAliases(labels) ?: Unit

    override fun addEmail(email: String) = source.get("addEmail")?.addEmail(email) ?: Unit

    override fun removeEmail(email: String) = source.get("removeEmail")?.removeEmail(email) ?: Unit

    override fun addSms(sms: String) = source.get("addSms")?.addSms(sms) ?: Unit

    override fun removeSms(sms: String) = source.get("removeSms")?.removeSms(sms) ?: Unit

    override fun addTag(
        key: String,
        value: String,
    ) = source.get("addTag")?.addTag(key, value) ?: Unit

    override fun addTags(tags: Map<String, String>) = source.get("addTags")?.addTags(tags) ?: Unit

    override fun removeTag(key: String) = source.get("removeTag")?.removeTag(key) ?: Unit

    override fun removeTags(keys: Collection<String>) = source.get("removeTags")?.removeTags(keys) ?: Unit

    override fun getTags(): Map<String, String> = source.get("getTags")?.getTags() ?: emptyMap()

    override fun addObserver(observer: IUserStateObserver) = registerOrQueue("addObserver", observer) { it.addObserver(observer) }

    override fun removeObserver(observer: IUserStateObserver) = unregisterOrCancel("removeObserver", "addObserver", observer) { it.removeObserver(observer) }

    override fun trackEvent(
        name: String,
        properties: Map<String, Any?>?,
    ) = source.get("trackEvent")?.trackEvent(name, properties) ?: Unit

    override fun replay(real: IUserManager) {
        super.replay(real)
        deferredPush.replay(real.pushSubscription)
    }
}

private class PushSubscriptionSource(
    private val user: ManagerSource<IUserManager>,
) : ManagerSource<IPushSubscription> {
    override fun get(member: String) = user.get("pushSubscription.$member")?.pushSubscription

    override fun peek() = user.peek()?.pushSubscription

    override suspend fun await(member: String) = user.await("pushSubscription.$member")?.pushSubscription

    override fun logQueued(member: String) = user.logQueued("pushSubscription.$member")
}

internal class DeferredPushSubscription(
    source: ManagerSource<IPushSubscription>,
) : DeferredManager<IPushSubscription>(source), IPushSubscription {
    override val id: String get() = source.get("id")?.id ?: ""
    override val token: String get() = source.get("token")?.token ?: ""
    override val optedIn: Boolean get() = source.get("optedIn")?.optedIn ?: false

    override fun optIn() = source.get("optIn")?.optIn() ?: Unit

    override fun optOut() = source.get("optOut")?.optOut() ?: Unit

    override fun addObserver(observer: IPushSubscriptionObserver) = registerOrQueue("addObserver", observer) { it.addObserver(observer) }

    override fun removeObserver(observer: IPushSubscriptionObserver) = unregisterOrCancel("removeObserver", "addObserver", observer) { it.removeObserver(observer) }
}

internal class DeferredNotificationsManager(
    source: ManagerSource<INotificationsManager>,
) : DeferredManager<INotificationsManager>(source), INotificationsManager {
    override val permission: Boolean get() = source.get("permission")?.permission ?: false
    override val canRequestPermission: Boolean get() = source.get("canRequestPermission")?.canRequestPermission ?: false

    override suspend fun requestPermission(fallbackToSettings: Boolean): Boolean =
        source.await("requestPermission")?.requestPermission(fallbackToSettings) ?: false

    override fun removeNotification(id: Int) = source.get("removeNotification")?.removeNotification(id) ?: Unit

    override fun removeGroupedNotifications(group: String) = source.get("removeGroupedNotifications")?.removeGroupedNotifications(group) ?: Unit

    override fun clearAllNotifications() = source.get("clearAllNotifications")?.clearAllNotifications() ?: Unit

    override fun addPermissionObserver(observer: IPermissionObserver) =
        registerOrQueue("addPermissionObserver", observer) { it.addPermissionObserver(observer) }

    override fun removePermissionObserver(observer: IPermissionObserver) =
        unregisterOrCancel("removePermissionObserver", "addPermissionObserver", observer) { it.removePermissionObserver(observer) }

    override fun addForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        registerOrQueue("addForegroundLifecycleListener", listener) { it.addForegroundLifecycleListener(listener) }

    override fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        unregisterOrCancel("removeForegroundLifecycleListener", "addForegroundLifecycleListener", listener) { it.removeForegroundLifecycleListener(listener) }

    override fun addClickListener(listener: INotificationClickListener) = registerOrQueue("addClickListener", listener) { it.addClickListener(listener) }

    override fun removeClickListener(listener: INotificationClickListener) =
        unregisterOrCancel("removeClickListener", "addClickListener", listener) { it.removeClickListener(listener) }
}

internal class DeferredInAppMessagesManager(
    source: ManagerSource<IInAppMessagesManager>,
) : DeferredManager<IInAppMessagesManager>(source), IInAppMessagesManager {
    @Volatile
    private var queuedPaused: Boolean? = null

    override var paused: Boolean
        get() = source.peek()?.paused ?: queuedPaused ?: source.get("paused")?.paused ?: false
        set(value) {
            setOrQueue("paused", { queuedPaused = value }) { it.paused = value }
        }

    override fun addTrigger(
        key: String,
        value: String,
    ) = source.get("addTrigger")?.addTrigger(key, value) ?: Unit

    override fun addTriggers(triggers: Map<String, String>) = source.get("addTriggers")?.addTriggers(triggers) ?: Unit

    override fun removeTrigger(key: String) = source.get("removeTrigger")?.removeTrigger(key) ?: Unit

    override fun removeTriggers(keys: Collection<String>) = source.get("removeTriggers")?.removeTriggers(keys) ?: Unit

    override fun clearTriggers() = source.get("clearTriggers")?.clearTriggers() ?: Unit

    override fun addLifecycleListener(listener: IInAppMessageLifecycleListener) =
        registerOrQueue("addLifecycleListener", listener) { it.addLifecycleListener(listener) }

    override fun removeLifecycleListener(listener: IInAppMessageLifecycleListener) =
        unregisterOrCancel("removeLifecycleListener", "addLifecycleListener", listener) { it.removeLifecycleListener(listener) }

    override fun addClickListener(listener: IInAppMessageClickListener) = registerOrQueue("addClickListener", listener) { it.addClickListener(listener) }

    override fun removeClickListener(listener: IInAppMessageClickListener) =
        unregisterOrCancel("removeClickListener", "addClickListener", listener) { it.removeClickListener(listener) }
}

internal class DeferredSessionManager(
    source: ManagerSource<ISessionManager>,
) : DeferredManager<ISessionManager>(source), ISessionManager {
    override fun addOutcome(name: String) = source.get("addOutcome")?.addOutcome(name) ?: Unit

    override fun addUniqueOutcome(name: String) = source.get("addUniqueOutcome")?.addUniqueOutcome(name) ?: Unit

    override fun addOutcomeWithValue(
        name: String,
        value: Float,
    ) = source.get("addOutcomeWithValue")?.addOutcomeWithValue(name, value) ?: Unit
}

internal class DeferredLocationManager(
    source: ManagerSource<ILocationManager>,
) : DeferredManager<ILocationManager>(source), ILocationManager {
    @Volatile
    private var queuedIsShared: Boolean? = null

    override var isShared: Boolean
        get() = source.peek()?.isShared ?: queuedIsShared ?: source.get("isShared")?.isShared ?: false
        set(value) {
            setOrQueue("isShared", { queuedIsShared = value }) { it.isShared = value }
        }

    override suspend fun requestPermission(): Boolean = source.await("requestPermission")?.requestPermission() ?: false
}

internal class DeferredJwtListeners(
    source: ManagerSource<JwtTokenStore>,
) : DeferredManager<JwtTokenStore>(source) {
    fun add(listener: IUserJwtInvalidatedListener) =
        registerOrQueue("addUserJwtInvalidatedListener", listener) { it.addUserJwtInvalidatedListener(listener) }

    fun remove(listener: IUserJwtInvalidatedListener) =
        unregisterOrCancel("removeUserJwtInvalidatedListener", "addUserJwtInvalidatedListener", listener) {
            it.removeUserJwtInvalidatedListener(listener)
        }
}
