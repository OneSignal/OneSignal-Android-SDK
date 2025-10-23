package com.onesignal.core.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.onesignal.core.R
import com.onesignal.core.internal.permissions.PermissionsViewModel
import com.onesignal.core.internal.permissions.PermissionsViewModel.Companion.INTENT_EXTRA_ANDROID_PERMISSION_STRING
import com.onesignal.core.internal.permissions.PermissionsViewModel.Companion.INTENT_EXTRA_CALLBACK_CLASS
import com.onesignal.core.internal.permissions.PermissionsViewModel.Companion.INTENT_EXTRA_PERMISSION_TYPE
import com.onesignal.core.internal.permissions.PermissionsViewModel.Companion.ONESIGNAL_PERMISSION_REQUEST_CODE
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity that handles runtime permission requests for OneSignal.
 * Uses ViewModel for business logic and state management that survives configuration changes.
 */
class PermissionsActivity : ComponentActivity() {
    private val viewModel: PermissionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.extras == null) {
            // This should never happen, but extras is null in rare crash reports
            finishActivity()
            return
        }

        // Observe the shouldFinish state to know when to close the activity
        lifecycleScope.launch {
            viewModel.shouldFinish.collectLatest { shouldFinish ->
                if (shouldFinish) {
                    finishActivity()
                }
            }
        }

        // Only handle bundle params on first creation, not on config changes
        // ViewModel retains state across config changes, so permission state survives rotation
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                handleBundleParams(intent.extras)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch {
            handleBundleParams(intent.extras)
        }
    }

    private fun finishActivity() {
        finish()
        overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out)
    }

    private suspend fun handleBundleParams(extras: Bundle?) {
        // https://github.com/OneSignal/OneSignal-Android-SDK/issues/30
        // Activity maybe invoked directly through automated testing, omit prompting on old Android versions.
        if (Build.VERSION.SDK_INT < 23) {
            finishActivity()
            return
        }

        reregisterCallbackHandlers(extras)

        val permissionType = extras!!.getString(INTENT_EXTRA_PERMISSION_TYPE)
        val androidPermissionString = extras.getString(INTENT_EXTRA_ANDROID_PERMISSION_STRING)

        // Initialize OneSignal and ViewModel (handles initialization in one place)
        if (!viewModel.initialize(this, permissionType, androidPermissionString)) {
            finishActivity()
            return
        }

        // Request permission - this is Activity-layer logic
        requestPermission(androidPermissionString!!)
    }

    // Required if the app was killed while this prompt was showing
    private fun reregisterCallbackHandlers(extras: Bundle?) {
        val className = extras!!.getString(INTENT_EXTRA_CALLBACK_CLASS)
        try {
            // Loads class into memory so it's static initialization block runs
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(
                "Could not find callback class for PermissionActivity: $className",
            )
        }
    }

    /**
     * Request permission from the Activity (not ViewModel).
     * This is UI-layer logic that should not be in the ViewModel.
     */
    private fun requestPermission(androidPermissionString: String) {
        // Check if we should request (ViewModel tracks state)
        if (viewModel.shouldRequestPermission()) {
            // Store the rationale state before requesting
            viewModel.recordRationaleState(
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    androidPermissionString,
                ),
            )

            // Actually request the permission (Activity responsibility)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(androidPermissionString),
                ONESIGNAL_PERMISSION_REQUEST_CODE,
            )
        }
    }

    // NOTE: This code assumes only one permission was prompted for
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // TODO improve this method
        // TODO after we remove IAM from being an activity window we may be able to remove this handler
        // This is not a good solution!
        // Current problem: IAM depends on an activity, because of prompt permission the evaluation of IAM
        // is being called before the prompt activity dismisses, so it's attaching the IAM to PermissionActivity
        // We need to wait for other activity to show
        if (requestCode == ONESIGNAL_PERMISSION_REQUEST_CODE) {
            // Check shouldShowRequestPermissionRationale AFTER the user responded
            val shouldShowRationaleAfter =
                if (permissions.isNotEmpty()) {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])
                } else {
                    false
                }

            // Let ViewModel handle the business logic
            viewModel.onRequestPermissionsResult(permissions, grantResults, shouldShowRationaleAfter)
            // Activity will finish when ViewModel sets shouldFinish state
        }
    }
}
