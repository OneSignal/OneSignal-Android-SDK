package com.onesignal.common.services

import com.onesignal.debug.internal.logging.Logging
import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

/**
 * A registration of a service during the build phase.
 */
abstract class ServiceRegistration<T> {
    val services: MutableSet<Class<*>> = mutableSetOf()

    inline fun <reified TService : Any> provides(): ServiceRegistration<T> {
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

    abstract fun resolve(
        provider: IServiceProvider,
        vararg params: Any?,
    ): Any?
}

/**
 * A registration of a service that should use reflection to instantiate the
 * service.  This implementation takes as input [clazz] and using reflection
 * will determine the best constructor to use for creating a new instance of
 * that [clazz].
 *
 * The constructor can have parameters, as long as the parameter types are:
 *
 * 1. A service that can be resolved via [IServiceProvider.getService].
 * 2. A generic list of a service that can be resolved via [IServiceProvider.getAllServices].
 *
 * The instantiated service is treated as a singleton, instantiation will only
 * happen once.
 */
class ServiceRegistrationReflection<T>(
    private val clazz: Class<*>,
) : ServiceRegistration<T>() {
    private var obj: T? = null

    override fun resolve(
        provider: IServiceProvider,
        vararg params: Any?,
    ): Any? {
        if (obj != null) {
            Logging.debug("${ServiceProvider.indent}Already instantiated: $obj")
            return obj
        }

        // use reflection to try to instantiate the thing
        for (constructor in clazz.constructors) {
            if (doesHaveAllParameters(constructor, provider, params)) {
                Logging.debug("${ServiceProvider.indent}Found constructor: $constructor")
                var paramList: MutableList<Any?> = mutableListOf()

                for (param in constructor.genericParameterTypes) {
                    if (param is ParameterizedType) {
                        val argType = param.actualTypeArguments.firstOrNull()
                        if (argType is WildcardType) {
                            val clazz = argType.upperBounds.first()
                            if (clazz is Class<*>) {
                                paramList.add(provider.getAllServices(clazz))
                            } else {
                                paramList.add(null)
                            }
                        } else if (argType is Class<*>) {
                            paramList.add(provider.getAllServices(argType))
                        } else {
                            paramList.add(null)
                        }
                    } else if (param is Class<*>) {
                        // TODO: Is looping even what we want?
                        // Should we allow mixing inputParams and auto fill in ones?
                        val passedInParam =
                            params.firstOrNull {
                                param.isInstance(it)
                            }
                        if (passedInParam != null) {
                            paramList.add(passedInParam)
                        } else {
                            paramList.add(provider.getService(param) as T)
                        }
                    } else {
                        paramList.add(null)
                    }
                }

                // The spread operator '*' will populate java varargs from the array
                obj = constructor.newInstance(*paramList.toTypedArray()) as T
                break
            }
        }

        return obj
    }

    private fun doesHaveAllParameters(
        constructor: Constructor<*>,
        provider: IServiceProvider,
        vararg params: Any?,
    ): Boolean {
        for (param in constructor.genericParameterTypes) {
            if (param is ParameterizedType) {
                val argType = param.actualTypeArguments.firstOrNull()

                if (argType is WildcardType) {
                    val clazz = argType.upperBounds.first()
                    if (clazz is Class<*>) {
                        if (!provider.hasService(clazz)) {
                            // TODO: Figure out how to check type
//                            val result = ArrayList<Any?>()
//                            for (t in params) {
//                                result.add(t)
//                            }
//                            val paramClass = result[0]
//                            val javaClass = paramClass!!.javaClass
//                            if (clazz is paramClass::class) {
//                                Logging.debug("HERE")
//                            }
                            return true
//                            Logging.debug("Constructor $constructor could not find service: $clazz")
//                            return false
                        }
                    }
                } else if (argType is Class<*>) {
                    if (!provider.hasService(argType)) {
                        // TODO: Figure out how to check types in params
                        return true
//                        Logging.debug("Constructor $constructor could not find service: $argType")
//                        return false
                    }
                } else {
                    return false
                }
            } else if (param is Class<*>) {
                if (!provider.hasService(param)) {
                    // TODO: Figure out how to check types in params
                    return true
//                    Logging.debug("Constructor $constructor could not find service: $param")
//                    return false
                }
            } else {
                Logging.debug("Constructor $constructor could not identify param type: $param")
                return false
            }
        }

        return true
    }
}

/**
 * A registration of a service that is provided the instance of the service
 * to use.  This implementation takes as input [obj] and will return that
 * instance whenever it is requested.
 */
class ServiceRegistrationSingleton<T>(
    private var obj: T,
) : ServiceRegistration<T>() {
    override fun resolve(
        provider: IServiceProvider,
        vararg params: Any?,
    ): Any? = obj
}

/**
 * A registration of a service that should will call a lambda function [create]
 * when the service is to be instantiated.
 *
 * The instantiated service is treated as a singleton, instantiation will only
 * happen once.
 */
class ServiceRegistrationLambda<T>(
    private val create: ((IServiceProvider) -> T),
) : ServiceRegistration<T>() {
    private var obj: T? = null

    override fun resolve(
        provider: IServiceProvider,
        vararg params: Any?,
    ): Any? {
        if (obj != null) {
            return obj
        }

        obj = create(provider)

        return obj
    }
}
