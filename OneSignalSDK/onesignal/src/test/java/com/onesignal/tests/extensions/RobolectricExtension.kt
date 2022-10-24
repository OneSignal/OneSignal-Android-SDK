/**
 * Code taken from https://github.com/kotest/kotest-extensions-robolectric with a
 * fix in the intercept method.
 *
 * LICENSE: https://github.com/kotest/kotest-extensions-robolectric/blob/master/LICENSE
 */
package com.onesignal.tests.extensions

import android.app.Application
import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.extensions.SpecExtension
import io.kotest.core.spec.AutoScan
import io.kotest.core.spec.Spec
import org.robolectric.annotation.Config
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@AutoScan
internal class RobolectricExtension : ConstructorExtension, SpecExtension {
    private fun Class<*>.getParentClass(): List<Class<*>> {
        if (superclass == null) return listOf()
        return listOf(superclass) + superclass.getParentClass()
    }

    private fun KClass<*>.getConfig(): Config {
        val annotations = listOf(this.java).plus(this.java.getParentClass())
            .mapNotNull { it.kotlin.findAnnotation<RobolectricTest>() }
            .asSequence()

        val application: KClass<out Application>? = annotations
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

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) {
        // FIXED: Updated code based on https://github.com/kotest/kotest/issues/2717
        val hasRobolectricAnnotation = spec::class.annotations.any { annotation ->
            annotation.annotationClass.qualifiedName == RobolectricTest::class.qualifiedName
        }

        if (!hasRobolectricAnnotation) {
            return execute(spec)
        }

        val containedRobolectricRunner = ContainedRobolectricRunner(spec::class.getConfig())

        beforeSpec(containedRobolectricRunner)
        execute(spec)
        afterSpec(containedRobolectricRunner)
    }

    private fun beforeSpec(containedRobolectricRunner: ContainedRobolectricRunner) {
        Thread.currentThread().contextClassLoader =
            containedRobolectricRunner.sdkEnvironment.robolectricClassLoader
        containedRobolectricRunner.containedBefore()
    }

    private fun afterSpec(containedRobolectricRunner: ContainedRobolectricRunner) {
        containedRobolectricRunner.containedAfter()
        Thread.currentThread().contextClassLoader = RobolectricExtension::class.java.classLoader
    }
}

internal class KotestDefaultApplication : Application()

annotation class RobolectricTest(
    val application: KClass<out Application> = KotestDefaultApplication::class,
    val sdk: Int = -1
)
