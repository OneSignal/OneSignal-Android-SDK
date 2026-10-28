package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.PIIHasher
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.user.internal.backend.CreateUserResponse
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.reservedLoginAliasLabel
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal object LoginProfileApplier {
    const val PROFILE_EMAIL_KEY = "__profile_email"
    const val PROFILE_SMS_KEY = "__profile_sms"

    fun isProfileSubscriptionKey(key: String): Boolean = key == PROFILE_EMAIL_KEY || key == PROFILE_SMS_KEY

    /**
     * [profileSubscription] receives the grouped entry for the same address when there is one, and
     * returns null to leave it as it is.
     */
    fun addSubscriptions(
        op: LoginUserOperation,
        subscriptions: Map<String, SubscriptionObject>,
        profileSubscription: (SubscriptionObjectType, String, SubscriptionObject?) -> SubscriptionObject?,
    ): Map<String, SubscriptionObject> {
        val mutable = subscriptions.toMutableMap()
        for ((type, token, profileKey) in profileAddresses(op)) {
            val grouped = mutable.entries.firstOrNull { it.value.type == type && it.value.token == token }
            val subscription = profileSubscription(type, token, grouped?.value) ?: continue
            mutable[grouped?.key ?: profileKey] = subscription
        }
        return mutable
    }

    private fun profileAddresses(op: LoginUserOperation): List<Triple<SubscriptionObjectType, String, String>> =
        buildList {
            op.email?.takeIf { it.isNotBlank() }?.let { add(Triple(SubscriptionObjectType.EMAIL, it, PROFILE_EMAIL_KEY)) }
            op.sms?.takeIf { it.isNotBlank() }?.let { add(Triple(SubscriptionObjectType.SMS, it, PROFILE_SMS_KEY)) }
        }

    fun SubscriptionObject.withStatus(enabled: Boolean): SubscriptionObject =
        SubscriptionObject(
            id = id,
            type = type,
            token = token,
            enabled = enabled,
            notificationTypes = if (enabled) SubscriptionStatus.SUBSCRIBED.value else SubscriptionStatus.UNSUBSCRIBE.value,
            sdk = sdk,
            deviceModel = deviceModel,
            deviceOS = deviceOS,
            rooted = rooted,
            netType = netType,
            carrier = carrier,
            appVersion = appVersion,
        )

    /**
     * The backend wins where it spoke, the request fills the rest. Never deletes: Create User can
     * echo a partial body, and dropping keys it omitted would discard state we just sent.
     */
    fun hydrate(
        op: LoginUserOperation,
        response: CreateUserResponse,
        identityModelStore: IdentityModelStore,
        propertiesModelStore: PropertiesModelStore,
    ) {
        val identityModel = identityModelStore.model
        // onesignal_id and external_id arrive on every response and are owned by the executor.
        for ((label, id) in op.aliases + response.identities) {
            if (reservedLoginAliasLabel(label)) continue
            identityModel.setStringProperty(label, id, ModelChangeTags.HYDRATE)
        }

        val propertiesModel = propertiesModelStore.model
        response.properties.language?.let {
            propertiesModel.setStringProperty(PropertiesModel::language.name, it, ModelChangeTags.HYDRATE)
        }
        response.properties.country?.let {
            propertiesModel.setStringProperty(PropertiesModel::country.name, it, ModelChangeTags.HYDRATE)
        }

        val backendTags = response.properties.tags.orEmpty().mapNotNull { (key, value) -> value?.let { key to it } }
        val tagsModel = propertiesModel.tags
        for ((key, value) in op.tags + backendTags) {
            tagsModel.setStringProperty(key, value, ModelChangeTags.HYDRATE)
        }
    }

    /** [optedIn] null means the caller stated no status, so an existing record keeps the one it has. */
    fun persistSubscription(
        backend: SubscriptionObject,
        fallbackToken: String?,
        subscriptionsModelStore: SubscriptionModelStore,
        optedIn: Boolean? = null,
    ) {
        val id = backend.id
        val token = backend.token ?: fallbackToken
        val type = subscriptionType(backend.type)
        if (id == null || token == null || type == null) return

        val ignoreCase = type == SubscriptionType.EMAIL
        val hashed = PIIHasher.hash(token)
        val existing =
            subscriptionsModelStore.list().firstOrNull { sub ->
                sub.type == type && (sub.address.equals(token, ignoreCase) || sub.address == hashed)
            }
        if (existing != null) {
            existing.setStringProperty(SubscriptionModel::id.name, id, ModelChangeTags.HYDRATE)
            if (optedIn != null) applyStatus(existing, optedIn)
            return
        }
        val model = SubscriptionModel()
        model.id = id
        model.type = type
        model.address = token
        applyStatus(model, optedIn ?: true)
        subscriptionsModelStore.add(model, ModelChangeTags.HYDRATE)
    }

    private fun applyStatus(
        model: SubscriptionModel,
        optedIn: Boolean,
    ) {
        model.status = if (optedIn) SubscriptionStatus.SUBSCRIBED else SubscriptionStatus.UNSUBSCRIBE
        model.optedIn = optedIn
    }

    fun subscriptionType(type: SubscriptionObjectType?): SubscriptionType? =
        when (type) {
            SubscriptionObjectType.EMAIL -> SubscriptionType.EMAIL
            SubscriptionObjectType.SMS -> SubscriptionType.SMS
            else -> null
        }

    fun fallbackToken(
        backendType: SubscriptionObjectType?,
        op: LoginUserOperation,
    ): String? =
        when (subscriptionType(backendType)) {
            SubscriptionType.EMAIL -> op.email
            SubscriptionType.SMS -> op.sms
            else -> null
        }
}
