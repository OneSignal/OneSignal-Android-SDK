package com.onesignal.debug.internal.logging.otel.crash

import com.onesignal.core.internal.application.IApplicationService
import java.io.File

internal class OneSignalCrashConfigProvider(
    private val _applicationService: IApplicationService
) : IOneSignalCrashConfigProvider {
    override val path: String by lazy {
        _applicationService.appContext.cacheDir.path + File.separator +
            "onesignal" + File.separator +
            "otel" + File.separator +
            "crashes"
    }

    override val minFileAgeForReadMillis: Long = 5_000
}
