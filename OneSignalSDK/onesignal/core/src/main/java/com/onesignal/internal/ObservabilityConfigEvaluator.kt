package com.onesignal.internal

import com.onesignal.debug.LogLevel

/** Snapshot of the observability-relevant fields from remote config. */
internal data class ObservabilityConfig(
    val isEnabled: Boolean,
    val logLevel: LogLevel?,
) {
    companion object {
        val DISABLED = ObservabilityConfig(isEnabled = false, logLevel = null)
    }
}

/** What [LoggerLifecycleManager] should do after a config change. */
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

/** Pure diff of two [ObservabilityConfig]s; sees desired config only, never actual liveness. */
internal object ObservabilityConfigEvaluator {
    /** [old] is null on the first evaluation of a cold start. */
    fun evaluate(old: ObservabilityConfig?, new: ObservabilityConfig): ObservabilityConfigAction {
        val wasEnabled = old?.isEnabled == true
        val isNowEnabled = new.isEnabled

        return when {
            !wasEnabled && isNowEnabled -> {
                val level = new.logLevel ?: LogLevel.ERROR
                ObservabilityConfigAction.Enable(level)
            }
            wasEnabled && !isNowEnabled -> ObservabilityConfigAction.Disable
            wasEnabled && isNowEnabled && old?.logLevel != new.logLevel -> {
                val oldLevel = old?.logLevel ?: LogLevel.ERROR
                val newLevel = new.logLevel ?: LogLevel.ERROR
                ObservabilityConfigAction.UpdateLogLevel(oldLevel, newLevel)
            }
            else -> ObservabilityConfigAction.NoChange
        }
    }
}
