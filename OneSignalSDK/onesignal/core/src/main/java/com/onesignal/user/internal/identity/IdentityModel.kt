package com.onesignal.user.internal.identity

import com.onesignal.common.modeling.MapModel
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.user.internal.backend.IdentityConstants

/**
 * The identity model as a [MapModel] i.e. a simple key-value pair where the key represents
 * the alias label and the value represents the alias ID for that alias label.  This model
 * provides simple access to more well-defined aliases.
 */
class IdentityModel : MapModel<String>() {
    /**
     * The OneSignal id for this identity.
     *
     * WARNING: This *might* be a local id, depending on whether the user has been successfully
     * created on the backend or not.
     */
    var onesignalId: String
        get() = getStringProperty(IdentityConstants.ONESIGNAL_ID)
        set(value) {
            setStringProperty(IdentityConstants.ONESIGNAL_ID, value)
        }

    /**
     * The (developer managed) identifier that uniquely identifies this user.
     */
    var externalId: String?
        get() = getOptStringProperty(IdentityConstants.EXTERNAL_ID)
        set(value) {
            setOptStringProperty(IdentityConstants.EXTERNAL_ID, value)
        }

    /**
     * A JWT token generated on your server and given to a OneSignal Client SDK so it can manage
     * a specific User, their Subscriptions, and Identities (AKA add/remove Aliases).
     */
    var jwtToken: String?
        get() = getOptStringProperty(IdentityConstants.JWT_TOKEN)
        set(value) {
            setOptStringProperty(IdentityConstants.JWT_TOKEN, value, ModelChangeTags.NO_PROPOGATE, true)
        }
}
