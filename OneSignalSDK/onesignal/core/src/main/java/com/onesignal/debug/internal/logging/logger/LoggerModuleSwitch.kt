package com.onesignal.debug.internal.logging.logger

import android.content.Context
import com.onesignal.debug.internal.logging.otel.android.OtelIdResolver

/**
 * Routes the SDK's observability (remote logging, crash capture, crash upload, ANR detection)
 * through either:
 *  - the legacy OpenTelemetry-based `otel` module (default), or
 *  - the new, multiplatform, OpenTelemetry-free `logger` module.
 *
 * The choice is driven by the [com.onesignal.core.internal.features.FeatureFlag.SDK_CUSTOM_LOGGING]
 * remote feature flag, read from the cached config in SharedPreferences via [OtelIdResolver]. Because
 * the value comes from the config the *previous* session persisted, enabling/disabling the flag takes
 * effect on the next app start — never mid-session (the flag is [FeatureActivationMode.APP_STARTUP]).
 *
 * The flag is read directly from prefs (not through [com.onesignal.core.internal.features.FeatureManager])
 * because the module decision is made during early init, before service bootstrap. It is read fresh on
 * each call — consumers ([com.onesignal.internal.LoggerLifecycleManager] and the crash uploader) all run
 * early in the same init pass, before the first remote config fetch can change the persisted value, so
 * they resolve to the same module for the session.
 *
 * Once the `logger` module has been validated in production, the otel path (and this switch) can be
 * removed along with the `:otel` module.
 */
internal object LoggerModuleSwitch {
    fun useLoggerModule(context: Context): Boolean = OtelIdResolver(context).resolveCustomLoggingEnabled()
}
