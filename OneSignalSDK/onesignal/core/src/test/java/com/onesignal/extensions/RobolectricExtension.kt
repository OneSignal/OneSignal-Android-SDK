/**
 * Code taken from https://github.com/kotest/kotest-extensions-robolectric with a
 * fix in the intercept method.
 *
 * LICENSE: https://github.com/kotest/kotest-extensions-robolectric/blob/master/LICENSE
 */
package com.onesignal.extensions

import android.app.Application
import io.kotest.common.runBlocking
import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.listeners.FinalizeSpecListener
import io.kotest.core.spec.AutoScan
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * We override TestCaseExtension to configure the Robolectric environment because TestCase intercept
 * occurs on the same thread the test is run.  This is unfortunate because it is run for every test,
 * rather than every spec. But the SpecExtension intercept is run on a different thread.
 */
@AutoScan
internal class RobolectricExtension : ConstructorExtension, TestCaseExtension,
    FinalizeSpecListener {
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
        instantiateConfig = clazz.getConfig()

        containedRobolectricRunner = ContainedRobolectricRunner(instantiateConfig)

//      containedRobolectricRunner!!.containedBefore()

        // NOTE: Must be the passed in spec, otherwise kotest will skip
        specClass = clazz
        return containedRobolectricRunner!!
            .sdkEnvironment.bootstrappedClass<Spec>(specClass!!.java).newInstance()
    }

    override suspend fun finalizeSpec(
        kclass: KClass<out Spec>,
        results: Map<TestCase, TestResult>,
    ) {
        println("finalizeSpec")
        containedRobolectricRunner!!.containedAfter()
    }

    companion object {
        var instantiateConfig: Config? = null
        var containedRobolectricRunner: ContainedRobolectricRunner? = null
        var specClass: KClass<*>? = null
    }

    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult,
    ): TestResult {
        // FIXED: Updated code based on https://github.com/kotest/kotest/issues/2717
        val hasRobolectricAnnotation =
            testCase.spec::class.annotations.any { annotation ->
                annotation.annotationClass.qualifiedName == RobolectricTest::class.qualifiedName
            }

        if (!hasRobolectricAnnotation) {
            return execute(testCase)
        }

        val containedRobolectricRunner = ContainedRobolectricRunner(instantiateConfig)
        val blockingQueue = LinkedBlockingQueue<TestResult>()
        containedRobolectricRunner.sdkEnvironment.runOnMainThread {
            val blockingQueueInner = LinkedBlockingQueue<TestResult>()
            containedRobolectricRunner.containedBefore()
            var result: TestResult? = null
            runBlocking {
                result = execute(testCase)
            }
            containedRobolectricRunner.containedAfter()
            blockingQueue.put(result)
        }
        return blockingQueue.take()
//        return result
    }
}

internal class KotestDefaultApplication : Application()

annotation class RobolectricTest(
    val application: KClass<out Application> = KotestDefaultApplication::class,
    val sdk: Int = -1,
)
