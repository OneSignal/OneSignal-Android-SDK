package com.onesignal.benchmark

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onesignal.OneSignal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will
 * output the result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class ExampleBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmark_OneSignal_initWithContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appId = "77e32082-ea27-42e3-a898-c72e141824ef"
        benchmarkRule.measureRepeated {
            // measuring OneSignal initialization
            OneSignal.initWithContext(context, appId)
        }
    }   
}