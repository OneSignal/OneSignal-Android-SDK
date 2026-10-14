package com.onesignal.internal

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

// Handed out before init; apps may keep it, so every call re-resolves. Registrations (listeners,
// observers, settings) are queued and replayed in order on success; other calls are logged and dropped.
internal open class DeferredManager<T : Any>(
    protected val source: ManagerSource<T>,
) {
    private val pending = mutableListOf<Pair<String, (T) -> Unit>>()
    private var replayed = false

    protected fun forwardOrQueue(
        member: String,
        call: (T) -> Unit,
    ) {
        source.peek()?.let {
            call(it)
            return
        }
        val queued = synchronized(pending) { if (!replayed) pending.add(member to call) else false }
        if (queued) source.logQueued(member) else source.get(member)?.let(call)
    }

    @Suppress("TooGenericExceptionCaught")
    open fun replay(real: T) {
        val calls =
            synchronized(pending) {
                replayed = true
                pending.toList().also { pending.clear() }
            }
        for ((member, call) in calls) {
            try {
                call(real)
            } catch (e: Exception) {
                Logging.error("Replaying '$member' after initWithContext failed", e)
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

    override fun addObserver(observer: IUserStateObserver) = forwardOrQueue("addObserver") { it.addObserver(observer) }

    override fun removeObserver(observer: IUserStateObserver) = forwardOrQueue("removeObserver") { it.removeObserver(observer) }

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

    override fun addObserver(observer: IPushSubscriptionObserver) = forwardOrQueue("addObserver") { it.addObserver(observer) }

    override fun removeObserver(observer: IPushSubscriptionObserver) = forwardOrQueue("removeObserver") { it.removeObserver(observer) }
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
        forwardOrQueue("addPermissionObserver") { it.addPermissionObserver(observer) }

    override fun removePermissionObserver(observer: IPermissionObserver) =
        forwardOrQueue("removePermissionObserver") { it.removePermissionObserver(observer) }

    override fun addForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        forwardOrQueue("addForegroundLifecycleListener") { it.addForegroundLifecycleListener(listener) }

    override fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        forwardOrQueue("removeForegroundLifecycleListener") { it.removeForegroundLifecycleListener(listener) }

    override fun addClickListener(listener: INotificationClickListener) = forwardOrQueue("addClickListener") { it.addClickListener(listener) }

    override fun removeClickListener(listener: INotificationClickListener) =
        forwardOrQueue("removeClickListener") { it.removeClickListener(listener) }
}

internal class DeferredInAppMessagesManager(
    source: ManagerSource<IInAppMessagesManager>,
) : DeferredManager<IInAppMessagesManager>(source), IInAppMessagesManager {
    override var paused: Boolean
        get() = source.get("paused")?.paused ?: false
        set(value) = forwardOrQueue("paused") { it.paused = value }

    override fun addTrigger(
        key: String,
        value: String,
    ) = source.get("addTrigger")?.addTrigger(key, value) ?: Unit

    override fun addTriggers(triggers: Map<String, String>) = source.get("addTriggers")?.addTriggers(triggers) ?: Unit

    override fun removeTrigger(key: String) = source.get("removeTrigger")?.removeTrigger(key) ?: Unit

    override fun removeTriggers(keys: Collection<String>) = source.get("removeTriggers")?.removeTriggers(keys) ?: Unit

    override fun clearTriggers() = source.get("clearTriggers")?.clearTriggers() ?: Unit

    override fun addLifecycleListener(listener: IInAppMessageLifecycleListener) =
        forwardOrQueue("addLifecycleListener") { it.addLifecycleListener(listener) }

    override fun removeLifecycleListener(listener: IInAppMessageLifecycleListener) =
        forwardOrQueue("removeLifecycleListener") { it.removeLifecycleListener(listener) }

    override fun addClickListener(listener: IInAppMessageClickListener) = forwardOrQueue("addClickListener") { it.addClickListener(listener) }

    override fun removeClickListener(listener: IInAppMessageClickListener) =
        forwardOrQueue("removeClickListener") { it.removeClickListener(listener) }
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
    override var isShared: Boolean
        get() = source.get("isShared")?.isShared ?: false
        set(value) = forwardOrQueue("isShared") { it.isShared = value }

    override suspend fun requestPermission(): Boolean = source.await("requestPermission")?.requestPermission() ?: false
}
