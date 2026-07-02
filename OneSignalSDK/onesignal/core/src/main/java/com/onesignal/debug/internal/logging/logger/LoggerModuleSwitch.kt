package com.onesignal.debug.internal.logging.logger

/**
 * Single switch that routes the SDK's observability (remote logging, crash capture,
 * crash upload, ANR detection) through either:
 *  - the legacy OpenTelemetry-based `otel` module (default), or
 *  - the new, multiplatform, OpenTelemetry-free `logger` module.
 *
 * Flip [USE_LOGGER_MODULE] to `true` to exercise the `logger` module end-to-end on
 * Android. Keeping it `false` by default leaves the existing otel path — and all of
 * its tests — completely unchanged, so the swap is a one-line, low-risk toggle.
 *
 * Once the `logger` module has been validated in production, the otel path (and this
 * switch) can be removed along with the `:otel` module.
 */
internal object LoggerModuleSwitch {
    const val USE_LOGGER_MODULE = false
}
