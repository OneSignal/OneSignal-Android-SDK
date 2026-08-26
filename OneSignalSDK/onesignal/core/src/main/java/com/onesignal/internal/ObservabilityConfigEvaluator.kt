package com.onesignal.internal

import com.onesignal.debug.LogLevel

/**
 * Snapshot of the observability-relevant fields from remote config.
 * Used by [ObservabilityConfigEvaluator] to diff old vs new config.
 */
internal data class ObservabilityConfig(
    val isEnabled: Boolean,
    val logLevel: LogLevel?,
) {
    companion object {
        val DISABLED = ObservabilityConfig(isEnabled = false, logLevel = null)
    }
}

/**
 * Describes what the [LoggerLifecycleManager] should do after a config change.
 */
internal sealed class ObservabilityConfigAction {
    /** Nothing changed that affects observability features. */
    object NoChange : ObservabilityConfigAction()

    /** Observability features should be started at the given [logLevel]. */
    data class Enable(val logLevel: LogLevel) : ObservabilityConfigAction()

    /** The remote log level changed while features remain enabled. */
    data class UpdateLogLevel(val oldLevel: LogLevel, val newLevel: LogLevel) : ObservabilityConfigAction()

    /** Observability features should be stopped/torn down. */
    object Disable : ObservabilityConfigAction()
}

/**
 * Pure, side-effect-free evaluator that compares old and new [ObservabilityConfig]
 * and returns the [ObservabilityConfigAction] the lifecycle manager should execute.
 */
internal object ObservabilityConfigEvaluator {
    /**
     * @param old the previous config snapshot, or null on first evaluation (cold start).
     * @param new the freshly-arrived config snapshot.
     */
    fun evaluate(old: ObservabilityConfig?, new: ObservabilityConfig): ObservabilityConfigAction {
        val wasEnabled = old?.isEnabled == true
        val isNowEnabled = new.isEnabled

        return when {
            // Transition: off -> on
            !wasEnabled && isNowEnabled -> {
                val level = new.logLevel ?: LogLevel.ERROR
                ObservabilityConfigAction.Enable(level)
            }
            // Transition: on -> off
            wasEnabled && !isNowEnabled -> ObservabilityConfigAction.Disable
            // Stays enabled but log level changed
            wasEnabled && isNowEnabled && old?.logLevel != new.logLevel -> {
                val oldLevel = old?.logLevel ?: LogLevel.ERROR
                val newLevel = new.logLevel ?: LogLevel.ERROR
                ObservabilityConfigAction.UpdateLogLevel(oldLevel, newLevel)
            }
            // Everything else: no meaningful change
            else -> ObservabilityConfigAction.NoChange
        }
    }
}
