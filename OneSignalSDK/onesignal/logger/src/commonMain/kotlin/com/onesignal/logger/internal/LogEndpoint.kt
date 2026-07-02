package com.onesignal.logger.internal

/**
 * Builds the log ingestion endpoint, mirroring the path the OpenTelemetry exporter
 * used: `{apiBaseUrl}/sdk/log?app_id={appId}`.
 */
internal object LogEndpoint {
    private const val LOG_PATH = "sdk/log"

    fun build(apiBaseUrl: String, appId: String): String {
        val base = apiBaseUrl.trimEnd('/')
        return "$base/$LOG_PATH?app_id=$appId"
    }
}
