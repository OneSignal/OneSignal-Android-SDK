package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model
import java.util.UUID

internal class IdentityModel : Model() {
    var oneSignalId: UUID
        get() = get(::oneSignalId.name) { UUID.randomUUID() }
        set(value) { set(::oneSignalId.name, value) }

    var userId: String?
        get() = get(::userId.name)
        set(value) { set(::userId.name, value) }

    var userIdAuthHash: String?
        get() = get(::userIdAuthHash.name)
        set(value) { set(::userIdAuthHash.name, value) }

    var aliases: Map<String, String>
        get() = get(::aliases.name) { mapOf() }
        set(value) { set(::aliases.name, value) }
}
