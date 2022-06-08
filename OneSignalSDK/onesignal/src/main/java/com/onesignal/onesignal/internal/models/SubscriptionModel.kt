package com.onesignal.onesignal.internal.models

import com.onesignal.onesignal.internal.modeling.Model

class SubscriptionModel : Model() {
    var enabled: Boolean
        get() = get(::enabled.name)
        set(value) { set(::enabled.name, value) }

    var address: String
        get() = get(::address.name)
        set(value) { set(::address.name, value) }
}