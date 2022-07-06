package com.onesignal.onesignal.internal

interface IServiceProvider {

    /**
     * Retrieve the service that is a [T]
     */
    fun <T> getService(c: Class<T>): T

    /**
     * Retrieve the service, which may not exist.
     */
    fun <T> getServiceOrNull(c: Class<T>): T?
}