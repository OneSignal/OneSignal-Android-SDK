package com.onesignal.common.services

import com.onesignal.debug.internal.logging.Logging

/**
 * A service provider gives access to the implementations of a service.
 */
class ServiceProvider(
    registrations: List<ServiceRegistration<*>>,
) : IServiceProvider {
    private val serviceMap = mutableMapOf<Class<*>, MutableList<ServiceRegistration<*>>>()

    init {
        // go through the registrations to create the service map for easier lookup post-build
        for (reg in registrations) {
            for (service in reg.services) {
                if (!serviceMap.containsKey(service)) {
                    serviceMap[service] = mutableListOf(reg)
                } else {
                    serviceMap[service]!!.add(reg)
                }
            }
        }
    }

    internal inline fun <reified T : Any> hasService(): Boolean {
        return hasService(T::class.java)
    }

    internal inline fun <reified T : Any> getAllServices(): List<T> {
        return getAllServices(T::class.java)
    }

    internal inline fun <reified T : Any> getService(): T {
        return getService(T::class.java)
    }

    internal inline fun <reified T : Any> getServiceOrNull(): T? {
        return getServiceOrNull(T::class.java)
    }

    override fun <T> hasService(c: Class<T>): Boolean {
        synchronized(serviceMap) {
            return serviceMap.containsKey(c)
        }
    }

    override fun <T> getAllServices(c: Class<T>): List<T> {
        synchronized(serviceMap) {
            val listOfServices: MutableList<T> = mutableListOf()

            if (serviceMap.containsKey(c)) {
                for (serviceReg in serviceMap!![c]!!) {
                    val service =
                        serviceReg.resolve(this) as T?
                            ?: throw Exception("Could not instantiate service: $serviceReg")

                    listOfServices.add(service)
                }
            }

            return listOfServices
        }
    }

    override fun <T> getService(c: Class<T>): T {
        val service = getServiceOrNull(c)
        if (service == null) {
            Logging.warn("Service not found: $c")
            throw Exception("Service $c could not be instantiated")
        }

        return service
    }

    override fun <T> getServiceOrNull(c: Class<T>): T? {
        synchronized(serviceMap) {
            // Logging.debug("${indent}Retrieving service $c")
            return serviceMap[c]?.last()?.resolve(this) as T?
        }
    }

    companion object {
        var indent: String = ""
    }
}
