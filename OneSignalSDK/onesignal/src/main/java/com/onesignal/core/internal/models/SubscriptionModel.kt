package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

internal enum class SubscriptionType() {
    EMAIL,
    SMS,
    PUSH
}

internal class SubscriptionModel : Model() {
    var enabled: Boolean
        get() = getProperty(::enabled.name)
        set(value) { setProperty(::enabled.name, value) }

    var type: SubscriptionType
        get() = SubscriptionType.valueOf(getProperty(::type.name))
        set(value) { setProperty(::type.name, value.toString()) }

    var address: String
        get() = getProperty(::address.name)
        set(value) { setProperty(::address.name, value) }
}
