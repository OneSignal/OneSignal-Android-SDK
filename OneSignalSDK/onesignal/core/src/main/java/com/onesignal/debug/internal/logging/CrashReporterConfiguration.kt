package com.onesignal.debug.internal.logging

//import io.opentelemetry.android.agent.dsl.OpenTelemetryDslMarker
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.common.EventAttributesExtractor
import io.opentelemetry.android.instrumentation.crash.CrashDetails
import io.opentelemetry.android.instrumentation.crash.CrashReporterInstrumentation

//@OpenTelemetryDslMarker
class CrashReporterConfiguration internal constructor(
    private val config: OtelRumConfig,
)
//    : WithEventAttributes<CrashDetails>,
//    CanBeEnabledAndDisabled
{
//    private val crashReporterInstrumentation: CrashReporterInstrumentation by lazy {
//        AndroidInstrumentationLoader.getInstrumentation(
//            CrashReporterInstrumentation::class.java,
//        )
//    }

//    override fun addAttributesExtractor(value: EventAttributesExtractor<CrashDetails>) {
//        crashReporterInstrumentation.addAttributesExtractor(value)
//    }
//
//    override fun enabled(enabled: Boolean) {
//        if (enabled) {
//            config.allowInstrumentation(crashReporterInstrumentation.name)
//        } else {
//            config.suppressInstrumentation(crashReporterInstrumentation.name)
//        }
//    }
}