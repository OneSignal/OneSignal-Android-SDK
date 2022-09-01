package com.onesignal.onesignal.core.internal.backend

interface IParamsBackendService {
    suspend fun fetchAndSaveRemoteParams(appId: String, subscriptionId: String?)
}
