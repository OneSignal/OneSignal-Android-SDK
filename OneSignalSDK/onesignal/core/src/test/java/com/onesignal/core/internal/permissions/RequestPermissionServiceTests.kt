package com.onesignal.core.internal.permissions

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.IHostPermissionPrompt
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.impl.RequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

@RobolectricTest
class RequestPermissionServiceTests : FunSpec({
    beforeTest {
        Logging.logLevel = LogLevel.NONE
        OneSignalWrapper.hostPermissionPrompt = null
    }

    afterTest {
        OneSignalWrapper.hostPermissionPrompt = null
    }

    test("startPrompt launches PermissionsActivity when it is resolvable") {
        val env = Env()

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.hostActivity.startActivity(any()) }
        verify(exactly = 0) { env.callback.onReject(any()) }
        verify(exactly = 0) { env.app.removeActivityLifecycleHandler(any()) }
    }

    test("startPrompt rejects and unsubscribes when no host can prompt") {
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

    test("a granted wrapper host prompt accepts instead of crashing") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        val prompt = RecordingPrompt(granted = true)
        OneSignalWrapper.hostPermissionPrompt = prompt

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        prompt.requested shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onAccept() }
        verify(exactly = 0) { env.callback.onReject(any()) }
    }

    test("a denied wrapper host prompt still drives onReject") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        val prompt = RecordingPrompt(granted = false)
        OneSignalWrapper.hostPermissionPrompt = prompt

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        prompt.requested shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onReject(any()) }
        verify(exactly = 0) { env.callback.onAccept() }
    }

    test("a wrapper host prompt with no activity completes the prompt as denied") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        OneSignalWrapper.hostPermissionPrompt =
            object : IHostPermissionPrompt {
                override fun request(
                    androidPermission: String,
                    callback: IHostPermissionPrompt.Callback,
                ): Boolean = false
            }

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.callback.onReject(true) }
        verify(exactly = 0) { env.callback.onAccept() }
    }

    test("a wrapper host prompt that throws completes the prompt as denied") {
        val env = Env()
        every { env.hostActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        OneSignalWrapper.hostPermissionPrompt =
            object : IHostPermissionPrompt {
                override fun request(
                    androidPermission: String,
                    callback: IHostPermissionPrompt.Callback,
                ): Boolean = throw IllegalStateException("boom")
            }

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(env.hostActivity)

        verify(exactly = 1) { env.callback.onReject(true) }
    }

    test("a ComponentActivity host prompts through its own result registry") {
        val env = Env()
        val componentActivity = mockk<ComponentActivity>(relaxed = true)
        val registry = TestRegistry(granted = true)
        every { componentActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        every { componentActivity.activityResultRegistry } returns registry

        env.service.startPrompt(true, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(componentActivity)

        registry.launchedInput shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onAccept() }
        verify(exactly = 0) { env.callback.onReject(any()) }
    }

    test("a denied ComponentActivity host prompt drives onReject") {
        val env = Env()
        val componentActivity = mockk<ComponentActivity>(relaxed = true)
        val registry = TestRegistry(granted = false)
        every { componentActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        every { componentActivity.activityResultRegistry } returns registry

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handler.onActivityAvailable(componentActivity)

        registry.launchedInput shouldBe ANDROID_PERMISSION
        verify(exactly = 1) { env.callback.onReject(any()) }
        verify(exactly = 0) { env.callback.onAccept() }
    }

    test("two host prompts in flight each deliver their own result") {
        val env = Env()
        val locationCallback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)
        env.service.registerAsCallback(LOCATION_PERMISSION_TYPE, locationCallback)

        val componentActivity = mockk<ComponentActivity>(relaxed = true)
        val registry = DeferringRegistry()
        every { componentActivity.startActivity(any()) } throws ActivityNotFoundException("missing")
        every { componentActivity.activityResultRegistry } returns registry

        env.service.startPrompt(false, PERMISSION_TYPE, ANDROID_PERMISSION, Env.Callback::class.java)
        env.handlers[0].onActivityAvailable(componentActivity)
        env.service.startPrompt(false, LOCATION_PERMISSION_TYPE, LOCATION_PERMISSION, Env.Callback::class.java)
        env.handlers[1].onActivityAvailable(componentActivity)

        registry.dispatchAll(granted = true)

        verify(exactly = 1) { env.callback.onAccept() }
        verify(exactly = 1) { locationCallback.onAccept() }
    }
})

private const val PERMISSION_TYPE = "NOTIFICATION"
private const val ANDROID_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val LOCATION_PERMISSION_TYPE = "LOCATION"
private const val LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"

/** Dispatches the contract result inline so the prompt path stays synchronous under test. */
private class TestRegistry(private val granted: Boolean) : ActivityResultRegistry() {
    var launchedInput: Any? = null

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launchedInput = input

        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, granted as O)
    }
}

/** Holds results so two prompts can be in flight at once. */
private class DeferringRegistry : ActivityResultRegistry() {
    private val pending = mutableListOf<Int>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        pending.add(requestCode)
    }

    fun dispatchAll(granted: Boolean) {
        pending.forEach { dispatchResult(it, granted) }
        pending.clear()
    }
}

private class RecordingPrompt(private val granted: Boolean) : IHostPermissionPrompt {
    var requested: String? = null

    override fun request(
        androidPermission: String,
        callback: IHostPermissionPrompt.Callback,
    ): Boolean {
        requested = androidPermission
        callback.onResult(granted)
        return true
    }
}

private class Env {
    interface Callback : IRequestPermissionService.PermissionCallback

    val callback = mockk<IRequestPermissionService.PermissionCallback>(relaxed = true)
    val app = mockk<IApplicationService>(relaxed = true)
    val preferences = mockk<IPreferencesService>(relaxed = true)
    val hostActivity = mockk<Activity>(relaxed = true)
    val handlers = mutableListOf<IActivityLifecycleHandler>()
    val service = RequestPermissionService(app, preferences)

    val handler: IActivityLifecycleHandler
        get() = handlers.last()

    init {
        every { app.addActivityLifecycleHandler(capture(handlers)) } just runs
        service.registerAsCallback(PERMISSION_TYPE, callback)
    }
}
