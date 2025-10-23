package com.onesignal.debug.internal.logging

import android.app.Application
import android.content.Context
import android.util.Log
import io.honeycomb.opentelemetry.android.Honeycomb
import io.honeycomb.opentelemetry.android.HoneycombOptions
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import java.util.ServiceLoader

//import io.opentelemetry.sdk.logs.LogRecordProcessor
//import io.opentelemetry.sdk.logs.ReadWriteLogRecord
//import io.opentelemetry.sdk.OpenTelemetrySdk


internal object HoneyCombIOLogging {

    fun listClasses () {
        var test = CrashReporterInstrumentation()
        Log.e("test", "HERE12312424" + test)
        ServiceLoader
            .load(AndroidInstrumentation::class.java)
            .associateBy {
                it.javaClass
            }
            .toMutableMap()
    }

    fun start(context: Context) {
        listClasses()
        val attr: Map<String, String> = mapOf("test-setResourceAttributes" to "12345")
        val options: HoneycombOptions =
            HoneycombOptions.Builder(context) // Uncomment the line below to send to EU instance. Defaults to US.
                // .setApiEndpoint("https://api.eu1.honeycomb.io:443")
                .setApiKey("API_KEY_HERE")
                .setServiceName("OS-Android-SDK-Test")
                .setServiceVersion("0.0.1")
                .setDebug(true)
                .setResourceAttributes(attr)
//                .setLogRecordProcessor(LogRecordProcessor { context: Context?, logRecord: ReadWriteLogRecord? ->  // NOTE: This gets call when a crash happens
//                    // TODO: When moving logic to SDK, set app_id, package, org_id, subscription_id, and onesignal_id
//                    logRecord!!.setAttribute<String?>(
//                        AttributeKey.stringKey("test-onEmit"),
//                        "test-onEmit-value"
//                    )
//                })
                //.setLogRecordProcessor { context, logRecord -> {}}
                .build()
        Honeycomb.Companion.configure(context.applicationContext as Application, options)
        //(openTelRum.openTelemetry as OpenTelemetrySdk)
//        openTelRum.openTelemetry.logsBridge.
    }
}
