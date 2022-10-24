package com.onesignal.user.internal.identity

import com.onesignal.common.modeling.MapModel
import com.onesignal.user.internal.backend.IdentityConstants

/**
 * The identity model as a [MapModel] i.e. a simple key-value pair where the key represents
 * the alias label and the value represents the alias ID for that alias label.  This model
 * provides simple access to more well-defined aliases.
 */
class IdentityModel : MapModel<String>() {
    /**
     * The OneSignal id for this identity
     */
    var onesignalId: String
        get() = getProperty(IdentityConstants.ONESIGNAL_ID)
        set(value) { setProperty(IdentityConstants.ONESIGNAL_ID, value) }

    /**
     * The (developer managed) identifier that uniquely identifies this user.
     */
    var externalId: String?
        get() = getProperty(IdentityConstants.EXTERNAL_ID)
        set(value) { setProperty(IdentityConstants.EXTERNAL_ID, value) }
}
