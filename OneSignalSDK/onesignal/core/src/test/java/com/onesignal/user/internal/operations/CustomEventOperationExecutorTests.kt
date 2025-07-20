package com.onesignal.user.internal.operations

import android.content.Context
import android.os.Build
import com.onesignal.common.OneSignalUtils
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.Operation
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.customEvents.ICustomEventBackendService
import com.onesignal.user.internal.customEvents.impl.CustomEvent
import com.onesignal.user.internal.operations.impl.executors.CustomEventOperationExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class CustomEventOperationExecutorTests : FunSpec({
    test("execution of track event operation") {
        // Given
        val mockCustomEventBackendService = mockk<ICustomEventBackendService>()
        coEvery { mockCustomEventBackendService.sendCustomEvent(any(), any(), any(), any(), any(), any()) } returns ExecutionResponse(ExecutionResult.SUCCESS)

        val mockApplicationService = MockHelper.applicationService()
        val mockContext = mockk<Context>(relaxed = true)
        every { mockApplicationService.appContext } returns mockContext
        val mockDeviceService = MockHelper.deviceService()
        every { mockDeviceService.deviceType } returns IDeviceService.DeviceType.Android

        val deviceMode = Build.MODEL
        val deviceOS = Build.VERSION.RELEASE

        val customEvent =
            CustomEvent(
                "event-name",
                mapOf("key" to "value"),
            )
        val customEventOperationExecutor =
            CustomEventOperationExecutor(mockCustomEventBackendService, mockApplicationService, mockDeviceService)
        val operations = listOf<Operation>(TrackEventOperation("appId", "onesignalId", null, 1, customEvent))

        // When
        val response = customEventOperationExecutor.execute(operations)

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify(exactly = 1) {
            mockCustomEventBackendService.sendCustomEvent(
                "appId",
                "onesignalId",
                null,
                1,
                customEvent,
                withArg {
                    it.sdk shouldBe OneSignalUtils.SDK_VERSION
                    it.appVersion?.shouldBeEqual("0")
                    it.type?.shouldBeEqual(("AndroidPush"))
                    it.deviceType?.shouldBeEqual(("Android"))
                    it.deviceModel shouldBe deviceMode
                    it.deviceOS shouldBe deviceOS
                },
            )
        }
    }
})
