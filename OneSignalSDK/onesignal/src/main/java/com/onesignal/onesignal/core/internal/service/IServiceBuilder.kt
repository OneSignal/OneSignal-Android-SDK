package com.onesignal.onesignal.core.internal.service

/**
 * A service builder allows for the registering of implementations and the associating of the
 * services that each implementation provides.  Ultimately, the registered implementations
 * and provided services will be built, and can be used through the [IServiceProvider].
 */
interface IServiceBuilder {
    /**
     * Register [T] as an implementation managed by the service infrastructure.  It is expected
     * a service will be provided by this implementation via a subsequent call to
     * [IServiceRegistration].
     *
     * The implementation will be instantiated via reflection when required.  This requires all
     * constructor parameters on this implementation to *also* have an implementation registered and relevant service provided.
     *
     * @return The service registration that should be used to indicate what services this
     * implementation provides.
     */
    fun <T> register(c: Class<T>): ServiceRegistration<T>

    /**
     * Register [T] as an implementation managed by the service infrastructure.  It is expected
     * a service will be provided by this implementation via a subsequent call to
     * [IServiceRegistration].
     *
     * The first time a service provided by this implementation is required, this [create] lambda
     * will be executed to instantiate the instance.
     *
     * @param create The lambda that will be called when this implementation must be instantiated.
     *
     * @return The service registration that should be used to indicate what services this
     * implementation provides.
     */
    fun <T> register(create: (IServiceProvider) -> T): ServiceRegistration<T>

    /**
     * Register [T] as an implementation managed by the service infrastructure.  It is expected
     * a service will be provided by this implementation via a subsequent call to
     * [IServiceRegistration].
     *
     * The instance of the implementation is provided at registration time.
     *
     * @param obj The instance of the implementation that will be used.
     *
     * @return The service registration that should be used to indicate what services this
     * implementation provides.
     */
    fun <T> register(obj: T): ServiceRegistration<T>

    /**
     * Build the registered implementations, mapping the services they provide, and return the
     * [IServiceProvider] that will be able to retrieve instances of those provided services.
     *
     * @return The service provider that can be used to retrieve the provided services.
     */
    fun build(): ServiceProvider
}
