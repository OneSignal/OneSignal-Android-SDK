/**
 * Code taken from https://github.com/kotest/kotest-extensions-robolectric with no changes.
 *
 * LICENSE: https://github.com/kotest/kotest-extensions-robolectric/blob/master/LICENSE
 */
package com.onesignal.testhelpers.extensions

import org.json.JSONObject
import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.internal.bytecode.InstrumentationConfiguration
import org.robolectric.pluginapi.config.ConfigurationStrategy
import org.robolectric.plugins.ConfigConfigurer
import java.lang.reflect.Method

class ContainedRobolectricRunner(
    private val config: Config?,
) : RobolectricTestRunner(PlaceholderTest::class.java, injector) {
    private val placeHolderMethod: FrameworkMethod = children[0]
    val sdkEnvironment =
        getSandbox(placeHolderMethod).also {
            configureSandbox(it, placeHolderMethod)
        }
    private val bootStrapMethod =
        sdkEnvironment.bootstrappedClass<Any>(testClass.javaClass)
            .getMethod(PlaceholderTest::bootStrapMethod.name)

    fun containedBefore() {
        Thread.currentThread().contextClassLoader = sdkEnvironment.robolectricClassLoader
        super.beforeTest(sdkEnvironment, placeHolderMethod, bootStrapMethod)
    }

    fun containedAfter() {
        super.afterTest(placeHolderMethod, bootStrapMethod)
        super.finallyAfterTest(placeHolderMethod)
        Thread.currentThread().contextClassLoader = ContainedRobolectricRunner::class.java.classLoader
    }

    override fun createClassLoaderConfig(method: FrameworkMethod?): InstrumentationConfiguration {
        val builder =
            InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
                .doNotAcquirePackage("io.kotest")

        if (keepExistingOrgJson()) builder.doNotAcquirePackage("org.json")

        return builder.build()
    }

    // Don't let Robolectric replace "org.json" if it's something other than
    // Google's non-working stub.
    // It's common for developers to include the "org.json:json" package
    // in their build.gradle and we shouldn't unexpectedly replace it.
    // One known issue is if a tests attempts to utilize a Kotlin extension
    // method that is defined in the "src" (AKA production) it won't be found
    // at runtime.
    private fun keepExistingOrgJson(): Boolean {
        return try {
            // This throws if we have Google's non-working stub
            JSONObject().put("test", "test")
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    override fun getConfig(method: Method?): Config {
        val defaultConfiguration =
            injector.getInstance(ConfigurationStrategy::class.java)
                .getConfig(testClass.javaClass, method)

        if (config != null) {
            val configConfigurer = injector.getInstance(ConfigConfigurer::class.java)
            return configConfigurer.merge(defaultConfiguration[Config::class.java], config)
        }

        return super.getConfig(method)
    }

    class PlaceholderTest {
        @org.junit.Test
        fun testPlaceholder() {
        }

        fun bootStrapMethod() {
        }
    }

    companion object {
        private val injector = defaultInjector().build()
    }
}
