package com.onesignal.core.internal.service

class ServiceBuilder : IServiceBuilder {
    private val _registrations: MutableList<ServiceRegistration<*>> = mutableListOf()

    internal inline fun <reified T : Any> register(): ServiceRegistration<T> {
        return register(T::class.java)
    }

    override fun <T> register(c: Class<T>): ServiceRegistration<T> {
        val registration = ServiceRegistrationReflection<T>(c)
        _registrations.add(registration)
        return registration
    }

    override fun <T> register(create: (IServiceProvider) -> T): ServiceRegistration<T> {
        val registration = ServiceRegistrationLambda<T>(create)
        _registrations.add(registration)
        return registration
    }

    override fun <T> register(obj: T): ServiceRegistration<T> {
        val registration = ServiceRegistrationSingleton(obj)
        _registrations.add(registration)
        return registration
    }

    override fun build(): ServiceProvider {
        return ServiceProvider(_registrations)
    }
}
