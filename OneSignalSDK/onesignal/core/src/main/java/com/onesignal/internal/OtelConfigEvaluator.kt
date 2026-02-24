package com.onesignal.internal

import com.onesignal.debug.LogLevel

/**
 * Snapshot of the Otel-relevant fields from remote config.
 * Used by [OtelConfigEvaluator] to diff old vs new config.
 */
internal data class OtelConfig(
    val isEnabled: Boolean,
    val logLevel: LogLevel?,
) {
    companion object {
        val DISABLED = OtelConfig(isEnabled = false, logLevel = null)
    }
}

/**
 * Describes what the [OtelLifecycleManager] should do after a config change.
 */
internal sealed class OtelConfigAction {
    /** Nothing changed that affects Otel features. */
    object NoChange : OtelConfigAction()

    /** Otel features should be started at the given [logLevel]. */
    data class Enable(val logLevel: LogLevel) : OtelConfigAction()

    /** The remote log level changed while features remain enabled. */
    data class UpdateLogLevel(val oldLevel: LogLevel, val newLevel: LogLevel) : OtelConfigAction()

    /** Otel features should be stopped/torn down. */
    object Disable : OtelConfigAction()
}

/**
 * Pure, side-effect-free evaluator that compares old and new [OtelConfig]
 * and returns the [OtelConfigAction] the lifecycle manager should execute.
 *
 * Designed to be fully unit-testable without mocks.
 */
internal object OtelConfigEvaluator {
    /**
     * @param old the previous config snapshot, or null on first evaluation (cold start).
     * @param new the freshly-arrived config snapshot.
     */
    fun evaluate(old: OtelConfig?, new: OtelConfig): OtelConfigAction {
        val wasEnabled = old?.isEnabled == true
        val isNowEnabled = new.isEnabled

        return when {
            // Transition: off -> on
            !wasEnabled && isNowEnabled -> {
                val level = new.logLevel ?: LogLevel.ERROR
                OtelConfigAction.Enable(level)
            }
            // Transition: on -> off
            wasEnabled && !isNowEnabled -> OtelConfigAction.Disable
            // Stays enabled but log level changed
            wasEnabled && isNowEnabled && old?.logLevel != new.logLevel -> {
                val oldLevel = old?.logLevel ?: LogLevel.ERROR
                val newLevel = new.logLevel ?: LogLevel.ERROR
                OtelConfigAction.UpdateLogLevel(oldLevel, newLevel)
            }
            // Everything else: no meaningful change
            else -> OtelConfigAction.NoChange
        }
    }
}
