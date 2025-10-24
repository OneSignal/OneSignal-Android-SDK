package com.onesignal.core.internal.permissions.impl

import android.app.Activity
import android.content.Intent
import com.onesignal.core.R
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.permissions.PermissionsViewModel

internal class RequestPermissionService(
    private val _application: IApplicationService,
) : IRequestPermissionService {
    var waiting = false
    var fallbackToSettings = false
    var shouldShowRequestPermissionRationaleBeforeRequest = false
    private val callbackMap = HashMap<String?, IRequestPermissionService.PermissionCallback>()

    override fun registerAsCallback(
        permissionType: String,
        callback: IRequestPermissionService.PermissionCallback,
    ) {
        callbackMap[permissionType] =
            callback
    }

    fun getCallback(permissionType: String): IRequestPermissionService.PermissionCallback? {
        return callbackMap[permissionType]
    }

    override fun startPrompt(
        fallbackCondition: Boolean,
        permissionRequestType: String?,
        androidPermissionString: String?,
        callbackClass: Class<*>,
    ) {
        if (waiting) {
            return
        }

        fallbackToSettings = fallbackCondition

        // it's possible the prompt is started before there's an activity available, or the
        // current activity is changed.  We keep trying to add the permission prompt whenever
        // an activity becomes available, until our permission activity is the one that's
        // available.
        _application.addActivityLifecycleHandler(
            object : IActivityLifecycleHandler {
                override fun onActivityAvailable(activity: Activity) {
                    if (activity.javaClass == PermissionsActivity::class.java) {
                        _application.removeActivityLifecycleHandler(this)
                    } else {
                        val intent = Intent(activity, PermissionsActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        intent.putExtra(PermissionsViewModel.INTENT_EXTRA_PERMISSION_TYPE, permissionRequestType)
                            .putExtra(PermissionsViewModel.INTENT_EXTRA_ANDROID_PERMISSION_STRING, androidPermissionString)
                            .putExtra(PermissionsViewModel.INTENT_EXTRA_CALLBACK_CLASS, callbackClass.name)
                        activity.startActivity(intent)
                        activity.overridePendingTransition(
                            R.anim.onesignal_fade_in,
                            R.anim.onesignal_fade_out,
                        )
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                }
            },
        )
    }
}
