package com.onesignal.onesignal.core.internal.permissions.impl

import android.app.Activity
import android.content.Intent
import com.onesignal.R
import com.onesignal.onesignal.core.activities.PermissionsActivity
import com.onesignal.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.permissions.IRequestPermissionService
import java.util.HashMap

class RequestPermissionService(
    private val _application: IApplicationService
) : Activity(), IRequestPermissionService {

    var waiting = false
    var fallbackToSettings = false
    var neverAskAgainClicked = false
    private val callbackMap = HashMap<String?, IRequestPermissionService.PermissionCallback>()

    override fun registerAsCallback(
        permissionType: String,
        callback: IRequestPermissionService.PermissionCallback
    ) {
        callbackMap[permissionType] =
            callback
    }

    fun getCallback(permissionType: String) : IRequestPermissionService.PermissionCallback? {
        return callbackMap[permissionType]
    }

    override fun startPrompt(
        fallbackCondition: Boolean,
        permissionRequestType: String?,
        androidPermissionString: String?,
        callbackClass: Class<*>
    ) {
        if (waiting)
            return

        fallbackToSettings = fallbackCondition

        _application.addActivityLifecycleHandler(object : IActivityLifecycleHandler {
            override fun onActivityAvailable(activity: Activity) {
                if (activity.javaClass != PermissionsActivity::class.java) {
                    val intent = Intent(activity, PermissionsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    intent.putExtra(PermissionsActivity.INTENT_EXTRA_PERMISSION_TYPE, permissionRequestType)
                        .putExtra(PermissionsActivity.INTENT_EXTRA_ANDROID_PERMISSION_STRING, androidPermissionString)
                        .putExtra(PermissionsActivity.INTENT_EXTRA_CALLBACK_CLASS, callbackClass.name)
                    activity!!.startActivity(intent)
                    activity!!.overridePendingTransition(
                        R.anim.onesignal_fade_in,
                        R.anim.onesignal_fade_out
                    )
                }
            }

            override fun onActivityStopped(activity: Activity) {

            }
        })
    }
}