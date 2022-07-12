package com.onesignal.onesignal.core.internal.service

import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

/**
 * A registration of a service during the build phase.
 */
abstract class ServiceRegistration<T> {
    val services: MutableSet<Class<*>> = mutableSetOf()

    internal inline fun <reified TService: Any> provides(): ServiceRegistration<T> {
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
    fun <TService> provides(c: Class<TService>) : ServiceRegistration<T> {
        services.add(c)
        return this
    }

    abstract fun resolve(provider: IServiceProvider) : Any?
}

class ServiceRegistrationReflection<T>(
    private val clazz: Class<*>
) : ServiceRegistration<T>() {

    private var obj: T? = null

    override fun resolve(provider: IServiceProvider) : Any? {
        if(obj != null)
            return obj

        // use reflection to try to instantiate the thing
        for(constructor in clazz!!.constructors)
        {
            if(doesHaveAllParameters(constructor, provider)) {
                var paramList: MutableList<Any?> = mutableListOf()

                for(param in constructor.genericParameterTypes)
                {
                    if(param is ParameterizedType) {
                        val argType = param.actualTypeArguments.firstOrNull()
                        if(argType is WildcardType) {
                            val clazz = argType.upperBounds.first()
                            if(clazz is Class<*>) {
                                paramList.add(provider.getAllServices(clazz))
                            }
                            else paramList.add(null)
                        }
                        else if(argType is Class<*>) {
                            paramList.add(provider.getAllServices(argType))
                        }
                        else paramList.add(null)
                    }
                    else if(param is Class<*>) {
                        paramList.add(provider.getServiceOrNull(param) as T)
                    }
                    else paramList.add(null)
                }

                // The spread operator '*' will populate java varargs from the array
                obj = constructor.newInstance(*paramList.toTypedArray()) as T
                break
            }
        }

        return obj
    }

    private fun doesHaveAllParameters(constructor: Constructor<*>, provider: IServiceProvider) : Boolean {
        for(param in constructor.genericParameterTypes) {
            if(param is ParameterizedType) {
                val argType = param.actualTypeArguments.firstOrNull()

                if(argType is WildcardType) {
                    val clazz = argType.upperBounds.first()
                    if(clazz is Class<*>) {
                        if(!provider.hasService(clazz)) {
                            return false
                        }
                    }
                }
                else if(argType is Class<*>) {
                    if(!provider.hasService(argType))
                        return false
                }
                else return false
            }
            else if(param is Class<*>) {
                if (!provider.hasService(param)) {
                    return false
                }
            }
            else return false
        }

        return true
    }
}

class ServiceRegistrationSingleton<T>(
    private var obj: T
) : ServiceRegistration<T>() {

    override fun resolve(provider: IServiceProvider) : Any? = obj
}

class ServiceRegistrationLambda<T>(
    private val create: ((IServiceProvider) -> T),
) : ServiceRegistration<T>() {

    private var obj: T? = null

    override fun resolve(provider: IServiceProvider) : Any? {
        if(obj != null)
            return obj

        obj = create(provider)

        return obj
    }
}