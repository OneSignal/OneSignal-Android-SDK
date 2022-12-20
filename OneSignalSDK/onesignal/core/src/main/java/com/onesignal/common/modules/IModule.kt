package com.onesignal.common.modules

import com.onesignal.common.services.ServiceBuilder

/**
 * A module represents a container of functionality within the SDK, a module is responsible
 * for registering the services and behaviors required for that functionality to function
 * property.
 */
interface IModule {

    /**
     * Register all services and behaviors for this module.  This is called during the initialization
     * of the OneSignal SDK.
     *
     * @param builder The [ServiceBuilder] that is used to register/provide services.
     */
    fun register(builder: ServiceBuilder)
}
