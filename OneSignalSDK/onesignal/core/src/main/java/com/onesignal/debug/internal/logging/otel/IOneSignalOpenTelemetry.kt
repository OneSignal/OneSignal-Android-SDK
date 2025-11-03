package com.onesignal.debug.internal.logging.otel

import android.os.Build
import androidx.annotation.RequiresApi
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.common.CompletableResultCode

@RequiresApi(Build.VERSION_CODES.O)
internal interface IOneSignalOpenTelemetry {
    val logger: Logger

    suspend fun forceFlush(): CompletableResultCode
}
