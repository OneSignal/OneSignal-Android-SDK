package com.onesignal.core.internal.permissions.impl

import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging

/**
 * Resolves a permission grant into the settings-fallback decision and the registered
 * [com.onesignal.core.internal.permissions.IRequestPermissionService.PermissionCallback].
 *
 * Shared by every surface that can host the OS prompt: `PermissionsActivity`, the host
 * activity's result registry, and a wrapper-supplied [com.onesignal.common.IHostPermissionPrompt].
 */
internal class PermissionsResultHandler(
    private val _requestPermissionService: RequestPermissionService,
    private val _preferences: IPreferencesService,
) {
    /**
     * @param permission null when the OS returned no permissions, which is always a denial.
     */
    fun handleResult(
        permissionRequestType: String?,
        permission: String?,
        granted: Boolean,
        shouldShowRationaleAfter: Boolean,
    ) {
        var isGranted = granted
        var showSettings = false

        if (permission == null) {
            isGranted = false
        } else {
            if (isGranted) {
                _preferences.saveBool(
                    PreferenceStores.ONESIGNAL,
                    "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission",
                    true,
                )
            } else {
                showSettings = shouldShowSettings(permission, shouldShowRationaleAfter)
            }

            // Must be persisted after shouldShowSettings() reads it so the recovery path
            // only considers requests prior to the current one.
            _preferences.saveBool(
                PreferenceStores.ONESIGNAL,
                "${PreferenceOneSignalKeys.PREFS_OS_PROMPTED_PERMISSION_PREFIX}$permission",
                true,
            )
        }

        executeCallback(permissionRequestType, isGranted, showSettings)
    }

    private fun executeCallback(
        permissionRequestType: String?,
        granted: Boolean,
        showSettings: Boolean,
    ) {
        permissionRequestType?.let { type ->
            val callback =
                _requestPermissionService.getCallback(type)
                    ?: throw RuntimeException("Missing handler for permissionRequestType: $type")

            if (granted) {
                callback.onAccept()
            } else {
                callback.onReject(showSettings)
            }
        } ?: Logging.error(
            "PermissionsResultHandler: Cannot resolve callback because permissionRequestType is null. Ending permission flow.",
        )
    }

    /**
     * Settings is offered once the OS stops showing its prompt. That shows up as
     * `shouldShowRequestPermissionRationale` going true -> false across a denied request.
     */
    private fun shouldShowSettings(
        permission: String,
        shouldShowRationaleAfter: Boolean,
    ): Boolean {
        if (!_requestPermissionService.fallbackToSettings) {
            return false
        }

        val resolvedKey = "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission"
        val rationaleBefore = _requestPermissionService.shouldShowRequestPermissionRationaleBeforeRequest

        if (rationaleBefore && !shouldShowRationaleAfter) {
            _preferences.saveBool(PreferenceStores.ONESIGNAL, resolvedKey, true)
            return false
        }

        // Recovery path for an already permanently-denied permission. If the OS won't surface
        // its prompt (rationale is false before and after a denied request) but OneSignal has
        // requested this permission before, the permission is permanently blocked even though
        // we never witnessed the true -> false transition (e.g. it was denied across a prior
        // session or outside OneSignal's flow). Remember it so the fallback is no longer stuck.
        val hasPromptedBefore =
            _preferences.getBool(
                PreferenceStores.ONESIGNAL,
                "${PreferenceOneSignalKeys.PREFS_OS_PROMPTED_PERMISSION_PREFIX}$permission",
                false,
            ) ?: false
        if (hasPromptedBefore && !rationaleBefore && !shouldShowRationaleAfter) {
            _preferences.saveBool(PreferenceStores.ONESIGNAL, resolvedKey, true)
            return true
        }

        return _preferences.getBool(PreferenceStores.ONESIGNAL, resolvedKey, false) ?: false
    }
}
