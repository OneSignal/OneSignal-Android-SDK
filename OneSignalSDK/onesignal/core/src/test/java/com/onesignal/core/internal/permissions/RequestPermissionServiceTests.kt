package com.onesignal.core.internal.permissions

import android.app.Activity
import android.content.ActivityNotFoundException
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

@RobolectricTest
class RequestPermissionServiceTests : FunSpec({
    beforeTest {
        Logging.logLevel = LogLevel.NONE
    }

    test("startPrompt launches PermissionsActivity when it is resolvable") {
        val env = Env()

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.hostActivity.startActivity(any()) }
        verify(exactly = 0) { env.callback.onReject(any()) }
        verify(exactly = 0) { env.app.removeActivityLifecycleHandler(any()) }
    }

    test("startPrompt rejects and unsubscribes when startActivity throws ActivityNotFoundException") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.hostActivity.startActivity(any()) }
        verify(exactly = 1) { env.callback.onReject(true) }
        verify(exactly = 1) { env.app.removeActivityLifecycleHandler(env.handler) }
    }

    test("startPrompt removes the handler when PermissionsActivity is already current") {
        val env = Env()
        val permissionsActivity = mockk<PermissionsActivity>(relaxed = true)

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(permissionsActivity)

        verify(exactly = 0) { permissionsActivity.startActivity(any()) }
        verify(exactly = 0) { env.callback.onReject(any()) }
        verify(exactly = 1) { env.app.removeActivityLifecycleHandler(env.handler) }
    }

    test("startPrompt is a no-op while waiting") {
        val env = Env()
        env.service.waiting = true

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)

        verify(exactly = 0) { env.app.addActivityLifecycleHandler(any()) }
    }
})

private const val PERMISSION_TYPE = "NOTIFICATION"
private const val ANDROID_PERMISSION = "android.permission.POST_NOTIFICATIONS"

private class Env {
    interface Callback : IRequestPermissionService.PermissionCallback

    val callback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)
    val app = mockk<IApplicationService>(relaxed = true)
    val hostActivity = mockk<Activity>(relaxed = true)
    private val handlerSlot = slot<IActivityLifecycleHandler>()
    val service = RequestPermissionService(app)

    val handler: IActivityLifecycleHandler
        get() = handlerSlot.captured

    init {
        every { app.addActivityLifecycleHandler(capture(handlerSlot)) } just runs
        service.registerAsCallback(PERMISSION_TYPE, callback)
    }
}
