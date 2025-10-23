package com.onesignal.debug.internal.logging

import com.google.auto.service.AutoService
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.instrumentation.common.EventAttributesExtractor
import io.opentelemetry.sdk.OpenTelemetrySdk

/** Entrypoint for installing the crash reporting instrumentation.  */
@AutoService(AndroidInstrumentation::class)
class CrashReporterInstrumentation : AndroidInstrumentation {
    private val additionalExtractors: MutableList<EventAttributesExtractor<CrashDetails>> =
        mutableListOf()

    /** Adds an [EventAttributesExtractor] that will extract additional attributes.  */
    fun addAttributesExtractor(extractor: EventAttributesExtractor<CrashDetails>) {
        additionalExtractors.add(extractor)
    }

    override fun install(ctx: InstallationContext) {
        // For battery, heap size, and storage free space
        // addAttributesExtractor(RuntimeDetailsExtractor.create(ctx.context))
        val crashReporter = CrashReporter(additionalExtractors)

        // TODO avoid using OpenTelemetrySdk methods, only use the ones from OpenTelemetry api.
        crashReporter.install(ctx.openTelemetry as OpenTelemetrySdk)
    }

    override val name: String = "os-crash"
}
