package com.onesignal.user.internal.identity

import com.onesignal.common.IDManager
import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.user.internal.backend.IdentityConstants

const val IDENTITY_NAME_SPACE = "identity"

open class IdentityModelStore(prefs: IPreferencesService) : SingletonModelStore<IdentityModel>(
    SimpleModelStore({ IdentityModel() }, IDENTITY_NAME_SPACE, prefs),
)

/**
 * Checks if the identity model has a OneSignal ID.
 * Used to determine if a user is already initialized or needs to be created.
 */
fun IdentityModelStore.hasOneSignalId(): Boolean = model.hasProperty(IdentityConstants.ONESIGNAL_ID)

/**
 * The OneSignal ID the backend assigned to the current user, or null while the user only
 * has a local placeholder ID (before the create user response) or no ID at all.
 */
fun IdentityModelStore.backendOneSignalIdOrNull(): String? {
    if (!hasOneSignalId()) return null
    val onesignalId = model.onesignalId
    return if (onesignalId.isEmpty() || IDManager.isLocalId(onesignalId)) null else onesignalId
}
