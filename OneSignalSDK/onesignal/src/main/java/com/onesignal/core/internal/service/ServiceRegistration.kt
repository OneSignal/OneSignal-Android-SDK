package com.onesignal.core.internal.service

import com.onesignal.core.internal.logging.Logging
import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

/**
 * A registration of a service during the build phase.
 */
internal abstract class ServiceRegistration<T> {
    val services: MutableSet<Class<*>> = mutableSetOf()

    internal inline fun <reified TService : Any> provides(): ServiceRegistration<T> {
        return provides(TService::class.java)
    }

    /**
     * Indicate this registration wants to provide the provided class as
     * a service.  The expectation is the provided class is implemented
     * by the registered implementation.
     *
     * @param c The class that is to be provided as a service.
     *
     * @return The service registration for chaining of calls.
     */
    fun <TService> provides(c: Class<TService>): ServiceRegistration<T> {
        services.add(c)
        return this
    }

    abstract fun resolve(provider: IServiceProvider): Any?
}

internal class ServiceRegistrationReflection<T>(
    private val clazz: Class<*>
) : ServiceRegistration<T>() {

    private var obj: T? = null

    override fun resolve(provider: IServiceProvider): Any? {
        if (obj != null) {
            Logging.info("${ServiceProvider.indent}Already instantiated: $obj")
            return obj
        }

        // use reflection to try to instantiate the thing
        for (constructor in clazz!!.constructors) {
            if (doesHaveAllParameters(constructor, provider)) {
                Logging.info("${ServiceProvider.indent}Found constructor: $constructor")
                var paramList: MutableList<Any?> = mutableListOf()

                for (param in constructor.genericParameterTypes) {
                    if (param is ParameterizedType) {
                        val argType = param.actualTypeArguments.firstOrNull()
                        if (argType is WildcardType) {
                            val clazz = argType.upperBounds.first()
                            if (clazz is Class<*>) {
                                paramList.add(provider.getAllServices(clazz))
                            } else paramList.add(null)
                        } else if (argType is Class<*>) {
                            paramList.add(provider.getAllServices(argType))
                        } else paramList.add(null)
                    } else if (param is Class<*>) {
                        paramList.add(provider.getService(param) as T)
                    } else paramList.add(null)
                }

                // The spread operator '*' will populate java varargs from the array
                obj = constructor.newInstance(*paramList.toTypedArray()) as T
                break
            }
        }

        return obj
    }

    private fun doesHaveAllParameters(constructor: Constructor<*>, provider: IServiceProvider): Boolean {
        for (param in constructor.genericParameterTypes) {
            if (param is ParameterizedType) {
                val argType = param.actualTypeArguments.firstOrNull()

                if (argType is WildcardType) {
                    val clazz = argType.upperBounds.first()
                    if (clazz is Class<*>) {
                        if (!provider.hasService(clazz)) {
                            Logging.debug("Constructor $constructor could not find service: $clazz")
                            return false
                        }
                    }
                } else if (argType is Class<*>) {
                    if (!provider.hasService(argType)) {
                        Logging.debug("Constructor $constructor could not find service: $argType")
                        return false
                    }
                } else return false
            } else if (param is Class<*>) {
                if (!provider.hasService(param)) {
                    Logging.debug("Constructor $constructor could not find service: $param")
                    return false
                }
            } else {
                Logging.debug("Constructor $constructor could not identify param type: $param")
                return false
            }
        }

        return true
    }
}

internal class ServiceRegistrationSingleton<T>(
    private var obj: T
) : ServiceRegistration<T>() {

    override fun resolve(provider: IServiceProvider): Any? = obj
}

internal class ServiceRegistrationLambda<T>(
    private val create: ((IServiceProvider) -> T),
) : ServiceRegistration<T>() {

    private var obj: T? = null

    override fun resolve(provider: IServiceProvider): Any? {
        if (obj != null)
            return obj

        obj = create(provider)

        return obj
    }
}
