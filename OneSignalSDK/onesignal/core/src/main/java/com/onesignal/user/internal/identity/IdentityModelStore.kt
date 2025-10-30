package com.onesignal.user.internal.identity

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.user.internal.backend.IdentityConstants

open class IdentityModelStore(prefs: IPreferencesService) : SingletonModelStore<IdentityModel>(
    SimpleModelStore({ IdentityModel() }, "identity", prefs),
)

/**
 * Checks if the identity model has a OneSignal ID.
 * Used to determine if a user is already initialized or needs to be created.
 */
fun IdentityModelStore.hasOneSignalId(): Boolean = model.hasProperty(IdentityConstants.ONESIGNAL_ID)
