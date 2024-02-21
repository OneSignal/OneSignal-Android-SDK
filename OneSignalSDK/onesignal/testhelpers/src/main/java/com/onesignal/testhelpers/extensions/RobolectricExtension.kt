/**
 * Code taken from https://github.com/kotest/kotest-extensions-robolectric with a
 * fix in the intercept method.
 *
 * LICENSE: https://github.com/kotest/kotest-extensions-robolectric/blob/master/LICENSE
 */
package com.onesignal.testhelpers.extensions

import android.app.Application
import io.kotest.common.runBlocking
import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.AutoScan
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.robolectric.annotation.Config
import java.util.concurrent.Callable
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.time.Duration

/**
 * We override TestCaseExtension to configure the Robolectric environment because TestCase intercept
 * occurs on the same thread the test is run.  This is unfortunate because it is run for every test,
 * rather than every spec. But the SpecExtension intercept is run on a different thread.
 */
@AutoScan
class RobolectricExtension : ConstructorExtension, TestCaseExtension {
    private fun Class<*>.getParentClass(): List<Class<*>> {
        if (superclass == null) return listOf()
        return listOf(superclass) + superclass.getParentClass()
    }

    private fun KClass<*>.getConfig(): Config {
        val annotations =
            listOf(this.java).plus(this.java.getParentClass())
                .mapNotNull { it.kotlin.findAnnotation<RobolectricTest>() }
                .asSequence()

        val application: KClass<out Application>? =
            annotations
                .firstOrNull { it.application != KotestDefaultApplication::class }?.application
        val sdk: Int? = annotations.firstOrNull { it.sdk != -1 }?.takeUnless { it.sdk == -1 }?.sdk

        return Config.Builder()
            .also { builder ->
                if (application != null) {
                    builder.setApplication(application.java)
                }

                if (sdk != null) {
                    builder.setSdk(sdk)
                }
            }.build()
    }

    override fun <T : Spec> instantiate(clazz: KClass<T>): Spec? {
        clazz.findAnnotation<RobolectricTest>() ?: return null

        return ContainedRobolectricRunner(clazz.getConfig())
            .sdkEnvironment.bootstrappedClass<Spec>(clazz.java).newInstance()
    }

    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult {
        return try {
            runTest(testCase, execute)
        } catch (t: Throwable) {
            // Without this the whole test class will be silently be skipped
            // if something throws
            TestResult.Error(Duration.ZERO, t)
        }
    }

    private suspend fun runTest(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult {
        // FIXED: Updated code based on https://github.com/kotest/kotest/issues/2717
        val hasRobolectricAnnotation =
            testCase.spec::class.annotations.any { annotation ->
                annotation.annotationClass.qualifiedName == RobolectricTest::class.qualifiedName
            }

        return if (hasRobolectricAnnotation) {
            runTestRobolectric(testCase, execute)
        } else {
            execute(testCase)
        }
    }

    private suspend fun runTestRobolectric(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult {
        val containedRobolectricRunner =
            ContainedRobolectricRunner(testCase.spec::class.getConfig())
        // sdkEnvironment.runOnMainThread is important to ensure Robolectric's
        // looper state doesn't carry over to the next test class.
        return containedRobolectricRunner.sdkEnvironment.runOnMainThread(
            Callable {
                containedRobolectricRunner.containedBefore()
                val result = runBlocking { execute(testCase) }
                containedRobolectricRunner.containedAfter()
                result
            },
        )
    }
}

internal class KotestDefaultApplication : Application()

annotation class RobolectricTest(
    val application: KClass<out Application> = KotestDefaultApplication::class,
    val sdk: Int = -1,
)
