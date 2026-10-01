package com.onesignal.core.internal.permissions.impl

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.onesignal.common.AndroidUtils
import com.onesignal.common.IHostPermissionPrompt
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.R
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.permissions.PermissionsViewModel
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.atomic.AtomicInteger

internal class RequestPermissionService(
    private val _application: IApplicationService,
    private val _preferences: IPreferencesService,
) : IRequestPermissionService {
    var waiting = false
    var fallbackToSettings = false
    var shouldShowRequestPermissionRationaleBeforeRequest = false
    private val callbackMap = HashMap<String?, IRequestPermissionService.PermissionCallback>()
    private val hostPromptCount = AtomicInteger()

    val resultHandler: PermissionsResultHandler by lazy { PermissionsResultHandler(this, _preferences) }

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
                        onPermissionsActivityMissing(this, activity, permissionRequestType, androidPermissionString)
                        return
                    }

                    try {
                        activity.startActivity(intent)
                        activity.overridePendingTransition(
                            R.anim.onesignal_fade_in,
                            R.anim.onesignal_fade_out,
                        )
                    } catch (e: ActivityNotFoundException) {
                        onPermissionsActivityMissing(this, activity, permissionRequestType, androidPermissionString, e)
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                }
            },
        )
    }

    /**
     * Falls back to prompting on the host's own activity, which survives `tools:node="replace"`.
     * Only when no host can report a grant back do we complete the prompt as denied.
     */
    private fun onPermissionsActivityMissing(
        handler: IActivityLifecycleHandler,
        activity: Activity,
        permissionRequestType: String?,
        androidPermissionString: String?,
        cause: Throwable? = null,
    ) {
        _application.removeActivityLifecycleHandler(handler)
        Logging.error(
            "PermissionsActivity is missing from the merged manifest. " +
                "<application tools:node=\"replace\"> drops library activities. " +
                "Use tools:replace on the specific attribute instead.",
            cause,
        )

        val permission = androidPermissionString
        val wrapperPrompt = OneSignalWrapper.hostPermissionPrompt
        if (permission == null || (wrapperPrompt == null && activity !is ComponentActivity)) {
            Logging.error("No host activity can prompt for $permission. Completing the prompt as denied.")
            completeAsDenied(permissionRequestType)
            return
        }

        // Both the result registry and ActivityCompat.requestPermissions are main-thread only.
        runOnMain {
            promptOnHost(activity, permissionRequestType, permission, wrapperPrompt)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (AndroidUtils.isRunningOnMainThread()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }

    private fun promptOnHost(
        activity: Activity,
        permissionRequestType: String?,
        permission: String,
        wrapperPrompt: IHostPermissionPrompt?,
    ) {
        shouldShowRequestPermissionRationaleBeforeRequest =
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

        try {
            if (wrapperPrompt != null) {
                val accepted =
                    wrapperPrompt.request(permission) { granted ->
                        onHostPromptResult(permissionRequestType, permission, granted)
                    }
                if (accepted) {
                    return
                }
                Logging.warn("Wrapper host prompt declined the request for $permission.")
            }

            if (activity is ComponentActivity) {
                launchOnHostRegistry(activity, permissionRequestType, permission)
                return
            }
        } catch (e: Exception) {
            // A prompt that throws here would otherwise strand the caller suspended forever.
            Logging.error("Host permission prompt failed for $permission.", e)
        }

        completeAsDenied(permissionRequestType)
    }

    private fun launchOnHostRegistry(
        activity: ComponentActivity,
        permissionRequestType: String?,
        permission: String,
    ) {
        // The no-LifecycleOwner overload is required: OneSignal routinely reaches this point
        // after the host activity is already STARTED, which the lifecycle-aware overload rejects.
        // Reusing a key would silently replace an in-flight prompt's callback, stranding it.
        var launcher: ActivityResultLauncher<String>? = null
        launcher =
            activity.activityResultRegistry.register(
                "$HOST_REGISTRY_KEY#${hostPromptCount.incrementAndGet()}",
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                launcher?.unregister()
                onHostPromptResult(permissionRequestType, permission, granted)
            }
        launcher.launch(permission)
    }

    private fun onHostPromptResult(
        permissionRequestType: String?,
        permission: String,
        granted: Boolean,
    ) {
        val current = _application.current
        val shouldShowRationaleAfter =
            current != null && ActivityCompat.shouldShowRequestPermissionRationale(current, permission)

        resultHandler.handleResult(permissionRequestType, permission, granted, shouldShowRationaleAfter)
    }

    private fun completeAsDenied(permissionRequestType: String?) {
        permissionRequestType?.let { getCallback(it)?.onReject(fallbackToSettings) }
    }

    private companion object {
        const val HOST_REGISTRY_KEY = "com.onesignal.core.permissions.HOST_PROMPT"
    }
}
