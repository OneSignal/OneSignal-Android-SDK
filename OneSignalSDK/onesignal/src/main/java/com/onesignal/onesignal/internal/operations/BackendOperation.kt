package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService

abstract class BackendOperation(protected val api: IApiService, name: String) : Operation(name)  {
}