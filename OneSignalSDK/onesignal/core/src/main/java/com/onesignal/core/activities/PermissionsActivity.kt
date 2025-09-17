package com.onesignal.core.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import androidx.core.app.ActivityCompat
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.R
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores

class PermissionsActivity : Activity() {
    private var requestPermissionService: RequestPermissionService? = null
    private var preferenceService: IPreferencesService? = null
    private var permissionRequestType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.extras == null) {
            // This should never happen, but extras is null in rare crash reports
            finishActivity()
            return
        }

        // handle OneSignal bundle params in background
        suspendifyOnThread {
            if (!OneSignal.initWithContext(this)) {
                finishActivity()
                return@suspendifyOnThread
            }

            requestPermissionService = OneSignal.getService()
            preferenceService = OneSignal.getService()

            handleBundleParams(intent.extras)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBundleParams(intent.extras)
    }

    private fun finishActivity() {
        finish()
        overridePendingTransition(R.anim.onesignal_fade_in, R.anim.onesignal_fade_out)
    }

    private fun handleBundleParams(extras: Bundle?) {
        // https://github.com/OneSignal/OneSignal-Android-SDK/issues/30
        // Activity maybe invoked directly through automated testing, omit prompting on old Android versions.
        if (Build.VERSION.SDK_INT < 23) {
            finishActivity()
            return
        }

        reregisterCallbackHandlers(extras)
        permissionRequestType = extras!!.getString(INTENT_EXTRA_PERMISSION_TYPE)
        val androidPermissionString = extras.getString(INTENT_EXTRA_ANDROID_PERMISSION_STRING)

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

    private fun requestPermission(androidPermissionString: String) {
        if (!requestPermissionService!!.waiting) {
            requestPermissionService!!.waiting = true
            requestPermissionService!!.shouldShowRequestPermissionRationaleBeforeRequest =
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this@PermissionsActivity,
                    androidPermissionString,
                )
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
        requestPermissionService!!.waiting = false

        // TODO improve this method
        // TODO after we remove IAM from being an activity window we may be able to remove this handler
        // This is not a good solution!
        // Current problem: IAM depends on an activity, because of prompt permission the evaluation of IAM
        // is being called before the prompt activity dismisses, so it's attaching the IAM to PermissionActivity
        // We need to wait for other activity to show
        if (requestCode == ONESIGNAL_PERMISSION_REQUEST_CODE) {
            Handler().postDelayed({
                val callback =
                    requestPermissionService!!.getCallback(permissionRequestType!!)
                        ?: throw RuntimeException("Missing handler for permissionRequestType: $permissionRequestType")

                // It is possible that the permissions request interaction with the user is interrupted. In this case
                // we will receive empty permissions which should be treated as a cancellation and will not prompt
                // for the permission setting
                val defaultFallbackSetting = false
                if (permissions.isEmpty()) {
                    callback.onReject(defaultFallbackSetting)
                } else {
                    val permission = permissions[0]
                    val granted =
                        grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        callback.onAccept()
                        preferenceService!!.saveBool(
                            PreferenceStores.ONESIGNAL,
                            "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission",
                            true,
                        )
                    } else {
                        callback.onReject(shouldShowSettings(permission))
                    }
                }
            }, DELAY_TIME_CALLBACK_CALL.toLong())
        }

        finishActivity()
    }

    private fun shouldShowSettings(permission: String): Boolean {
        if (!requestPermissionService!!.fallbackToSettings) {
            return false
        }

        // We want to show settings after the user has clicked "Don't Allow" 2 times.
        // After the first time shouldShowRequestPermissionRationale becomes true, after
        // the second time shouldShowRequestPermissionRationale becomes false again. We
        // look for the change from `true` -> `false`. When this happens we remember this
        // rejection, as the user will never be prompted again.
        if (requestPermissionService!!.shouldShowRequestPermissionRationaleBeforeRequest) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this@PermissionsActivity,
                    permission,
                )
            ) {
                preferenceService!!.saveBool(
                    PreferenceStores.ONESIGNAL,
                    "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission",
                    true,
                )
                return false
            }
        }

        return preferenceService!!.getBool(
            PreferenceStores.ONESIGNAL,
            "${PreferenceOneSignalKeys.PREFS_OS_USER_RESOLVED_PERMISSION_PREFIX}$permission",
            false,
        )!!
    }

    companion object {
        // TODO this will be removed once the handled is deleted
        // Default animation duration in milliseconds
        const val DELAY_TIME_CALLBACK_CALL = 500
        const val ONESIGNAL_PERMISSION_REQUEST_CODE = 2

        const val INTENT_EXTRA_PERMISSION_TYPE = "INTENT_EXTRA_PERMISSION_TYPE"
        const val INTENT_EXTRA_ANDROID_PERMISSION_STRING =
            "INTENT_EXTRA_ANDROID_PERMISSION_STRING"
        const val INTENT_EXTRA_CALLBACK_CLASS = "INTENT_EXTRA_CALLBACK_CLASS"
    }
}
