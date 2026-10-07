package com.onesignal.core.internal.permissions.impl

import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging

/**
 * Per request, so overlapping prompts stay independent. A null permission is a denial.
 */
internal data class PermissionPromptRequest(
    val permissionRequestType: String?,
    val permission: String?,
    val fallbackToSettings: Boolean,
    val rationaleBefore: Boolean,
)

/**
 * Turns a permission grant into the settings-fallback decision and the registered callback.
 * Shared by `PermissionsActivity`, the host result registry, and wrapper-supplied prompts.
 */
@Suppress("ConstructorParameterNaming")
internal class PermissionsResultHandler(
    private val _requestPermissionService: RequestPermissionService,
    private val _preferences: IPreferencesService,
) {
    /**
     * @param rationaleAfter null when no activity was available to read it from. Unknown is not
     *   the same as false: a false reading is what marks a permission permanently denied.
     */
    fun handleResult(
        request: PermissionPromptRequest,
        granted: Boolean,
        rationaleAfter: Boolean?,
        dialogShown: Boolean = true,
    ) {
        val permission = request.permission
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
                showSettings = shouldShowSettings(request, permission, rationaleAfter)
            }

            // After shouldShowSettings reads it, so this attempt is not "prompted before".
            // A missing dialog must not count: the recovery path treats the flag as an OS refusal.
            if (dialogShown) {
                _preferences.saveBool(
                    PreferenceStores.ONESIGNAL,
                    "${PreferenceOneSignalKeys.PREFS_OS_PROMPTED_PERMISSION_PREFIX}$permission",
                    true,
                )
            }
        }

        executeCallback(request.permissionRequestType, isGranted, showSettings)
    }

    @Suppress("TooGenericExceptionThrown")
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
    @Suppress("ReturnCount")
    private fun shouldShowSettings(
        request: PermissionPromptRequest,
        permission: String,
        rationaleAfter: Boolean?,
    ): Boolean {
        if (!request.fallbackToSettings) {
            return false
        }

        val resolvedKey = "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission"
        val alreadyResolved = _preferences.getBool(PreferenceStores.ONESIGNAL, resolvedKey, false) ?: false

        // No reading means no transition to infer, so report only what was already known.
        if (rationaleAfter == null) {
            return alreadyResolved
        }

        if (request.rationaleBefore && !rationaleAfter) {
            _preferences.saveBool(PreferenceStores.ONESIGNAL, resolvedKey, true)
            return false
        }

        // Recovery path for an already permanently-denied permission: rationale false before and
        // after a denied request means the OS will not prompt again, even though we never saw the
        // true -> false transition.
        val hasPromptedBefore =
            _preferences.getBool(
                PreferenceStores.ONESIGNAL,
                "${PreferenceOneSignalKeys.PREFS_OS_PROMPTED_PERMISSION_PREFIX}$permission",
                false,
            ) ?: false
        if (hasPromptedBefore && !request.rationaleBefore && !rationaleAfter) {
            _preferences.saveBool(PreferenceStores.ONESIGNAL, resolvedKey, true)
            return true
        }

        return alreadyResolved
    }
}
