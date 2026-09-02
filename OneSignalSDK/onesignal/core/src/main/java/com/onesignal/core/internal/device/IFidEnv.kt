package com.onesignal.core.internal.device

/**
 * Host-app FID-readiness signals for the OneSignal-Fid-Env request header.
 * google-services.json is not packaged; gs=1 means the plugin wrote google_app_id.
 */
internal interface IFidEnv {
    fun headerValue(): String
}
