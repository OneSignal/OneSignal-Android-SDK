package com.onesignal.common.services

/**
 * A service provider gives access to the implementations of a service.
 */
interface IServiceProvider {
    /**
     * Determine if the service provide has the provided service.
     *
     * @return true if the service has been defined, false otherwise.
     */
    fun <T> hasService(c: Class<T>): Boolean

    /**
     * Retrieve the service that is a [T]. If multiple services exist, the last one registered
     * will be returned.  If no service exists, an exception will be thrown.
     *
     * @param c The class type of the service that is to be retrieved.
     *
     * @return The instance of the implementation of that service.
     */
    fun <T> getService(c: Class<T>): T

    suspend fun <T> getSuspendService(c: Class<T>): T

    /**
     * Retrieve the service that is a [T].  If multiple services
     * exist, the last one registered will be returned. If no service exists, null will
     * be returned.
     *
     * @param c The class type of the service that is to be retrieved.
     *
     * @return The instance of the implementation of that service, or null if no service exists.
     */
    fun <T> getServiceOrNull(c: Class<T>): T?

    /**
     * Retrieve the list of services that are a [T].  If no service exists, an empty
     * list will be returned.
     *
     * @param c The class type of the services that are to be retrieved.
     *
     * @return A list of the instances of the implementation of that service.
     */
    fun <T> getAllServices(c: Class<T>): List<T>
}
