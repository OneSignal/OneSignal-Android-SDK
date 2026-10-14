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

// Handed out while the SDK is not initialized. Apps may hold onto these, so every call
// re-resolves: once init succeeds they forward, until then `real` logs and returns null.

@Suppress("TooManyFunctions") // one per IUserManager member
internal class DeferredUserManager(
    private val real: () -> IUserManager?,
) : IUserManager {
    private val deferredPush = DeferredPushSubscription { real()?.pushSubscription }

    override val pushSubscription: IPushSubscription get() = real()?.pushSubscription ?: deferredPush
    override val onesignalId: String get() = real()?.onesignalId ?: ""
    override val externalId: String get() = real()?.externalId ?: ""

    override fun setLanguage(value: String) = real()?.setLanguage(value) ?: Unit

    override fun addAlias(
        label: String,
        id: String,
    ) = real()?.addAlias(label, id) ?: Unit

    override fun addAliases(aliases: Map<String, String>) = real()?.addAliases(aliases) ?: Unit

    override fun removeAlias(label: String) = real()?.removeAlias(label) ?: Unit

    override fun removeAliases(labels: Collection<String>) = real()?.removeAliases(labels) ?: Unit

    override fun addEmail(email: String) = real()?.addEmail(email) ?: Unit

    override fun removeEmail(email: String) = real()?.removeEmail(email) ?: Unit

    override fun addSms(sms: String) = real()?.addSms(sms) ?: Unit

    override fun removeSms(sms: String) = real()?.removeSms(sms) ?: Unit

    override fun addTag(
        key: String,
        value: String,
    ) = real()?.addTag(key, value) ?: Unit

    override fun addTags(tags: Map<String, String>) = real()?.addTags(tags) ?: Unit

    override fun removeTag(key: String) = real()?.removeTag(key) ?: Unit

    override fun removeTags(keys: Collection<String>) = real()?.removeTags(keys) ?: Unit

    override fun getTags(): Map<String, String> = real()?.getTags() ?: emptyMap()

    override fun addObserver(observer: IUserStateObserver) = real()?.addObserver(observer) ?: Unit

    override fun removeObserver(observer: IUserStateObserver) = real()?.removeObserver(observer) ?: Unit

    override fun trackEvent(
        name: String,
        properties: Map<String, Any?>?,
    ) = real()?.trackEvent(name, properties) ?: Unit
}

internal class DeferredPushSubscription(
    private val real: () -> IPushSubscription?,
) : IPushSubscription {
    override val id: String get() = real()?.id ?: ""
    override val token: String get() = real()?.token ?: ""
    override val optedIn: Boolean get() = real()?.optedIn ?: false

    override fun optIn() = real()?.optIn() ?: Unit

    override fun optOut() = real()?.optOut() ?: Unit

    override fun addObserver(observer: IPushSubscriptionObserver) = real()?.addObserver(observer) ?: Unit

    override fun removeObserver(observer: IPushSubscriptionObserver) = real()?.removeObserver(observer) ?: Unit
}

internal class DeferredNotificationsManager(
    private val real: () -> INotificationsManager?,
) : INotificationsManager {
    override val permission: Boolean get() = real()?.permission ?: false
    override val canRequestPermission: Boolean get() = real()?.canRequestPermission ?: false

    override suspend fun requestPermission(fallbackToSettings: Boolean): Boolean =
        real()?.requestPermission(fallbackToSettings) ?: false

    override fun removeNotification(id: Int) = real()?.removeNotification(id) ?: Unit

    override fun removeGroupedNotifications(group: String) = real()?.removeGroupedNotifications(group) ?: Unit

    override fun clearAllNotifications() = real()?.clearAllNotifications() ?: Unit

    override fun addPermissionObserver(observer: IPermissionObserver) = real()?.addPermissionObserver(observer) ?: Unit

    override fun removePermissionObserver(observer: IPermissionObserver) = real()?.removePermissionObserver(observer) ?: Unit

    override fun addForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        real()?.addForegroundLifecycleListener(listener) ?: Unit

    override fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener) =
        real()?.removeForegroundLifecycleListener(listener) ?: Unit

    override fun addClickListener(listener: INotificationClickListener) = real()?.addClickListener(listener) ?: Unit

    override fun removeClickListener(listener: INotificationClickListener) = real()?.removeClickListener(listener) ?: Unit
}

internal class DeferredInAppMessagesManager(
    private val real: () -> IInAppMessagesManager?,
) : IInAppMessagesManager {
    override var paused: Boolean
        get() = real()?.paused ?: false
        set(value) {
            real()?.paused = value
        }

    override fun addTrigger(
        key: String,
        value: String,
    ) = real()?.addTrigger(key, value) ?: Unit

    override fun addTriggers(triggers: Map<String, String>) = real()?.addTriggers(triggers) ?: Unit

    override fun removeTrigger(key: String) = real()?.removeTrigger(key) ?: Unit

    override fun removeTriggers(keys: Collection<String>) = real()?.removeTriggers(keys) ?: Unit

    override fun clearTriggers() = real()?.clearTriggers() ?: Unit

    override fun addLifecycleListener(listener: IInAppMessageLifecycleListener) = real()?.addLifecycleListener(listener) ?: Unit

    override fun removeLifecycleListener(listener: IInAppMessageLifecycleListener) = real()?.removeLifecycleListener(listener) ?: Unit

    override fun addClickListener(listener: IInAppMessageClickListener) = real()?.addClickListener(listener) ?: Unit

    override fun removeClickListener(listener: IInAppMessageClickListener) = real()?.removeClickListener(listener) ?: Unit
}

internal class DeferredSessionManager(
    private val real: () -> ISessionManager?,
) : ISessionManager {
    override fun addOutcome(name: String) = real()?.addOutcome(name) ?: Unit

    override fun addUniqueOutcome(name: String) = real()?.addUniqueOutcome(name) ?: Unit

    override fun addOutcomeWithValue(
        name: String,
        value: Float,
    ) = real()?.addOutcomeWithValue(name, value) ?: Unit
}

internal class DeferredLocationManager(
    private val real: () -> ILocationManager?,
) : ILocationManager {
    override var isShared: Boolean
        get() = real()?.isShared ?: false
        set(value) {
            real()?.isShared = value
        }

    override suspend fun requestPermission(): Boolean = real()?.requestPermission() ?: false
}
