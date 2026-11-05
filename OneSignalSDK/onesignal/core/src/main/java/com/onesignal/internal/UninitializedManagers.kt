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

// Handed out by the accessors when init never started or failed, so calls do nothing instead of
// crashing. The accessor already logged the call, so these stay silent.

@Suppress("TooManyFunctions")
internal object UninitializedUserManager : IUserManager {
    override val pushSubscription: IPushSubscription = UninitializedPushSubscription
    override val onesignalId = ""
    override val externalId = ""

    override fun setLanguage(value: String) {}

    override fun addAlias(
        label: String,
        id: String,
    ) {}

    override fun addAliases(aliases: Map<String, String>) {}

    override fun removeAlias(label: String) {}

    override fun removeAliases(labels: Collection<String>) {}

    override fun addEmail(email: String) {}

    override fun removeEmail(email: String) {}

    override fun addSms(sms: String) {}

    override fun removeSms(sms: String) {}

    override fun addTag(
        key: String,
        value: String,
    ) {}

    override fun addTags(tags: Map<String, String>) {}

    override fun removeTag(key: String) {}

    override fun removeTags(keys: Collection<String>) {}

    override fun getTags(): Map<String, String> = emptyMap()

    override fun addObserver(observer: IUserStateObserver) {}

    override fun removeObserver(observer: IUserStateObserver) {}

    override fun trackEvent(
        name: String,
        properties: Map<String, Any?>?,
    ) {}
}

internal object UninitializedPushSubscription : IPushSubscription {
    override val id = ""
    override val token = ""
    override val optedIn = false

    override fun optIn() {}

    override fun optOut() {}

    override fun addObserver(observer: IPushSubscriptionObserver) {}

    override fun removeObserver(observer: IPushSubscriptionObserver) {}
}

@Suppress("TooManyFunctions")
internal object UninitializedNotificationsManager : INotificationsManager {
    override val permission = false
    override val canRequestPermission = false

    override suspend fun requestPermission(fallbackToSettings: Boolean) = false

    override fun removeNotification(id: Int) {}

    override fun removeGroupedNotifications(group: String) {}

    override fun clearAllNotifications() {}

    override fun addPermissionObserver(observer: IPermissionObserver) {}

    override fun removePermissionObserver(observer: IPermissionObserver) {}

    override fun addForegroundLifecycleListener(listener: INotificationLifecycleListener) {}

    override fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener) {}

    override fun addClickListener(listener: INotificationClickListener) {}

    override fun removeClickListener(listener: INotificationClickListener) {}
}

internal object UninitializedInAppMessagesManager : IInAppMessagesManager {
    override var paused: Boolean
        get() = false
        set(_) {}

    override fun addTrigger(
        key: String,
        value: String,
    ) {}

    override fun addTriggers(triggers: Map<String, String>) {}

    override fun removeTrigger(key: String) {}

    override fun removeTriggers(keys: Collection<String>) {}

    override fun clearTriggers() {}

    override fun addLifecycleListener(listener: IInAppMessageLifecycleListener) {}

    override fun removeLifecycleListener(listener: IInAppMessageLifecycleListener) {}

    override fun addClickListener(listener: IInAppMessageClickListener) {}

    override fun removeClickListener(listener: IInAppMessageClickListener) {}
}

internal object UninitializedLocationManager : ILocationManager {
    override var isShared: Boolean
        get() = false
        set(_) {}

    override suspend fun requestPermission() = false
}

internal object UninitializedSessionManager : ISessionManager {
    override fun addOutcome(name: String) {}

    override fun addUniqueOutcome(name: String) {}

    override fun addOutcomeWithValue(
        name: String,
        value: Float,
    ) {}
}
