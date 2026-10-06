package com.onesignal.core.internal.permissions

import android.app.Activity
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onesignal.OneSignal
import com.onesignal.core.internal.permissions.impl.PermissionPromptRequest
import com.onesignal.core.internal.permissions.impl.PermissionsResultHandler
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Permission state for PermissionsActivity. Survives rotation and does not hold the Activity. */
class PermissionsViewModel : ViewModel() {
    // OneSignal.getService throws before init.
    private val requestPermissionService: RequestPermissionService by lazy { OneSignal.getService() }
    private val resultHandler: PermissionsResultHandler by lazy { requestPermissionService.resultHandler }

    private val _shouldFinish = MutableStateFlow(false)
    val shouldFinish: StateFlow<Boolean> = _shouldFinish.asStateFlow()

    private val _waiting = MutableStateFlow(false)
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()

    var permissionRequestType: String? = null
        private set

    private var androidPermissionString: String? = null

    /** Returns false when init or the intent extras fail. Does not retain [activity]. */
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

    /** Clears waiting so an interrupted activity can prompt again. */
    fun resetWaitingState() {
        _waiting.value = false
    }

    /**
     * Record the rationale state before the permission request.
     * Activity calls this with the result of shouldShowRequestPermissionRationale().
     */
    fun recordRationaleState(shouldShowRationale: Boolean) {
        requestPermissionService.shouldShowRequestPermissionRationaleBeforeRequest = shouldShowRationale
    }

    /** [shouldShowRationaleAfter] is the reading after the user answered. */
    fun onRequestPermissionsResult(
        permissions: Array<String>,
        grantResults: IntArray,
        shouldShowRationaleAfter: Boolean = false,
    ) {
        _waiting.value = false

        // Use viewModelScope with delay for smooth transition
        viewModelScope.launch {
            delay(DELAY_TIME_CALLBACK_CALL.toLong())

            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

            resultHandler.handleResult(
                PermissionPromptRequest(
                    permissionRequestType,
                    permissions.firstOrNull(),
                    requestPermissionService.fallbackToSettings,
                    requestPermissionService.shouldShowRequestPermissionRationaleBeforeRequest,
                ),
                granted,
                shouldShowRationaleAfter,
            )

            // Signal the activity to finish
            _shouldFinish.value = true
        }
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
