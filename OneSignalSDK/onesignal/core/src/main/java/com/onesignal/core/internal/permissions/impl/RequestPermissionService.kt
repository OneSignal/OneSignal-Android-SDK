package com.onesignal.core.internal.permissions.impl

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.R
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.permissions.PermissionsViewModel
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class RequestPermissionService(
    private val _application: IApplicationService,
    private val _preferences: IPreferencesService,
) : IRequestPermissionService {
    var waiting = false
    var fallbackToSettings = false
    var shouldShowRequestPermissionRationaleBeforeRequest = false
    private val callbackMap = HashMap<String?, IRequestPermissionService.PermissionCallback>()
    private val hostPromptsInFlight = ConcurrentHashMap<String, Prompt>()

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
            Prompt(permissionRequestType, androidPermissionString, callbackClass, fallbackCondition),
        )
    }

    /**
     * One prompt attempt. All request state lives here rather than on the service so that
     * overlapping prompts cannot overwrite each other's settings fallback or rationale.
     */
    internal inner class Prompt(
        private val permissionRequestType: String?,
        private val permission: String?,
        private val callbackClass: Class<*>,
        private val fallbackToSettings: Boolean,
    ) : IActivityLifecycleHandler {
        private val completed = AtomicBoolean(false)
        private var hostPrompt: HostPrompt? = null

        override fun onActivityAvailable(activity: Activity) {
            if (completed.get()) {
                return
            }

            val host = hostPrompt
            if (host != null) {
                runOnMainThread { host.rebind(activity) }
                return
            }

            if (activity is PermissionsActivity) {
                _application.removeActivityLifecycleHandler(this)
                return
            }

            launchPermissionsActivity(activity)
        }

        override fun onActivityStopped(activity: Activity) {
        }

        private fun launchPermissionsActivity(activity: Activity) {
            val intent = Intent(activity, PermissionsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra(PermissionsViewModel.INTENT_EXTRA_PERMISSION_TYPE, permissionRequestType)
                .putExtra(PermissionsViewModel.INTENT_EXTRA_ANDROID_PERMISSION_STRING, permission)
                .putExtra(PermissionsViewModel.INTENT_EXTRA_CALLBACK_CLASS, callbackClass.name)

            // Host tools:node=replace can drop this activity. Do not retry on later activities.
            if (intent.resolveActivity(activity.packageManager) == null) {
                onPermissionsActivityMissing(activity, null)
                return
            }

            try {
                activity.startActivity(intent)
                activity.overridePendingTransition(
                    R.anim.onesignal_fade_in,
                    R.anim.onesignal_fade_out,
                )
            } catch (e: ActivityNotFoundException) {
                onPermissionsActivityMissing(activity, e)
            }
        }

        /**
         * Falls back to prompting on the host's own activity, which survives `tools:node="replace"`.
         * Only when no host can report a grant back do we complete the prompt as denied.
         */
        private fun onPermissionsActivityMissing(
            activity: Activity,
            cause: Throwable?,
        ) {
            Logging.warn(
                "PermissionsActivity is missing from the merged manifest. " +
                    "<application tools:node=\"replace\"> drops library activities. " +
                    "Use tools:replace on the specific attribute instead.",
                cause,
            )

            val wrapper = OneSignalWrapper.hostPermissionPrompt
            if (permission == null || (wrapper == null && activity !is ComponentActivity)) {
                Logging.error("No host activity can prompt for $permission. Completing the prompt as denied.")
                completeAsDenied()
                return
            }

            // A second prompt for the same permission would take over this one's registry key.
            if (hostPromptsInFlight.putIfAbsent(permission, this) != null) {
                Logging.warn("A host prompt for $permission is already in flight. Completing this one as denied.")
                completeAsDenied()
                return
            }

            val host =
                HostPrompt(
                    permission,
                    wrapper,
                    { _application.current },
                    { completed.get() },
                ) { granted, rationaleAfter -> completeWith(granted, rationaleAfter) }
            hostPrompt = host

            runOnMainThread {
                val prompted = runCatching { host.start(activity) }
                if (prompted.isFailure) {
                    // A prompt that throws would otherwise strand the caller suspended forever.
                    Logging.error("Host permission prompt failed for $permission.", prompted.exceptionOrNull())
                }
                if (prompted.getOrDefault(false).not()) {
                    completeAsDenied()
                }
            }
        }

        private fun completeWith(
            granted: Boolean,
            rationaleAfter: Boolean?,
        ) {
            val host = hostPrompt
            if (!claimCompletion()) {
                return
            }
            resultHandler.handleResult(
                PermissionPromptRequest(
                    permissionRequestType,
                    permission,
                    fallbackToSettings,
                    host?.rationaleBefore ?: false,
                ),
                granted,
                rationaleAfter,
            )
        }

        private fun completeAsDenied() {
            if (!claimCompletion()) {
                return
            }
            permissionRequestType?.let { getCallback(it)?.onReject(fallbackToSettings) }
        }

        private fun claimCompletion(): Boolean {
            if (!completed.compareAndSet(false, true)) {
                return false
            }
            _application.removeActivityLifecycleHandler(this)
            permission?.let { hostPromptsInFlight.remove(it, this) }
            return true
        }
    }
}
