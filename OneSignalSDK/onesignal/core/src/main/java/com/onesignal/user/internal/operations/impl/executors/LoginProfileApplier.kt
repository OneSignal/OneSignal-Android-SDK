package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.reservedLoginAliasLabel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal object LoginProfileApplier {
    const val PROFILE_EMAIL_KEY = "__profile_email"
    const val PROFILE_SMS_KEY = "__profile_sms"

    fun isProfileSubscriptionKey(key: String): Boolean = key == PROFILE_EMAIL_KEY || key == PROFILE_SMS_KEY

    fun addSubscriptions(
        op: LoginUserOperation,
        subscriptions: Map<String, SubscriptionObject>,
    ): Map<String, SubscriptionObject> {
        val mutable = subscriptions.toMutableMap()
        val email = op.email?.takeIf { it.isNotBlank() }
        if (email != null && mutable.values.none { it.type == SubscriptionObjectType.EMAIL && it.token == email }) {
            mutable[PROFILE_EMAIL_KEY] = SubscriptionObject(type = SubscriptionObjectType.EMAIL, token = email)
        }
        val phone = op.phoneNumber?.takeIf { it.isNotBlank() }
        if (phone != null && mutable.values.none { it.type == SubscriptionObjectType.SMS && it.token == phone }) {
            mutable[PROFILE_SMS_KEY] = SubscriptionObject(type = SubscriptionObjectType.SMS, token = phone)
        }
        return mutable
    }

    fun hydrate(
        op: LoginUserOperation,
        identityModelStore: IdentityModelStore,
        propertiesModelStore: PropertiesModelStore,
    ) {
        val identityModel = identityModelStore.model
        for ((label, id) in op.aliases) {
            if (reservedLoginAliasLabel(label)) {
                Logging.warn("LoginProfileApplier: skipping reserved alias label")
                continue
            }
            identityModel.setStringProperty(label, id, ModelChangeTags.HYDRATE)
        }
        val tagsModel = propertiesModelStore.model.tags
        for ((key, value) in op.tags) {
            tagsModel.setStringProperty(key, value, ModelChangeTags.HYDRATE)
        }
    }

    fun persistSubscription(
        backend: SubscriptionObject,
        fallbackToken: String?,
        subscriptionsModelStore: SubscriptionModelStore,
    ) {
        val id = backend.id
        val token = backend.token ?: fallbackToken
        val type = subscriptionType(backend.type)
        if (id == null || token == null || type == null) return

        val existing = subscriptionsModelStore.list().firstOrNull { it.type == type && it.address == token }
        if (existing != null) {
            existing.setStringProperty(SubscriptionModel::id.name, id, ModelChangeTags.HYDRATE)
            return
        }
        val model = SubscriptionModel()
        model.id = id
        model.type = type
        model.address = token
        model.status = SubscriptionStatus.SUBSCRIBED
        model.optedIn = true
        subscriptionsModelStore.add(model, ModelChangeTags.HYDRATE)
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
            SubscriptionType.SMS -> op.phoneNumber
            else -> null
        }
}
