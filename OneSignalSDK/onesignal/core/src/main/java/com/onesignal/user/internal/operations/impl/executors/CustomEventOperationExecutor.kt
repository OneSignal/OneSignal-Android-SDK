package com.onesignal.user.internal.operations.impl.executors

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.NetworkUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.customEvents.ICustomEventBackendService
import com.onesignal.user.internal.customEvents.impl.CustomEventMetadata
import com.onesignal.user.internal.operations.TrackCustomEventOperation

internal class CustomEventOperationExecutor(
    private val customEventBackendService: ICustomEventBackendService,
    private val applicationService: IApplicationService,
    private val deviceService: IDeviceService,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(CUSTOM_EVENT)

    private val eventMetadataJson: CustomEventMetadata by lazy {
        CustomEventMetadata(
            deviceType = deviceService.deviceType.name,
            sdk = OneSignalUtils.sdkVersion,
            appVersion = AndroidUtils.getAppVersion(applicationService.appContext),
            type = "AndroidPush",
            deviceModel = Build.MODEL,
            deviceOS = Build.VERSION.RELEASE,
        )
    }

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        val operation = operations.first()

        try {
            when (operation) {
                is TrackCustomEventOperation -> {
                    customEventBackendService.sendCustomEvent(
                        operation.appId,
                        operation.onesignalId,
                        operation.externalId,
                        operation.timeStamp,
                        operation.eventName,
                        operation.eventProperties,
                        eventMetadataJson,
                    )
                }
            }
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    companion object {
        const val CUSTOM_EVENT = "custom-event"
    }
}
