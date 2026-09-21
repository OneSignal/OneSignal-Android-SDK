package com.onesignal.core.internal.permissions.impl

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import com.onesignal.core.R
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.permissions.PermissionsViewModel
import com.onesignal.debug.internal.logging.Logging

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
                    if (activity is PermissionsActivity) {
                        _application.removeActivityLifecycleHandler(this)
                        return
                    }

                    val intent = Intent(activity, PermissionsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    intent.putExtra(PermissionsViewModel.INTENT_EXTRA_PERMISSION_TYPE, permissionRequestType)
                        .putExtra(PermissionsViewModel.INTENT_EXTRA_ANDROID_PERMISSION_STRING, androidPermissionString)
                        .putExtra(PermissionsViewModel.INTENT_EXTRA_CALLBACK_CLASS, callbackClass.name)

                    // Host tools:node=replace can drop this activity. Do not retry on later activities.
                    if (intent.resolveActivity(activity.packageManager) == null) {
                        failMissingActivity(this, permissionRequestType)
                        return
                    }

                    try {
                        activity.startActivity(intent)
                        activity.overridePendingTransition(
                            R.anim.onesignal_fade_in,
                            R.anim.onesignal_fade_out,
                        )
                    } catch (e: ActivityNotFoundException) {
                        failMissingActivity(this, permissionRequestType, e)
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                }
            },
        )
    }

    private fun failMissingActivity(
        handler: IActivityLifecycleHandler,
        permissionRequestType: String?,
        cause: Throwable? = null,
    ) {
        _application.removeActivityLifecycleHandler(handler)
        Logging.error(
            "PermissionsActivity is missing from the merged manifest. " +
                "<application tools:node=\"replace\"> drops library activities. " +
                "Use tools:replace on the specific attribute instead.",
            cause,
        )
        permissionRequestType?.let { getCallback(it)?.onReject(fallbackToSettings) }
    }
}
