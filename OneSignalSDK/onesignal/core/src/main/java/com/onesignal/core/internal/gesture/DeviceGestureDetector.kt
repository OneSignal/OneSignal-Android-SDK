package com.onesignal.core.internal.gesture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import com.onesignal.common.IDManager
import com.onesignal.common.threading.suspendifyOnMain
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging

/**
 * Detects the test-device gesture: [REQUIRED_CYCLES] background/foreground cycles within
 * [WINDOW_MS], then copies the push subscription ID to the clipboard, prefixed `os:` (see
 * [clipText]), so the person can paste it into the dashboard.
 *
 * A cycle is an unfocus/focus pair whose background phase lasts at least
 * [MIN_BACKGROUND_DWELL_MS]; the floor filters the synthetic pair
 * [com.onesignal.core.internal.application.impl.ApplicationService.onOrientationChanged]
 * fires when an activity declaring orientation in `configChanges` rotates. The window is the
 * only rate rule; six cycles inside it takes sustained five-second round trips.
 *
 * Adding [KILL_SWITCH_KEY] to the app's enabled feature keys disables the gesture. Absent
 * means enabled, so a device that has never fetched flags still has it. Reads the raw
 * [com.onesignal.core.internal.config.ConfigModel.sdkRemoteFeatureFlags] list because
 * [com.onesignal.core.internal.features.IFeatureManager] only resolves keys the KMP catalog
 * registers.
 */
internal class DeviceGestureDetector(
    private val applicationService: IApplicationService,
    private val configModelStore: ConfigModelStore,
) : IStartableService,
    IApplicationLifecycleHandler {
    /**
     * Monotonic clock, so wall-clock jumps from NTP or manual time changes cannot stretch or
     * shrink the window. Test-only override; kept out of the constructor so the IoC's
     * reflection-based resolver still picks the only constructor (see the class KDoc on
     * [com.onesignal.core.internal.config.impl.FeatureFlagsRefreshService]).
     */
    internal var monotonicMillis: () -> Long = { SystemClock.uptimeMillis() }

    private var lastUnfocusedAt: Long? = null
    private val cycleTimestamps = mutableListOf<Long>()

    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
    }

    override fun onFocus(firedOnSubscribe: Boolean) {
        // The subscribe-time replay is not a background-to-foreground transition, and it can
        // arrive on a non-main thread during startup.
        if (firedOnSubscribe) {
            return
        }
        val now = monotonicMillis()
        val completedGesture =
            synchronized(this) {
                val backgroundedAt = lastUnfocusedAt
                lastUnfocusedAt = null
                when {
                    // Cold start or first focus after start(); nothing to pair with.
                    backgroundedAt == null -> false
                    // Faster than any human app switch; rotation produces synthetic pairs like this.
                    now - backgroundedAt < MIN_BACKGROUND_DWELL_MS -> {
                        Logging.verbose(
                            "DeviceGestureDetector: ignored a ${now - backgroundedAt}ms background blip (rotation filter)",
                        )
                        false
                    }
                    else -> {
                        cycleTimestamps.add(now)
                        cycleTimestamps.removeAll { now - it > WINDOW_MS }
                        Logging.verbose(
                            "DeviceGestureDetector: cycle ${cycleTimestamps.size}/$REQUIRED_CYCLES within the window " +
                                "(background ${now - backgroundedAt}ms)",
                        )
                        if (cycleTimestamps.size >= REQUIRED_CYCLES) {
                            cycleTimestamps.clear()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        if (completedGesture) {
            copySubscriptionIdToClipboard()
        }
    }

    override fun onUnfocused() {
        val now = monotonicMillis()
        synchronized(this) {
            lastUnfocusedAt = now
        }
    }

    private fun copySubscriptionIdToClipboard() {
        val config = configModelStore.model
        val subscriptionId = config.pushSubscriptionId
        when {
            config.consentRequired == true && config.consentGiven != true ->
                Logging.debug("DeviceGestureDetector: gesture detected but privacy consent is not granted")
            config.sdkRemoteFeatureFlags.any { it.equals(KILL_SWITCH_KEY, ignoreCase = true) } ->
                Logging.debug("DeviceGestureDetector: gesture detected but disabled remotely")
            subscriptionId.isNullOrEmpty() || IDManager.isLocalId(subscriptionId) ->
                Logging.info("DeviceGestureDetector: gesture detected before the push subscription exists, nothing copied")
            else -> writeToClipboard(subscriptionId)
        }
    }

    private fun writeToClipboard(subscriptionId: String) {
        suspendifyOnMain {
            val context = applicationService.appContext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                Logging.warn("DeviceGestureDetector: clipboard service unavailable, nothing copied")
            } else {
                // No EXTRA_IS_SENSITIVE: the Android 13+ copy preview is the person's confirmation.
                clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, clipText(subscriptionId)))
                Logging.info("DeviceGestureDetector: push subscription ID copied to clipboard")
            }
        }
    }

    companion object {
        internal const val REQUIRED_CYCLES = 6
        internal const val WINDOW_MS = 30_000L

        /** Shortest background phase a human can produce; anything faster is synthetic. */
        internal const val MIN_BACKGROUND_DWELL_MS = 250L

        internal const val KILL_SWITCH_KEY = "sdk_device_gesture_disabled"
        private const val CLIP_LABEL = "OneSignal subscription ID"

        /**
         * The `os:` prefix marks the value as a OneSignal ID, for the dashboard's paste target and
         * for anyone who copied it by accident.
         */
        internal fun clipText(subscriptionId: String): String = "os: $subscriptionId"
    }
}
