package com.onesignal.core.internal.permissions.impl

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.onesignal.common.AndroidUtils
import com.onesignal.common.IHostPermissionPrompt
import com.onesignal.debug.internal.logging.Logging

internal fun runOnMainThread(block: () -> Unit) {
    if (AndroidUtils.isRunningOnMainThread()) {
        block()
    } else {
        Handler(Looper.getMainLooper()).post(block)
    }
}

/**
 * Prompts on the host app's own activity, used when `PermissionsActivity` was dropped from the
 * merged manifest. Prefers a wrapper-supplied prompt, otherwise the activity's result registry.
 */
internal class HostPrompt(
    private val permission: String,
    private val wrapper: IHostPermissionPrompt?,
    private val currentActivity: () -> Activity?,
    private val isComplete: () -> Boolean,
    private val onResult: (granted: Boolean, rationaleAfter: Boolean?) -> Unit,
) {
    private var boundActivity: Activity? = null
    private var launcher: ActivityResultLauncher<String>? = null

    /** True only after the registry, not a wrapper, accepted this prompt. */
    private var registryAccepted = false

    var rationaleBefore = false
        private set

    /** @return false when no surface could be asked, so the caller can complete as denied. */
    @Suppress("ReturnCount")
    fun start(activity: Activity): Boolean {
        rationaleBefore = rationaleOn(activity) ?: false

        if (wrapper != null) {
            val accepted = wrapper.request(permission) { granted -> runOnMainThread { deliver(granted) } }
            if (isComplete()) {
                return true
            }
            if (accepted) {
                Logging.debug("Prompting for $permission through the wrapper host.")
                return true
            }
            Logging.warn("Wrapper host declined to prompt for $permission.")
        }

        if (activity !is ComponentActivity) {
            return false
        }

        registryAccepted = true
        bindTo(activity)
        launcher?.launch(permission)
        Logging.debug("Prompting for $permission on the host activity result registry.")
        return true
    }

    /**
     * A recreated host restores the registry key but not our callback, so the OS answer sits
     * undelivered until we register again. Relaunch only when nothing was waiting for us.
     */
    fun rebind(activity: Activity) {
        if (isComplete() || activity === boundActivity) {
            return
        }
        // A wrapper that accepted the prompt redelivers across its own activity changes.
        if (!registryAccepted || activity !is ComponentActivity) {
            return
        }

        Logging.debug("Host activity changed while prompting for $permission. Rebinding.")
        bindTo(activity)
        launcher?.launch(permission)
    }

    private fun bindTo(activity: ComponentActivity) {
        boundActivity = activity
        // The no-LifecycleOwner overload is required: OneSignal routinely reaches this point
        // after the host activity is already STARTED, which the lifecycle-aware overload rejects.
        val registered =
            activity.activityResultRegistry.register(
                "$REGISTRY_KEY_PREFIX$permission",
                ActivityResultContracts.RequestPermission(),
            ) { granted -> deliver(granted) }

        // register() hands back an answer parked by an earlier process before it returns. Leaving
        // the launcher null then keeps callers from prompting again for a question already answered.
        if (isComplete()) {
            registered.unregister()
            launcher = null
        } else {
            launcher = registered
        }
    }

    private fun deliver(granted: Boolean) {
        launcher?.unregister()
        launcher = null
        Logging.debug("Host prompt for $permission returned granted=$granted.")
        onResult(granted, rationaleOn(currentActivity() ?: boundActivity))
    }

    /** @return null when no activity could be read, which is not the same as a false reading. */
    private fun rationaleOn(activity: Activity?): Boolean? {
        if (activity == null) {
            return null
        }
        return runCatching { ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) }
            .getOrNull()
    }

    private companion object {
        const val REGISTRY_KEY_PREFIX = "com.onesignal.core.permissions.HOST_PROMPT#"
    }
}
