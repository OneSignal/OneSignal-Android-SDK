package com.onesignal.core.internal.backend

/**
 * Implement this interface and provide an instance to [IParamsBackendService.addParamsBackendServiceObserver]
 * in order to receive control when the params has fetched on the current device.
 */
interface IParamsBackendServiceObserver {
    fun onParamsFetched(params: ParamsObject)
}
