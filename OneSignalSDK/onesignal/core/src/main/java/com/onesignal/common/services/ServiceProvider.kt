package com.onesignal.common.services

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * A service provider gives access to the implementations of a service.
 */
class ServiceProvider(
    registrations: List<ServiceRegistration<*>>,
) : IServiceProvider {
    private val serviceMap = mutableMapOf<Class<*>, MutableList<ServiceRegistration<*>>>()

    // Register init blocks per service (sync or suspend)
    private val initBlocks = ConcurrentHashMap<Class<*>, suspend (Any, IServiceProvider) -> Unit>()
    // Per-service waiter completed when init finishes
    private val initWaiters = ConcurrentHashMap<Class<*>, CompletableDeferred<Unit>>()

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

    /** Register a synchronous first-access initializer. */
    fun <T : Any> onFirstAccess(c: Class<T>, block: (T, IServiceProvider) -> Unit) {
        initBlocks[c] = { service, serviceProvider -> block(c.cast(service), serviceProvider) }
    }

    inline fun <reified T : Any> onFirstAccess(noinline block: (T, IServiceProvider) -> Unit) =
        onFirstAccess(T::class.java, block)

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
                    runInitAndWait(c, service)
                    listOfServices.add(service)
                }
            }

            return listOfServices
        }
    }

    override fun <T> getService(c: Class<T>): T {
        val service = getServiceOrNull(c)
        if (service == null) {
            throw Exception("Service $c could not be instantiated")
        }

        return service
    }

    override fun <T> getServiceOrNull(c: Class<T>): T? {
        synchronized(serviceMap) {
            val service = serviceMap[c]?.last()?.resolve(this) as T?
            if (service != null) {
                runInitAndWait(c, service)
            }
            return service
        }
    }

    private fun <T> runInitAndWait(c: Class<T>, service: T) {
        val newWaiter = CompletableDeferred<Unit>()
        val existing = initWaiters.putIfAbsent(c, newWaiter)
        val waiter = existing ?: newWaiter

        if (existing == null) {
            // We are the first accessor → run the initializer and complete the waiter.
            val block = initBlocks[c]
            if (block == null) {
                waiter.complete(Unit)
                return
            }
            try {
                // Run synchronously
                runBlocking {
                    Logging.debug("Waiting on class $c and service ${service}")
                    block(service as Any, this@ServiceProvider)
                    // waiter stays completed so future calls fast-path.
                    initBlocks.remove(c)
                }
            } catch (t: Throwable) {
                waiter.completeExceptionally(t)
                throw t
            }
            // Keep waiter completed in the map so future callers don’t redo work.
        } else {
            // Someone else is initializing → wait until it completes.
            // WARNING: If you're on the main thread, this will block; avoid calling from main if init is heavy.
            runBlocking { waiter.await() }
        }
    }

    companion object {
        var indent: String = ""
    }
}
