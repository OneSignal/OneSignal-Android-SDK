package com.onesignal.core.internal.backend

internal interface IParamsBackendService {
    suspend fun fetchAndSaveRemoteParams(appId: String, subscriptionId: String?)
}
