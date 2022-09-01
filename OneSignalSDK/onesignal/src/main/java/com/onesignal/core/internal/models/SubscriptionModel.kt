package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model

internal enum class SubscriptionType {
    EMAIL,
    SMS,
    PUSH
}

internal class SubscriptionModel : Model() {
    var enabled: Boolean
        get() = get(::enabled.name)
        set(value) { set(::enabled.name, value) }

    var type: SubscriptionType
        get() = get(::type.name)
        set(value) { set(::type.name, value) }

    var address: String
        get() = get(::address.name)
        set(value) { set(::address.name, value) }
}
