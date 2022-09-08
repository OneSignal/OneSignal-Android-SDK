package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.Model
import java.util.UUID

internal class IdentityModel : Model() {
    /**
     * The (OneSignal backend provided) identifier that uniquely identifies this user.
     */
    var oneSignalId: UUID?
        get() = get(::oneSignalId.name)
        set(value) { set(::oneSignalId.name, value) }

    /**
     * The (developer managed) identifier that uniquely identifies this user.
     */
    var userId: String?
        get() = get(::userId.name)
        set(value) { set(::userId.name, value) }

    /**
     * The (developer managed) JWT bearer token for this user.
     */
    var jwtBearerToken: String?
        get() = get(::jwtBearerToken.name)
        set(value) { set(::jwtBearerToken.name, value) }

    /**
     * The complete collection of aliases that uniquely identify this user.
     */
    var aliases: Map<String, String>
        get() = get(::aliases.name) { mapOf() }
        set(value) { set(::aliases.name, value) }
}
