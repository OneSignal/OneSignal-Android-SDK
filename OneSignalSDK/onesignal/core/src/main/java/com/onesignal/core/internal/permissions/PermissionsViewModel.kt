package com.onesignal.core.internal.permissions

import android.app.Activity
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onesignal.OneSignal
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that handles the business logic for permission requests.
 * This separates the permission handling logic from the Activity lifecycle.
 * Uses AndroidX ViewModel with StateFlow for lifecycle-aware state management.
 *
 * Responsibilities:
 * - Store permission request state (survives configuration changes)
 * - Handle permission result business logic
 * - Manage callbacks and preferences
 * - Does NOT hold Activity references or call Activity APIs directly
 */
class PermissionsViewModel : ViewModel() {
    // Lazy initialization to ensure OneSignal is ready before accessing services
    private val requestPermissionService: RequestPermissionService by lazy { OneSignal.getService() }
    private val preferenceService: IPreferencesService by lazy { OneSignal.getService() }

    private val _shouldFinish = MutableStateFlow(false)
    val shouldFinish: StateFlow<Boolean> = _shouldFinish.asStateFlow()

    private val _waiting = MutableStateFlow(false)
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()

    var permissionRequestType: String? = null
        private set

    private var androidPermissionString: String? = null

    /**
     * Initialize OneSignal and the ViewModel with intent data.
     * Returns false if initialization fails.
     * @param activity Activity context (not stored, used only for initialization)
     */
    suspend fun initialize(
        activity: Activity,
        permissionType: String?,
        androidPermission: String?,
    ): Boolean {
        // First ensure OneSignal is initialized
        if (!OneSignal.initWithContext(activity)) {
            _shouldFinish.value = true
            return false
        }

        // Then validate intent parameters
        if (permissionType == null || androidPermission == null) {
            _shouldFinish.value = true
            return false
        }

        permissionRequestType = permissionType
        androidPermissionString = androidPermission
        return true
    }

    /**
     * Check if we should request permission (prevents duplicate requests).
     * Activity should call this before requesting permission.
     */
    fun shouldRequestPermission(): Boolean {
        if (_waiting.value) {
            return false
        }
        _waiting.value = true
        return true
    }

    /**
     * Record the rationale state before the permission request.
     * Activity calls this with the result of shouldShowRequestPermissionRationale().
     */
    fun recordRationaleState(shouldShowRationale: Boolean) {
        requestPermissionService.shouldShowRequestPermissionRationaleBeforeRequest = shouldShowRationale
    }

    /**
     * Handle the permission request result.
     * Activity should call this with the result from onRequestPermissionsResult.
     *
     * @param shouldShowRationaleAfter The result of shouldShowRequestPermissionRationale AFTER the user responded
     */
    fun onRequestPermissionsResult(
        permissions: Array<String>,
        grantResults: IntArray,
        shouldShowRationaleAfter: Boolean = false,
    ) {
        _waiting.value = false

        // Use viewModelScope with delay for smooth transition
        viewModelScope.launch {
            delay(DELAY_TIME_CALLBACK_CALL.toLong())

            val granted: Boolean
            val showSettings: Boolean

            if (permissions.isEmpty()) {
                granted = false
                showSettings = false
            } else {
                val permission = permissions[0]
                granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    preferenceService.saveBool(
                        PreferenceStores.ONESIGNAL,
                        "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission",
                        true,
                    )
                    showSettings = false
                } else {
                    showSettings = shouldShowSettings(permission, shouldShowRationaleAfter)
                }
            }

            // Execute the callback
            executeCallback(granted, showSettings)

            // Signal the activity to finish
            _shouldFinish.value = true
        }
    }

    private fun executeCallback(
        granted: Boolean,
        showSettings: Boolean,
    ) {
        val callback =
            requestPermissionService.getCallback(permissionRequestType!!)
                ?: throw RuntimeException("Missing handler for permissionRequestType: $permissionRequestType")

        if (granted) {
            callback.onAccept()
        } else {
            callback.onReject(showSettings)
        }
    }

    /**
     * Determine if we should show the settings fallback.
     * This matches the original logic from the Activity.
     *
     * We want to show settings after the user has clicked "Don't Allow" 2 times.
     * After the first time shouldShowRequestPermissionRationale becomes true, after
     * the second time shouldShowRequestPermissionRationale becomes false again. We
     * look for the change from `true` -> `false`. When this happens we remember this
     * rejection, as the user will never be prompted again.
     *
     * @param permission The permission string
     * @param shouldShowRationaleAfter The result of shouldShowRequestPermissionRationale AFTER the user responded
     */
    private fun shouldShowSettings(
        permission: String,
        shouldShowRationaleAfter: Boolean,
    ): Boolean {
        if (!requestPermissionService.fallbackToSettings) {
            return false
        }

        // We want to show settings after the user has clicked "Don't Allow" 2 times.
        // After the first time shouldShowRequestPermissionRationale becomes true, after
        // the second time shouldShowRequestPermissionRationale becomes false again. We
        // look for the change from `true` -> `false`. When this happens we remember this
        // rejection, as the user will never be prompted again.
        if (requestPermissionService.shouldShowRequestPermissionRationaleBeforeRequest) {
            if (!shouldShowRationaleAfter) {
                // The rationale changed from true -> false, meaning permanent denial
                preferenceService.saveBool(
                    PreferenceStores.ONESIGNAL,
                    "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission",
                    true,
                )
                return false
            }
        }

        return preferenceService.getBool(
            PreferenceStores.ONESIGNAL,
            "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission",
            false,
        ) ?: false
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources if needed
    }

    companion object {
        // TODO this will be removed once the handler is deleted
        // Default animation duration in milliseconds
        const val DELAY_TIME_CALLBACK_CALL = 500
        const val ONESIGNAL_PERMISSION_REQUEST_CODE = 2

        const val INTENT_EXTRA_PERMISSION_TYPE = "INTENT_EXTRA_PERMISSION_TYPE"
        const val INTENT_EXTRA_ANDROID_PERMISSION_STRING =
            "INTENT_EXTRA_ANDROID_PERMISSION_STRING"
        const val INTENT_EXTRA_CALLBACK_CLASS = "INTENT_EXTRA_CALLBACK_CLASS"
    }
}
