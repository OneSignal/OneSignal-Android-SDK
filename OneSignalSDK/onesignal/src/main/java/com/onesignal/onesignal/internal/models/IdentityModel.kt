package com.onesignal.onesignal.internal.models

import com.onesignal.onesignal.internal.modeling.Model

class IdentityModel : Model() {
    var oneSignalId: String?
        get() = get(::oneSignalId.name)
        set(value) { set(::oneSignalId.name, value) }

    var userId: String?
        get() = get(::userId.name)
        set(value) { set(::userId.name, value) }

    var userIdAuthHash: String?
        get() = get(::userIdAuthHash.name)
        set(value) { set(::userIdAuthHash.name, value) }

    var aliases: Map<String, String>
        get() = get(::aliases.name, mapOf())
        set(value) { set(::aliases.name, value) }
}