/**
 * Code taken from https://github.com/kotest/kotest-extensions-robolectric with no changes.
 *
 * LICENSE: https://github.com/kotest/kotest-extensions-robolectric/blob/master/LICENSE
 */
package com.onesignal.notifications.extensions

import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.internal.bytecode.InstrumentationConfiguration
import org.robolectric.pluginapi.config.ConfigurationStrategy
import org.robolectric.plugins.ConfigConfigurer
import java.lang.reflect.Method

internal class ContainedRobolectricRunner(
    private val config: Config?,
) : RobolectricTestRunner(PlaceholderTest::class.java, injector) {
    private val placeHolderMethod: FrameworkMethod = children[0]
    val sdkEnvironment = getSandbox(placeHolderMethod).also {
        configureSandbox(it, placeHolderMethod)
    }
    private val bootStrapMethod = sdkEnvironment.bootstrappedClass<Any>(testClass.javaClass)
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
        return InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
            .doNotAcquirePackage("io.kotest")
            .build()
    }

    override fun getConfig(method: Method?): Config {
        val defaultConfiguration = injector.getInstance(ConfigurationStrategy::class.java)
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
